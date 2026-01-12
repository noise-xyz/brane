// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.benchmark;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.WebSocketService;

import io.brane.rpc.WebSocketProvider;

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
                String useInfura = System.getProperty("brane.benchmark.useInfura");
                if ("true".equalsIgnoreCase(useInfura)) {
                    String infuraUrl = System.getenv("INFURA_BASE_WSS_URL");
                    if (infuraUrl == null || infuraUrl.isEmpty()) {
                        infuraUrl = System.getProperty("INFURA_BASE_WSS_URL");
                    }

                    if (infuraUrl != null && !infuraUrl.isEmpty()) {
                        return infuraUrl;
                    }
                    System.err.println(
                            "Warning: brane.benchmark.useInfura=true but INFURA_BASE_WSS_URL not set. Falling back to public node.");
                }
                return "wss://base-rpc.publicnode.com";
            case "Arbitrum":
                return "wss://arbitrum-one-rpc.publicnode.com";
            default:
                throw new IllegalArgumentException("Unknown network: " + network);
        }
    }

    private WebSocketProvider braneProvider;
    private WebSocketService web3jService;
    private Web3j web3j;

    private static final String VITALIK_ADDRESS = "0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045";

    @Setup(Level.Trial)
    public void setup() {
        String url = getUrl();
        System.out.println("Connecting to " + network + " (" + url + ")...");

        try {
            // 1. Brane WebSocketProvider
            try {
                braneProvider = WebSocketProvider.create(url);
                braneProvider.send("eth_chainId", Collections.emptyList());
            } catch (Exception e) {
                System.err.println("Brane init failed: " + e.getMessage());
            }

            // 2. Web3j
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
            if (braneProvider != null)
                braneProvider.close();
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
    public void brane_chainId(Blackhole bh) throws Exception {
        bh.consume(braneProvider.send("eth_chainId", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void web3j_chainId(Blackhole bh) throws Exception {
        bh.consume(web3j.ethChainId().send().getChainId());
    }

    // ==================== THROUGHPUT: Block Number ====================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void brane_blockNumber(Blackhole bh) throws Exception {
        bh.consume(braneProvider.send("eth_blockNumber", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void web3j_blockNumber(Blackhole bh) throws Exception {
        bh.consume(web3j.ethBlockNumber().send().getBlockNumber());
    }

    // ==================== THROUGHPUT: Get Balance ====================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void brane_getBalance(Blackhole bh) throws Exception {
        bh.consume(braneProvider.send("eth_getBalance", List.of(VITALIK_ADDRESS, "latest")));
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
    public void brane_latency_blockNumber(Blackhole bh) throws Exception {
        bh.consume(braneProvider.send("eth_blockNumber", Collections.emptyList()));
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
    public void brane_pipeline_5(Blackhole bh) throws Exception {
        CompletableFuture<?>[] futures = new CompletableFuture[5];
        for (int i = 0; i < 5; i++) {
            futures[i] = braneProvider.sendAsync("eth_blockNumber", Collections.emptyList());
        }
        for (CompletableFuture<?> f : futures) {
            bh.consume(f.get());
        }
    }
}
