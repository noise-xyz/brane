package io.brane.core;

import io.brane.core.error.RevertException;
import io.brane.internal.web3j.abi.FunctionReturnDecoder;
import io.brane.internal.web3j.abi.TypeReference;
import io.brane.internal.web3j.abi.datatypes.Type;
import io.brane.internal.web3j.abi.datatypes.Utf8String;
import io.brane.internal.web3j.abi.datatypes.generated.Uint256;
import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

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

    private static final String ERROR_STRING_SELECTOR = "08c379a0";
    private static final String PANIC_SELECTOR = "4e487b71";

    private static final TypeReference<Utf8String> UTF8_REF = new TypeReference<>() {
    };
    private static final TypeReference<Uint256> UINT256_REF = new TypeReference<>() {
    };
    private static final List<TypeReference<Type<?>>> ERROR_STRING_OUTPUT = List.of(castType(UTF8_REF));
    private static final List<TypeReference<Type<?>>> PANIC_OUTPUT = List.of(castType(UINT256_REF));

    public enum RevertKind {
        ERROR_STRING,
        PANIC,
        CUSTOM,
        UNKNOWN
    }

    public record Decoded(RevertKind kind, String reason, String rawDataHex) {
    }

    public record CustomErrorAbi(String name, List<TypeReference<? extends Type<?>>> outputs) {
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
    @SuppressWarnings("rawtypes")
    public static Decoded decode(
            final String rawDataHex, final Map<String, CustomErrorAbi> customErrors) {
        final Map<String, CustomErrorAbi> errors = customErrors != null ? customErrors : Map.of();
        if (rawDataHex == null || !rawDataHex.startsWith("0x") || rawDataHex.length() < 10) {
            return new Decoded(RevertKind.UNKNOWN, null, rawDataHex);
        }

        final String selector = rawDataHex.substring(2, 10).toLowerCase(Locale.ROOT);

        if (ERROR_STRING_SELECTOR.equals(selector)) {
            final String encoded = "0x" + rawDataHex.substring(10);
            @SuppressWarnings("unchecked")
            final List<Type> results = FunctionReturnDecoder.decode(encoded,
                    (List<TypeReference<Type>>) (List<?>) ERROR_STRING_OUTPUT);
            if (!results.isEmpty() && results.get(0) instanceof Utf8String) {
                return new Decoded(
                        RevertKind.ERROR_STRING, ((Utf8String) results.get(0)).getValue(), rawDataHex);
            }
            return new Decoded(RevertKind.UNKNOWN, null, rawDataHex);
        }

        if (PANIC_SELECTOR.equals(selector)) {
            final String encoded = "0x" + rawDataHex.substring(10);
            @SuppressWarnings("unchecked")
            final List<Type> results = FunctionReturnDecoder.decode(encoded,
                    (List<TypeReference<Type>>) (List<?>) PANIC_OUTPUT);
            if (!results.isEmpty() && results.get(0) instanceof Uint256) {
                final BigInteger code = ((Uint256) results.get(0)).getValue();
                return new Decoded(RevertKind.PANIC, mapPanicReason(code), rawDataHex);
            }
            return new Decoded(RevertKind.UNKNOWN, null, rawDataHex);
        }

        CustomErrorAbi custom = errors.get(selector);
        if (custom == null) {
            custom = errors.get(selector.toUpperCase(Locale.ROOT));
        }
        if (custom != null) {
            final String encoded = "0x" + rawDataHex.substring(10);
            @SuppressWarnings("unchecked")
            final List<Type<?>> results = (List<Type<?>>) (List<?>) FunctionReturnDecoder.decode(encoded,
                    (List<TypeReference<Type>>) (List<?>) castTypes(custom.outputs()));
            final String reason = formatCustomReason(custom.name(), results);
            return new Decoded(RevertKind.CUSTOM, reason, rawDataHex);
        }

        return new Decoded(RevertKind.UNKNOWN, null, rawDataHex);
    }

    @SuppressWarnings("unchecked")
    private static TypeReference<Type<?>> castType(TypeReference<?> ref) {
        return (TypeReference<Type<?>>) (TypeReference<?>) ref;
    }

    @SuppressWarnings("unchecked")
    private static List<TypeReference<Type<?>>> castTypes(List<? extends TypeReference<?>> refs) {
        return (List<TypeReference<Type<?>>>) (List<?>) refs;
    }

    private static String mapPanicReason(final BigInteger code) {
        final String hexCode = code.toString(16);
        return switch (hexCode) {
            case "1" -> "assertion failed";
            case "11" -> "arithmetic overflow or underflow";
            case "12" -> "division or modulo by zero";
            case "21" -> "enum conversion out of range";
            case "22" -> "invalid storage byte array indexing";
            case "31" -> "pop on empty array";
            case "32" -> "array index out of bounds";
            case "41" -> "memory allocation overflow";
            case "51" -> "zero-initialized variable of internal function type";
            default -> "panic with code 0x" + hexCode;
        };
    }

    private static String formatCustomReason(final String name, final List<Type<?>> decoded) {
        if (decoded == null || decoded.isEmpty()) {
            return name;
        }
        final String args = decoded.stream()
                .map(Type::getValue)
                .map(String::valueOf)
                .collect(Collectors.joining(", "));
        return name + "(" + args + ")";
    }
}
