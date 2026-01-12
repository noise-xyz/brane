// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.benchmark;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.WebSocketService;

import io.brane.rpc.WebSocketProvider;

/**
 * Comprehensive benchmark comparing Brane WebSocketProvider vs Web3j.
 *
 * Tests:
 * 1. Single request latency (p50, p95, p99)
 * 2. Burst throughput (5, 10, 20 concurrent requests)
 * 3. Sustained throughput (requests per second over 10 seconds)
 * 4. Mixed workload (latency-sensitive + batch)
 */
public class ComprehensiveBenchmark {

    private static final String[] NETWORKS = {
            "wss://base-rpc.publicnode.com",
            "wss://arbitrum-one-rpc.publicnode.com",
            "wss://ethereum-rpc.publicnode.com"
    };

    private static final String[] NETWORK_NAMES = {
            "Base Mainnet",
            "Arbitrum One",
            "Ethereum Mainnet"
    };

    private static final int LATENCY_SAMPLES = 50;
    private static final int BURST_SIZES[] = { 5, 10, 20 };
    private static final int SUSTAINED_DURATION_SECONDS = 10;
    private static final long WARMUP_MS = 2000;

    public static void main(String[] args) throws Exception {
        System.out.println("╔════════════════════════════════════════════════════════════════════╗");
        System.out.println("║       BRANE vs WEB3J COMPREHENSIVE BENCHMARK                       ║");
        System.out.println("╚════════════════════════════════════════════════════════════════════╝");
        System.out.println();

        StringBuilder results = new StringBuilder();
        results.append("# Benchmark Results\n\n");
        results.append("| Network | Test | Brane | Web3j | Winner | Speedup |\n");
        results.append("|---------|------|-------|-------|--------|--------|\n");

        for (int i = 0; i < NETWORKS.length; i++) {
            String url = NETWORKS[i];
            String name = NETWORK_NAMES[i];

            System.out.println("\n┌─────────────────────────────────────────────────────────────────────┐");
            System.out.println("│ Network: " + name);
            System.out.println("│ URL: " + url);
            System.out.println("└─────────────────────────────────────────────────────────────────────┘");

            try {
                runBenchmarkSuite(url, name, results);
            } catch (Exception e) {
                System.err.println("  ✗ Failed: " + e.getMessage());
            }
        }

        System.out.println("\n" + results.toString());
    }

    private static void runBenchmarkSuite(String url, String networkName, StringBuilder results) throws Exception {
        // Connect both providers
        try (WebSocketProvider brane = WebSocketProvider.create(url)) {
            WebSocketService wsService = new WebSocketService(url, false);
            wsService.connect();
            Web3j web3j = Web3j.build(wsService);

            try {
                // Warmup
                System.out.println("\n  ⏳ Warming up...");
                warmup(brane, web3j);

                // Test 1: Single Request Latency
                System.out.println("\n  📊 Test 1: Single Request Latency");
                LatencyResult braneLatency = measureLatency(brane);
                LatencyResult web3jLatency = measureLatency(web3j);
                printLatencyComparison(braneLatency, web3jLatency);
                addLatencyResults(results, networkName, braneLatency, web3jLatency);

                Thread.sleep(1000);

                // Test 2: Burst Throughput
                System.out.println("\n  📊 Test 2: Burst Throughput");
                for (int burstSize : BURST_SIZES) {
                    double braneTps = measureBurstThroughput(brane, burstSize);
                    double web3jTps = measureBurstThroughput(web3j, burstSize);
                    printBurstComparison(burstSize, braneTps, web3jTps);
                    addBurstResult(results, networkName, burstSize, braneTps, web3jTps);
                    Thread.sleep(500);
                }

                Thread.sleep(1000);

                // Test 3: Sustained Throughput
                System.out.println("\n  📊 Test 3: Sustained Throughput (" + SUSTAINED_DURATION_SECONDS + "s)");
                double braneSustained = measureSustainedThroughput(brane);
                Thread.sleep(2000);
                double web3jSustained = measureSustainedThroughput(web3j);
                printSustainedComparison(braneSustained, web3jSustained);
                addSustainedResult(results, networkName, braneSustained, web3jSustained);

            } finally {
                wsService.close();
            }
        }
    }

    private static void warmup(WebSocketProvider brane, Web3j web3j) throws Exception {
        // Warmup Brane
        for (int i = 0; i < 10; i++) {
            brane.sendAsync("eth_chainId", Collections.emptyList()).join();
        }

        // Warmup Web3j
        for (int i = 0; i < 10; i++) {
            web3j.ethChainId().send();
        }

        Thread.sleep(WARMUP_MS);
    }

    // ==================== LATENCY ====================

    static class LatencyResult {
        double avg, p50, p95, p99, min, max;
    }

