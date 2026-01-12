// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.benchmark;

import io.brane.rpc.Brane;

/**
 * Manual (non-JMH) benchmark for quick WebSocket RPC performance testing.
 *
 * <p>Runs a simple warmup + measurement loop to measure:
 * <ul>
 *   <li>Latency (ops/sec) for eth_chainId calls</li>
 *   <li>Sequential throughput (ops/sec) for eth_getBlockByNumber calls</li>
 * </ul>
 *
 * <p>Requires Anvil running on ws://127.0.0.1:8545.
 * Run via: {@code ./gradlew :brane-benchmark:runManualBenchmark}
 */
public class ManualBenchmark {

    private static final String WS_URL = "ws://127.0.0.1:8545";
    private static final int WARMUP_ITERATIONS = 100;
    private static final int ITERATIONS = 1000;

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Manual Benchmark...");

        try (Brane client = Brane.connect(WS_URL)) {

            // Warmup
            System.out.println("Warming up...");
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                client.chainId();
            }

            // Latency Test
            System.out.println("Running Latency Test (eth_chainId)...");
            long start = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                client.chainId();
            }
            long end = System.nanoTime();
            double latencyOps = (double) ITERATIONS / ((end - start) / 1_000_000_000.0);
            System.out.printf("Latency: %.2f ops/s%n", latencyOps);

            // Throughput Test (Sequential)
            System.out.println("Running Throughput Test (eth_blockNumber)...");

            start = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                // Brane methods are sync (blocking), so this measures sequential
                // throughput (latency-bound).
                // To measure true throughput we need async API or multiple threads.
                client.getLatestBlock();
            }
            end = System.nanoTime();
            double throughputOps = (double) ITERATIONS / ((end - start) / 1_000_000_000.0);
            System.out.printf("Throughput (Sequential): %.2f ops/s%n", throughputOps);

        }
    }
}
