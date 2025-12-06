package io.brane.benchmark;

import io.brane.rpc.WebSocketProvider;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.websocket.WebSocketService;

import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive WebSocket benchmark comparing Brane UltraFast vs Web3j.
 * 
 * Metrics measured:
 * - Throughput (ops/sec)
 * - Average latency (time/op)
 * - Sample latency distribution (for p50, p99, p999)
 * - Single-shot cold start time
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1, jvmArgs = { "-Xms512m", "-Xmx512m", "-XX:+UseG1GC" })
public class WebSocketBenchmark {

    private static final String WS_URL = "ws://127.0.0.1:8545";

    // Using the new UltraFast provider
    private WebSocketProvider braneProvider;

    private WebSocketService web3jService;
    private Web3j web3j;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        // Brane Setup - UltraFast provider
        braneProvider = WebSocketProvider.create(WS_URL);

        // Web3j Setup
        web3jService = new WebSocketService(WS_URL, false);
        web3jService.connect();
        web3j = Web3j.build(web3jService);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (braneProvider != null) {
            braneProvider.close();
        }
        if (web3jService != null) {
            web3jService.close();
        }
    }

    // ==================== THROUGHPUT BENCHMARKS ====================

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void brane_throughput_chainId(Blackhole bh) throws Exception {
        bh.consume(braneProvider.send("eth_chainId", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void web3j_throughput_chainId(Blackhole bh) throws Exception {
        bh.consume(web3j.ethChainId().send().getChainId());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void brane_throughput_blockNumber(Blackhole bh) throws Exception {
        bh.consume(braneProvider.send("eth_blockNumber", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void web3j_throughput_blockNumber(Blackhole bh) throws Exception {
        bh.consume(web3j.ethBlockNumber().send().getBlockNumber());
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void brane_throughput_getBalance(Blackhole bh) throws Exception {
        bh.consume(braneProvider.send("eth_getBalance",
                java.util.List.of("0x0000000000000000000000000000000000000000", "latest")));
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void web3j_throughput_getBalance(Blackhole bh) throws Exception {
        bh.consume(web3j.ethGetBalance("0x0000000000000000000000000000000000000000",
                org.web3j.protocol.core.DefaultBlockParameterName.LATEST).send());
    }

    // ==================== AVERAGE LATENCY BENCHMARKS ====================

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void brane_avgLatency_chainId(Blackhole bh) throws Exception {
        bh.consume(braneProvider.send("eth_chainId", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void web3j_avgLatency_chainId(Blackhole bh) throws Exception {
        bh.consume(web3j.ethChainId().send().getChainId());
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void brane_avgLatency_blockNumber(Blackhole bh) throws Exception {
        bh.consume(braneProvider.send("eth_blockNumber", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void web3j_avgLatency_blockNumber(Blackhole bh) throws Exception {
        bh.consume(web3j.ethBlockNumber().send().getBlockNumber());
    }

    // ==================== LATENCY DISTRIBUTION (p50, p99, p999)
    // ====================

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void brane_latencyDist_chainId(Blackhole bh) throws Exception {
        bh.consume(braneProvider.send("eth_chainId", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    @OutputTimeUnit(TimeUnit.MICROSECONDS)
    public void web3j_latencyDist_chainId(Blackhole bh) throws Exception {
        bh.consume(web3j.ethChainId().send().getChainId());
    }

    // ==================== ASYNC/PIPELINING BENCHMARKS ====================
    // These showcase Brane's async capabilities

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void brane_async_pipeline_10(Blackhole bh) throws Exception {
        // Fire 10 requests in parallel using the async API
        @SuppressWarnings("unchecked")
        CompletableFuture<?>[] futures = new CompletableFuture[10];
        for (int i = 0; i < 10; i++) {
            futures[i] = braneProvider.sendAsync("eth_chainId", Collections.emptyList());
        }
        for (CompletableFuture<?> f : futures) {
            bh.consume(f.get());
        }
    }

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void web3j_async_pipeline_10(Blackhole bh) throws Exception {
        // Fire 10 requests in parallel, wait for all
        @SuppressWarnings("unchecked")
        CompletableFuture<?>[] futures = new CompletableFuture[10];
        for (int i = 0; i < 10; i++) {
            futures[i] = web3j.ethChainId().sendAsync();
        }
        for (CompletableFuture<?> f : futures) {
            bh.consume(f.get());
        }
    }

    // ==================== BATCH REQUEST BENCHMARKS ====================
    // Brane supports batch requests for amortized network overhead

    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    public void brane_batch_10(Blackhole bh) throws Exception {
        java.util.List<CompletableFuture<?>> futures = new ArrayList<>(10);
        for (int i = 0; i < 10; i++) {
            futures.add(braneProvider.sendAsyncBatch("eth_chainId", Collections.emptyList()));
        }
        for (CompletableFuture<?> f : futures) {
            bh.consume(f.get());
        }
    }

    // ==================== SINGLE SHOT (COLD START) ====================

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Warmup(iterations = 0)
    @Measurement(iterations = 10)
    public void brane_singleShot_chainId(Blackhole bh) throws Exception {
        bh.consume(braneProvider.send("eth_chainId", Collections.emptyList()));
    }

    @Benchmark
    @BenchmarkMode(Mode.SingleShotTime)
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Warmup(iterations = 0)
    @Measurement(iterations = 10)
    public void web3j_singleShot_chainId(Blackhole bh) throws Exception {
        bh.consume(web3j.ethChainId().send().getChainId());
    }
}
