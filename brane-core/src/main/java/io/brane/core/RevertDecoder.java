package io.brane.core;

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

public final class RevertDecoder {

    private static final String ERROR_STRING_SELECTOR = "08c379a0";
    private static final String PANIC_SELECTOR = "4e487b71";

    private static final TypeReference<Utf8String> UTF8_REF = new TypeReference<>() {};
    private static final TypeReference<Uint256> UINT256_REF = new TypeReference<>() {};
    private static final List<TypeReference<Type>> ERROR_STRING_OUTPUT =
            List.of(castType(UTF8_REF));
    private static final List<TypeReference<Type>> PANIC_OUTPUT = List.of(castType(UINT256_REF));

    public enum RevertKind {
        ERROR_STRING,
        PANIC,
        CUSTOM,
        UNKNOWN
    }

    public record Decoded(RevertKind kind, String reason, String rawDataHex) {}

    public record CustomErrorAbi(String name, List<TypeReference<? extends Type>> outputs) {
        public CustomErrorAbi {
            Objects.requireNonNull(name, "name must not be null");
            outputs = Objects.requireNonNullElse(outputs, List.of());
        }
    }

    private RevertDecoder() {}

    public static Decoded decode(final String rawDataHex) {
        return decode(rawDataHex, Map.of());
    }

    public static Decoded decode(
            final String rawDataHex, final Map<String, CustomErrorAbi> customErrors) {
        final Map<String, CustomErrorAbi> errors = customErrors != null ? customErrors : Map.of();
        if (rawDataHex == null || !rawDataHex.startsWith("0x") || rawDataHex.length() < 10) {
            return new Decoded(RevertKind.UNKNOWN, null, rawDataHex);
        }

        final String selector = rawDataHex.substring(2, 10).toLowerCase(Locale.ROOT);

        if (ERROR_STRING_SELECTOR.equals(selector)) {
            final String encoded = "0x" + rawDataHex.substring(10);
            final var decoded = FunctionReturnDecoder.decode(encoded, ERROR_STRING_OUTPUT);
            if (decoded.isEmpty()) {
                return new Decoded(RevertKind.UNKNOWN, null, rawDataHex);
            }
            return new Decoded(
                    RevertKind.ERROR_STRING, ((Utf8String) decoded.get(0)).getValue(), rawDataHex);
        }

        if (PANIC_SELECTOR.equals(selector)) {
            final String encoded = "0x" + rawDataHex.substring(10);
            final var decoded = FunctionReturnDecoder.decode(encoded, PANIC_OUTPUT);
            if (decoded.isEmpty()) {
                return new Decoded(RevertKind.UNKNOWN, null, rawDataHex);
            }
            final BigInteger code = ((Uint256) decoded.get(0)).getValue();
            return new Decoded(RevertKind.PANIC, mapPanicReason(code), rawDataHex);
        }

        CustomErrorAbi custom = errors.get(selector);
        if (custom == null) {
            custom = errors.get(selector.toUpperCase(Locale.ROOT));
        }
        if (custom != null) {
            final String encoded = "0x" + rawDataHex.substring(10);
            final var decoded =
                    FunctionReturnDecoder.decode(encoded, castTypes(custom.outputs()));
            final String reason = formatCustomReason(custom.name(), decoded);
            return new Decoded(RevertKind.CUSTOM, reason, rawDataHex);
        }

        return new Decoded(RevertKind.UNKNOWN, null, rawDataHex);
    }

    @SuppressWarnings("unchecked")
    private static TypeReference<Type> castType(TypeReference<? extends Type> ref) {
        return (TypeReference<Type>) ref;
    }

    private static List<TypeReference<Type>> castTypes(List<TypeReference<? extends Type>> refs) {
        return refs.stream().map(RevertDecoder::castType).collect(Collectors.toList());
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

    private static String formatCustomReason(final String name, final List<Type> decoded) {
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
