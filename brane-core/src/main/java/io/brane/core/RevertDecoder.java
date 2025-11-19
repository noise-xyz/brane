package io.brane.core;

import io.brane.internal.web3j.abi.FunctionReturnDecoder;
import io.brane.internal.web3j.abi.TypeReference;
import io.brane.internal.web3j.abi.datatypes.Type;
import io.brane.internal.web3j.abi.datatypes.Utf8String;
import java.util.List;

public final class RevertDecoder {

    private static final String ERROR_STRING_SELECTOR = "08c379a0";
    private static final TypeReference<Utf8String> UTF8_REF = new TypeReference<>() {};
    private static final List<TypeReference<Type>> ERROR_STRING_OUTPUT =
            List.of(castType(UTF8_REF));

    public record Decoded(String reason, String rawDataHex) {}

    private RevertDecoder() {}

    public static Decoded decode(final String rawDataHex) {
        if (rawDataHex == null || !rawDataHex.startsWith("0x") || rawDataHex.length() < 10) {
            return new Decoded(null, rawDataHex);
        }

        final String selector = rawDataHex.substring(2, 10);
        if (!ERROR_STRING_SELECTOR.equals(selector)) {
            return new Decoded(null, rawDataHex);
        }

        final String encoded = "0x" + rawDataHex.substring(10);
        final var decoded = FunctionReturnDecoder.decode(encoded, ERROR_STRING_OUTPUT);
        if (decoded.isEmpty()) {
            return new Decoded(null, rawDataHex);
        }
        return new Decoded(((Utf8String) decoded.get(0)).getValue(), rawDataHex);
    }

    @SuppressWarnings("unchecked")
    private static TypeReference<Type> castType(TypeReference<? extends Type> ref) {
        return (TypeReference<Type>) ref;
    }
}
