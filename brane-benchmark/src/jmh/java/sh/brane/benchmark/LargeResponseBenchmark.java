// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.benchmark;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

import sh.brane.core.model.LogEntry;
import sh.brane.rpc.internal.LogParser;

/**
 * JMH benchmark measuring handling of large eth_getLogs responses.
 *
 * <p>This benchmark simulates responses with 1000+ log entries to measure:
 * <ul>
 *   <li>JSON parsing performance for large arrays</li>
 *   <li>Memory allocation patterns under load</li>
 *   <li>Comparison between Brane's LogParser and generic Jackson parsing</li>
 * </ul>
 *
 * <p>The benchmark uses synthetic but realistic log entry data matching
 * the structure returned by Ethereum JSON-RPC {@code eth_getLogs}.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 2, jvmArgs = {"-Xms512m", "-Xmx512m", "-XX:+UseG1GC"})
public class LargeResponseBenchmark {

    // Sample log entry template (realistic ERC-20 Transfer event)
    private static final String LOG_TEMPLATE = """
            {
              "address": "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
              "blockHash": "0x%s",
              "blockNumber": "0x%x",
              "data": "0x0000000000000000000000000000000000000000000000000000000005f5e100",
              "logIndex": "0x%x",
              "removed": false,
              "topics": [
                "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
                "0x000000000000000000000000%s",
                "0x000000000000000000000000%s"
              ],
              "transactionHash": "0x%s",
              "transactionIndex": "0x%x"
            }""";

    @Param({"100", "1000", "5000"})
    private int logCount;

    private String largeJsonResponse;
    private byte[] largeJsonBytes;
    private Object parsedJsonObject;
    private ObjectMapper objectMapper;

    @Setup(Level.Trial)
    public void setup() {
        objectMapper = new ObjectMapper();
        largeJsonResponse = generateLargeLogsResponse(logCount);
        largeJsonBytes = largeJsonResponse.getBytes(StandardCharsets.UTF_8);

        // Pre-parse JSON for LogParser benchmark (simulates what the RPC layer does)
        try {
            var wrapper = objectMapper.readValue(largeJsonBytes, Map.class);
            parsedJsonObject = wrapper.get("result");
        } catch (Exception e) {
            throw new RuntimeException("Failed to pre-parse JSON", e);
        }
    }

    /**
     * Generates a realistic eth_getLogs JSON-RPC response with the specified number of log entries.
     */
    private String generateLargeLogsResponse(int count) {
        var sb = new StringBuilder(count * 600); // ~600 bytes per log entry
        sb.append("{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":[");

        for (int i = 0; i < count; i++) {
            if (i > 0) {
                sb.append(",");
            }
            String blockHash = padHex64(Integer.toHexString(1000000 + i / 100));
            long blockNumber = 1000000L + i / 100;
            String fromAddr = padHex40(Integer.toHexString(i * 2));
            String toAddr = padHex40(Integer.toHexString(i * 2 + 1));
            String txHash = padHex64(Integer.toHexString(i));

            sb.append(String.format(LOG_TEMPLATE,
                    blockHash,
                    blockNumber,
                    i,
                    fromAddr,
                    toAddr,
                    txHash,
                    i % 100));
        }

        sb.append("]}");
        return sb.toString();
    }

    private static String padHex64(String hex) {
        return "0".repeat(64 - hex.length()) + hex;
    }

    private static String padHex40(String hex) {
        return "0".repeat(40 - hex.length()) + hex;
    }

    // ==================== FULL PARSE BENCHMARKS ====================

    /**
     * Benchmark: Full JSON parsing + LogEntry creation using Brane's LogParser.
     * This measures the complete parsing pipeline as used in production.
     */
    @Benchmark
    public void brane_fullParse_logs(Blackhole bh) throws Exception {
        // Parse JSON first (as RPC layer does)
        var wrapper = objectMapper.readValue(largeJsonBytes, Map.class);
        Object result = wrapper.get("result");

        // Then parse logs using Brane's LogParser
        List<LogEntry> logs = LogParser.parseLogs(result, true);
        bh.consume(logs);
    }

    /**
     * Benchmark: Full JSON parsing using generic Jackson (baseline).
     * This represents a naive approach without specialized parsing.
     */
    @Benchmark
    public void jackson_fullParse_logs(Blackhole bh) throws Exception {
        var response = objectMapper.readValue(largeJsonBytes,
                new TypeReference<Map<String, Object>>() {});
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> logs = (List<Map<String, Object>>) response.get("result");
        bh.consume(logs);
    }

    // ==================== LOG PARSING ONLY BENCHMARKS ====================
    // These isolate the log parsing from JSON deserialization

    /**
     * Benchmark: LogParser.parseLogs on pre-parsed JSON object.
     * Measures only the LogEntry creation overhead.
     */
    @Benchmark
    public void brane_logParserOnly(Blackhole bh) {
        List<LogEntry> logs = LogParser.parseLogs(parsedJsonObject, true);
        bh.consume(logs);
    }

    /**
     * Benchmark: Manual iteration over pre-parsed logs (baseline for comparison).
     * Shows the overhead of LogEntry record creation vs raw map access.
     */
    @Benchmark
    public void jackson_rawMapAccess(Blackhole bh) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rawLogs = (List<Map<String, Object>>) parsedJsonObject;
        List<String> addresses = new ArrayList<>(rawLogs.size());
        for (Map<String, Object> log : rawLogs) {
            addresses.add((String) log.get("address"));
        }
        bh.consume(addresses);
    }

    // ==================== THROUGHPUT MODE BENCHMARKS ====================

    /**
     * Benchmark: Throughput of full parse pipeline.
     * Useful for measuring ops/sec under sustained load.
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void brane_throughput_fullParse(Blackhole bh) throws Exception {
        var wrapper = objectMapper.readValue(largeJsonBytes, Map.class);
        Object result = wrapper.get("result");
        List<LogEntry> logs = LogParser.parseLogs(result, true);
        bh.consume(logs);
    }

    /**
     * Benchmark: Throughput of LogParser only (pre-parsed input).
     */
    @Benchmark
    @BenchmarkMode(Mode.Throughput)
    @OutputTimeUnit(TimeUnit.SECONDS)
    public void brane_throughput_logParserOnly(Blackhole bh) {
        List<LogEntry> logs = LogParser.parseLogs(parsedJsonObject, true);
        bh.consume(logs);
    }
}
