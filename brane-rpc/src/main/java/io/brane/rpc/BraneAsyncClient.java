package io.brane.rpc;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

/**
 * Async wrapper around {@link BraneProvider} for users who prefer futures.
 *
 * <p>
 * This facade provides a {@link CompletableFuture}-based API that delegates
 * to the underlying synchronous provider. By default, it uses virtual threads
 * for I/O-bound operations.
 *
 * <p>
 * <strong>Usage:</strong>
 * 
 * <pre>{@code
 * BraneProvider provider = HttpBraneProvider.create(config);
 * BraneAsyncClient client = new BraneAsyncClient(provider);
 *
 * client.sendAsync("eth_blockNumber", List.of())
 *         .thenAccept(response -> System.out.println(response.result()));
 * }</pre>
 *
 * <p>
 * <strong>WebSocket Optimization:</strong> When the underlying provider is a
 * {@link WebSocketProvider}, this facade delegates directly to its native
 * {@code sendAsync} method for optimal performance, bypassing the executor.
 *
 * <p>
 * <strong>Thread Safety:</strong> This class is thread-safe and can be shared
 * across multiple threads.
 *
 * @since 0.2.0
 */
public final class BraneAsyncClient {

    /**
     * Shared default executor for async operations.
     * Virtual thread executors are lightweight, but sharing avoids unnecessary
     * object creation. Named threads aid debugging and profiling.
     */
    private static final Executor DEFAULT_EXECUTOR = Executors.newThreadPerTaskExecutor(
            Thread.ofVirtual().name("brane-async-", 0).factory());

    private final BraneProvider provider;
    private final Executor executor;

    /**
     * Creates an async client with virtual thread executor (default).
     *
     * @param provider the underlying RPC provider
     */
    public BraneAsyncClient(BraneProvider provider) {
        this(provider, DEFAULT_EXECUTOR);
    }

    /**
     * Creates an async client with a custom executor.
     *
     * @param provider the underlying RPC provider
     * @param executor the executor for async operations
     */
    public BraneAsyncClient(BraneProvider provider, Executor executor) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.executor = Objects.requireNonNull(executor, "executor");
    }

    /**
     * Sends an asynchronous JSON-RPC request.
     *
     * <p>
     * If the underlying provider is a {@link WebSocketProvider}, this method
     * delegates to its native {@code sendAsync} for optimal performance.
     * Otherwise, it wraps the synchronous {@code send} in a CompletableFuture.
     *
     * @param method the JSON-RPC method name
     * @param params the method parameters
     * @return a CompletableFuture that completes with the response
     */
    public CompletableFuture<JsonRpcResponse> sendAsync(String method, List<?> params) {
        // Optimize: delegate to WebSocketProvider's native async if available
        if (provider instanceof WebSocketProvider wsProvider) {
            return wsProvider.sendAsync(method, params);
        }

        // Otherwise, wrap sync call in CompletableFuture
        return CompletableFuture.supplyAsync(
                () -> provider.send(method, params),
                executor);
    }

    /**
     * Returns the underlying provider.
     *
     * @return the provider
     */
    public BraneProvider getProvider() {
        return provider;
    }

    /**
     * Returns the executor used for async operations.
     *
     * @return the executor
     */
    public Executor getExecutor() {
        return executor;
    }
}
