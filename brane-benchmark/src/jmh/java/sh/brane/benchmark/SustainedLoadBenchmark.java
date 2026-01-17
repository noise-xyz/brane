// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.benchmark;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import sh.brane.rpc.WebSocketConfig;
import sh.brane.rpc.WebSocketProvider;

/**
 * JMH benchmark measuring throughput under sustained load with varying
 * WriteBufferWaterMark configurations.
 *
 * <p>This benchmark sends 10K requests per iteration to measure how different
 * write buffer water mark settings affect throughput and backpressure behavior.
 * The WriteBufferWaterMark controls Netty's flow control:
 * <ul>
 *   <li><b>Low water mark:</b> When buffer drops below this, channel becomes writable</li>
 *   <li><b>High water mark:</b> When buffer exceeds this, channel becomes not writable</li>
 * </ul>
 *
 * <p>Smaller buffers detect backpressure earlier but may cause more write suspensions.
 * Larger buffers allow more data in flight but delay backpressure detection.
 *
 * <p>Requires Anvil running on ws://127.0.0.1:8545.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 5)
@Measurement(iterations = 3, time = 10)
@Fork(value = 1, jvmArgs = {"-Xms512m", "-Xmx512m", "-XX:+UseG1GC"})
public class SustainedLoadBenchmark {

    private static final String WS_URL = "ws://127.0.0.1:8545";

    /**
     * Number of requests per sustained load batch.
     * 10K requests provides meaningful throughput measurement.
     */
    private static final int REQUESTS_PER_BATCH = 10_000;

    /**
     * WriteBufferWaterMark configurations to test.
     * Format: "lowKB_highKB" (e.g., "8_32" means 8KB low, 32KB high).
     *
     * <p>Configurations tested:
     * <ul>
     *   <li>8_32: Default - balanced for most workloads</li>
     *   <li>16_64: Larger buffers - higher throughput, more memory</li>
     *   <li>32_128: Large buffers - maximum throughput, delayed backpressure</li>
     *   <li>4_16: Small buffers - early backpressure detection</li>
     * </ul>
     */
    @Param({"8_32", "16_64", "32_128", "4_16"})
    private String waterMarkConfig;

    private WebSocketProvider provider;

    @Setup(Level.Trial)
    public void setup() {
        String[] parts = waterMarkConfig.split("_");
        int lowKB = Integer.parseInt(parts[0]);
        int highKB = Integer.parseInt(parts[1]);

        WebSocketConfig config = WebSocketConfig.builder(WS_URL)
                .writeBufferWaterMark(lowKB * 1024, highKB * 1024)
                .build();

        provider = WebSocketProvider.create(config);
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        if (provider != null) {
            provider.close();
        }
    }

    /**
     * Measures throughput of sustained eth_chainId requests.
     *
     * <p>eth_chainId is a lightweight RPC call ideal for measuring raw
     * throughput without server-side processing overhead.
     */
    @Benchmark
    public void sustainedLoad_chainId(Blackhole bh) throws Exception {
        for (int i = 0; i < REQUESTS_PER_BATCH; i++) {
            bh.consume(provider.send("eth_chainId", List.of()));
        }
    }

    /**
     * Measures throughput of sustained eth_blockNumber requests.
     *
     * <p>eth_blockNumber is another lightweight call useful for
     * validating consistent throughput patterns.
     */
    @Benchmark
    public void sustainedLoad_blockNumber(Blackhole bh) throws Exception {
        for (int i = 0; i < REQUESTS_PER_BATCH; i++) {
            bh.consume(provider.send("eth_blockNumber", List.of()));
        }
    }

    /**
     * Measures throughput with concurrent async requests.
     *
     * <p>This benchmark fires all requests asynchronously then waits for
     * completion, measuring the system's ability to handle concurrent
     * in-flight requests under different buffer configurations.
     */
    @Benchmark
    public void sustainedLoad_asyncBurst(Blackhole bh) throws Exception {
        var futures = new java.util.concurrent.CompletableFuture<?>[REQUESTS_PER_BATCH];

        // Fire all requests
        for (int i = 0; i < REQUESTS_PER_BATCH; i++) {
            futures[i] = provider.sendAsync("eth_chainId", List.of());
        }

        // Wait for all to complete
        for (var future : futures) {
            bh.consume(future.join());
        }
    }
}
