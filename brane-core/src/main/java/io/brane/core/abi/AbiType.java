package io.brane.core.abi;

/**
 * Represents a Solidity type that can be encoded/decoded.
 */
public sealed interface AbiType permits
        UInt, Int, AddressType, Bool, Bytes, Utf8String, Array, Tuple {

    /**
     * Returns the static byte size of this type (usually 32).
     * For dynamic types, this is the size of the head (offset), which is also 32.
     * The actual data size is separate.
     */
    int byteSize();

    /**
     * Returns true if this type is dynamic (bytes, string, T[], or tuple containing
     * dynamic types).
     */
    boolean isDynamic();

    /**
     * Returns the canonical Solidity type name (e.g., "uint256", "address[]").
     */
    String typeName();
}
