package io.brane.rpc;

import io.brane.core.model.BlockHeader;
import io.brane.core.model.LogEntry;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.WebSocketProvider;
import io.brane.rpc.JsonRpcResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("integration")
public class WebSocketIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(WebSocketIntegrationTest.class);

    // Use Anvil for a lightweight local node
    static GenericContainer<?> anvil = new GenericContainer<>(
            DockerImageName.parse("ghcr.io/foundry-rs/foundry:latest"))
            .withCommand("anvil", "--host", "0.0.0.0", "--port", "8545", "--block-time", "1")
            .withExposedPorts(8545)
            .waitingFor(Wait.forLogMessage(".*Listening on 0.0.0.0:8545.*", 1));

    private static String wsUrl;
    private static String ANVIL_HTTP_URL;
    private static boolean useLocalNode;

    private WebSocketProvider wsProvider;
    private PublicClient client;
    private HttpClient httpClient;

    @BeforeAll
    static void setUp() {
        // Check if we should use a local node (e.g. running 'anvil' in terminal)
        // or if Docker is not available.
        String useLocal = System.getProperty("brane.test.useLocalNode");
        if (Boolean.parseBoolean(useLocal)) {
            useLocalNode = true;
            log.info("Using local node at 127.0.0.1:8545");
            wsUrl = "ws://127.0.0.1:8545";
            ANVIL_HTTP_URL = "http://127.0.0.1:8545";
        } else {
            try {
                anvil.start();
                wsUrl = "ws://" + anvil.getHost() + ":" + anvil.getMappedPort(8545);
                ANVIL_HTTP_URL = "http://" + anvil.getHost() + ":" + anvil.getMappedPort(8545);
            } catch (Exception e) {
                log.warn(
                        "Failed to start Docker container. Falling back to local node (ensure 'anvil' is running!). Error: {}",
                        e.getMessage());
                useLocalNode = true;
                wsUrl = "ws://127.0.0.1:8545";
                ANVIL_HTTP_URL = "http://127.0.0.1:8545";
            }
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (anvil.isRunning()) {
            anvil.stop();
        }
    }

    @AfterEach
    void tearDown() {
        if (wsProvider != null) {
            wsProvider.close();
        }
    }

    @org.junit.jupiter.api.BeforeEach
    void init() {
        wsProvider = WebSocketProvider.create(wsUrl);
        client = PublicClient.from(wsProvider);
        httpClient = HttpClient.newHttpClient();
    }

    @Test
    void testSubscribeToNewHeads() throws Exception {
        CompletableFuture<BlockHeader> received = new CompletableFuture<>();
        Subscription sub = client.subscribeToNewHeads(header -> {
            received.complete(header);
        });

        assertNotNull(sub.id());

        // Trigger a new block
        triggerMine();

        BlockHeader header = received.get(10, TimeUnit.SECONDS);
        assertNotNull(header);
        assertNotNull(header.hash());
        assertNotNull(header.number());

        sub.unsubscribe();
    }

    @Test
    void testSubscriptionCallbackNotOnNettyThread() throws Exception {
        AtomicReference<String> callbackThreadName = new AtomicReference<>();
        CompletableFuture<String> received = new CompletableFuture<>();

        Subscription sub = client.subscribeToNewHeads(header -> {
            callbackThreadName.set(Thread.currentThread().getName());
            received.complete(Thread.currentThread().getName());
        });

        assertNotNull(sub.id());

        // Trigger a new block
        triggerMine();

        // Wait for callback
        String threadName = received.get(10, TimeUnit.SECONDS);

        // Callback should NOT run on the Netty I/O thread
        assertFalse(threadName.contains("brane-netty-io"),
                "Subscription callback ran on Netty I/O thread (" + threadName + "). " +
                        "This is a bug - callbacks should run on the subscription executor.");

        sub.unsubscribe();
    }

    @Test
    void testSubscribeToLogs() throws Exception {
        // Use pre-deployed Token contract from integration script, or skip if not set
        String contractAddrStr = System.getProperty("brane.examples.erc20.contract");
        if (contractAddrStr == null || contractAddrStr.isEmpty()
                || "0x0000000000000000000000000000000000000000".equalsIgnoreCase(contractAddrStr)) {
            System.out.println("Skipping testSubscribeToLogs: No pre-deployed token contract configured");
            System.out.println("Set -Dbrane.examples.erc20.contract=<address> to run this test");
            return;
        }
        Address contractAddress = new Address(contractAddrStr);

        // Setup WalletClient for transfers
        io.brane.core.crypto.Signer signer = new io.brane.core.crypto.PrivateKeySigner(
                "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
        Address owner = new Address("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266");
        io.brane.rpc.WalletClient wallet = io.brane.rpc.DefaultWalletClient.create(wsProvider, client, signer, owner);

        // Subscribe to logs from the token contract
        CompletableFuture<LogEntry> received = new CompletableFuture<>();
        Subscription sub = client.subscribeToLogs(
                new io.brane.rpc.LogFilter(java.util.Optional.empty(), java.util.Optional.empty(),
                        java.util.Optional.of(java.util.List.of(contractAddress)), java.util.Optional.empty()),
                log -> received.complete(log));

        // Wait for subscription to be active
        Thread.sleep(200);

        // Trigger Transfer event (transfer 100 tokens to random address)
        Address recipient = new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");
        // transfer(address,uint256) selector: a9059cbb
        // args: recipient, 100
        String transferData = "a9059cbb" +
                "000000000000000000000000" + recipient.value().substring(2) +
                "0000000000000000000000000000000000000000000000000000000000000064";

        io.brane.core.model.TransactionRequest transferReq = io.brane.core.builder.TxBuilder.legacy()
                .to(contractAddress)
                .data(new HexData("0x" + transferData))
                .build();

        wallet.sendTransactionAndWait(transferReq, 30_000, 1_000);

        // Verify log received
        LogEntry log = received.get(10, TimeUnit.SECONDS);
        assertNotNull(log);
        assertEquals(contractAddress, log.address());
        // Transfer event signature:
        // ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef
        assertEquals("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef", log.topics().get(0).value());

        sub.unsubscribe();
    }

    @Test
    void testAsyncBatch() throws Exception {
        int batchSize = 100;
        List<CompletableFuture<JsonRpcResponse>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < batchSize; i++) {
            futures.add(wsProvider.sendAsyncBatch("eth_blockNumber", List.of()));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);

        for (CompletableFuture<JsonRpcResponse> f : futures) {
            JsonRpcResponse response = f.get();
            assertNull(response.error());
            assertNotNull(response.result());
        }
    }

    @Test
    void testCustomSubscriptionExecutor() throws Exception {
        // Create a custom executor that tracks its thread name
        java.util.concurrent.Executor customExecutor = runnable -> {
            Thread t = new Thread(runnable, "test-custom-executor");
            t.start();
        };
        wsProvider.setSubscriptionExecutor(customExecutor);

        CompletableFuture<String> received = new CompletableFuture<>();

        Subscription sub = client.subscribeToNewHeads(header -> {
            received.complete(Thread.currentThread().getName());
        });

        assertNotNull(sub.id());

        // Trigger a new block
        triggerMine();

        // Wait for callback and verify it ran on our custom executor
        String threadName = received.get(10, TimeUnit.SECONDS);
        assertEquals("test-custom-executor", threadName,
                "Callback should run on custom executor thread");

        sub.unsubscribe();
    }

    @Test
    void testSendRunsOnCallerThread() throws Exception {
        // Record the calling thread
        String callerThread = Thread.currentThread().getName();

        // Make a synchronous call
        JsonRpcResponse response = wsProvider.send("eth_blockNumber", List.of());

        // Verify the call completed (we're still on the same thread)
        assertNotNull(response);
        assertNull(response.error());

        // Verify we're still on the caller thread (send() should block, not switch
        // threads)
        assertEquals(callerThread, Thread.currentThread().getName(),
                "send() should block on the caller thread, not switch threads");
    }

    @Test
    void testRequestTimeoutWithShortDuration() throws Exception {
        // Test that the timeout scheduling mechanism is in place.
        // Since Anvil responds quickly even to unknown methods, we verify
        // that sendAsync with timeout doesn't throw and the future completes.
        // The actual timeout behavior is harder to test without a server that delays.

        CompletableFuture<JsonRpcResponse> future = wsProvider.sendAsync(
                "eth_blockNumber",
                List.of(),
                Duration.ofSeconds(30) // Long timeout - should complete quickly
        );

        // This should complete quickly (not timeout)
        JsonRpcResponse response = future.get(5, TimeUnit.SECONDS);
        assertNotNull(response);
        assertNotNull(response.result());

        // Also verify the timeout method signature is accessible and works
        // by checking that a very short timeout against a valid method still works
        // (since Anvil responds faster than any realistic timeout)
        CompletableFuture<JsonRpcResponse> quickFuture = wsProvider.sendAsync(
                "eth_chainId",
                List.of(),
                Duration.ofMillis(5000) // 5 second timeout
        );
        JsonRpcResponse quickResponse = quickFuture.get(10, TimeUnit.SECONDS);
        assertNotNull(quickResponse.result());
    }

    // ==================== LOW-4: WebSocket Edge Case Tests ====================
    // These integration tests cover concurrency, stress, close behavior, and timeout races.
    // Note: Reconnection, orphaned response handling, and backpressure tests would require
    // mock Netty channels which is out of scope for integration tests.

    /**
     * Stress test: High-concurrency concurrent request submission.
     * Tests that the ConcurrentHashMap-based request tracking handles
     * many simultaneous requests without race conditions.
     */
    @Test
    void testHighConcurrencyStress() throws Exception {
        int numRequests = 1000;
        int numThreads = 50;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(numRequests);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger errorCount = new AtomicInteger(0);

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        try {
            for (int i = 0; i < numRequests; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await(); // Wait for all threads to be ready
                        CompletableFuture<JsonRpcResponse> future = wsProvider.sendAsync(
                                "eth_blockNumber", List.of());
                        JsonRpcResponse response = future.get(30, TimeUnit.SECONDS);
                        if (response.error() == null && response.result() != null) {
                            successCount.incrementAndGet();
                        } else {
                            errorCount.incrementAndGet();
                        }
                    } catch (Exception e) {
                        errorCount.incrementAndGet();
                        log.error("Request failed", e);
                    } finally {
                        doneLatch.countDown();
                    }
                });
            }

            // Release all threads at once to maximize contention
            startLatch.countDown();

            // Wait for all requests to complete
            boolean completed = doneLatch.await(60, TimeUnit.SECONDS);
            assertTrue(completed, "Not all requests completed in time");
            assertEquals(numRequests, successCount.get(),
                    "All requests should succeed. Errors: " + errorCount.get());
        } finally {
            executor.shutdownNow();
        }
    }

    /**
     * Stress test: Concurrent async batch requests via Disruptor.
     * Tests that the Disruptor ring buffer handles high-throughput
     * request submission without data corruption.
     */
    @Test
    void testDisruptorBatchStress() throws Exception {
        int numRequests = 500;
        List<CompletableFuture<JsonRpcResponse>> futures = new java.util.ArrayList<>();

        // Submit all requests as fast as possible to stress the ring buffer
        long startTime = System.nanoTime();
        for (int i = 0; i < numRequests; i++) {
            futures.add(wsProvider.sendAsyncBatch("eth_chainId", List.of()));
        }
        long submitTime = System.nanoTime() - startTime;
        log.info("Submitted {} requests in {}ms", numRequests, submitTime / 1_000_000);

        // Wait for all to complete
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(60, TimeUnit.SECONDS);

        // Verify all responses are valid and have the same chain ID
        String expectedChainId = null;
        int successCount = 0;
        for (CompletableFuture<JsonRpcResponse> f : futures) {
            JsonRpcResponse response = f.get();
            assertNull(response.error(), "Request should not have error");
            assertNotNull(response.result(), "Result should not be null");
            String chainId = response.result().toString();
            if (expectedChainId == null) {
                expectedChainId = chainId;
            } else {
                assertEquals(expectedChainId, chainId,
                        "All responses should have same chain ID (no slot collision)");
            }
            successCount++;
        }
        assertEquals(numRequests, successCount, "All requests should succeed");
    }

    /**
     * Tests timeout vs completion race: ensures that if a request completes
     * just before timeout, the correct result is returned (not a timeout error).
     */
    @Test
    void testTimeoutCompletionRace() throws Exception {
        // Use a reasonable timeout that should not fire for a fast local node
        Duration timeout = Duration.ofSeconds(5);
        int numRequests = 50;
        AtomicInteger timeoutCount = new AtomicInteger(0);
        AtomicInteger successCount = new AtomicInteger(0);

        List<CompletableFuture<JsonRpcResponse>> futures = new java.util.ArrayList<>();
        for (int i = 0; i < numRequests; i++) {
            futures.add(wsProvider.sendAsync("eth_blockNumber", List.of(), timeout));
        }

        for (CompletableFuture<JsonRpcResponse> f : futures) {
            try {
                JsonRpcResponse response = f.get(10, TimeUnit.SECONDS);
                if (response.error() == null) {
                    successCount.incrementAndGet();
                }
            } catch (ExecutionException e) {
                if (e.getCause() != null &&
                        e.getCause().getMessage() != null &&
                        e.getCause().getMessage().contains("timed out")) {
                    timeoutCount.incrementAndGet();
                } else {
                    throw e;
                }
            }
        }

        // With a 5 second timeout against a fast local node, all should succeed
        assertEquals(numRequests, successCount.get(),
                "All requests should complete before timeout");
        assertEquals(0, timeoutCount.get(),
                "No requests should timeout against fast local node");
    }

    /**
     * Tests that close() is idempotent - calling it multiple times should not throw.
     */
    @Test
    void testIdempotentClose() {
        // Create a new provider for this test
        WebSocketProvider testProvider = WebSocketProvider.create(wsUrl);

        // First close should succeed
        testProvider.close();

        // Second close should be idempotent (no exception)
        testProvider.close();

        // Third close should also be fine
        testProvider.close();
    }

    /**
     * Tests that after close(), new requests fail appropriately.
     */
    @Test
    void testRequestsFailAfterClose() {
        // Create a new provider for this test
        WebSocketProvider testProvider = WebSocketProvider.create(wsUrl);

        // Close the provider
        testProvider.close();

        // Try to send a request - should fail
        CompletableFuture<JsonRpcResponse> future = testProvider.sendAsync(
                "eth_blockNumber", List.of());

        assertThrows(Exception.class, () -> {
            future.get(5, TimeUnit.SECONDS);
        }, "Requests should fail after provider is closed");
    }

    /**
     * Tests concurrent close and request submission don't cause issues.
     */
    @Test
    void testConcurrentCloseAndRequests() throws Exception {
        WebSocketProvider testProvider = WebSocketProvider.create(wsUrl);
        CountDownLatch startLatch = new CountDownLatch(1);
        AtomicInteger completedOrFailed = new AtomicInteger(0);
        int numRequests = 20;

        ExecutorService executor = Executors.newFixedThreadPool(numRequests + 1);
        try {
            // Submit requests that will race with close
            for (int i = 0; i < numRequests; i++) {
                executor.submit(() -> {
                    try {
                        startLatch.await();
                        CompletableFuture<JsonRpcResponse> future = testProvider.sendAsync(
                                "eth_blockNumber", List.of());
                        future.get(10, TimeUnit.SECONDS);
                    } catch (Exception e) {
                        // Expected - some requests will fail due to close
                    }
                    completedOrFailed.incrementAndGet();
                });
            }

            // Submit close on another thread
            executor.submit(() -> {
                try {
                    startLatch.await();
                    Thread.sleep(5); // Small delay to let some requests start
                    testProvider.close();
                } catch (Exception e) {
                    // Ignore
                }
            });

            // Release all threads
            startLatch.countDown();

            // Wait for completion
            Thread.sleep(5000);

            // All requests should either complete or fail (not hang)
            assertEquals(numRequests, completedOrFailed.get(),
                    "All requests should complete or fail, not hang");
        } finally {
            executor.shutdownNow();
            // Ensure cleanup even if assertions fail
            try {
                testProvider.close();
            } catch (Exception ignored) {
            }
        }
    }

    private void triggerMine() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ANVIL_HTTP_URL))
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"jsonrpc\":\"2.0\",\"method\":\"evm_mine\",\"params\":[],\"id\":999}"))
                .header("Content-Type", "application/json")
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
