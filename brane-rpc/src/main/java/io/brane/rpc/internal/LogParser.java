package io.brane.rpc.internal;

import static io.brane.rpc.internal.RpcUtils.MAPPER;

import com.fasterxml.jackson.core.type.TypeReference;
import io.brane.core.model.LogEntry;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Utility class for parsing Ethereum log entries from JSON-RPC responses.
 * <p>
 * This class consolidates log parsing logic used by both PublicClient (eth_getLogs)
 * and WalletClient (transaction receipt logs).
 * <p>
 * <strong>Internal Use Only:</strong> This class is package-private and not part
 * of the public API.
 */
public final class LogParser {

    private LogParser() {
        // Utility class - prevent instantiation
    }

    /**
     * Parses a list of raw log maps into LogEntry objects.
     * <p>
     * Each map is expected to contain:
     * <ul>
     *   <li>{@code address}: Contract address (hex string)</li>
     *   <li>{@code data}: Log data payload (hex string)</li>
     *   <li>{@code topics}: Array of topic hashes</li>
     *   <li>{@code blockHash}: Block hash (hex string)</li>
     *   <li>{@code transactionHash}: Transaction hash (hex string)</li>
     *   <li>{@code logIndex}: Index in block (hex number)</li>
     *   <li>{@code removed}: Whether log was removed due to reorg (boolean)</li>
     * </ul>
     *
     * @param value the raw value from JSON-RPC response (typically a List of Maps)
     * @return list of parsed LogEntry objects
     */
    public static List<LogEntry> parseLogs(final Object value) {
        if (value == null) {
            return List.of();
        }
        final List<Map<String, Object>> rawLogs = MAPPER.convertValue(
                value,
                new TypeReference<List<Map<String, Object>>>() {}
        );
        final List<LogEntry> logs = new ArrayList<>(rawLogs.size());
        for (Map<String, Object> map : rawLogs) {
            logs.add(parseLog(map));
        }
        return List.copyOf(logs);
    }

    /**
     * Parses a single log entry map into a LogEntry object.
     *
     * @param map the raw map from JSON-RPC response
     * @return parsed LogEntry
     */
    public static LogEntry parseLog(final Map<String, Object> map) {
        final String address = RpcUtils.stringValue(map.get("address"));
        final String data = RpcUtils.stringValue(map.get("data"));
        final String blockHash = RpcUtils.stringValue(map.get("blockHash"));
        final String txHash = RpcUtils.stringValue(map.get("transactionHash"));
        final long logIndex = RpcUtils.decodeHexLong(map.get("logIndex"));

        @SuppressWarnings("unchecked")
        final List<String> topicsHex = MAPPER.convertValue(
                map.get("topics"),
                new TypeReference<List<String>>() {}
        );
        final List<Hash> topics = new ArrayList<>();
        if (topicsHex != null) {
            for (String t : topicsHex) {
                topics.add(new Hash(t));
            }
        }

        return new LogEntry(
                address != null ? new Address(address) : null,
                data != null ? new HexData(data) : HexData.EMPTY,
                List.copyOf(topics),
                blockHash != null ? new Hash(blockHash) : null,
                txHash != null ? new Hash(txHash) : null,
                logIndex,
                Boolean.TRUE.equals(map.get("removed")));
    }

    /**
     * Parses a list of raw log maps with strict logIndex validation.
     * <p>
     * Unlike {@link #parseLogs(Object)}, this method throws an exception if
     * any log entry is missing a logIndex. Use this for eth_getLogs results
     * where logIndex is expected.
     *
     * @param value the raw value from JSON-RPC response
     * @param requireLogIndex if true, throws exception when logIndex is missing
     * @return list of parsed LogEntry objects
     * @throws io.brane.core.error.AbiDecodingException if requireLogIndex is true and logIndex is missing
     */
    public static List<LogEntry> parseLogs(final Object value, final boolean requireLogIndex) {
        if (!requireLogIndex) {
            return parseLogs(value);
        }
        if (value == null) {
            return List.of();
        }

        final List<Map<String, Object>> raw = MAPPER.convertValue(
                value,
                new TypeReference<List<Map<String, Object>>>() {}
        );
        final List<LogEntry> logs = new ArrayList<>(raw.size());
        for (Map<String, Object> map : raw) {
            logs.add(parseLogStrict(map));
        }
        return List.copyOf(logs);
    }

    /**
     * Parses a single log entry with strict validation.
     *
     * <p>This method validates that logIndex is present in the log entry. Missing logIndex
     * indicates a malformed response from the RPC node, not an RPC protocol error, hence
     * {@link io.brane.core.error.AbiDecodingException} is thrown rather than RpcException.
     *
     * @param map the raw map from JSON-RPC response
     * @return parsed LogEntry
     * @throws io.brane.core.error.AbiDecodingException if logIndex is missing (malformed response)
     */
    public static LogEntry parseLogStrict(final Map<String, Object> map) {
        final String address = RpcUtils.stringValue(map.get("address"));
        final String data = RpcUtils.stringValue(map.get("data"));
        final String blockHash = RpcUtils.stringValue(map.get("blockHash"));
        final String txHash = RpcUtils.stringValue(map.get("transactionHash"));

        // Check for missing logIndex before decoding (decodeHexLong returns 0 for null)
        final Object rawLogIndex = map.get("logIndex");
        if (rawLogIndex == null) {
            throw new io.brane.core.error.AbiDecodingException(
                    "Missing logIndex in log entry: " + map);
        }
        final long logIndex = RpcUtils.decodeHexLong(rawLogIndex);

        @SuppressWarnings("unchecked")
        final List<String> topicsHex = MAPPER.convertValue(
                map.get("topics"),
                new TypeReference<List<String>>() {}
        );
        final List<Hash> topics = new ArrayList<>();
        if (topicsHex != null) {
            for (String t : topicsHex) {
                topics.add(new Hash(t));
            }
        }

        return new LogEntry(
                address != null ? new Address(address) : null,
                data != null ? new HexData(data) : HexData.EMPTY,
                List.copyOf(topics),
                blockHash != null ? new Hash(blockHash) : null,
                txHash != null ? new Hash(txHash) : null,
                logIndex,
                Boolean.TRUE.equals(map.get("removed")));
    }
}
