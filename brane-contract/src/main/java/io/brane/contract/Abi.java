package io.brane.contract;

import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.internal.web3j.utils.Numeric;
import java.nio.charset.StandardCharsets;

public interface Abi {

    static Abi fromJson(final String json) {
        return new InternalAbi(json);
    }

    static Hash eventTopic(final String eventSignature) {
        final String signature = requireNonEmpty(eventSignature, "eventSignature");
        final byte[] digest =
                io.brane.internal.web3j.crypto.Hash.sha3(signature.getBytes(StandardCharsets.UTF_8));
        return new Hash("0x" + Numeric.toHexStringNoPrefix(digest));
    }

    static HexData functionSelector(final String functionSignature) {
        final String signature = requireNonEmpty(functionSignature, "functionSignature");
        final byte[] digest =
                io.brane.internal.web3j.crypto.Hash.sha3(
                        signature.getBytes(StandardCharsets.UTF_8));
        final String hex = Numeric.toHexStringNoPrefix(digest).substring(0, 8);
        return new HexData("0x" + hex);
    }

    FunctionCall encodeFunction(String name, Object... args);

    private static String requireNonEmpty(final String value, final String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must be provided");
        }
        return value;
    }

    interface FunctionCall {
        String data();

        <T> T decode(String output, Class<T> returnType);
    }
}
