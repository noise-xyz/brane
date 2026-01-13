// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.abi;

/**
 * Represents a Solidity type that can be encoded/decoded.
 *
 * <p>
 * This sealed interface is the root of the ABI type hierarchy. Implementations
 * represent concrete values like {@link UInt}, {@link AddressType}, etc.
 *
 * @see AbiEncoder
 * @see AbiDecoder
 */
public sealed interface AbiType permits
        StaticAbiType, DynamicAbiType {

    /**
     * Returns the static byte size of this type (usually 32).
     * <p>
     * For dynamic types, this is the size of the head (offset), which is also 32.
     * The actual data size is separate.
     *
     * @return the static size in bytes
     */
    int byteSize();

    /**
     * Returns true if this type is dynamic.
     * <p>
     * Dynamic types include bytes, string, T[] (dynamic array), and tuples
     * containing dynamic types.
     *
     * @return true if dynamic, false otherwise
     */
    boolean isDynamic();

    /**
     * Returns the canonical Solidity type name.
     * <p>
     * Examples: "uint256", "address[]", "(bool,uint256)".
     *
     * @return the type name string
     */
    String typeName();

    /**
     * Returns the size in bytes of the dynamic content (tail).
     * Returns 0 for static types.
     *
     * @return the content size in bytes
     */
    default int contentByteSize() {
        return 0;
    }
}
