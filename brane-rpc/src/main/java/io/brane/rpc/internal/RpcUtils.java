package io.brane.rpc.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.brane.core.error.RpcException;
import java.lang.reflect.Array;
import java.math.BigInteger;
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
     * 
     * <p>
     * JSON-RPC errors can have nested data structures. This method:
     * <ul>
     * <li>Returns strings immediately (already extracted)</li>
     * <li>Recursively searches maps, arrays, and iterables for nested data</li>
     * <li>Falls back to string representation if no specific data found</li>
     * </ul>
     * 
     * <p>
     * Common pattern: Node returns {@code {data: {data: "0x..."}}}, this flattens
     * to "0x...".
     * 
     * @param dataValue the error data object from JSON-RPC response
     * @return extracted error data string, or null if dataValue is null
     */
    public static String extractErrorData(final Object dataValue) {
        return switch (dataValue) {
            case null -> null;
            case String s -> s;
            case Map<?, ?> map -> map.values().stream()
                    .map(RpcUtils::extractErrorData)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElseGet(dataValue::toString);
            case Object array when dataValue.getClass().isArray() -> extractFromArray(array, dataValue);
            case Iterable<?> iterable -> extractFromIterable(iterable, dataValue);
            default -> dataValue.toString();
        };
    }

    public static String extractFromIterable(final Iterable<?> iterable, final Object fallback) {
        for (final Object item : iterable) {
            final String extracted = extractErrorData(item);
            if (extracted != null) {
                return extracted;
            }
        }
        return fallback.toString();
    }

    public static String extractFromArray(final Object array, final Object fallback) {
        final int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            final String extracted = extractErrorData(Array.get(array, i));
            if (extracted != null) {
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
     * Decodes hex quantity string to BigInteger, handling "0x" prefix and
     * null/empty values.
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
}
