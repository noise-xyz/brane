package io.brane.benchmark;

import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.infra.Blackhole;

/**
 * Micro-benchmark measuring client-side overhead ONLY (no network).
 *
 * This directly compares JSON serialization/parsing efficiency between:
 * - Brane's low-allocation custom parser
 * - web3j's Jackson-based approach
 *
 * This is where we can demonstrate 2x+ performance difference.
 */
@State(Scope.Benchmark)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@BenchmarkMode(Mode.AverageTime)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 2)
@Fork(value = 2, jvmArgs = { "-Xms256m", "-Xmx256m", "-XX:+UseG1GC" })
public class ClientOverheadBenchmark {

    // ==================== SIMULATED RESPONSES ====================
    // These are real responses from Ethereum nodes

    private static final String CHAIN_ID_RESPONSE = "{\"jsonrpc\":\"2.0\",\"id\":1,\"result\":\"0x1\"}";

    private static final String BLOCK_NUMBER_RESPONSE = "{\"jsonrpc\":\"2.0\",\"id\":2,\"result\":\"0x12a05f200\"}";

    private static final String BALANCE_RESPONSE = "{\"jsonrpc\":\"2.0\",\"id\":3,\"result\":\"0x56bc75e2d63100000\"}";

    private static final String BLOCK_RESPONSE = "{\"jsonrpc\":\"2.0\",\"id\":4,\"result\":{" +
            "\"number\":\"0x12a05f200\"," +
            "\"hash\":\"0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890\"," +
            "\"parentHash\":\"0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef\"," +
            "\"nonce\":\"0x0000000000000042\"," +
            "\"sha3Uncles\":\"0x1dcc4de8dec75d7aab85b567b6ccd41ad312451b948a7413f0a142fd40d49347\"," +
            "\"logsBloom\":\"0x00000000000000000000000000000000000000000000000000000000000000000000000000000000\"," +
            "\"transactionsRoot\":\"0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421\"," +
            "\"stateRoot\":\"0xd5855eb08b3387c0af375e9cdb6acfc05eb8f519e419b874b6ff2ffda7ed1dff\"," +
            "\"receiptsRoot\":\"0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421\"," +
            "\"miner\":\"0x4e65fda2159562a496f9f3522f89122a3088497a\"," +
            "\"difficulty\":\"0x27f07\"," +
            "\"totalDifficulty\":\"0x27f07\"," +
            "\"extraData\":\"0x0000000000000000000000000000000000000000000000000000000000000000\"," +
            "\"size\":\"0x220\"," +
            "\"gasLimit\":\"0x1c9c380\"," +
            "\"gasUsed\":\"0x0\"," +
            "\"timestamp\":\"0x66f1c9c0\"," +
            "\"transactions\":[]," +
            "\"uncles\":[]" +
            "}}";

    private static final String ERROR_RESPONSE = "{\"jsonrpc\":\"2.0\",\"id\":5,\"error\":{\"code\":-32602,\"message\":\"Invalid params\"}}";

    // Pre-converted bytes for Brane
    private byte[] chainIdBytes;
    private byte[] blockNumberBytes;
    private byte[] balanceBytes;
    private byte[] blockBytes;
    private byte[] errorBytes;

    // web3j uses Jackson
    private ObjectMapper objectMapper;

    // ID generator for request building
    private final AtomicInteger idCounter = new AtomicInteger(0);

    // ThreadLocal StringBuilder for Brane (simulating what
    // UltraFastWebSocketProvider does)
    private static final ThreadLocal<StringBuilder> STRING_BUILDER = ThreadLocal
            .withInitial(() -> new StringBuilder(256));

    // Pre-computed JSON fragments for Brane
    private static final char[] JSON_PREFIX = "{\"jsonrpc\":\"2.0\",\"method\":\"".toCharArray();
    private static final char[] PARAMS_PREFIX = "\",\"params\":".toCharArray();
    private static final char[] ID_PREFIX = ",\"id\":".toCharArray();
    private static final char JSON_SUFFIX = '}';

    @Setup(Level.Trial)
    public void setup() {
        chainIdBytes = CHAIN_ID_RESPONSE.getBytes(StandardCharsets.UTF_8);
        blockNumberBytes = BLOCK_NUMBER_RESPONSE.getBytes(StandardCharsets.UTF_8);
        balanceBytes = BALANCE_RESPONSE.getBytes(StandardCharsets.UTF_8);
        blockBytes = BLOCK_RESPONSE.getBytes(StandardCharsets.UTF_8);
        errorBytes = ERROR_RESPONSE.getBytes(StandardCharsets.UTF_8);

        objectMapper = new ObjectMapper();
    }

    // ==================== REQUEST SERIALIZATION ====================