    private static LatencyResult measureLatency(WebSocketProvider provider) throws Exception {
        List<Long> latencies = new ArrayList<>(LATENCY_SAMPLES);

        for (int i = 0; i < LATENCY_SAMPLES; i++) {
            long start = System.nanoTime();
            provider.sendAsync("eth_blockNumber", Collections.emptyList()).join();
            long end = System.nanoTime();
            latencies.add((end - start) / 1000); // microseconds
            Thread.sleep(100); // Small delay between requests
        }

        return calculateLatencyStats(latencies);
    }

    private static LatencyResult measureLatency(Web3j web3j) throws Exception {
        List<Long> latencies = new ArrayList<>(LATENCY_SAMPLES);

        for (int i = 0; i < LATENCY_SAMPLES; i++) {
            long start = System.nanoTime();
            web3j.ethBlockNumber().send();
            long end = System.nanoTime();
            latencies.add((end - start) / 1000); // microseconds
            Thread.sleep(100);
        }

        return calculateLatencyStats(latencies);
    }

    private static LatencyResult calculateLatencyStats(List<Long> latencies) {
        Collections.sort(latencies);
        LatencyResult result = new LatencyResult();

        result.avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0; // ms
        result.p50 = latencies.get((int) (latencies.size() * 0.50)) / 1000.0;
        result.p95 = latencies.get((int) (latencies.size() * 0.95)) / 1000.0;
        result.p99 = latencies.get((int) (latencies.size() * 0.99)) / 1000.0;
        result.min = latencies.get(0) / 1000.0;
        result.max = latencies.get(latencies.size() - 1) / 1000.0;

        return result;
    }

    private static void printLatencyComparison(LatencyResult brane, LatencyResult web3j) {
        System.out.println("     ┌────────────┬──────────┬──────────┬──────────┐");
        System.out.println("     │   Metric   │   Brane  │  Web3j   │  Winner  │");
        System.out.println("     ├────────────┼──────────┼──────────┼──────────┤");
        System.out.printf("     │ Avg        │ %6.2f ms│ %6.2f ms│ %8s │%n",
                brane.avg, web3j.avg, brane.avg < web3j.avg ? "Brane" : "Web3j");
        System.out.printf("     │ p50        │ %6.2f ms│ %6.2f ms│ %8s │%n",
                brane.p50, web3j.p50, brane.p50 < web3j.p50 ? "Brane" : "Web3j");
        System.out.printf("     │ p95        │ %6.2f ms│ %6.2f ms│ %8s │%n",
                brane.p95, web3j.p95, brane.p95 < web3j.p95 ? "Brane" : "Web3j");
        System.out.printf("     │ p99        │ %6.2f ms│ %6.2f ms│ %8s │%n",
                brane.p99, web3j.p99, brane.p99 < web3j.p99 ? "Brane" : "Web3j");
        System.out.println("     └────────────┴──────────┴──────────┴──────────┘");
    }

    // ==================== BURST THROUGHPUT ====================

    private static double measureBurstThroughput(WebSocketProvider provider, int burstSize) throws Exception {
        long start = System.nanoTime();

        CompletableFuture<?>[] futures = new CompletableFuture[burstSize];
        for (int i = 0; i < burstSize; i++) {
            // Use sendAsyncBatch for burst scenarios to leverage Disruptor batching
            futures[i] = provider.sendAsyncBatch("eth_blockNumber", Collections.emptyList());
        }
        CompletableFuture.allOf(futures).join();

        long end = System.nanoTime();
        double durationMs = (end - start) / 1_000_000.0;
        return burstSize * 1000.0 / durationMs; // ops/s
    }

    private static double measureBurstThroughput(Web3j web3j, int burstSize) throws Exception {
        long start = System.nanoTime();

        CompletableFuture<?>[] futures = new CompletableFuture[burstSize];
        for (int i = 0; i < burstSize; i++) {
            futures[i] = web3j.ethBlockNumber().sendAsync();
        }
        CompletableFuture.allOf(futures).join();

        long end = System.nanoTime();
        double durationMs = (end - start) / 1_000_000.0;
        return burstSize * 1000.0 / durationMs; // ops/s
    }

    private static void printBurstComparison(int burstSize, double braneTps, double web3jTps) {
        String winner = braneTps > web3jTps ? "Brane" : "Web3j";
        double speedup = braneTps > web3jTps ? braneTps / web3jTps : web3jTps / braneTps;
        System.out.printf("     Burst %2d: Brane %7.1f ops/s | Web3j %7.1f ops/s | %s %.2fx%n",
                burstSize, braneTps, web3jTps, winner, speedup);
    }

    // ==================== SUSTAINED THROUGHPUT ====================

