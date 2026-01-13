// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import sh.brane.core.abi.AbiDecoder;
import sh.brane.core.abi.AbiType;
import sh.brane.core.abi.TypeSchema;
import sh.brane.core.abi.UInt;
import sh.brane.core.abi.Utf8String;
import sh.brane.core.error.RevertException;
import sh.brane.core.error.RpcException;
import sh.brane.primitives.Hex;

/**
 * Decodes EVM revert reasons from raw transaction revert data.
 *
 * <p>
 * When a smart contract reverts, the EVM returns hex-encoded revert data.
 * This class decodes that data into human-readable revert reasons and
 * categorizes
 * the revert type.
 *
 * <p>
 * <strong>Revert Types:</strong>
 * <ul>
 * <li><strong>ERROR_STRING:</strong> Standard {@code Error(string)} revert with
 * message
 * <ul>
 * <li>Selector: {@code 0x08c379a0}</li>
 * <li>Triggered by: {@code revert("reason")},
 * {@code require(false, "reason")}</li>
 * <li>Example: "Insufficient balance"</li>
 * </ul>
 * </li>
 * <li><strong>PANIC:</strong> Solidity {@code Panic(uint256)} with error code
 * <ul>
 * <li>Selector: {@code 0x4e487b71}</li>
 * <li>Triggered by: {@code assert(false)}, division by zero, array out of
 * bounds</li>
 * <li>Codes: 0x01=assert, 0x11=overflow, 0x12=div by zero, 0x32=array bounds,
 * etc.</li>
 * </ul>
 * </li>
 * <li><strong>CUSTOM:</strong> Solidity custom errors (defined with
 * {@code error} keyword)
 * <ul>
 * <li>More gas-efficient than string reverts</li>
 * <li>Can include typed parameters</li>
 * <li>Example:
 * {@code error InsufficientBalance(uint256 available, uint256 required);}</li>
 * </ul>
 * </li>
 * <li><strong>UNKNOWN:</strong> Unrecognized revert data format</li>
 * </ul>
 *
 * <p>
 * <strong>Usage Example:</strong>
 *
 * <pre>{@code
 * try {
 *     client.sendTransactionAndWait(request);
 * } catch (RevertException e) {
 *     RevertDecoder.Decoded decoded = RevertDecoder.decode(e.rawDataHex());
 *
 *     switch (decoded.kind()) {
 *         case ERROR_STRING ->
 *             System.err.println("Reverted: " + decoded.reason());
 *         case PANIC ->
 *             System.err.println("Panic code: " + decoded.reason());
 *         case CUSTOM ->
 *             System.err.println("Custom error: " + decoded.reason());
 *     }
 * }
 * }</pre>
 *
 * @see RevertException
 */
public final class RevertDecoder {

    private static final Logger LOG = LoggerFactory.getLogger(RevertDecoder.class);

    private static final String ERROR_STRING_SELECTOR = "08c379a0";
    private static final String PANIC_SELECTOR = "4e487b71";

    private static final List<TypeSchema> ERROR_STRING_OUTPUT = List.of(new TypeSchema.StringSchema());
    private static final List<TypeSchema> PANIC_OUTPUT = List.of(new TypeSchema.UIntSchema(256));

    public enum RevertKind {
        ERROR_STRING,
        PANIC,
        CUSTOM,
        UNKNOWN
    }

    public record Decoded(RevertKind kind, String reason, String rawDataHex) {
    }

    public record CustomErrorAbi(String name, List<TypeSchema> outputs) {
        public CustomErrorAbi {
            Objects.requireNonNull(name, "name must not be null");
            outputs = Objects.requireNonNullElse(outputs, List.of());
        }
    }

    private RevertDecoder() {
    }

    /**
     * Decodes a revert reason from raw hex data.
     *
     * @param rawDataHex the raw hex string returned by the node (e.g. "0x...")
     * @return the decoded result, or {@link RevertKind#UNKNOWN} if decoding fails
     */
    public static Decoded decode(final String rawDataHex) {
        return decode(rawDataHex, Map.of());
    }

