// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.benchmark;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import sh.brane.rpc.WebSocketConfig;
import sh.brane.rpc.WebSocketConfig.WaitStrategyType;
import sh.brane.rpc.WebSocketProvider;

/**
 * Benchmark to measure latency distribution with BUSY_SPIN wait strategy.
 *
 * <p>This benchmark is specifically designed to measure p50, p99, and p99.9
 * latencies using the BUSY_SPIN Disruptor wait strategy. Compares against
 * the default YIELDING strategy baseline.
 *
 * <p>Requires Anvil running on ws://127.0.0.1:8545.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 3, time = 3)
@Measurement(iterations = 5, time = 3)
@Fork(value = 1, jvmArgs = { "-Xms512m", "-Xmx512m", "-XX:+UseG1GC" })
public class WaitStrategyBenchmark {

    private static final String WS_URL = "ws://127.0.0.1:8545";

    private WebSocketProvider busySpinProvider;

    @Setup(Level.Trial)
    public void setup() throws Exception {
        // Create provider with BUSY_SPIN wait strategy
        WebSocketConfig config = WebSocketConfig.builder(WS_URL)
                .waitStrategy(WaitStrategyType.BUSY_SPIN)
                .build();
        busySpinProvider = WebSocketProvider.create(config);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (busySpinProvider != null) {
            busySpinProvider.close();
        }
    }

    /**
     * Latency distribution benchmark using BUSY_SPIN wait strategy.
     * This measures the same operation as brane_latencyDist_chainId but with BUSY_SPIN.
     */
    @Benchmark
    @BenchmarkMode(Mode.SampleTime)
    public void brane_latencyDist_chainId_busySpin(Blackhole bh) throws Exception {
        bh.consume(busySpinProvider.send("eth_chainId", List.of()));
    }
}
