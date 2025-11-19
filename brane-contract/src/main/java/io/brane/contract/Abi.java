package io.brane.contract;

public interface Abi {

    static Abi fromJson(final String json) {
        return new InternalAbi(json);
    }

    FunctionCall encodeFunction(String name, Object... args);

    interface FunctionCall {
        String data();

        <T> T decode(String output, Class<T> returnType);
    }
}
