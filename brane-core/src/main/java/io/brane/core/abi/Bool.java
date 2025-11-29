package io.brane.core.abi;

/**
 * Represents a Solidity boolean (bool).
 * 
 * @param value the boolean value
 */
public record Bool(boolean value) implements AbiType {
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
        return "bool";
    }
}
