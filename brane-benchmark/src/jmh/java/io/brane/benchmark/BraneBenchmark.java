package io.brane.benchmark;

import io.brane.rpc.PublicClient;
import io.brane.rpc.WebSocketBraneProvider;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 1, time = 2)
@Measurement(iterations = 3, time = 2)
@Fork(1)
public class BraneBenchmark {

    private static final String WS_URL = "ws://127.0.0.1:8545";

    private WebSocketBraneProvider braneProvider;
    private PublicClient braneClient;

    @Setup
    public void setup() {
        braneProvider = WebSocketBraneProvider.create(WS_URL);
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
