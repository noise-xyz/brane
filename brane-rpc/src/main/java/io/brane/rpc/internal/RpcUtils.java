package io.brane.rpc.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.brane.core.error.RpcException;
import io.brane.core.model.AccessListEntry;
import io.brane.core.types.Hash;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Internal utility methods for RPC data encoding, decoding, and error handling.
 *
 * <p>
 * This class consolidates common RPC-related operations used across the RPC
 * layer:
 * <ul>
 * <li>Hex quantity encoding/decoding (numbers ↔ "0x..." strings)</li>
 * <li>JSON-RPC error data extraction</li>
 * <li>Type conversion helpers</li>
 * <li>Shared ObjectMapper instance</li>
 * </ul>
 *
 * <p>
 * <strong>Internal Use Only:</strong> This class is package-private and not
 * part
 * of the public API. It exists to eliminate code duplication between RPC
 * implementations.
 *
 * @see RpcException
 */
public final class RpcUtils {

    /**
     * Shared, thread-safe ObjectMapper instance for JSON serialization/deserialization.
     * <p>
     * ObjectMapper is expensive to create and thread-safe after configuration,
     * so a single shared instance is used across all RPC classes. The default
     * configuration is suitable for JSON-RPC operations.
     */
    public static final ObjectMapper MAPPER = new ObjectMapper();

    private RpcUtils() {
        // Utility class - prevent instantiation
    }

    /**
     * Recursively extracts error data from complex JSON-RPC error responses.
     * <p>
     * JSON-RPC errors can have nested data structures. This method:
     * <ul>
     *   <li>Returns hex strings (starting with "0x") immediately as revert data</li>
     *   <li>Recursively searches maps, arrays, and iterables for nested hex data</li>
     *   <li>Falls back to string representation if no hex data found</li>
     * </ul>
     * <p>
     * Common patterns:
     * <ul>
     *   <li>Node returns {@code {data: "0x..."}}, extracts to "0x..."</li>
     *   <li>Node returns {@code {data: {data: "0x..."}}}, extracts nested "0x..."</li>
     *   <li>Non-hex strings fall through as error messages</li>
     * </ul>
     *
     * @param dataValue the error data object from JSON-RPC response
     * @return extracted error data string (hex preferred), or null if dataValue is null
     */
    public static String extractErrorData(final Object dataValue) {
        return switch (dataValue) {
            case null -> null;
            case String s when s.trim().startsWith("0x") -> s;
            case Map<?, ?> map -> extractFromIterable(map.values(), dataValue);
            case Object array when dataValue.getClass().isArray() -> extractFromArray(array, dataValue);
            case Iterable<?> iterable -> extractFromIterable(iterable, dataValue);
            default -> dataValue.toString();
        };
    }

    /**
     * Extracts hex error data from an iterable, preferring "0x..." values.
     */
    public static String extractFromIterable(final Iterable<?> iterable, final Object fallback) {
        for (final Object item : iterable) {
            final String extracted = extractErrorData(item);
            // Only return if we found a hex string (actual revert data)
            if (extracted != null && extracted.trim().startsWith("0x")) {
                return extracted;
            }
        }
        return fallback.toString();
    }

    /**
     * Extracts hex error data from an array, preferring "0x..." values.
     */
    public static String extractFromArray(final Object array, final Object fallback) {
        final int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            final String extracted = extractErrorData(Array.get(array, i));
            // Only return if we found a hex string (actual revert data)
            if (extracted != null && extracted.trim().startsWith("0x")) {
                return extracted;
            }
        }
        return fallback.toString();
    }

    /**
     * Safely converts object to string, returning null for null inputs.
     */
    public static String stringValue(final Object value) {
        return value != null ? value.toString() : null;
    }

    /**
     * Decodes hex quantity string to Long, handling "0x" prefix and null/empty
     * values.
     */
    public static Long decodeHexLong(final Object value) {
        if (value == null) {
            return null;
        }
        final String hex = value.toString();
        if (hex.isEmpty()) {
            return 0L;
        }
        final String normalized = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (normalized.isEmpty()) {
            return 0L;
        }
        return Long.parseLong(normalized, 16);
    }

    /**
     * Decodes a hex quantity string to {@link BigInteger}.
     *
     * <p>This method handles the "0x" prefix and treats null/empty values as zero,
     * which aligns with JSON-RPC conventions where absent or empty fields often
     * represent zero values.
     *
     * <p><strong>Null/Empty Handling:</strong>
     * <ul>
     *   <li>{@code null} → {@link BigInteger#ZERO}</li>
     *   <li>{@code ""} (empty string) → {@link BigInteger#ZERO}</li>
     *   <li>{@code "0x"} (prefix only) → {@link BigInteger#ZERO}</li>
     *   <li>{@code "0x0"} → {@link BigInteger#ZERO}</li>
     *   <li>{@code "0x1a"} → {@code BigInteger.valueOf(26)}</li>
     * </ul>
     *
     * <p><strong>Note:</strong> This lenient behavior is intentional for RPC parsing.
     * If you need strict validation that rejects empty strings, validate the input
     * before calling this method.
     *
     * @param hex the hex string to decode, may be null or empty
     * @return the decoded value, never null (returns {@link BigInteger#ZERO} for null/empty input)
     * @throws NumberFormatException if the string contains invalid hex characters
     */
    public static BigInteger decodeHexBigInteger(final String hex) {
        if (hex == null || hex.isEmpty()) {
            return BigInteger.ZERO;
        }
        final String normalized = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (normalized.isEmpty()) {
            return BigInteger.ZERO;
        }
        return new BigInteger(normalized, 16);
    }

    /**
     * Converts BigInteger to hex quantity string with "0x" prefix.
     */
    public static String toQuantityHex(final BigInteger value) {
        return "0x" + value.toString(16);
    }

    /**
     * Converts Long block number to hex string with "0x" prefix, handling null.
     */
    public static String toHexBlock(final Long block) {
        if (block == null) {
            return null;
        }
        return "0x" + Long.toHexString(block).toLowerCase();
    }

    /**
     * Converts an access list to JSON-RPC format for transaction calls.
     * <p>
     * Each entry is converted to a map with:
     * <ul>
     *   <li>{@code address}: The contract address as hex string</li>
     *   <li>{@code storageKeys}: List of storage slot hashes as hex strings</li>
     * </ul>
     *
     * @param entries the access list entries
     * @return list of maps suitable for JSON-RPC serialization
     */
    public static List<Map<String, Object>> toJsonAccessList(final List<AccessListEntry> entries) {
        return entries.stream()
                .map(entry -> {
                    final Map<String, Object> map = new LinkedHashMap<>();
                    map.put("address", entry.address().value());
                    map.put("storageKeys", entry.storageKeys().stream().map(Hash::value).toList());
                    return map;
                })
                .toList();
    }
}
