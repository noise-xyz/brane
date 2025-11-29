package io.brane.core.abi;

import io.brane.core.types.Address;
import java.util.Objects;

/**
 * Represents a Solidity address (20 bytes).
 * 
 * @param value the Address wrapper containing the 20-byte value
 */
public record AddressType(Address value) implements AbiType {
    public AddressType {
        Objects.requireNonNull(value, "value cannot be null");
    }

    @Override
    public int byteSize() {
        return 32;
    }

    @Override
    public boolean isDynamic() {
        return false;
    }

    @Override
    public String typeName() {
        return "address";
    }
}
