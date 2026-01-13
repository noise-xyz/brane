// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.abi;

import java.util.Objects;

/**
 * Represents a Solidity string (UTF-8 encoded).
 *
 * @param value the string value
 */
public record Utf8String(String value) implements DynamicAbiType {
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
        int len = utf8ByteLength(value);
        int remainder = len % 32;
        int padding = remainder == 0 ? 0 : 32 - remainder;
        return 32 + len + padding; // Length (32) + Data + Padding
    }

    /**
     * Computes UTF-8 byte length without allocating a byte array.
     *
     * <p>This is more efficient than {@code value.getBytes(UTF_8).length} which
     * allocates a new array on every call.
     *
     * <p>Handles surrogate pairs correctly: a valid surrogate pair (high + low)
     * encodes to 4 UTF-8 bytes. Lone surrogates are treated as 3-byte BMP characters.
     */
    private static int utf8ByteLength(String s) {
        int len = 0;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x80) {
                len += 1;
            } else if (c < 0x800) {
                len += 2;
            } else if (Character.isHighSurrogate(c)
                    && i + 1 < s.length()
                    && Character.isLowSurrogate(s.charAt(i + 1))) {
                // Valid surrogate pair: encodes to 4 UTF-8 bytes
                len += 4;
                i++; // Skip low surrogate
            } else {
                // BMP character or lone surrogate: 3 bytes
                len += 3;
            }
        }
        return len;
    }
}
