package io.brane.benchmark;

import io.brane.rpc.NettyBraneProvider;
import io.brane.rpc.UltraFastWebSocketProvider;
import io.brane.rpc.WebSocketBraneProvider;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.WebSocketService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Mainnet benchmark comparing all Brane providers vs Web3j on real
 * Ethereum-compatible networks.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 1, time = 3)
@Measurement(iterations = 3, time = 3)
@Fork(value = 1, jvmArgs = { "-Xms1G", "-Xmx1G", "-XX:+UseG1GC" })
public class MainnetBenchmark {

    @Param({ "Ethereum", "Base", "Arbitrum" })
    private String network;

    private String getUrl() {
        switch (network) {
            case "Ethereum":
                return "wss://ethereum-rpc.publicnode.com";
            case "Base":
                return "wss://base-rpc.publicnode.com";
            case "Arbitrum":
                return "wss://arbitrum-one-rpc.publicnode.com";
            default:
                throw new IllegalArgumentException("Unknown network: " + network);
        }
    }

    private UltraFastWebSocketProvider ultraProvider;
    private NettyBraneProvider nettyProvider;
    private WebSocketBraneProvider stdProvider;
    private WebSocketService web3jService;
    private Web3j web3j;

    private static final String VITALIK_ADDRESS = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";

