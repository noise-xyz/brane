// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc.internal;

import java.lang.reflect.Array;
import java.math.BigInteger;
import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.brane.core.DebugLogger;
import io.brane.core.InternalApi;
import io.brane.core.LogFormatter;
import io.brane.core.error.RpcException;
import io.brane.core.model.AccessListEntry;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.rpc.JsonRpcError;

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
 * <strong>Internal Use Only:</strong> This class is not part of the public API.
 * It exists to eliminate code duplication between RPC implementations. Methods
 * are public only due to cross-package access requirements within the SDK.
 *
 * @see RpcException
 */
@InternalApi
public final class RpcUtils {

    /**
     * Shared, thread-safe ObjectMapper instance for JSON serialization/deserialization.
     *
     * <p>ObjectMapper is expensive to create and thread-safe after configuration,
     * so a single shared instance is used across all RPC classes.
     *
     * <h3>Jackson Configuration</h3>
     * <p>This mapper uses Jackson's default configuration with no additional modules registered:
     * <ul>
     *   <li>No custom modules registered (e.g., no JavaTimeModule, Jdk8Module)</li>
     *   <li>Default serialization/deserialization settings</li>
     *   <li>Unknown properties are NOT ignored by default (can fail on unknown fields)</li>
     *   <li>Standard JSON parsing (no comments, trailing commas, etc.)</li>
     * </ul>
     *
     * <h3>Brane Type Handling</h3>
     * <p>Brane domain types ({@code Address}, {@code Wei}, {@code Hash}, etc.) are NOT
     * automatically serializable as they lack Jackson annotations. The RPC layer handles
     * these types by:
     * <ul>
     *   <li>Manually extracting primitive values (e.g., {@code address.value()}) before serialization</li>
     *   <li>Constructing domain objects from parsed JSON primitives</li>
     * </ul>
     *
     * <h3>Customization</h3>
     * <p>This mapper is intentionally not configurable to maintain simplicity and consistency
     * across the SDK. If you need custom serialization behavior:
     * <ul>
     *   <li>Use your own ObjectMapper for application-level JSON processing</li>
     *   <li>The Brane RPC layer will continue using this internal mapper for JSON-RPC</li>
     * </ul>
     *
     * <p><strong>Thread Safety:</strong> This instance is safe for concurrent use across threads.
     * Do not modify its configuration after initialization.
     *
     * @see com.fasterxml.jackson.databind.ObjectMapper
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
     * Creates an RpcException from a JSON-RPC error response.
     *
     * <p>This factory method consolidates the common pattern of converting
     * a {@link JsonRpcError} to an {@link RpcException}, including proper
     * extraction of error data.
     *
     * @param err the JSON-RPC error from the response
     * @return a new RpcException with the error details
     */
    public static RpcException toRpcException(final JsonRpcError err) {
        return new RpcException(err.code(), err.message(), extractErrorData(err.data()), (Long) null);
    }

    /**
     * Safely converts object to string, returning null for null inputs.
     */
    public static String stringValue(final Object value) {
        return value != null ? value.toString() : null;
    }

