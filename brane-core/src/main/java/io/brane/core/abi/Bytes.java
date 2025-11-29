package io.brane.core.abi;

import io.brane.core.types.HexData;
import java.util.Objects;

/**
 * Represents a Solidity bytes type (bytes or bytesN).
 * 
 * @param value     the HexData wrapper containing the byte array
 * @param isDynamic true for 'bytes', false for 'bytesN'
 */
public record Bytes(HexData value, boolean isDynamic) implements AbiType {
    public Bytes {
        Objects.requireNonNull(value, "value cannot be null");
        if (!isDynamic && value.value().length() > 66) { // 0x + 32 bytes * 2 = 66 chars
            // Actually bytesN can be up to 32 bytes.
            // If it's static, it must be bytes1..bytes32.
            // We don't strictly enforce N here, but we could.
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
        // Calculate N from value length?
        // Or should we store N?
        // Usually bytesN implies a fixed size.
        // If we just wrap HexData, we might not know the intended N.
        // But for encoding, we just pad to 32.
        // For typeName generation, we might need N.
        // Let's assume bytes32 for now if static, or derive from length.
        int n = (value.value().length() - 2) / 2;
        return "bytes" + n;
    }

    public static Bytes of(byte[] data) {
        return new Bytes(new HexData(io.brane.primitives.Hex.encode(data)), true);
    }

    public static Bytes ofStatic(byte[] data) {
        return new Bytes(new HexData(io.brane.primitives.Hex.encode(data)), false);
    }
}