    @Setup(Level.Trial)
    public void setup() {
        String url = getUrl();
        System.out.println("Connecting to " + network + " (" + url + ")...");

        try {
            // 1. UltraFast
            try {
                ultraProvider = UltraFastWebSocketProvider.create(url);
                ultraProvider.send("eth_chainId", Collections.emptyList());
            } catch (Exception e) {
                System.err.println("UltraFast init failed: " + e.getMessage());
            }

            // 2. Netty
            try {
                nettyProvider = NettyBraneProvider.create(url);
                nettyProvider.send("eth_chainId", Collections.emptyList());
            } catch (Exception e) {
                System.err.println("Netty init failed: " + e.getMessage());
            }

            // 3. Standard
            try {
                stdProvider = WebSocketBraneProvider.create(url);
                stdProvider.send("eth_chainId", Collections.emptyList());
            } catch (Exception e) {
                System.err.println("Standard init failed: " + e.getMessage());
            }

            // 4. Web3j
            try {
                web3jService = new WebSocketService(url, false);
                web3jService.connect();
                web3j = Web3j.build(web3jService);
                web3j.ethChainId().send().getChainId();
            } catch (Exception e) {
                System.err.println("Web3j init failed: " + e.getMessage());
            }

            System.out.println("Providers initialized (some may have failed).");
            // Warmup delay
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        try {
            if (ultraProvider != null)
                ultraProvider.close();
            if (nettyProvider != null)
                nettyProvider.close();
            if (stdProvider != null)
                stdProvider.close();
        } catch (Exception e) {
            // Ignore close errors
        }

        try {
            if (web3jService != null)
                web3jService.close();
        } catch (Exception e) {
            // Ignore close errors
        }

        // Cool down to avoid rate limits between trials
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ==================== THROUGHPUT: Chain ID ====================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void ultra_chainId(Blackhole bh) throws Exception {
        bh.consume(ultraProvider.send("eth_chainId", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void netty_chainId(Blackhole bh) throws Exception {
        bh.consume(nettyProvider.send("eth_chainId", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void std_chainId(Blackhole bh) throws Exception {
        bh.consume(stdProvider.send("eth_chainId", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void web3j_chainId(Blackhole bh) throws Exception {
        bh.consume(web3j.ethChainId().send().getChainId());
    }

    // ==================== THROUGHPUT: Block Number ====================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void ultra_blockNumber(Blackhole bh) throws Exception {
        bh.consume(ultraProvider.send("eth_blockNumber", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void netty_blockNumber(Blackhole bh) throws Exception {
        bh.consume(nettyProvider.send("eth_blockNumber", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void std_blockNumber(Blackhole bh) throws Exception {
        bh.consume(stdProvider.send("eth_blockNumber", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void web3j_blockNumber(Blackhole bh) throws Exception {
        bh.consume(web3j.ethBlockNumber().send().getBlockNumber());
    }

    // ==================== THROUGHPUT: Get Balance ====================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void ultra_getBalance(Blackhole bh) throws Exception {
        bh.consume(ultraProvider.send("eth_getBalance", List.of(VITALIK_ADDRESS, "latest")));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void netty_getBalance(Blackhole bh) throws Exception {
        bh.consume(nettyProvider.send("eth_getBalance", List.of(VITALIK_ADDRESS, "latest")));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void std_getBalance(Blackhole bh) throws Exception {
        bh.consume(stdProvider.send("eth_getBalance", List.of(VITALIK_ADDRESS, "latest")));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void web3j_getBalance(Blackhole bh) throws Exception {
        bh.consume(
                web3j.ethGetBalance(VITALIK_ADDRESS, org.web3j.protocol.core.DefaultBlockParameterName.LATEST).send());
    }

    // ==================== LATENCY: Block Number (ms) ====================

    @Benchmark
    @BenchmarkMode({ Mode.AverageTime, Mode.SampleTime })
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void ultra_latency_blockNumber(Blackhole bh) throws Exception {
        bh.consume(ultraProvider.send("eth_blockNumber", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode({ Mode.AverageTime, Mode.SampleTime })
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void netty_latency_blockNumber(Blackhole bh) throws Exception {
        bh.consume(nettyProvider.send("eth_blockNumber", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode({ Mode.AverageTime, Mode.SampleTime })
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void std_latency_blockNumber(Blackhole bh) throws Exception {
        bh.consume(stdProvider.send("eth_blockNumber", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode({ Mode.AverageTime, Mode.SampleTime })
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public void web3j_latency_blockNumber(Blackhole bh) throws Exception {
        bh.consume(web3j.ethBlockNumber().send().getBlockNumber());
    }

    // ==================== PIPELINE: 5 Async Requests ====================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void ultra_pipeline_5(Blackhole bh) throws Exception {
        CompletableFuture<?>[] futures = new CompletableFuture[5];
        for (int i = 0; i < 5; i++) {
            futures[i] = ultraProvider.sendAsync("eth_blockNumber", Collections.emptyList());
        }
        for (CompletableFuture<?> f : futures) {
            bh.consume(f.get());
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void netty_pipeline_5(Blackhole bh) throws Exception {
        CompletableFuture<?>[] futures = new CompletableFuture[5];
        for (int i = 0; i < 5; i++) {
            futures[i] = nettyProvider.sendAsync("eth_blockNumber", Collections.emptyList());
        }
        for (CompletableFuture<?> f : futures) {
            bh.consume(f.get());
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void std_pipeline_5(Blackhole bh) throws Exception {
        CompletableFuture<?>[] futures = new CompletableFuture[5];
        for (int i = 0; i < 5; i++) {
            futures[i] = stdProvider.sendAsync("eth_blockNumber", Collections.emptyList());
        }
        for (CompletableFuture<?> f : futures) {
            bh.consume(f.get());
        }
    }

    // ==================== BATCH: 5 Requests (RPC Batch) ====================
    // Note: NettyBraneProvider is excluded as it favors internal batching over
    // explicit RPC batching in current impl

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void ultra_batch_5(Blackhole bh) throws Exception {
        List<UltraFastWebSocketProvider.BatchRequest> batch = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            batch.add(new UltraFastWebSocketProvider.BatchRequest("eth_blockNumber", Collections.emptyList()));
        }
        bh.consume(ultraProvider.sendBatch(batch).get());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void std_batch_5(Blackhole bh) throws Exception {
        List<WebSocketBraneProvider.BatchRequest> batch = new ArrayList<>(5);
        for (int i = 0; i < 5; i++) {
            batch.add(new WebSocketBraneProvider.BatchRequest("eth_blockNumber", Collections.emptyList()));
        }
        bh.consume(stdProvider.sendBatch(batch).get());
    }
}
