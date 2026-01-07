package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Unit tests for {@link BraneAsyncClient}.
 *
 * <p>Tests cover:
 * <ul>
 *   <li>sendAsync delegation to WebSocketProvider's native async method</li>
 *   <li>sendAsync wrapping synchronous provider call</li>
 *   <li>shutdownDefaultExecutor lifecycle management</li>
 *   <li>Constructor null validation</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
class BraneAsyncClientTest {

    @Mock
    private BraneProvider mockProvider;

    @Mock
    private WebSocketProvider mockWebSocketProvider;

    @Mock
    private Executor mockExecutor;

    @AfterEach
    void tearDown() {
        // Clean up default executor after each test to prevent test pollution
        BraneAsyncClient.shutdownDefaultExecutor(100);
    }

    // ==================== Constructor null validation tests ====================

    @Test
    void constructor_throwsOnNullProvider() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new BraneAsyncClient(null));
        assertEquals("provider", ex.getMessage());
    }

    @Test
    void constructor_throwsOnNullProviderWithExecutor() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new BraneAsyncClient(null, mockExecutor));
        assertEquals("provider", ex.getMessage());
    }

    @Test
    void constructor_throwsOnNullExecutor() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> new BraneAsyncClient(mockProvider, null));
        assertEquals("executor", ex.getMessage());
    }

    @Test
    void constructor_acceptsValidProviderAndExecutor() {
        BraneAsyncClient client = new BraneAsyncClient(mockProvider, mockExecutor);
        assertNotNull(client);
        assertEquals(mockProvider, client.getProvider());
        assertEquals(mockExecutor, client.getExecutor());
    }

    @Test
    void constructor_withProviderOnly_usesDefaultExecutor() {
        BraneAsyncClient client = new BraneAsyncClient(mockProvider);
        assertNotNull(client);
        assertEquals(mockProvider, client.getProvider());
        assertNotNull(client.getExecutor());
    }

    // ==================== sendAsync delegation to WebSocketProvider tests ====================

    @Test
    void sendAsync_delegatesToWebSocketProviderNativeAsync() throws Exception {
        JsonRpcResponse expectedResponse = new JsonRpcResponse("2.0", "0x10", null, "1");
        CompletableFuture<JsonRpcResponse> expectedFuture = CompletableFuture.completedFuture(expectedResponse);

        when(mockWebSocketProvider.sendAsync(eq("eth_blockNumber"), eq(List.of())))
                .thenReturn(expectedFuture);

        BraneAsyncClient client = new BraneAsyncClient(mockWebSocketProvider, mockExecutor);
        CompletableFuture<JsonRpcResponse> result = client.sendAsync("eth_blockNumber", List.of());

        // Should return the same future from WebSocketProvider
        assertSame(expectedFuture, result);

        // Verify WebSocketProvider's sendAsync was called
        verify(mockWebSocketProvider).sendAsync("eth_blockNumber", List.of());

        // Verify synchronous send was NOT called
        verify(mockWebSocketProvider, never()).send(any(), any());
    }

    @Test
    void sendAsync_webSocketProvider_withParams() throws Exception {
        List<Object> params = List.of("0x1234567890abcdef", true);
        JsonRpcResponse expectedResponse = new JsonRpcResponse("2.0", Map.of("result", "data"), null, "1");
        CompletableFuture<JsonRpcResponse> expectedFuture = CompletableFuture.completedFuture(expectedResponse);

        when(mockWebSocketProvider.sendAsync(eq("eth_getBlockByHash"), eq(params)))
                .thenReturn(expectedFuture);

        BraneAsyncClient client = new BraneAsyncClient(mockWebSocketProvider, mockExecutor);
        CompletableFuture<JsonRpcResponse> result = client.sendAsync("eth_getBlockByHash", params);

        assertSame(expectedFuture, result);
        verify(mockWebSocketProvider).sendAsync("eth_getBlockByHash", params);
    }

    @Test
    void sendAsync_webSocketProvider_handlesException() {
        RuntimeException expectedException = new RuntimeException("Connection lost");
        CompletableFuture<JsonRpcResponse> failedFuture = CompletableFuture.failedFuture(expectedException);

        when(mockWebSocketProvider.sendAsync(any(), any())).thenReturn(failedFuture);

        BraneAsyncClient client = new BraneAsyncClient(mockWebSocketProvider, mockExecutor);
        CompletableFuture<JsonRpcResponse> result = client.sendAsync("eth_chainId", List.of());

        assertTrue(result.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, result::get);
        assertSame(expectedException, ex.getCause());
    }

    // ==================== sendAsync wrapping sync call tests ====================

    @Test
    void sendAsync_wrapsNonWebSocketProviderSyncCall() throws Exception {
        JsonRpcResponse expectedResponse = new JsonRpcResponse("2.0", "0x1", null, "1");
        when(mockProvider.send(eq("eth_chainId"), eq(List.of()))).thenReturn(expectedResponse);

        // Use a direct executor to make the test synchronous
        Executor directExecutor = Runnable::run;
        BraneAsyncClient client = new BraneAsyncClient(mockProvider, directExecutor);

        CompletableFuture<JsonRpcResponse> result = client.sendAsync("eth_chainId", List.of());

        // Should complete with the expected response
        JsonRpcResponse actualResponse = result.get(1, TimeUnit.SECONDS);
        assertEquals(expectedResponse, actualResponse);

        // Verify synchronous send was called
        verify(mockProvider).send("eth_chainId", List.of());
    }

    @Test
    void sendAsync_wrapsNonWebSocketProvider_withParams() throws Exception {
        List<Object> params = List.of("0xabc123", "latest");
        JsonRpcResponse expectedResponse = new JsonRpcResponse("2.0", "0x100", null, "1");
        when(mockProvider.send(eq("eth_getBalance"), eq(params))).thenReturn(expectedResponse);

        Executor directExecutor = Runnable::run;
        BraneAsyncClient client = new BraneAsyncClient(mockProvider, directExecutor);

        CompletableFuture<JsonRpcResponse> result = client.sendAsync("eth_getBalance", params);

        JsonRpcResponse actualResponse = result.get(1, TimeUnit.SECONDS);
        assertEquals(expectedResponse, actualResponse);
        verify(mockProvider).send("eth_getBalance", params);
    }

    @Test
    void sendAsync_wrapsNonWebSocketProvider_handlesException() {
        RuntimeException expectedException = new RuntimeException("Network error");
        when(mockProvider.send(any(), any())).thenThrow(expectedException);

        Executor directExecutor = Runnable::run;
        BraneAsyncClient client = new BraneAsyncClient(mockProvider, directExecutor);

        CompletableFuture<JsonRpcResponse> result = client.sendAsync("eth_call", List.of());

        assertTrue(result.isCompletedExceptionally());
        ExecutionException ex = assertThrows(ExecutionException.class, result::get);
        assertSame(expectedException, ex.getCause());
    }

    @Test
    void sendAsync_usesProvidedExecutor() throws Exception {
        JsonRpcResponse expectedResponse = new JsonRpcResponse("2.0", "result", null, "1");
        when(mockProvider.send(any(), any())).thenReturn(expectedResponse);

        // Create a counting executor to verify it's used
        java.util.concurrent.atomic.AtomicInteger executorCallCount = new java.util.concurrent.atomic.AtomicInteger(0);
        Executor countingExecutor = runnable -> {
            executorCallCount.incrementAndGet();
            runnable.run();
        };

        BraneAsyncClient client = new BraneAsyncClient(mockProvider, countingExecutor);
        CompletableFuture<JsonRpcResponse> result = client.sendAsync("eth_test", List.of());

        result.get(1, TimeUnit.SECONDS);
        assertEquals(1, executorCallCount.get());
    }

    // ==================== shutdownDefaultExecutor lifecycle tests ====================

    @Test
    void shutdownDefaultExecutor_returnsTrue_whenExecutorNotInitialized() {
        // Ensure executor is not initialized by shutting down any existing one
        BraneAsyncClient.shutdownDefaultExecutor(100);

        // Should return true immediately when executor is null
        boolean result = BraneAsyncClient.shutdownDefaultExecutor(100);
        assertTrue(result);
    }

    @Test
    void shutdownDefaultExecutor_shutsDownExecutor() throws Exception {
        // Initialize the default executor by creating a client
        BraneAsyncClient client = new BraneAsyncClient(mockProvider);
        Executor defaultExecutor = client.getExecutor();
        assertNotNull(defaultExecutor);

        // Shutdown should succeed
        boolean result = BraneAsyncClient.shutdownDefaultExecutor(1000);
        assertTrue(result);
    }

    @Test
    void shutdownDefaultExecutor_allowsNewExecutorCreation() throws Exception {
        // Create client to initialize executor
        BraneAsyncClient client1 = new BraneAsyncClient(mockProvider);
        Executor executor1 = client1.getExecutor();

        // Shutdown executor
        boolean shutdownResult = BraneAsyncClient.shutdownDefaultExecutor(1000);
        assertTrue(shutdownResult);

        // Create new client - should get a new executor
        BraneAsyncClient client2 = new BraneAsyncClient(mockProvider);
        Executor executor2 = client2.getExecutor();

        assertNotNull(executor2);
        // The executors should be different instances
        assertNotSame(executor1, executor2);
    }

    @Test
    void shutdownDefaultExecutor_isIdempotent() {
        // First shutdown
        boolean result1 = BraneAsyncClient.shutdownDefaultExecutor(100);
        assertTrue(result1);

        // Second shutdown should also return true (no-op)
        boolean result2 = BraneAsyncClient.shutdownDefaultExecutor(100);
        assertTrue(result2);

        // Third shutdown should also return true
        boolean result3 = BraneAsyncClient.shutdownDefaultExecutor(100);
        assertTrue(result3);
    }

    @Test
    void shutdownDefaultExecutorNow_returnsEmptyListWhenNotInitialized() {
        // Ensure executor is not initialized
        BraneAsyncClient.shutdownDefaultExecutor(100);

        List<Runnable> tasks = BraneAsyncClient.shutdownDefaultExecutorNow();
        assertNotNull(tasks);
        assertTrue(tasks.isEmpty());
    }

    @Test
    void shutdownDefaultExecutorNow_shutsDownImmediately() {
        // Initialize the default executor
        BraneAsyncClient client = new BraneAsyncClient(mockProvider);
        assertNotNull(client.getExecutor());

        // ShutdownNow should work
        List<Runnable> tasks = BraneAsyncClient.shutdownDefaultExecutorNow();
        assertNotNull(tasks);
    }

    @Test
    void shutdownDefaultExecutorNow_isIdempotent() {
        // First shutdown
        List<Runnable> tasks1 = BraneAsyncClient.shutdownDefaultExecutorNow();
        assertNotNull(tasks1);

        // Second shutdown should also work
        List<Runnable> tasks2 = BraneAsyncClient.shutdownDefaultExecutorNow();
        assertNotNull(tasks2);
        assertTrue(tasks2.isEmpty());
    }

    // ==================== getProvider and getExecutor tests ====================

    @Test
    void getProvider_returnsConfiguredProvider() {
        BraneAsyncClient client = new BraneAsyncClient(mockProvider, mockExecutor);
        assertSame(mockProvider, client.getProvider());
    }

    @Test
    void getExecutor_returnsConfiguredExecutor() {
        BraneAsyncClient client = new BraneAsyncClient(mockProvider, mockExecutor);
        assertSame(mockExecutor, client.getExecutor());
    }

}