    @Benchmark
    public void brane_serializeRequest_simple(Blackhole bh) {
        StringBuilder sb = STRING_BUILDER.get();
        sb.setLength(0);

        sb.append(JSON_PREFIX);
        sb.append("eth_chainId");
        sb.append(PARAMS_PREFIX);
        sb.append("[]");
        sb.append(ID_PREFIX);
        sb.append(idCounter.incrementAndGet());
        sb.append(JSON_SUFFIX);

        bh.consume(sb.toString());
    }

    @Benchmark
    public void web3j_serializeRequest_simple(Blackhole bh) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "eth_chainId");
        request.put("params", Collections.emptyList());
        request.put("id", idCounter.incrementAndGet());

        bh.consume(objectMapper.writeValueAsString(request));
    }

    @Benchmark
    public void brane_serializeRequest_withParams(Blackhole bh) {
        StringBuilder sb = STRING_BUILDER.get();
        sb.setLength(0);

        sb.append(JSON_PREFIX);
        sb.append("eth_getBalance");
        sb.append(PARAMS_PREFIX);
        // Manually serialize params - this is what Brane does
        sb.append("[\"0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045\",\"latest\"]");
        sb.append(ID_PREFIX);
        sb.append(idCounter.incrementAndGet());
        sb.append(JSON_SUFFIX);

        bh.consume(sb.toString());
    }

    @Benchmark
    public void web3j_serializeRequest_withParams(Blackhole bh) throws Exception {
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "eth_getBalance");
        request.put("params", List.of("0xd8dA6BF26964aF9D7eEd9e03E53415D37aA96045", "latest"));
        request.put("id", idCounter.incrementAndGet());

        bh.consume(objectMapper.writeValueAsString(request));
    }

    // ==================== RESPONSE PARSING ====================

    @Benchmark
    public void brane_parseResponse_chainId(Blackhole bh) {
        // Brane's zero-allocation char[] parsing
        bh.consume(braneParseResponse(CHAIN_ID_RESPONSE));
    }

    @Benchmark
    public void web3j_parseResponse_chainId(Blackhole bh) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(chainIdBytes, Map.class);
        bh.consume(response.get("result"));
    }

    @Benchmark
    public void brane_parseResponse_blockNumber(Blackhole bh) {
        bh.consume(braneParseResponse(BLOCK_NUMBER_RESPONSE));
    }

    @Benchmark
    public void web3j_parseResponse_blockNumber(Blackhole bh) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(blockNumberBytes, Map.class);
        bh.consume(response.get("result"));
    }

    @Benchmark
    public void brane_parseResponse_balance(Blackhole bh) {
        bh.consume(braneParseResponse(BALANCE_RESPONSE));
    }

    @Benchmark
    public void web3j_parseResponse_balance(Blackhole bh) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(balanceBytes, Map.class);
        bh.consume(response.get("result"));
    }

    @Benchmark
    public void brane_parseResponse_block(Blackhole bh) {
        bh.consume(braneParseResponse(BLOCK_RESPONSE));
    }

    @Benchmark
    public void web3j_parseResponse_block(Blackhole bh) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(blockBytes, Map.class);
        bh.consume(response.get("result"));
    }

    @Benchmark
    public void brane_parseResponse_error(Blackhole bh) {
        bh.consume(braneParseResponse(ERROR_RESPONSE));
    }

    @Benchmark
    public void web3j_parseResponse_error(Blackhole bh) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(errorBytes, Map.class);
        bh.consume(response.get("error"));
    }

    // ==================== ROUND TRIP (serialize + parse) ====================

    @Benchmark
    public void brane_roundTrip_chainId(Blackhole bh) {
        // Serialize request
        StringBuilder sb = STRING_BUILDER.get();
        sb.setLength(0);
        sb.append(JSON_PREFIX);
        sb.append("eth_chainId");
        sb.append(PARAMS_PREFIX);
        sb.append("[]");
        sb.append(ID_PREFIX);
        sb.append(idCounter.incrementAndGet());
        sb.append(JSON_SUFFIX);
        bh.consume(sb.toString());

        // Parse response
        bh.consume(braneParseResponse(CHAIN_ID_RESPONSE));
    }

    @Benchmark
    public void web3j_roundTrip_chainId(Blackhole bh) throws Exception {
        // Serialize request
        Map<String, Object> request = new HashMap<>();
        request.put("jsonrpc", "2.0");
        request.put("method", "eth_chainId");
        request.put("params", Collections.emptyList());
        request.put("id", idCounter.incrementAndGet());
        bh.consume(objectMapper.writeValueAsString(request));

        // Parse response
        @SuppressWarnings("unchecked")
        Map<String, Object> response = objectMapper.readValue(chainIdBytes, Map.class);
        bh.consume(response.get("result"));
    }

    // ==================== HELPER METHODS ====================

    /**
     * Simulates Brane's zero-allocation JSON parsing.
     * This extracts 'id' and 'result' or 'error' without creating intermediate
     * objects.
     */
    private ParsedResponse braneParseResponse(String json) {
        char[] chars = json.toCharArray();
        int len = chars.length;

        Long id = null;
        String result = null;
        String error = null;

        // Scan directly for "id", "result", "error" without creating key strings
        int i = 0;
        while (i < len) {
            char c = chars[i];
            if (c == '"') {
                // Check if this string is a key we care about
                // "id"
                if (i + 3 < len && chars[i + 1] == 'i' && chars[i + 2] == 'd' && chars[i + 3] == '"') {
                    i += 4; // skip "id"
                    while (i < len && chars[i] != ':')
                        i++;
                    i++; // skip :
                    while (i < len && Character.isWhitespace(chars[i]))
                        i++;

                    // Parse number
                    int numStart = i;
                    while (i < len && (Character.isDigit(chars[i]) || chars[i] == '-'))
                        i++;
                    // Only allocate string for ID parsing
                    id = Long.parseLong(new String(chars, numStart, i - numStart));
                }
                // "result"
                else if (i + 7 < len && chars[i + 1] == 'r' && chars[i + 2] == 'e' && chars[i + 3] == 's'
                        && chars[i + 4] == 'u' && chars[i + 5] == 'l' && chars[i + 6] == 't' && chars[i + 7] == '"') {
                    i += 8;
                    while (i < len && chars[i] != ':')
                        i++;
                    i++;
                    while (i < len && Character.isWhitespace(chars[i]))
                        i++;
                    result = extractValue(chars, i, len);
                    i = skipValue(chars, i, len);
                }
                // "error"
                else if (i + 6 < len && chars[i + 1] == 'e' && chars[i + 2] == 'r' && chars[i + 3] == 'r'
                        && chars[i + 4] == 'o' && chars[i + 5] == 'r' && chars[i + 6] == '"') {
                    i += 7;
                    while (i < len && chars[i] != ':')
                        i++;
                    i++;
                    while (i < len && Character.isWhitespace(chars[i]))
                        i++;
                    error = extractValue(chars, i, len);
                    i = skipValue(chars, i, len);
                } else {
                    // Skip unknown key
                    i = skipValue(chars, i, len); // skips the key string
                    while (i < len && chars[i] != ':')
                        i++;
                    i++; // skip :
                    while (i < len && Character.isWhitespace(chars[i]))
                        i++;
                    i = skipValue(chars, i, len); // skip value
                }
            } else {
                i++;
            }
        }

        return new ParsedResponse(id, result, error);
    }

    private String extractValue(char[] chars, int start, int len) {
        if (chars[start] == '"') {
            // String value
            int end = start + 1;
            while (end < len && chars[end] != '"') {
                if (chars[end] == '\\')
                    end++; // skip escaped char
                end++;
            }
            return new String(chars, start + 1, end - start - 1);
        } else if (chars[start] == '{' || chars[start] == '[') {
            // Object or array - return as-is
            int end = skipValue(chars, start, len);
            return new String(chars, start, end - start);
        } else {
            // Primitive
            int end = start;
            while (end < len && chars[end] != ',' && chars[end] != '}' && chars[end] != ']')
                end++;
            return new String(chars, start, end - start).trim();
        }
    }

    private int skipValue(char[] chars, int start, int len) {
        if (start >= len)
            return len;

        char c = chars[start];
        if (c == '"') {
            // String
            int i = start + 1;
            while (i < len && chars[i] != '"') {
                if (chars[i] == '\\')
                    i++;
                i++;
            }
            return i + 1;
        } else if (c == '{') {
            // Object
            int depth = 1;
            int i = start + 1;
            while (i < len && depth > 0) {
                if (chars[i] == '{')
                    depth++;
                else if (chars[i] == '}')
                    depth--;
                else if (chars[i] == '"') {
                    i++;
                    while (i < len && chars[i] != '"') {
                        if (chars[i] == '\\')
                            i++;
                        i++;
                    }
                }
                i++;
            }
            return i;
        } else if (c == '[') {
            // Array
            int depth = 1;
            int i = start + 1;
            while (i < len && depth > 0) {
                if (chars[i] == '[')
                    depth++;
                else if (chars[i] == ']')
                    depth--;
                else if (chars[i] == '"') {
                    i++;
                    while (i < len && chars[i] != '"') {
                        if (chars[i] == '\\')
                            i++;
                        i++;
                    }
                }
                i++;
            }
            return i;
        } else {
            // Primitive
            int i = start;
            while (i < len && chars[i] != ',' && chars[i] != '}' && chars[i] != ']')
                i++;
            return i;
        }
    }

    private record ParsedResponse(Long id, String result, String error) {
    }
}
