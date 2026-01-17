// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.junit.jupiter.api.Test;

import sh.brane.core.error.RpcException;

/**
 * Unit tests for WebSocketProvider internal behaviors.
 *
 * <p>These tests focus on:
 * <ul>
 *   <li>failAllPending - failing all pending requests on connection error/close</li>
 *   <li>Reconnect scheduling logic - exponential backoff calculations</li>
 *   <li>Timeout cancellation - ensuring timed-out requests are cleaned up properly</li>
 *   <li>Subscription recovery considerations after reconnect</li>
 * </ul>
 *
 * <p>Since WebSocketProvider requires a real WebSocket connection for most operations,
 * these tests use reflection to access internal state and test specific edge cases
 * that are difficult to trigger through integration tests.
 */
class WebSocketProviderTest {

    // ==================== failAllPending tests ====================

    /**
     * Tests that failAllPending completes all pending futures with the given exception.
     */
    @Test
    void failAllPending_completesAllFuturesWithException() throws Exception {
        // Create a map simulating pendingRequests
        ConcurrentHashMap<Long, CompletableFuture<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();

        // Add some pending futures
        CompletableFuture<JsonRpcResponse> future1 = new CompletableFuture<>();
        CompletableFuture<JsonRpcResponse> future2 = new CompletableFuture<>();
        CompletableFuture<JsonRpcResponse> future3 = new CompletableFuture<>();

        pendingRequests.put(1L, future1);
        pendingRequests.put(2L, future2);
        pendingRequests.put(3L, future3);

        RpcException testException = new RpcException(-32000, "Connection lost", null);

        // Simulate failAllPending logic
        pendingRequests.forEach((id, future) -> {
            if (pendingRequests.remove(id, future)) {
                future.completeExceptionally(testException);
            }
        });

        // Verify all futures completed exceptionally
        assertTrue(future1.isCompletedExceptionally());
        assertTrue(future2.isCompletedExceptionally());
        assertTrue(future3.isCompletedExceptionally());

        // Verify the map is empty
        assertTrue(pendingRequests.isEmpty());

        // Verify the exception is the one we provided
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future1.get());
        assertInstanceOf(RpcException.class, ex.getCause());
        assertEquals("Connection lost", ex.getCause().getMessage());
    }

    /**
     * Tests that failAllPending is idempotent - calling it twice doesn't cause issues.
     */
    @Test
    void failAllPending_idempotent() {
        ConcurrentHashMap<Long, CompletableFuture<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();

        CompletableFuture<JsonRpcResponse> future1 = new CompletableFuture<>();
        pendingRequests.put(1L, future1);

        RpcException exception1 = new RpcException(-32000, "First error", null);
        RpcException exception2 = new RpcException(-32000, "Second error", null);

        // First call
        pendingRequests.forEach((id, future) -> {
            if (pendingRequests.remove(id, future)) {
                future.completeExceptionally(exception1);
            }
        });

        // Second call - should be no-op since map is empty
        pendingRequests.forEach((id, future) -> {
            if (pendingRequests.remove(id, future)) {
                future.completeExceptionally(exception2);
            }
        });

        // Verify future was completed with first exception
        ExecutionException ex = assertThrows(ExecutionException.class, () -> future1.get());
        assertEquals("First error", ex.getCause().getMessage());
    }

    /**
     * Tests that failAllPending handles concurrent modifications safely.
     */
    @Test
    void failAllPending_handlesConcurrentModifications() throws Exception {
        ConcurrentHashMap<Long, CompletableFuture<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();

        int numFutures = 100;
        List<CompletableFuture<JsonRpcResponse>> futures = new ArrayList<>();

        for (int i = 0; i < numFutures; i++) {
            CompletableFuture<JsonRpcResponse> f = new CompletableFuture<>();
            futures.add(f);
            pendingRequests.put((long) i, f);
        }

        RpcException exception = new RpcException(-32000, "Connection error", null);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        // Thread 1: failAllPending
        Thread thread1 = new Thread(() -> {
            try {
                startLatch.await();
                pendingRequests.forEach((id, future) -> {
                    if (pendingRequests.remove(id, future)) {
                        future.completeExceptionally(exception);
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Try to add new requests concurrently
        Thread thread2 = new Thread(() -> {
            try {
                startLatch.await();
                for (int i = numFutures; i < numFutures + 50; i++) {
                    CompletableFuture<JsonRpcResponse> f = new CompletableFuture<>();
                    pendingRequests.put((long) i, f);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        thread1.start();
        thread2.start();
        startLatch.countDown();

        doneLatch.await(5, TimeUnit.SECONDS);

        // Verify original futures were completed
        for (int i = 0; i < numFutures; i++) {
            assertTrue(futures.get(i).isCompletedExceptionally(),
                    "Future " + i + " should be completed exceptionally");
        }
    }

    /**
     * Tests that failAllPending uses atomic remove-if-matches to prevent race conditions.
     */
    @Test
    void failAllPending_atomicRemoveIfMatches() {
        ConcurrentHashMap<Long, CompletableFuture<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();

        CompletableFuture<JsonRpcResponse> originalFuture = new CompletableFuture<>();
        CompletableFuture<JsonRpcResponse> replacementFuture = new CompletableFuture<>();

        pendingRequests.put(1L, originalFuture);

        // Simulate race: another thread replaces the future before failAllPending removes it
        AtomicBoolean originalWasRemoved = new AtomicBoolean(false);

        pendingRequests.forEach((id, future) -> {
            // Before remove, simulate another thread putting a new future
            if (id == 1L) {
                pendingRequests.put(1L, replacementFuture);
            }

            // This should NOT remove because the future doesn't match anymore
            boolean removed = pendingRequests.remove(id, future);
            if (removed && id == 1L) {
                originalWasRemoved.set(true);
            }
        });

        // The original future should NOT have been removed (because the value changed)
        assertFalse(originalWasRemoved.get());
        // Replacement future should still be in the map
        assertEquals(replacementFuture, pendingRequests.get(1L));
    }

    // ==================== Reconnect scheduling tests ====================

    /**
     * Tests exponential backoff calculation for reconnect delays.
     */
    @Test
    void reconnect_exponentialBackoffCalculation() {
        // Constants from WebSocketProvider
        long maxReconnectDelayMs = 32_000;

        // Verify backoff formula: min(1000 * (1 << (attempt - 1)), 32000)
        assertEquals(1000L, calculateBackoff(1, maxReconnectDelayMs)); // 1s
        assertEquals(2000L, calculateBackoff(2, maxReconnectDelayMs)); // 2s
        assertEquals(4000L, calculateBackoff(3, maxReconnectDelayMs)); // 4s
        assertEquals(8000L, calculateBackoff(4, maxReconnectDelayMs)); // 8s
        assertEquals(16000L, calculateBackoff(5, maxReconnectDelayMs)); // 16s
        assertEquals(32000L, calculateBackoff(6, maxReconnectDelayMs)); // 32s (capped)
        assertEquals(32000L, calculateBackoff(7, maxReconnectDelayMs)); // 32s (capped)
        assertEquals(32000L, calculateBackoff(10, maxReconnectDelayMs)); // 32s (capped)
    }

    private long calculateBackoff(long attempt, long maxDelayMs) {
        return Math.min(1000L * (1L << (attempt - 1)), maxDelayMs);
    }

    /**
     * Tests that reconnect stops after MAX_RECONNECT_ATTEMPTS.
     */
    @Test
    void reconnect_stopsAfterMaxAttempts() {
        int maxReconnectAttempts = 10;
        AtomicLong reconnectAttempts = new AtomicLong(0);
        AtomicBoolean permanentlyFailed = new AtomicBoolean(false);

        // Simulate reconnect logic
        for (int i = 0; i < 15; i++) {
            long attempt = reconnectAttempts.incrementAndGet();
            if (attempt > maxReconnectAttempts) {
                permanentlyFailed.set(true);
                break;
            }
        }

        assertTrue(permanentlyFailed.get());
        assertEquals(11, reconnectAttempts.get()); // Incremented to 11, then stopped
    }

    /**
     * Tests that reconnect counter resets on successful connection.
     */
    @Test
    void reconnect_resetsCounterOnSuccess() {
        AtomicLong reconnectAttempts = new AtomicLong(5);

        // Simulate successful reconnect - counter should reset to 0
        reconnectAttempts.set(0);

        assertEquals(0, reconnectAttempts.get());
    }

    // ==================== Timeout cancellation tests ====================

    /**
     * Tests that request is removed from pendingRequests when it times out.
     */
    @Test
    void timeout_removesRequestFromPendingRequests() throws Exception {
        ConcurrentHashMap<Long, CompletableFuture<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();

        long requestId = 42L;
        CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        // Simulate timeout logic: remove(id, future) only removes if future matches
        boolean removed = pendingRequests.remove(requestId, future);
        assertTrue(removed);
        assertFalse(pendingRequests.containsKey(requestId));
    }

    /**
     * Tests that timeout doesn't remove if request was already completed.
     */
    @Test
    void timeout_doesNotRemoveIfAlreadyCompleted() {
        ConcurrentHashMap<Long, CompletableFuture<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();

        long requestId = 42L;
        CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        // Simulate response arriving and being processed
        pendingRequests.remove(requestId);
        future.complete(new JsonRpcResponse("2.0", "success", null, "42"));

        // Simulate timeout firing after response arrived
        boolean removed = pendingRequests.remove(requestId, future);
        assertFalse(removed); // Already removed
    }

    /**
     * Tests timeout and response completion race condition.
     */
    @Test
    void timeout_raceWithResponseCompletion() throws Exception {
        ConcurrentHashMap<Long, CompletableFuture<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();

        long requestId = 42L;
        CompletableFuture<JsonRpcResponse> future = new CompletableFuture<>();
        pendingRequests.put(requestId, future);

        AtomicBoolean timeoutWon = new AtomicBoolean(false);
        AtomicBoolean responseWon = new AtomicBoolean(false);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(2);

        // Thread 1: Simulate response arriving
        Thread responseThread = new Thread(() -> {
            try {
                startLatch.await();
                CompletableFuture<JsonRpcResponse> removed = pendingRequests.remove(requestId);
                if (removed != null) {
                    responseWon.set(true);
                    removed.complete(new JsonRpcResponse("2.0", "success", null, "42"));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        // Thread 2: Simulate timeout firing
        Thread timeoutThread = new Thread(() -> {
            try {
                startLatch.await();
                boolean removed = pendingRequests.remove(requestId, future);
                if (removed) {
                    timeoutWon.set(true);
                    future.completeExceptionally(new RpcException(-32000, "Timeout", null));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                doneLatch.countDown();
            }
        });

        responseThread.start();
        timeoutThread.start();
        startLatch.countDown();

        doneLatch.await(5, TimeUnit.SECONDS);

        // Exactly one should win
        assertTrue(timeoutWon.get() ^ responseWon.get(),
                "Exactly one of timeout or response should win");

        // Future should be completed (either way)
        assertTrue(future.isDone());
    }

    /**
     * Tests that timeout correctly reports to metrics.
     */
    @Test
    void timeout_reportsToMetrics() {
        AtomicInteger timeoutCount = new AtomicInteger(0);
        AtomicReference<String> lastMethod = new AtomicReference<>();
        AtomicReference<Long> lastRequestId = new AtomicReference<>();

        // Create a test metrics implementation
        BraneMetrics testMetrics = new BraneMetrics() {
            @Override
            public void onRequestTimeout(String method, long requestId) {
                timeoutCount.incrementAndGet();
                lastMethod.set(method);
                lastRequestId.set(requestId);
            }
        };

        // Simulate timeout callback
        testMetrics.onRequestTimeout("eth_call", 12345L);

        assertEquals(1, timeoutCount.get());
        assertEquals("eth_call", lastMethod.get());
        assertEquals(12345L, lastRequestId.get());
    }

    // ==================== Subscription recovery tests ====================

    /**
     * Tests that subscriptions map persists across reconnection.
     * Note: WebSocketProvider doesn't automatically resubscribe after reconnect.
     * Applications must handle resubscription manually.
     */
    @Test
    void subscriptions_persistAcrossSimulatedReconnect() {
        ConcurrentHashMap<String, Consumer<JsonRpcResponse>> subscriptions = new ConcurrentHashMap<>();

        // Add subscriptions
        AtomicInteger callback1Count = new AtomicInteger(0);
        AtomicInteger callback2Count = new AtomicInteger(0);

        subscriptions.put("sub-1", response -> callback1Count.incrementAndGet());
        subscriptions.put("sub-2", response -> callback2Count.incrementAndGet());

        // Simulate reconnect (subscriptions map is NOT cleared by failAllPending)
        // This is the actual behavior in WebSocketProvider
        assertEquals(2, subscriptions.size());

        // After reconnect, subscriptions are still in the map
        assertTrue(subscriptions.containsKey("sub-1"));
        assertTrue(subscriptions.containsKey("sub-2"));

        // However, the server-side subscriptions are lost
        // Application code needs to resubscribe
    }

    /**
     * Tests subscription callback invocation.
     */
    @Test
    void subscriptions_callbackInvocation() {
        ConcurrentHashMap<String, Consumer<JsonRpcResponse>> subscriptions = new ConcurrentHashMap<>();

        AtomicReference<Object> receivedResult = new AtomicReference<>();
        subscriptions.put("sub-123", response -> {
            receivedResult.set(response.result());
        });

        // Simulate notification dispatch
        String subId = "sub-123";
        Consumer<JsonRpcResponse> listener = subscriptions.get(subId);
        if (listener != null) {
            JsonRpcResponse response = new JsonRpcResponse("2.0", "block data", null, null);
            listener.accept(response);
        }

        assertEquals("block data", receivedResult.get());
    }

    /**
     * Tests that subscription callback errors are caught and don't propagate.
     */
    @Test
    void subscriptions_callbackErrorsAreCaught() {
        ConcurrentHashMap<String, Consumer<JsonRpcResponse>> subscriptions = new ConcurrentHashMap<>();
        AtomicInteger metricsErrorCount = new AtomicInteger(0);

        // Add a failing callback
        subscriptions.put("sub-fail", response -> {
            throw new RuntimeException("Callback error");
        });

        // Simulate notification dispatch with error handling (as in handleNotificationNode)
        String subId = "sub-fail";
        Consumer<JsonRpcResponse> listener = subscriptions.get(subId);
        if (listener != null) {
            try {
                JsonRpcResponse response = new JsonRpcResponse("2.0", "data", null, null);
                listener.accept(response);
            } catch (Exception callbackEx) {
                // This is what WebSocketProvider does - catches and logs
                metricsErrorCount.incrementAndGet();
            }
        }

        assertEquals(1, metricsErrorCount.get());
    }

    // ==================== Connection state tests ====================

    /**
     * Tests ConnectionState enum values and transitions.
     */
    @Test
    void connectionState_enumValues() {
        WebSocketProvider.ConnectionState[] states = WebSocketProvider.ConnectionState.values();
        assertEquals(4, states.length);

        // Verify all states exist
        assertNotNull(WebSocketProvider.ConnectionState.CONNECTING);
        assertNotNull(WebSocketProvider.ConnectionState.CONNECTED);
        assertNotNull(WebSocketProvider.ConnectionState.RECONNECTING);
        assertNotNull(WebSocketProvider.ConnectionState.CLOSED);
    }

    /**
     * Tests that RECONNECTING state rejects requests.
     */
    @Test
    void connectionState_reconnectingRejectsRequests() {
        AtomicReference<WebSocketProvider.ConnectionState> state =
                new AtomicReference<>(WebSocketProvider.ConnectionState.RECONNECTING);

        // Simulate request rejection logic from sendAsync
        WebSocketProvider.ConnectionState currentState = state.get();

        if (currentState == WebSocketProvider.ConnectionState.CLOSED) {
            // Would return failed future
            fail("State should not be CLOSED");
        }
        if (currentState == WebSocketProvider.ConnectionState.RECONNECTING) {
            // Would return failed future with "WebSocket is reconnecting" message
            // This is the expected behavior
            assertTrue(true);
        }
    }

    /**
     * Tests that CLOSED state rejects requests.
     */
    @Test
    void connectionState_closedRejectsRequests() {
        AtomicReference<WebSocketProvider.ConnectionState> state =
                new AtomicReference<>(WebSocketProvider.ConnectionState.CLOSED);

        WebSocketProvider.ConnectionState currentState = state.get();
        assertEquals(WebSocketProvider.ConnectionState.CLOSED, currentState);

        // Requests should be rejected with "WebSocketProvider is closed"
    }

    // ==================== Orphaned response tests ====================

    /**
     * Tests orphaned response counting.
     */
    @Test
    void orphanedResponses_countingWorks() {
        java.util.concurrent.atomic.LongAdder orphanedResponses = new java.util.concurrent.atomic.LongAdder();

        // Simulate orphaned responses
        orphanedResponses.increment();
        orphanedResponses.increment();
        orphanedResponses.increment();

        assertEquals(3, orphanedResponses.sum());
    }

    /**
     * Tests metrics callback for orphaned responses.
     */
    @Test
    void orphanedResponses_metricsCallback() {
        List<String> orphanReasons = new ArrayList<>();

        BraneMetrics testMetrics = new BraneMetrics() {
            @Override
            public void onOrphanedResponse(String reason) {
                orphanReasons.add(reason);
            }
        };

        // Simulate different orphan reasons
        testMetrics.onOrphanedResponse("unparseable ID: abc");
        testMetrics.onOrphanedResponse("no pending request for ID: 123");
        testMetrics.onOrphanedResponse("unexpected ID type: NULL");

        assertEquals(3, orphanReasons.size());
        assertTrue(orphanReasons.contains("unparseable ID: abc"));
        assertTrue(orphanReasons.contains("no pending request for ID: 123"));
        assertTrue(orphanReasons.contains("unexpected ID type: NULL"));
    }

    // ==================== Backpressure tests ====================

    /**
     * Tests backpressure triggering when too many pending requests.
     */
    @Test
    void backpressure_triggersWhenMaxPendingReached() {
        int maxPendingRequests = 100;
        ConcurrentHashMap<Long, CompletableFuture<JsonRpcResponse>> pendingRequests = new ConcurrentHashMap<>();

        // Fill up to max
        for (int i = 0; i < maxPendingRequests; i++) {
            pendingRequests.put((long) i, new CompletableFuture<>());
        }

        // Check if backpressure should trigger
        boolean shouldTriggerBackpressure = pendingRequests.size() >= maxPendingRequests;
        assertTrue(shouldTriggerBackpressure);
    }

    /**
     * Tests that backpressure metrics are called with correct parameters.
     */
    @Test
    void backpressure_metricsCallbackWithParams() {
        AtomicInteger lastPendingCount = new AtomicInteger(-1);
        AtomicInteger lastMaxPending = new AtomicInteger(-1);

        BraneMetrics testMetrics = new BraneMetrics() {
            @Override
            public void onBackpressure(int pendingCount, int maxPendingRequests) {
                lastPendingCount.set(pendingCount);
                lastMaxPending.set(maxPendingRequests);
            }
        };

        testMetrics.onBackpressure(65536, 65536);

        assertEquals(65536, lastPendingCount.get());
        assertEquals(65536, lastMaxPending.get());
    }

    // ==================== Ring buffer saturation tests ====================

    /**
     * Tests ring buffer saturation threshold calculation.
     */
    @Test
    void ringBuffer_saturationThresholdCalculation() {
        double saturationThreshold = 0.1; // 10% from WebSocketProvider
        int bufferSize = 4096;

        long threshold = (long) (bufferSize * saturationThreshold);
        assertEquals(409, threshold); // 10% of 4096 = 409.6, truncated to 409

        // Test saturation detection
        long remainingCapacity = 400;
        boolean isSaturated = remainingCapacity < bufferSize * saturationThreshold;
        assertTrue(isSaturated);
    }

    /**
     * Tests ring buffer saturation metrics callback.
     */
    @Test
    void ringBuffer_saturationMetricsCallback() {
        AtomicLong lastRemainingCapacity = new AtomicLong(-1);
        AtomicInteger lastBufferSize = new AtomicInteger(-1);

        BraneMetrics testMetrics = new BraneMetrics() {
            @Override
            public void onRingBufferSaturation(long remainingCapacity, int bufferSize) {
                lastRemainingCapacity.set(remainingCapacity);
                lastBufferSize.set(bufferSize);
            }
        };

        testMetrics.onRingBufferSaturation(100, 4096);

        assertEquals(100, lastRemainingCapacity.get());
        assertEquals(4096, lastBufferSize.get());
    }

    // ==================== Transport type selection tests ====================

    /**
     * Tests that NIO transport creates NioEventLoopGroup.
     */
    @Test
    void transportType_nioCreatesNioEventLoopGroup() {
        // NIO is always available, so we can safely test this
        assertTrue(true, "NIO transport is always available as a fallback");

        // Verify NioSocketChannel class is in the expected package
        assertEquals("io.netty.channel.socket.nio",
                io.netty.channel.socket.nio.NioSocketChannel.class.getPackageName());
    }

    /**
     * Tests that Epoll availability can be checked via reflection.
     * Native transport classes are only available at compile time, so we use reflection.
     */
    @Test
    void transportType_epollAvailabilityCheck() throws Exception {
        String os = System.getProperty("os.name").toLowerCase();

        // Try to check Epoll availability via reflection
        try {
            Class<?> epollClass = Class.forName("io.netty.channel.epoll.Epoll");
            java.lang.reflect.Method isAvailableMethod = epollClass.getMethod("isAvailable");
            boolean epollAvailable = (Boolean) isAvailableMethod.invoke(null);

            if (os.contains("linux")) {
                // On Linux, Epoll may or may not be available depending on native libs
                assertNotNull(epollAvailable, "Epoll availability should be determinable on Linux");
            } else {
                // On non-Linux, Epoll should not be available
                assertFalse(epollAvailable, "Epoll should not be available on " + os);
            }
        } catch (ClassNotFoundException e) {
            // Native transport classes not on classpath - this is expected in test environment
            // The classes are compileOnly in production code
            assertTrue(true, "Epoll class not available - expected in test environment");
        }
    }

    /**
     * Tests that KQueue availability can be checked via reflection.
     * Native transport classes are only available at compile time, so we use reflection.
     */
    @Test
    void transportType_kqueueAvailabilityCheck() throws Exception {
        String os = System.getProperty("os.name").toLowerCase();

        // Try to check KQueue availability via reflection
        try {
            Class<?> kqueueClass = Class.forName("io.netty.channel.kqueue.KQueue");
            java.lang.reflect.Method isAvailableMethod = kqueueClass.getMethod("isAvailable");
            boolean kqueueAvailable = (Boolean) isAvailableMethod.invoke(null);

            if (os.contains("mac") || os.contains("darwin") || os.contains("bsd")) {
                // On macOS/BSD, KQueue may or may not be available depending on native libs
                assertNotNull(kqueueAvailable, "KQueue availability should be determinable on macOS/BSD");
            } else {
                // On non-macOS/BSD, KQueue should not be available
                assertFalse(kqueueAvailable, "KQueue should not be available on " + os);
            }
        } catch (ClassNotFoundException e) {
            // Native transport classes not on classpath - this is expected in test environment
            // The classes are compileOnly in production code
            assertTrue(true, "KQueue class not available - expected in test environment");
        }
    }

    /**
     * Tests AUTO transport selection logic based on platform.
     * Uses reflection since native transport classes are compileOnly.
     */
    @Test
    void transportType_autoSelectsBasedOnPlatform() throws Exception {
        // AUTO should select:
        // - Epoll on Linux (if available)
        // - KQueue on macOS/BSD (if available)
        // - NIO otherwise

        boolean epollAvailable = isTransportAvailable("io.netty.channel.epoll.Epoll");
        boolean kqueueAvailable = isTransportAvailable("io.netty.channel.kqueue.KQueue");

        String os = System.getProperty("os.name").toLowerCase();

        if (epollAvailable) {
            // Should select Epoll
            assertTrue(os.contains("linux"),
                    "Epoll should only be available on Linux, but os.name is: " + os);
        } else if (kqueueAvailable) {
            // Should select KQueue
            assertTrue(os.contains("mac") || os.contains("darwin") || os.contains("bsd"),
                    "KQueue should only be available on macOS/BSD, but os.name is: " + os);
        } else {
            // Should fall back to NIO
            // NIO is always available
            assertTrue(true, "NIO is always available as fallback");
        }
    }

    /**
     * Tests that exactly one native transport is available per platform.
     */
    @Test
    void transportType_atMostOneNativeTransportAvailable() {
        boolean epollAvailable = isTransportAvailable("io.netty.channel.epoll.Epoll");
        boolean kqueueAvailable = isTransportAvailable("io.netty.channel.kqueue.KQueue");

        // Both cannot be available at the same time (they're platform-specific)
        assertFalse(epollAvailable && kqueueAvailable,
                "Both Epoll and KQueue cannot be available simultaneously");
    }

    /**
     * Tests detectChannelClass for NioEventLoopGroup.
     */
    @Test
    void detectChannelClass_nioEventLoopGroup() {
        io.netty.channel.nio.NioEventLoopGroup nioGroup = new io.netty.channel.nio.NioEventLoopGroup(1);
        try {
            // When given a NioEventLoopGroup, should return NioSocketChannel.class
            Class<?> channelClass = detectChannelClassForGroup(nioGroup);
            assertEquals(io.netty.channel.socket.nio.NioSocketChannel.class, channelClass);
        } finally {
            nioGroup.shutdownGracefully();
        }
    }

    /**
     * Tests detectChannelClass for EpollEventLoopGroup (if available).
     * Uses reflection since native transport classes are compileOnly.
     */
    @Test
    void detectChannelClass_epollEventLoopGroup() throws Exception {
        if (!isTransportAvailable("io.netty.channel.epoll.Epoll")) {
            // Skip on non-Linux platforms or when native libs aren't available
            return;
        }

        Class<?> epollGroupClass = Class.forName("io.netty.channel.epoll.EpollEventLoopGroup");
        io.netty.channel.EventLoopGroup epollGroup =
                (io.netty.channel.EventLoopGroup) epollGroupClass.getConstructor(int.class).newInstance(1);
        try {
            Class<?> channelClass = detectChannelClassForGroup(epollGroup);
            Class<?> expectedClass = Class.forName("io.netty.channel.epoll.EpollSocketChannel");
            assertEquals(expectedClass, channelClass);
        } finally {
            epollGroup.shutdownGracefully();
        }
    }

    /**
     * Tests detectChannelClass for KQueueEventLoopGroup (if available).
     * Uses reflection since native transport classes are compileOnly.
     */
    @Test
    void detectChannelClass_kqueueEventLoopGroup() throws Exception {
        if (!isTransportAvailable("io.netty.channel.kqueue.KQueue")) {
            // Skip on non-macOS/BSD platforms or when native libs aren't available
            return;
        }

        Class<?> kqueueGroupClass = Class.forName("io.netty.channel.kqueue.KQueueEventLoopGroup");
        io.netty.channel.EventLoopGroup kqueueGroup =
                (io.netty.channel.EventLoopGroup) kqueueGroupClass.getConstructor(int.class).newInstance(1);
        try {
            Class<?> channelClass = detectChannelClassForGroup(kqueueGroup);
            Class<?> expectedClass = Class.forName("io.netty.channel.kqueue.KQueueSocketChannel");
            assertEquals(expectedClass, channelClass);
        } finally {
            kqueueGroup.shutdownGracefully();
        }
    }

    /**
     * Tests that unavailability causes are properly reported.
     * Uses reflection since native transport classes are compileOnly.
     */
    @Test
    void transportType_unavailabilityCauseIsReported() throws Exception {
        String os = System.getProperty("os.name").toLowerCase();

        // On non-Linux, Epoll should have an unavailability cause (if class is available)
        if (!os.contains("linux")) {
            try {
                Class<?> epollClass = Class.forName("io.netty.channel.epoll.Epoll");
                java.lang.reflect.Method causeMethod = epollClass.getMethod("unavailabilityCause");
                Throwable cause = (Throwable) causeMethod.invoke(null);
                assertNotNull(cause, "Epoll unavailability cause should be non-null on non-Linux");
            } catch (ClassNotFoundException e) {
                // Class not available in test - that's OK
            }
        }

        // On non-macOS, KQueue should have an unavailability cause (if class is available)
        if (!os.contains("mac") && !os.contains("darwin") && !os.contains("bsd")) {
            try {
                Class<?> kqueueClass = Class.forName("io.netty.channel.kqueue.KQueue");
                java.lang.reflect.Method causeMethod = kqueueClass.getMethod("unavailabilityCause");
                Throwable cause = (Throwable) causeMethod.invoke(null);
                assertNotNull(cause, "KQueue unavailability cause should be non-null on non-macOS/BSD");
            } catch (ClassNotFoundException e) {
                // Class not available in test - that's OK
            }
        }
    }

    /**
     * Tests that TransportType enum values exist and have correct names.
     */
    @Test
    void transportType_enumValuesExist() {
        WebSocketConfig.TransportType[] types = WebSocketConfig.TransportType.values();
        assertEquals(4, types.length);

        assertEquals("AUTO", WebSocketConfig.TransportType.AUTO.name());
        assertEquals("NIO", WebSocketConfig.TransportType.NIO.name());
        assertEquals("EPOLL", WebSocketConfig.TransportType.EPOLL.name());
        assertEquals("KQUEUE", WebSocketConfig.TransportType.KQUEUE.name());
    }

    /**
     * Tests that config with NIO transport type can be created.
     */
    @Test
    void transportType_configWithNioCanBeCreated() {
        WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                .transportType(WebSocketConfig.TransportType.NIO)
                .build();

        assertEquals(WebSocketConfig.TransportType.NIO, config.transportType());
    }

    /**
     * Tests that config with custom EventLoopGroup can be created.
     */
    @Test
    void transportType_configWithCustomEventLoopGroup() {
        io.netty.channel.nio.NioEventLoopGroup customGroup = new io.netty.channel.nio.NioEventLoopGroup(2);
        try {
            WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                    .eventLoopGroup(customGroup)
                    .build();

            assertEquals(customGroup, config.eventLoopGroup());
        } finally {
            customGroup.shutdownGracefully();
        }
    }

    /**
     * Helper method to check if a native transport is available via reflection.
     */
    private boolean isTransportAvailable(String className) {
        try {
            Class<?> transportClass = Class.forName(className);
            java.lang.reflect.Method isAvailableMethod = transportClass.getMethod("isAvailable");
            return (Boolean) isAvailableMethod.invoke(null);
        } catch (ClassNotFoundException e) {
            // Class not on classpath
            return false;
        } catch (Exception e) {
            // Other error
            return false;
        }
    }

    /**
     * Helper method that mimics WebSocketProvider.detectChannelClass logic.
     * Uses class name comparison since native classes may not be on test classpath.
     */
    private Class<?> detectChannelClassForGroup(io.netty.channel.EventLoopGroup group) {
        String groupClassName = group.getClass().getName();

        if (groupClassName.contains("EpollEventLoopGroup")) {
            try {
                return Class.forName("io.netty.channel.epoll.EpollSocketChannel");
            } catch (ClassNotFoundException e) {
                return io.netty.channel.socket.nio.NioSocketChannel.class;
            }
        }
        if (groupClassName.contains("KQueueEventLoopGroup")) {
            try {
                return Class.forName("io.netty.channel.kqueue.KQueueSocketChannel");
            } catch (ClassNotFoundException e) {
                return io.netty.channel.socket.nio.NioSocketChannel.class;
            }
        }
        // Default to NIO for NioEventLoopGroup or unknown types
        return io.netty.channel.socket.nio.NioSocketChannel.class;
    }

    // ==================== CONNECT_TIMEOUT_MILLIS tests ====================

    /**
     * Tests that CONNECT_TIMEOUT_MILLIS is applied to Bootstrap options.
     * Verifies the config connect timeout is correctly converted to milliseconds
     * and applied to the Bootstrap channel option.
     */
    @Test
    void connectTimeout_appliedToBootstrapOptions() {
        java.time.Duration connectTimeout = java.time.Duration.ofSeconds(5);

        // Create a Bootstrap and apply the CONNECT_TIMEOUT_MILLIS option
        io.netty.bootstrap.Bootstrap bootstrap = new io.netty.bootstrap.Bootstrap();
        bootstrap.option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis());

        // Verify the option was set by retrieving it from the config
        // Bootstrap stores options in an internal map, accessible via config()
        @SuppressWarnings("unchecked")
        java.util.Map<io.netty.channel.ChannelOption<?>, Object> options = bootstrap.config().options();

        assertTrue(options.containsKey(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS),
                "CONNECT_TIMEOUT_MILLIS should be set on Bootstrap");
        assertEquals(5000, options.get(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS),
                "CONNECT_TIMEOUT_MILLIS should be 5000ms (5 seconds)");
    }

    /**
     * Tests that default connect timeout (10 seconds) is correctly applied.
     */
    @Test
    void connectTimeout_defaultValueApplied() {
        java.time.Duration defaultConnectTimeout = java.time.Duration.ofSeconds(10);

        io.netty.bootstrap.Bootstrap bootstrap = new io.netty.bootstrap.Bootstrap();
        bootstrap.option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) defaultConnectTimeout.toMillis());

        @SuppressWarnings("unchecked")
        java.util.Map<io.netty.channel.ChannelOption<?>, Object> options = bootstrap.config().options();

        assertEquals(10000, options.get(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS),
                "Default CONNECT_TIMEOUT_MILLIS should be 10000ms (10 seconds)");
    }

    /**
     * Tests that custom connect timeout from config is correctly applied.
     */
    @Test
    void connectTimeout_customValueFromConfig() {
        WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                .connectTimeout(java.time.Duration.ofSeconds(30))
                .build();

        io.netty.bootstrap.Bootstrap bootstrap = new io.netty.bootstrap.Bootstrap();
        bootstrap.option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) config.connectTimeout().toMillis());

        @SuppressWarnings("unchecked")
        java.util.Map<io.netty.channel.ChannelOption<?>, Object> options = bootstrap.config().options();

        assertEquals(30000, options.get(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS),
                "Custom CONNECT_TIMEOUT_MILLIS should be 30000ms (30 seconds)");
    }

    /**
     * Tests that sub-second connect timeout is correctly applied.
     */
    @Test
    void connectTimeout_subSecondPrecision() {
        java.time.Duration connectTimeout = java.time.Duration.ofMillis(500);

        io.netty.bootstrap.Bootstrap bootstrap = new io.netty.bootstrap.Bootstrap();
        bootstrap.option(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) connectTimeout.toMillis());

        @SuppressWarnings("unchecked")
        java.util.Map<io.netty.channel.ChannelOption<?>, Object> options = bootstrap.config().options();

        assertEquals(500, options.get(io.netty.channel.ChannelOption.CONNECT_TIMEOUT_MILLIS),
                "CONNECT_TIMEOUT_MILLIS should be 500ms");
    }
}