    /**
     * Decodes a hex quantity string to {@code long}.
     *
     * <p>This method handles the "0x" prefix and treats null/empty values as zero,
     * which aligns with JSON-RPC conventions where absent or empty fields often
     * represent zero values.
     *
     * <p><strong>Null/Empty Handling:</strong>
     * <ul>
     *   <li>{@code null} → {@code 0L}</li>
     *   <li>{@code ""} (empty string) → {@code 0L}</li>
     *   <li>{@code "0x"} (prefix only) → {@code 0L}</li>
     *   <li>{@code "0x0"} → {@code 0L}</li>
     *   <li>{@code "0x1a"} → {@code 26L}</li>
     * </ul>
     *
     * <p><strong>Note:</strong> This lenient behavior is intentional for RPC parsing.
     * If you need strict validation that rejects null/empty strings, validate the input
     * before calling this method.
     *
     * @param value the hex string to decode, may be null or empty
     * @return the decoded value (returns {@code 0L} for null/empty input)
     * @throws NumberFormatException if the string contains invalid hex characters
     * @see #decodeHexBigInteger(String)
     */
    public static long decodeHexLong(final Object value) {
        if (value == null) {
            return 0L;
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
                    // Explicit type required: var would infer LinkedHashMap, causing List<Map> incompatibility
                    final Map<String, Object> map = new LinkedHashMap<>();
                    map.put("address", entry.address().value());
                    map.put("storageKeys", entry.storageKeys().stream().map(Hash::value).toList());
                    return map;
                })
                .toList();
    }

    /**
     * Builds a transaction object map suitable for JSON-RPC calls like
     * {@code eth_estimateGas} and {@code eth_call}.
     * <p>
     * Converts a {@link TransactionRequest} into the map format expected by
     * Ethereum JSON-RPC methods. Only non-null fields are included in the output.
     * <p>
     * Output fields:
     * <ul>
     *   <li>{@code from}: Sender address (required)</li>
     *   <li>{@code to}: Recipient address (optional)</li>
     *   <li>{@code value}: Wei value as hex quantity (optional)</li>
     *   <li>{@code data}: Call data as hex string (optional)</li>
     *   <li>{@code accessList}: EIP-2930 access list (optional)</li>
     * </ul>
     *
     * @param request the transaction request
     * @param from the sender address to use (overrides request.from() if different)
     * @return map suitable for JSON-RPC serialization
     */
    public static Map<String, Object> buildTxObject(final TransactionRequest request, final Address from) {
        final Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("from", from.value());
        request.toOpt().ifPresent(address -> tx.put("to", address.value()));
        request.valueOpt().ifPresent(v -> tx.put("value", toQuantityHex(v.value())));
        if (request.data() != null) {
            tx.put("data", request.data().value());
        }
        if (request.accessList() != null && !request.accessList().isEmpty()) {
            tx.put("accessList", toJsonAccessList(request.accessList()));
        }
        return tx;
    }

    /**
     * Executes an {@code eth_estimateGas} call with consistent debug logging and timing.
     *
     * <p>This method extracts the common pattern used by {@code DefaultSigner}
     * and {@code SmartGasStrategy} for gas estimation:
     * <ol>
     *   <li>Logs the estimate gas request (from, to, data)</li>
     *   <li>Times the RPC call execution</li>
     *   <li>Logs the result with timing information</li>
     * </ol>
     *
     * <p>The actual RPC call is delegated to the provided supplier, allowing callers
     * to use their own error handling and RPC mechanisms.
     *
     * @param tx the transaction object map (must contain "from", optionally "to" and "data")
     * @param rpcCall supplier that executes the actual {@code eth_estimateGas} RPC call
     * @return the gas estimate as a hex string (e.g., "0x5208")
     * @throws RpcException if the RPC call fails (propagated from supplier)
     */
    public static String timedEstimateGas(final Map<String, Object> tx, final Supplier<String> rpcCall) {
        DebugLogger.logTx(LogFormatter.formatEstimateGas(
                String.valueOf(tx.get("from")),
                String.valueOf(tx.get("to")),
                String.valueOf(tx.get("data"))));
        final long start = System.nanoTime();
        final String result = rpcCall.get();
        final long durationMicros = (System.nanoTime() - start) / 1_000L;
        DebugLogger.logTx(LogFormatter.formatEstimateGasResult(durationMicros, result));
        return result;
    }

    /**
     * Validates a URL string for RPC endpoint configuration.
     *
     * <p>This method performs common URL validation used by RPC configuration classes:
     * <ul>
     *   <li>Checks for null (throws NullPointerException)</li>
     *   <li>Validates the URL is a valid URI</li>
     *   <li>Validates the scheme is one of the allowed schemes</li>
     *   <li>Validates the host is present and non-empty</li>
     * </ul>
     *
     * @param url the URL string to validate
     * @param allowedSchemes the set of allowed URL schemes (e.g., "http", "https", "ws", "wss")
     * @throws NullPointerException if url is null
     * @throws IllegalArgumentException if the URL is invalid or uses an unsupported scheme
     */
    public static void validateUrl(final String url, final Set<String> allowedSchemes) {
        Objects.requireNonNull(url, "url");
        Objects.requireNonNull(allowedSchemes, "allowedSchemes");

        final String schemeList = String.join(" or ", allowedSchemes);

        try {
            URI uri = URI.create(url);
            String scheme = uri.getScheme();
            if (scheme == null || !allowedSchemes.contains(scheme.toLowerCase())) {
                throw new IllegalArgumentException(
                        "url must use " + schemeList + " scheme, got: " + (scheme == null ? "null" : scheme));
            }
            if (uri.getHost() == null || uri.getHost().isEmpty()) {
                throw new IllegalArgumentException("url must have a valid host");
            }
        } catch (IllegalArgumentException e) {
            if (e.getMessage().startsWith("url must")) {
                throw e;
            }
            throw new IllegalArgumentException("url is not a valid URI: " + e.getMessage(), e);
        }
    }

    /**
     * HTTP/HTTPS schemes for RPC endpoint URLs.
     * Insertion order preserved for consistent error messages.
     */
    public static final Set<String> HTTP_SCHEMES = new java.util.LinkedHashSet<>(List.of("http", "https"));

    /**
     * WebSocket schemes for RPC endpoint URLs.
     * Insertion order preserved for consistent error messages.
     */
    public static final Set<String> WS_SCHEMES = new java.util.LinkedHashSet<>(List.of("ws", "wss"));
}
