// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.benchmark;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import sh.brane.rpc.HttpBraneProvider;
import sh.brane.rpc.WebSocketConfig;
import sh.brane.rpc.WebSocketProvider;

/**
 * Benchmark measuring time to detect connection failure to an unreachable host.
 *
 * <p>Uses 192.0.2.1 (TEST-NET-1, RFC 5737) which is guaranteed to be unreachable
 * and will never respond. This allows accurate measurement of connection timeout
 * behavior without network variability.
 *
 * <p>This benchmark is useful for:
 * <ul>
 *   <li>Validating that timeout settings are honored</li>
 *   <li>Measuring overhead of connection failure detection</li>
 *   <li>Comparing HTTP vs WebSocket failure detection times</li>
 * </ul>
 *
 * <p><strong>Note:</strong> This benchmark intentionally triggers connection failures.
 * Each iteration will take approximately the configured timeout duration.
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 3)
@Fork(1)
public class ConnectionFailureBenchmark {

    /**
     * TEST-NET-1 (RFC 5737) - guaranteed unreachable IP address for documentation/testing.
     * This address will never respond, making it ideal for timeout testing.
     */
    private static final String UNREACHABLE_HTTP_URL = "http://192.0.2.1:8545";
    private static final String UNREACHABLE_WS_URL = "ws://192.0.2.1:8545";

    /**
     * Connection timeout values to test (in milliseconds).
     * Testing a range from very short (100ms) to moderate (2000ms).
     */
    @Param({ "100", "500", "1000", "2000" })
    private int timeoutMs;

    /**
     * Measures HTTP connection failure detection time.
     *
     * <p>This benchmark creates an HTTP provider with the parameterized timeout
     * and attempts a single RPC call. The call is expected to fail with a
     * connection timeout, and we measure how long it takes to detect this failure.
     */
    @Benchmark
    public void http_connectionFailure(Blackhole bh) {
        HttpBraneProvider provider = null;
        try {
            provider = HttpBraneProvider.builder(UNREACHABLE_HTTP_URL)
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .readTimeout(Duration.ofMillis(timeoutMs))
                    .build();

            // Attempt to make a call - this will fail with connection timeout
            provider.send("eth_chainId", List.of());
        } catch (Exception e) {
            // Expected: connection failure
            bh.consume(e);
        } finally {
            if (provider != null) {
                provider.close();
            }
        }
    }

    /**
     * Measures WebSocket connection failure detection time.
     *
     * <p>This benchmark creates a WebSocket provider with the parameterized timeout
     * and attempts to establish a connection. The connection is expected to fail
     * with a timeout, and we measure how long it takes to detect this failure.
     */
    @Benchmark
    public void ws_connectionFailure(Blackhole bh) {
        WebSocketProvider provider = null;
        try {
            WebSocketConfig config = WebSocketConfig.builder(UNREACHABLE_WS_URL)
                    .connectTimeout(Duration.ofMillis(timeoutMs))
                    .build();

            // Attempt to create provider - this will fail during connection
            provider = WebSocketProvider.create(config);

            // If somehow connection succeeded (shouldn't happen), try a call
            provider.send("eth_chainId", List.of());
        } catch (Exception e) {
            // Expected: connection failure
            bh.consume(e);
        } finally {
            if (provider != null) {
                try {
                    provider.close();
                } catch (Exception ignored) {
                    // Ignore close errors
                }
            }
        }
    }
}
