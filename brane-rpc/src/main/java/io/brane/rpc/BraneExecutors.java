// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory methods for creating executors optimized for different workloads.
 *
 * <p>
 * Brane's philosophy is to use the right executor for the job:
 * <ul>
 * <li><strong>I/O-bound work</strong> (network requests, file I/O): Use virtual
 * threads</li>
 * <li><strong>CPU-bound work</strong> (crypto, heavy parsing): Use bounded
 * platform threads</li>
 * </ul>
 *
 * <p>
 * <strong>Usage:</strong>
 *
 * <pre>{@code
 * // For I/O-bound tasks (e.g., parallel RPC calls)
 * try (var exec = BraneExecutors.newIoBoundExecutor()) {
 *     for (int i = 0; i < 10_000; i++) {
 *         exec.submit(() -> provider.send("eth_blockNumber", List.of()));
 *     }
 * }
 *
 * // For CPU-bound tasks (e.g., signature verification, heavy parsing)
 * try (var exec = BraneExecutors.newCpuBoundExecutor()) {
 *     for (var sig : signatures) {
 *         exec.submit(() -> crypto.verify(sig));
 *     }
 * }
 * }</pre>
 *
 * <p>
 * <strong>Why?</strong> Virtual threads excel at I/O-bound work because they
 * can
 * efficiently park/unpark on blocking operations. However, for CPU-intensive
 * tasks,
 * having more threads than CPU cores just adds scheduling overhead. Use
 * {@link #newCpuBoundExecutor()} for such workloads.
 *
 * @since 0.2.0
 */
public final class BraneExecutors {

    /**
     * Counter for unique CPU worker thread names.
     */
    private static final AtomicInteger CPU_THREAD_ID = new AtomicInteger(0);

    private BraneExecutors() {
        // Utility class
    }

    /**
     * Creates an executor for I/O-bound work using virtual threads.
     *
     * <p>
     * This is the default choice for most Brane operations like RPC calls,
     * subscriptions, and network I/O. Virtual threads efficiently handle
     * blocking operations without consuming platform thread resources.
     *
     * <p>
     * The returned executor creates a new virtual thread for each task.
     * Threads are named {@code brane-io-N} for easier debugging.
     *
     * @return a virtual-thread-per-task executor
     */
    public static ExecutorService newIoBoundExecutor() {
        return Executors.newThreadPerTaskExecutor(
                Thread.ofVirtual()
                        .name("brane-io-", 0)
                        .factory());
    }

    /**
     * Creates an executor for CPU-bound work using platform threads.
     *
     * <p>
     * The pool size matches the number of available processors, which is
     * optimal for CPU-intensive tasks like cryptographic operations, RLP
     * encoding, or heavy JSON parsing.
     *
     * <p>
     * Use this instead of virtual threads when:
     * <ul>
     * <li>Tasks are CPU-intensive (not I/O-bound)</li>
     * <li>Tasks rarely block on I/O</li>
     * <li>You want predictable, bounded parallelism</li>
     * </ul>
     *
     * @return a fixed-size thread pool with platform threads
     */
    public static ExecutorService newCpuBoundExecutor() {
        int cores = Runtime.getRuntime().availableProcessors();
        return newCpuBoundExecutor(cores);
    }

    /**
     * Creates a CPU-bound executor with a custom number of threads.
     *
     * <p>
     * Useful when you want more control over parallelism, for example
     * to reserve some cores for other work.
     *
     * @param threads the number of threads in the pool
     * @return a fixed-size thread pool with platform threads
     * @throws IllegalArgumentException if threads is less than 1
     */
    public static ExecutorService newCpuBoundExecutor(int threads) {
        if (threads < 1) {
            throw new IllegalArgumentException("threads must be at least 1, got: " + threads);
        }
        return Executors.newFixedThreadPool(threads, r -> {
            // Mask off sign bit to ensure non-negative thread IDs even after integer overflow
            int id = CPU_THREAD_ID.getAndIncrement() & 0x7FFFFFFF;
            Thread t = new Thread(r, "brane-cpu-worker-" + id);
            t.setDaemon(true);
            return t;
        });
    }
}
