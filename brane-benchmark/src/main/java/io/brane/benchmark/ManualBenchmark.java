package io.brane.benchmark;

import io.brane.rpc.PublicClient;
import io.brane.rpc.WebSocketProvider;

public class ManualBenchmark {

    private static final String WS_URL = "ws://127.0.0.1:8545";
    private static final int WARMUP_ITERATIONS = 100;
    private static final int ITERATIONS = 1000;

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Manual Benchmark...");

        try (WebSocketProvider provider = WebSocketProvider.create(WS_URL)) {
            PublicClient client = PublicClient.from(provider);

            // Warmup
            System.out.println("Warming up...");
            for (int i = 0; i < WARMUP_ITERATIONS; i++) {
                client.getChainId();
            }

            // Latency Test
            System.out.println("Running Latency Test (eth_chainId)...");
            long start = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                client.getChainId();
            }
            long end = System.nanoTime();
            double latencyOps = (double) ITERATIONS / ((end - start) / 1_000_000_000.0);
            System.out.printf("Latency: %.2f ops/s%n", latencyOps);

            // Throughput Test (Async)
            System.out.println("Running Throughput Test (eth_blockNumber)...");

            start = System.nanoTime();
            for (int i = 0; i < ITERATIONS; i++) {
                // Assuming getLatestBlock is synchronous in PublicClient interface for now,
                // but if we want throughput we should use async if available or just hammer it.
                // Since PublicClient methods are sync (blocking), this measures sequential
                // throughput (latency-bound).
                // To measure true throughput we need async API or multiple threads.
                // But wait, WebSocketProvider is async internally?
                // PublicClient methods return values, so they block.
                // So this test is actually measuring sequential throughput, which is similar to
                // latency.
                // To measure max throughput, we'd need to use the provider directly or an async
                // client.
                // But for now, let's just measure what we have.
                client.getLatestBlock();
            }
            end = System.nanoTime();
            double throughputOps = (double) ITERATIONS / ((end - start) / 1_000_000_000.0);
            System.out.printf("Throughput (Sequential): %.2f ops/s%n", throughputOps);

        }
    }
}
