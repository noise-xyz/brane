package io.brane.benchmark;

import io.brane.rpc.*;
import org.openjdk.jmh.annotations.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Comprehensive provider comparison benchmark for Section 9.1 of
 * Implementation.md.
 * 
 * Compares:
 * - HttpBraneProvider (Loom)
 * - WebSocketProvider with sendAsync
 * - WebSocketProvider with sendAsyncBatch
 * 
 * Run against local Anvil: anvil --host 0.0.0.0 --port 8545
 * 
 * Execute: ./gradlew :brane-benchmark:jmh
 * -Pjmh.includes="ProviderComparisonBenchmark"
 */
@State(Scope.Benchmark)
@BenchmarkMode({ Mode.Throughput, Mode.AverageTime })
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(1)
public class ProviderComparisonBenchmark {

    private static final String HTTP_URL = "http://127.0.0.1:8545";
    private static final String WS_URL = "ws://127.0.0.1:8545";

    private BraneProvider httpProvider;
    private WebSocketProvider wsProvider;

    @Setup
    public void setup() {
        httpProvider = HttpBraneProvider.builder(HTTP_URL).build();
        wsProvider = WebSocketProvider.create(WS_URL);
    }

    @TearDown
    public void tearDown() {
        if (wsProvider != null) {
            wsProvider.close();
        }
    }

    // ==================== HTTP Provider (Loom) ====================

    @Benchmark
    public JsonRpcResponse http_blockNumber() {
        return httpProvider.send("eth_blockNumber", List.of());
    }

    @Benchmark
    public JsonRpcResponse http_getBalance() {
        return httpProvider.send("eth_getBalance",
                List.of("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", "latest"));
    }

    // ==================== WebSocket sendAsync (single) ====================

    @Benchmark
    public JsonRpcResponse ws_sendAsync_blockNumber() throws Exception {
        return wsProvider.sendAsync("eth_blockNumber", List.of()).get();
    }

    @Benchmark
    public JsonRpcResponse ws_sendAsync_getBalance() throws Exception {
        return wsProvider.sendAsync("eth_getBalance",
                List.of("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", "latest")).get();
    }

    // ==================== WebSocket sendAsyncBatch (optimized)
    // ====================

    @Benchmark
    public JsonRpcResponse ws_sendAsyncBatch_blockNumber() throws Exception {
        return wsProvider.sendAsyncBatch("eth_blockNumber", List.of()).get();
    }

    @Benchmark
    public JsonRpcResponse ws_sendAsyncBatch_getBalance() throws Exception {
        return wsProvider.sendAsyncBatch("eth_getBalance",
                List.of("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266", "latest")).get();
    }

    // ==================== Throughput: Batch of 100 requests ====================

    @Benchmark
    @OperationsPerInvocation(100)
    public void ws_batch100_blockNumber() throws Exception {
        List<CompletableFuture<JsonRpcResponse>> futures = new ArrayList<>(100);
        for (int i = 0; i < 100; i++) {
            futures.add(wsProvider.sendAsyncBatch("eth_blockNumber", List.of()));
        }
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get();
    }

    @Benchmark
    @OperationsPerInvocation(100)
    public void http_batch100_blockNumber() throws Exception {
        // Using virtual threads for HTTP parallelism
        try (var exec = java.util.concurrent.Executors.newVirtualThreadPerTaskExecutor()) {
            List<java.util.concurrent.Future<JsonRpcResponse>> futures = new ArrayList<>(100);
            for (int i = 0; i < 100; i++) {
                futures.add(exec.submit(() -> httpProvider.send("eth_blockNumber", List.of())));
            }
            for (var f : futures) {
                f.get();
            }
        }
    }
}