    /**
     * Decodes a revert reason using a set of known custom error ABIs.
     *
     * @param rawDataHex   the raw hex string
     * @param customErrors a map of 4-byte selectors (without "0x") to error
     *                     definitions
     * @return the decoded result
     */
    public static Decoded decode(
            final String rawDataHex, final Map<String, CustomErrorAbi> customErrors) {
        final Map<String, CustomErrorAbi> errors = Objects.requireNonNullElse(customErrors, Map.of());
        if (rawDataHex == null || !rawDataHex.startsWith("0x") || rawDataHex.length() < 10) {
            return new Decoded(RevertKind.UNKNOWN, null, rawDataHex);
        }

        final String selector = rawDataHex.substring(2, 10).toLowerCase(Locale.ROOT);

        if (ERROR_STRING_SELECTOR.equals(selector)) {
            final String encoded = rawDataHex.substring(10);
            try {
                final List<AbiType> results = AbiDecoder.decode(Hex.decode(encoded), ERROR_STRING_OUTPUT);
                if (!results.isEmpty() && results.get(0) instanceof Utf8String str) {
                    return new Decoded(
                            RevertKind.ERROR_STRING, str.value(), rawDataHex);
                }
            } catch (Exception e) {
                // Fallthrough to UNKNOWN - selector matched but payload was malformed
                LOG.debug("Failed to decode Error(string) revert data: {}", e.getMessage());
            }
            return new Decoded(RevertKind.UNKNOWN, null, rawDataHex);
        }

        if (PANIC_SELECTOR.equals(selector)) {
            final String encoded = rawDataHex.substring(10);
            try {
                final List<AbiType> results = AbiDecoder.decode(Hex.decode(encoded), PANIC_OUTPUT);
                if (!results.isEmpty() && results.get(0) instanceof UInt u) {
                    final BigInteger code = u.value();
                    return new Decoded(RevertKind.PANIC, mapPanicReason(code), rawDataHex);
                }
            } catch (Exception e) {
                // Fallthrough to UNKNOWN - selector matched but payload was malformed
                LOG.debug("Failed to decode Panic(uint256) revert data: {}", e.getMessage());
            }
            return new Decoded(RevertKind.UNKNOWN, null, rawDataHex);
        }

        CustomErrorAbi custom = errors.get(selector);
        if (custom == null) {
            custom = errors.get(selector.toUpperCase(Locale.ROOT));
        }
        if (custom != null) {
            final String encoded = rawDataHex.substring(10);
            try {
                final List<AbiType> results = AbiDecoder.decode(Hex.decode(encoded), custom.outputs());
                final String reason = formatCustomReason(custom.name(), results);
                return new Decoded(RevertKind.CUSTOM, reason, rawDataHex);
            } catch (Exception e) {
                // Fallthrough to UNKNOWN - custom error matched but payload was malformed
                LOG.debug("Failed to decode custom error '{}': {}", custom.name(), e.getMessage());
            }
        }

        return new Decoded(RevertKind.UNKNOWN, null, rawDataHex);
    }

    /**
     * Checks if an RpcException contains revert data and throws a RevertException if so.
     *
     * <p>This method inspects the exception's data field. If it contains valid revert data
     * (starts with "0x" and has sufficient length for a selector), it decodes the revert
     * reason and throws a {@link RevertException}.
     *
     * @param e the RpcException to check
     * @throws RevertException if the exception contains decodable revert data
     */
    public static void throwIfRevert(final RpcException e) throws RevertException {
        final String raw = e.data();
        if (raw != null && raw.startsWith("0x") && raw.length() > 10) {
            final Decoded decoded = decode(raw);
            throw new RevertException(decoded.kind(), decoded.reason(), decoded.rawDataHex(), e);
        }
    }

    private static String mapPanicReason(final BigInteger code) {
        // Solidity panic codes - switch on integer value for clarity
        // See: https://docs.soliditylang.org/en/latest/control-structures.html#panic-via-assert-and-error-via-require
        // Guard against overflow: known panic codes fit in an int
        if (code.compareTo(BigInteger.valueOf(Integer.MAX_VALUE)) > 0 || code.signum() < 0) {
            return "panic with code 0x" + code.toString(16);
        }
        return switch (code.intValue()) {
            case 0x01 -> "assertion failed";
            case 0x11 -> "arithmetic overflow or underflow";
            case 0x12 -> "division or modulo by zero";
            case 0x21 -> "enum conversion out of range";
            case 0x22 -> "invalid storage byte array indexing";
            case 0x31 -> "pop on empty array";
            case 0x32 -> "array index out of bounds";
            case 0x41 -> "memory allocation overflow";
            case 0x51 -> "zero-initialized variable of internal function type";
            default -> "panic with code 0x" + code.toString(16);
        };
    }

    private static String formatCustomReason(final String name, final List<AbiType> decoded) {
        if (decoded == null || decoded.isEmpty()) {
            return name;
        }
        final String args = decoded.stream()
                .map(RevertDecoder::formatValue)
                .collect(Collectors.joining(", "));
        return name + "(" + args + ")";
    }

    private static String formatValue(AbiType type) {
        if (type instanceof Utf8String s) {
            return s.value();
        }
        if (type instanceof UInt u) {
            return String.valueOf(u.value());
        }
        // Add other types as needed, or just toString()
        return type.toString();
    }
}
