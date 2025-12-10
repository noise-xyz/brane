package io.brane.benchmark;

import io.brane.rpc.*;
import java.util.*;
import java.util.concurrent.*;

/**
 * Quick benchmark to measure all provider types against local Anvil.
 *
 * <p>
 * This benchmark provides a fast way to compare HTTP (Loom) vs WebSocket
 * providers
 * without the overhead of full JMH. Results are printed to stdout for quick
 * analysis.
 *
 * <p>
 * <strong>Prerequisites:</strong>
 * <ul>
 * <li>Anvil running on localhost: {@code anvil --host 0.0.0.0 --port 8545}</li>
 * </ul>
 *
 * <p>
 * <strong>Usage:</strong>
 * 
 * <pre>{@code
 * ./gradlew :brane-benchmark:runQuickBenchmark
 * }</pre>
 *
 * <p>
 * <strong>Output Metrics:</strong>
 * <ul>
 * <li><strong>ops/s</strong> - Operations per second (higher is better)</li>
 * <li><strong>ms/op</strong> - Milliseconds per operation (lower is
 * better)</li>
 * </ul>
 *
 * @since 0.2.0
 */
public class QuickProviderBenchmark {
    private static final String HTTP_URL = "http://127.0.0.1:8545";
    private static final String WS_URL = "ws://127.0.0.1:8545";
    private static final int WARMUP = 100;
    private static final int ITERATIONS = 1000;

    public static void main(String[] args) throws Exception {
        System.out.println("=====================================");
        System.out.println("Quick Provider Comparison Benchmark");
        System.out.println("=====================================");
        System.out.println();

        // HTTP Provider
        System.out.println("1. HTTP Provider (Loom virtual threads)");
        System.out.println("----------------------------------------");
        BraneProvider http = HttpBraneProvider.builder(HTTP_URL).build();
        benchmarkSync("HTTP blockNumber", http, "eth_blockNumber", List.of());
        benchmarkSync("HTTP getBalance", http, "eth_getBalance",
                List.of("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", "latest"));
        System.out.println();

        // WebSocket sendAsync
        System.out.println("2. WebSocket sendAsync (single requests)");
        System.out.println("----------------------------------------");
        try (WebSocketProvider ws = WebSocketProvider.create(WS_URL)) {
            // Warmup
            for (int i = 0; i < WARMUP; i++) {
                ws.sendAsync("eth_chainId", List.of()).join();
            }

            benchmarkAsync("WS sendAsync blockNumber", ws, "eth_blockNumber", List.of());
            benchmarkAsync("WS sendAsync getBalance", ws, "eth_getBalance",
                    List.of("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", "latest"));
            System.out.println();

            // WebSocket sendAsyncBatch
            System.out.println("3. WebSocket sendAsyncBatch (Disruptor batching)");
            System.out.println("------------------------------------------------");
            benchmarkBatch("WS batch blockNumber", ws, "eth_blockNumber", List.of());
            benchmarkBatch("WS batch getBalance", ws, "eth_getBalance",
                    List.of("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", "latest"));
            System.out.println();

            // Batch 100 comparison
            System.out.println("4. Batch 100 Parallel Requests");
            System.out.println("------------------------------");
            benchmarkParallel100("HTTP batch100", http, ws, false);
            benchmarkParallel100("WS batch100", http, ws, true);
        }

        System.out.println();
        System.out.println("=====================================");
        System.out.println("Benchmark Complete");
        System.out.println("=====================================");
    }

    private static void benchmarkSync(String name, BraneProvider provider, String method, List<?> params) {
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            provider.send(method, params);
        }
        long end = System.nanoTime();
        double ops = (double) ITERATIONS / ((end - start) / 1_000_000_000.0);
        double latencyMs = ((end - start) / 1_000_000.0) / ITERATIONS;
        System.out.printf("  %-25s: %8.1f ops/s | %.3f ms/op%n", name, ops, latencyMs);
    }

    private static void benchmarkAsync(String name, WebSocketProvider ws, String method, List<?> params)
            throws Exception {
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            ws.sendAsync(method, params).join();
        }
        long end = System.nanoTime();
        double ops = (double) ITERATIONS / ((end - start) / 1_000_000_000.0);
        double latencyMs = ((end - start) / 1_000_000.0) / ITERATIONS;
        System.out.printf("  %-25s: %8.1f ops/s | %.3f ms/op%n", name, ops, latencyMs);
    }

    private static void benchmarkBatch(String name, WebSocketProvider ws, String method, List<?> params)
            throws Exception {
        long start = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            ws.sendAsyncBatch(method, params).join();
        }
        long end = System.nanoTime();
        double ops = (double) ITERATIONS / ((end - start) / 1_000_000_000.0);
        double latencyMs = ((end - start) / 1_000_000.0) / ITERATIONS;
        System.out.printf("  %-25s: %8.1f ops/s | %.3f ms/op%n", name, ops, latencyMs);
    }

    private static void benchmarkParallel100(String name, BraneProvider http, WebSocketProvider ws, boolean useWs)
            throws Exception {
        int batchSize = 100;
        int batches = 10;

        long start = System.nanoTime();
        if (useWs) {
            for (int b = 0; b < batches; b++) {
                List<CompletableFuture<JsonRpcResponse>> futures = new ArrayList<>();
                for (int i = 0; i < batchSize; i++) {
                    futures.add(ws.sendAsyncBatch("eth_blockNumber", List.of()));
                }
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }
        } else {
            // Create executor once outside the loop to avoid overhead per batch
            try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
                for (int b = 0; b < batches; b++) {
                    List<Future<?>> futures = new ArrayList<>();
                    for (int i = 0; i < batchSize; i++) {
                        futures.add(exec.submit(() -> http.send("eth_blockNumber", List.of())));
                    }
                    for (var f : futures)
                        f.get();
                }
            }
        }
        long end = System.nanoTime();
        int totalOps = batchSize * batches;
        double ops = (double) totalOps / ((end - start) / 1_000_000_000.0);
        System.out.printf("  %-25s: %8.1f ops/s%n", name, ops);
    }
}
