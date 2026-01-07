package io.brane.benchmark;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import io.brane.rpc.PublicClient;
import io.brane.rpc.WebSocketProvider;

/**
 * JMH benchmark for Brane SDK's WebSocket RPC performance.
 *
 * <p>Measures throughput (ops/sec) for basic RPC calls over WebSocket:
 * <ul>
 *   <li>{@code latency_chainId} - eth_chainId call latency</li>
 *   <li>{@code throughput_blockNumber} - eth_getBlockByNumber throughput</li>
 * </ul>
 *
 * <p>Requires Anvil running on ws://127.0.0.1:8545.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 3, time = 2)
@Fork(1)
public class BraneBenchmark {

    private static final String WS_URL = "ws://127.0.0.1:8545";

    private WebSocketProvider braneProvider;
    private PublicClient braneClient;

    @Setup
    public void setup() {
        braneProvider = WebSocketProvider.create(WS_URL);
        braneClient = PublicClient.from(braneProvider);
    }

    @TearDown
    public void tearDown() {
        if (braneProvider != null) {
            braneProvider.close();
        }
    }

    @Benchmark
    public void latency_chainId() {
        braneClient.getChainId();
    }

    @Benchmark
    public void throughput_blockNumber() {
        braneClient.getLatestBlock();
    }
}
