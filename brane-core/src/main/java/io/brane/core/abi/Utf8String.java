package io.brane.core.abi;

import java.util.Objects;

/**
 * Represents a Solidity string (UTF-8 encoded).
 * 
 * @param value the string value
 */
public record Utf8String(String value) implements AbiType {
    public Utf8String {
        Objects.requireNonNull(value, "value cannot be null");
    }

    @Override
    public int byteSize() {
        return 32;
    }

    @Override
    public boolean isDynamic() {
        return true;
    }

    @Override
    public String typeName() {
        return "string";
    }
}