    private static double measureSustainedThroughput(WebSocketProvider provider) throws Exception {
        AtomicLong completed = new AtomicLong(0);
        AtomicLong errors = new AtomicLong(0);
        long endTime = System.currentTimeMillis() + (SUSTAINED_DURATION_SECONDS * 1000L);

        // Fire requests as fast as possible
        int inflight = 0;
        int maxInflight = 50;
        List<CompletableFuture<?>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() < endTime) {
            // Launch new requests up to maxInflight
            while (inflight < maxInflight && System.currentTimeMillis() < endTime) {
                CompletableFuture<?> f = provider.sendAsyncBatch("eth_blockNumber", Collections.emptyList())
                        .whenComplete((r, e) -> {
                            if (e != null)
                                errors.incrementAndGet();
                            else
                                completed.incrementAndGet();
                        });
                futures.add(f);
                inflight++;
            }

            // Wait for at least one to complete
            if (!futures.isEmpty()) {
                try {
                    CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0])).get(100, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    // Timeout is fine
                }
                // Remove completed futures
                futures.removeIf(CompletableFuture::isDone);
                inflight = futures.size();
            }
        }

        // Wait for remaining
        if (!futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Timeout
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        return completed.get() * 1000.0 / duration;
    }

    private static double measureSustainedThroughput(Web3j web3j) throws Exception {
        AtomicLong completed = new AtomicLong(0);
        AtomicLong errors = new AtomicLong(0);
        long endTime = System.currentTimeMillis() + (SUSTAINED_DURATION_SECONDS * 1000L);

        int inflight = 0;
        int maxInflight = 50;
        List<CompletableFuture<?>> futures = new ArrayList<>();

        long startTime = System.currentTimeMillis();

        while (System.currentTimeMillis() < endTime) {
            while (inflight < maxInflight && System.currentTimeMillis() < endTime) {
                CompletableFuture<?> f = web3j.ethBlockNumber().sendAsync()
                        .whenComplete((r, e) -> {
                            if (e != null)
                                errors.incrementAndGet();
                            else
                                completed.incrementAndGet();
                        });
                futures.add(f);
                inflight++;
            }

            if (!futures.isEmpty()) {
                try {
                    CompletableFuture.anyOf(futures.toArray(new CompletableFuture[0])).get(100, TimeUnit.MILLISECONDS);
                } catch (Exception e) {
                    // Timeout is fine
                }
                futures.removeIf(CompletableFuture::isDone);
                inflight = futures.size();
            }
        }

        if (!futures.isEmpty()) {
            try {
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);
            } catch (Exception e) {
                // Timeout
            }
        }

        long duration = System.currentTimeMillis() - startTime;
        return completed.get() * 1000.0 / duration;
    }

    private static void printSustainedComparison(double braneTps, double web3jTps) {
        String winner = braneTps > web3jTps ? "Brane" : "Web3j";
        double speedup = braneTps > web3jTps ? braneTps / web3jTps : web3jTps / braneTps;
        System.out.printf("     Sustained: Brane %7.1f ops/s | Web3j %7.1f ops/s | %s %.2fx%n",
                braneTps, web3jTps, winner, speedup);
    }

    // ==================== RESULT FORMATTING ====================

    private static void addLatencyResults(StringBuilder sb, String network, LatencyResult brane, LatencyResult web3j) {
        addResult(sb, network, "Latency p50",
                String.format("%.2f ms", brane.p50),
                String.format("%.2f ms", web3j.p50),
                brane.p50 < web3j.p50, web3j.p50 / brane.p50);
        addResult(sb, network, "Latency p95",
                String.format("%.2f ms", brane.p95),
                String.format("%.2f ms", web3j.p95),
                brane.p95 < web3j.p95, web3j.p95 / brane.p95);
    }

    private static void addBurstResult(StringBuilder sb, String network, int size, double brane, double web3j) {
        addResult(sb, network, "Burst " + size,
                String.format("%.0f ops/s", brane),
                String.format("%.0f ops/s", web3j),
                brane > web3j, brane / web3j);
    }

    private static void addSustainedResult(StringBuilder sb, String network, double brane, double web3j) {
        addResult(sb, network, "Sustained",
                String.format("%.0f ops/s", brane),
                String.format("%.0f ops/s", web3j),
                brane > web3j, brane / web3j);
    }

    private static void addResult(StringBuilder sb, String network, String test,
            String braneVal, String web3jVal, boolean braneWins, double ratio) {
        String winner = braneWins ? "**Brane**" : "Web3j";
        String speedup = braneWins ? String.format("%.2fx", ratio) : String.format("%.2fx", 1 / ratio);
        sb.append(String.format("| %s | %s | %s | %s | %s | %s |\n",
                network, test, braneVal, web3jVal, winner, speedup));
    }
}
