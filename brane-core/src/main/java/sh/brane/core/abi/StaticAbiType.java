// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.abi;

/**
 * Represents fixed-size ABI types that always occupy exactly 32 bytes in encoding.
 * <p>
 * Static types have predictable, constant-size encoding regardless of their value.
 * In Ethereum ABI encoding, all static types are padded or encoded to fill a 32-byte slot:
 * <ul>
 *   <li>{@link Bool} - boolean encoded as 32 bytes</li>
 *   <li>{@link AddressType} - 20-byte address left-padded to 32 bytes</li>
 *   <li>{@link Int} - signed integers (int8 to int256) encoded as 32 bytes</li>
 *   <li>{@link UInt} - unsigned integers (uint8 to uint256) encoded as 32 bytes</li>
 * </ul>
 * <p>
 * This is in contrast to dynamic types (bytes, string, arrays) which have variable-length
 * encoding and require offset-based indirection in the ABI encoding scheme.
 *
 * @see AbiType
 * @see AbiEncoder
 * @see AbiDecoder
 * @since 0.1.0-alpha
 */
public sealed interface StaticAbiType extends AbiType permits Bool, AddressType, Int, UInt {

    /**
     * Returns the byte size of this type in ABI encoding.
     * <p>
     * Static types always occupy exactly 32 bytes.
     *
     * @return 32 (always)
     */
    @Override
    default int byteSize() {
        return 32;
    }

    /**
     * Returns whether this type is dynamic.
     * <p>
     * Static types are never dynamic.
     *
     * @return false (always)
     */
    @Override
    default boolean isDynamic() {
        return false;
    }
}
