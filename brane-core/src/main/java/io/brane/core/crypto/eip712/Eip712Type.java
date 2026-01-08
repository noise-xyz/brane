package io.brane.core.crypto.eip712;

/**
 * Sealed interface representing all valid EIP-712 types.
 * <p>
 * EIP-712 defines a structured data hashing and signing scheme that supports
 * atomic types (uint, int, address, bool, bytes, string) and composite types
 * (arrays and structs).
 *
 * @see <a href="https://eips.ethereum.org/EIPS/eip-712">EIP-712</a>
 */
public sealed interface Eip712Type permits
        Eip712Type.Uint,
        Eip712Type.Int,
        Eip712Type.Address,
        Eip712Type.Bool,
        Eip712Type.FixedBytes,
        Eip712Type.DynamicBytes,
        Eip712Type.String,
        Eip712Type.Array,
        Eip712Type.Struct {

    /**
     * Unsigned integer type (uint8, uint16, ..., uint256).
     *
     * @param bits the bit width (must be 8-256 and divisible by 8)
     */
    record Uint(int bits) implements Eip712Type {
        public Uint {
            if (bits % 8 != 0 || bits < 8 || bits > 256) {
                throw new IllegalArgumentException("Invalid uint width: " + bits);
            }
        }
    }

    /**
     * Signed integer type (int8, int16, ..., int256).
     *
     * @param bits the bit width (must be 8-256 and divisible by 8)
     */
    record Int(int bits) implements Eip712Type {
        public Int {
            if (bits % 8 != 0 || bits < 8 || bits > 256) {
                throw new IllegalArgumentException("Invalid int width: " + bits);
            }
        }
    }

    /**
     * Ethereum address type (20 bytes).
     */
    record Address() implements Eip712Type {}

    /**
     * Boolean type.
     */
    record Bool() implements Eip712Type {}

    /**
     * Fixed-length byte array type (bytes1, bytes2, ..., bytes32).
     *
     * @param length the byte length (must be 1-32)
     */
    record FixedBytes(int length) implements Eip712Type {
        public FixedBytes {
            if (length < 1 || length > 32) {
                throw new IllegalArgumentException("Invalid bytes length: " + length);
            }
        }
    }

    /**
     * Dynamic-length byte array type (bytes).
     */
    record DynamicBytes() implements Eip712Type {}

    /**
     * Dynamic-length string type.
     */
    record String() implements Eip712Type {}

    /**
     * Array type with optional fixed length.
     *
     * @param elementType the type of elements in the array
     * @param fixedLength the fixed length, or null for dynamic arrays
     */
    record Array(Eip712Type elementType, Integer fixedLength) implements Eip712Type {}

    /**
     * Struct (custom composite) type.
     *
     * @param name the struct type name
     */
    record Struct(java.lang.String name) implements Eip712Type {}
}
