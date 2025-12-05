package io.brane.benchmark;

import io.brane.rpc.UltraFastWebSocketProvider;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.WebSocketService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class RealWorldLatencyTest {

    record NetworkConfig(String name, String url) {
    }

    private static final List<NetworkConfig> NETWORKS = List.of(
            new NetworkConfig("Base Mainnet", "wss://base-rpc.publicnode.com"),
            new NetworkConfig("Arbitrum One", "wss://arbitrum-one-rpc.publicnode.com"),
            new NetworkConfig("Ethereum Mainnet", "wss://ethereum-rpc.publicnode.com"));

    private static final int LATENCY_ITERATIONS = 15; // Reduced slightly for multi-chain
    private static final int BURST_SIZE = 5;
    private static final long SLEEP_MS = 1000;

    public static void main(String[] args) {
        System.out.println("🚀 Starting Multi-Chain Comparison: Brane vs Web3j");
        System.out.println("--------------------------------------------------");

        for (NetworkConfig network : NETWORKS) {
            System.out.println("\n🌐 Testing Network: " + network.name());
            System.out.println("   URL: " + network.url());

            try {
                runNetworkBenchmark(network);
            } catch (Exception e) {
                System.err.println("   ❌ Failed to test " + network.name() + ": " + e.getMessage());
            }
            System.out.println("--------------------------------------------------");
        }
    }

    private static void runNetworkBenchmark(NetworkConfig network) throws Exception {
        // 1. Latency Test
        System.out.println("   Phase 1: Latency Comparison (Interleaved)");
        runLatencyComparison(network.url());

        // 2. Burst Throughput Test
        System.out.println("\n   Phase 2: Burst Throughput Comparison (Batch of " + BURST_SIZE + ")");
        runBurstComparison(network.url());
    }

    private static void runLatencyComparison(String url) throws Exception {
        List<Long> braneLatencies = new ArrayList<>();
        List<Long> web3jLatencies = new ArrayList<>();

        try (io.brane.rpc.NettyBraneProvider brane = io.brane.rpc.NettyBraneProvider.create(url)) {
            WebSocketService wsService = new WebSocketService(url, false);
            wsService.connect();
            Web3j web3j = Web3j.build(wsService);

            // Warmup
            System.out.print("   Warming up...");
            brane.sendAsync("eth_chainId", Collections.emptyList()).join();
            web3j.ethChainId().send();
            System.out.println(" Done.");
            Thread.sleep(SLEEP_MS);

            for (int i = 1; i <= LATENCY_ITERATIONS; i++) {
                // Brane
                long startBrane = System.nanoTime();
                brane.sendAsync("eth_blockNumber", Collections.emptyList()).join();
                long endBrane = System.nanoTime();
                braneLatencies.add((endBrane - startBrane) / 1000);

                Thread.sleep(500);

                // Web3j
                long startWeb3j = System.nanoTime();
                web3j.ethBlockNumber().send();
                long endWeb3j = System.nanoTime();
                web3jLatencies.add((endWeb3j - startWeb3j) / 1000);

                Thread.sleep(SLEEP_MS);
            }
            wsService.close();
        }

        printStats("   Brane Latency", braneLatencies);
        printStats("   Web3j Latency", web3jLatencies);
    }

    private static void runBurstComparison(String url) throws Exception {
        // Brane Burst
        try (io.brane.rpc.NettyBraneProvider brane = io.brane.rpc.NettyBraneProvider.create(url)) {
            brane.sendAsync("eth_chainId", Collections.emptyList()).join(); // Warmup
            Thread.sleep(SLEEP_MS);

            System.out.print("   Running Brane Burst... ");
            long start = System.nanoTime();
            CompletableFuture<?>[] futures = new CompletableFuture[BURST_SIZE];
            for (int i = 0; i < BURST_SIZE; i++) {
                futures[i] = brane.sendAsync("eth_blockNumber", Collections.emptyList());
            }
            CompletableFuture.allOf(futures).join();
            long end = System.nanoTime();
            double durationMs = (end - start) / 1_000_000.0;
            System.out.printf("Done in %.2f ms (%.2f ops/s)%n", durationMs, (BURST_SIZE * 1000.0 / durationMs));
        }

        Thread.sleep(SLEEP_MS * 2);

        // Web3j Burst
        WebSocketService wsService = new WebSocketService(url, false);
        wsService.connect();
        Web3j web3j = Web3j.build(wsService);
        try {
            web3j.ethChainId().send(); // Warmup
            Thread.sleep(SLEEP_MS);

            System.out.print("   Running Web3j Burst... ");
            long start = System.nanoTime();
            CompletableFuture<?>[] futures = new CompletableFuture[BURST_SIZE];
            for (int i = 0; i < BURST_SIZE; i++) {
                futures[i] = web3j.ethBlockNumber().sendAsync();
            }
            CompletableFuture.allOf(futures).join();
            long end = System.nanoTime();
            double durationMs = (end - start) / 1_000_000.0;
            System.out.printf("Done in %.2f ms (%.2f ops/s)%n", durationMs, (BURST_SIZE * 1000.0 / durationMs));
        } finally {
            wsService.close();
        }
    }

    private static void printStats(String label, List<Long> latencies) {
        if (latencies.isEmpty())
            return;
        Collections.sort(latencies);
        double avg = latencies.stream().mapToLong(Long::longValue).average().orElse(0) / 1000.0;
        double p50 = latencies.get((int) (latencies.size() * 0.50)) / 1000.0;
        double p95 = latencies.get((int) (latencies.size() * 0.95)) / 1000.0;

        System.out.printf("%-18s | Avg: %6.2f ms | p50: %6.2f ms | p95: %6.2f ms%n", label, avg, p50, p95);
    }
}
