// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.abi;

import java.util.Objects;

import sh.brane.core.types.HexData;

/**
 * Represents a Solidity bytes type (bytes or bytesN).
 *
 * @param value     the HexData wrapper containing the byte array
 * @param isDynamic true for 'bytes', false for 'bytesN'
 */
public record Bytes(HexData value, boolean isDynamic) implements DynamicAbiType {

    /** Maximum size for static bytesN types (bytes1 to bytes32). */
    private static final int MAX_STATIC_BYTES = 32;

    public Bytes {
        Objects.requireNonNull(value, "value cannot be null");
        if (!isDynamic) {
            int byteLen = value.byteLength();
            if (byteLen == 0 || byteLen > MAX_STATIC_BYTES) {
                throw new IllegalArgumentException(
                        "Static bytes must be 1-32 bytes, got " + byteLen);
            }
        }
    }

    @Override
    public int byteSize() {
        return 32;
    }

    @Override
    public String typeName() {
        if (isDynamic) {
            return "bytes";
        }
        return "bytes" + value.byteLength();
    }

    @Override
    public int contentByteSize() {
        if (!isDynamic) {
            return 0;
        }
        int len = value.byteLength();
        int remainder = len % 32;
        int padding = remainder == 0 ? 0 : 32 - remainder;
        return 32 + len + padding; // Length (32) + Data + Padding
    }

    public static Bytes of(byte[] data) {
        return new Bytes(new HexData(sh.brane.primitives.Hex.encode(data)), true);
    }

    public static Bytes ofStatic(byte[] data) {
        return new Bytes(new HexData(sh.brane.primitives.Hex.encode(data)), false);
    }

    /**
     * Creates a static bytesN type from a sub-range of a byte array without copying.
     *
     * @param data   the source byte array
     * @param offset the starting offset
     * @param length the number of bytes (1-32)
     * @return a new static Bytes instance
     */
    public static Bytes ofStatic(byte[] data, int offset, int length) {
        return new Bytes(new HexData(sh.brane.primitives.Hex.encode(data, offset, length)), false);
    }
}
