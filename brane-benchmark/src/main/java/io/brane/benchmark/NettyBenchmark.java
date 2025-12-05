package io.brane.benchmark;

import io.brane.rpc.NettyBraneProvider;
import io.brane.rpc.JsonRpcResponse;

import java.util.Collections;
import java.util.concurrent.CompletableFuture;

public class NettyBenchmark {

    private static final String WS_URL = "ws://127.0.0.1:8545";
    private static final int ITERATIONS = 10000;

    public static void main(String[] args) throws Exception {
        System.out.println("🚀 Starting Netty Provider Benchmark");

        try (NettyBraneProvider provider = NettyBraneProvider.create(WS_URL)) {
            // Warmup
            System.out.print("Warming up...");
            for (int i = 0; i < 100; i++) {
                provider.sendAsync("eth_blockNumber", Collections.emptyList()).join();
            }
            System.out.println(" Done.");

            long start = System.nanoTime();
            CompletableFuture<?>[] futures = new CompletableFuture[ITERATIONS];

            for (int i = 0; i < ITERATIONS; i++) {
                futures[i] = provider.sendAsync("eth_blockNumber", Collections.emptyList());
            }

            CompletableFuture.allOf(futures).join();
            long end = System.nanoTime();

            double durationMs = (end - start) / 1_000_000.0;
            double throughput = (ITERATIONS * 1000.0) / durationMs;

            System.out.printf("Total Time: %.2f ms%n", durationMs);
            System.out.printf("Throughput: %.2f ops/s%n", throughput);
        }
    }
}
