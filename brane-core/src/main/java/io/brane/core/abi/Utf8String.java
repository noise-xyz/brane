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

    @Override
    public int contentByteSize() {
        // We calculate UTF-8 length.
        // Optimization: Avoid full array allocation if possible, but standard library
        // is fast.
        // For now, standard getBytes is fine, but we could cache it if we changed to a
        // class.
        // Since this is a record, we just calculate.
        int len = value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        int remainder = len % 32;
        int padding = remainder == 0 ? 0 : 32 - remainder;
        return 32 + len + padding; // Length (32) + Data + Padding
    }
}
