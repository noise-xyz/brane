package io.brane.core.abi;

import java.util.List;
import java.util.Objects;

/**
 * Represents a Solidity array (static T[N] or dynamic T[]).
 *
 * @param values          the list of elements
 * @param type            the class of the elements (e.g., UInt.class)
 * @param isDynamicLength true for 'T[]', false for 'T[N]'
 * @param elementTypeName the Solidity type name of elements (e.g., "uint256", "address")
 * @param <T>             the type of elements
 */
public record Array<T extends AbiType>(List<T> values, Class<T> type, boolean isDynamicLength, String elementTypeName)
        implements AbiType {
    public Array {
        Objects.requireNonNull(values, "values cannot be null");
        Objects.requireNonNull(type, "type cannot be null");
        Objects.requireNonNull(elementTypeName, "elementTypeName cannot be null");
    }

    @Override
    public int byteSize() {
        if (isDynamic()) {
            return 32;
        }
        // Static array: length * element size
        if (values.isEmpty())
            return 0;
        return values.size() * values.get(0).byteSize();
    }

    @Override
    public boolean isDynamic() {
        // Dynamic if:
        // 1. Dynamic length (T[])
        // 2. Static length but element is dynamic (T[N] where T is dynamic)
        if (isDynamicLength)
            return true;
        if (values.isEmpty())
            return false; // Empty static array is static? Yes.
        return values.get(0).isDynamic();
    }

    @Override
    public String typeName() {
        return elementTypeName + (isDynamicLength ? "[]" : "[" + values.size() + "]");
    }

    @Override
    public int contentByteSize() {
        if (!isDynamic()) {
            return 0;
        }
        int size = 0;
        if (isDynamicLength) {
            size += 32; // Length slot
        }
        for (AbiType v : values) {
            size += v.byteSize();
            if (v.isDynamic()) {
                size += v.contentByteSize();
            }
        }
        return size;
    }
}
