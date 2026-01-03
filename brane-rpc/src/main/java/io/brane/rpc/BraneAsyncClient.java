package io.brane.rpc;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

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
 * across multiple threads. The default executor is managed using atomic operations:
 * <ul>
 *   <li>{@link #getOrCreateDefaultExecutor()} uses compare-and-exchange for lazy initialization</li>
 *   <li>{@link #shutdownDefaultExecutor(long)} atomically nullifies before shutdown to prevent races</li>
 *   <li>Concurrent shutdown and creation calls are safe - shutdown only affects the executor
 *       that was current at call time, and new executors are created as needed</li>
 * </ul>
 *
 * <p>
 * <strong>Executor Lifecycle:</strong> The default executor is automatically cleaned
 * up via a JVM shutdown hook, so explicit shutdown is not required for normal usage.
 * However, in container environments (e.g., Spring Boot with context reloads) or test
 * frameworks, call {@link #shutdownDefaultExecutor(long)} explicitly to prevent thread
 * leaks between reloads. The executor will be lazily recreated if needed after shutdown.
 *
 * @since 0.2.0
 */
public final class BraneAsyncClient {

    /**
     * Lazily-initialized shared executor for async operations.
     *
     * <p>Uses lazy initialization to support lifecycle management via
     * {@link #shutdownDefaultExecutor(long)} for testing and container environments.
     * A shutdown hook is registered to clean up the executor on JVM exit.
     */
    private static final AtomicReference<ExecutorService> DEFAULT_EXECUTOR_REF = new AtomicReference<>();

    static {
        // Register shutdown hook to clean up the default executor on JVM exit.
        // Uses shutdownNow() for fast cleanup during shutdown.
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            ExecutorService executor = DEFAULT_EXECUTOR_REF.get();
            if (executor != null && !executor.isShutdown()) {
                executor.shutdownNow();
            }
        }, "brane-async-shutdown"));
    }

    private static ExecutorService getOrCreateDefaultExecutor() {
        // Loop handles the case where another thread shuts down the executor between checks
        while (true) {
            ExecutorService executor = DEFAULT_EXECUTOR_REF.get();
            if (executor != null && !executor.isShutdown()) {
                return executor;
            }
            ExecutorService newExecutor = Executors.newThreadPerTaskExecutor(
                    Thread.ofVirtual().name("brane-async-", 0).factory());
            ExecutorService witness = DEFAULT_EXECUTOR_REF.compareAndExchange(executor, newExecutor);
            if (witness == executor) {
                // CAS succeeded, we installed our executor
                return newExecutor;
            }
            // Another thread won the race, shut down our executor
            newExecutor.shutdown();
            // Check if witness is usable, otherwise loop again
            if (witness != null && !witness.isShutdown()) {
                return witness;
            }
            // witness is null or shutdown, loop and try again
        }
    }

    /**
     * Shuts down the shared default executor for testing or container lifecycle management.
     *
     * <p>This method is primarily intended for:
     * <ul>
     *   <li>Test cleanup to prevent thread leaks in test frameworks</li>
     *   <li>Container environments (e.g., Spring Boot context reloads)</li>
     *   <li>Applications that require graceful shutdown</li>
     * </ul>
     *
     * <p>After shutdown, new {@link BraneAsyncClient} instances using the default
     * executor will create a fresh executor automatically.
     *
     * <p><strong>Thread Safety:</strong> This method uses atomic compare-and-set to
     * nullify the executor reference before initiating shutdown. This prevents race
     * conditions where concurrent calls to {@link #getOrCreateDefaultExecutor()} could
     * see a shutdown-in-progress executor. The nullification ensures that:
     * <ul>
     *   <li>Concurrent creators will create a new executor instead of using the shutting-down one</li>
     *   <li>Only one thread will perform the actual shutdown</li>
     *   <li>The executor being shut down is exactly the one that was atomically retrieved</li>
     * </ul>
     *
     * @param timeoutMillis maximum time to wait for pending tasks to complete
     * @return true if the executor terminated within the timeout (or was already null/shutdown),
     *         false if timed out or interrupted
     */
    public static boolean shutdownDefaultExecutor(long timeoutMillis) {
        // Atomically get-and-nullify to prevent race with getOrCreateDefaultExecutor().
        // This ensures we shut down exactly the executor we retrieved and that concurrent
        // callers will create a new executor instead of using this shutting-down one.
        ExecutorService executor = DEFAULT_EXECUTOR_REF.getAndSet(null);
        if (executor == null || executor.isShutdown()) {
            return true;
        }
        executor.shutdown();
        try {
            return executor.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Immediately shuts down the default executor without waiting.
     *
     * <p>Use this for immediate cleanup when pending tasks can be abandoned.
     *
     * <p><strong>Thread Safety:</strong> This method atomically nullifies the executor
     * reference before calling {@code shutdownNow()}, preventing race conditions with
     * concurrent {@link #getOrCreateDefaultExecutor()} calls.
     *
     * @return list of tasks that were awaiting execution, or empty list if executor was null/shutdown
     */
    public static List<Runnable> shutdownDefaultExecutorNow() {
        // Atomically get-and-nullify to prevent race with getOrCreateDefaultExecutor()
        ExecutorService executor = DEFAULT_EXECUTOR_REF.getAndSet(null);
        if (executor != null && !executor.isShutdown()) {
            return executor.shutdownNow();
        }
        return List.of();
    }

    private final BraneProvider provider;
    private final Executor executor;

    /**
     * Creates an async client with virtual thread executor (default).
     *
     * @param provider the underlying RPC provider
     */
    public BraneAsyncClient(BraneProvider provider) {
        this(provider, getOrCreateDefaultExecutor());
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
