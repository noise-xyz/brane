// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.abi;

import java.util.List;
import java.util.Objects;

/**
 * Represents a Solidity array (static T[N] or dynamic T[]).
 *
 * <p>The {@code elementTypeName} parameter is required to correctly generate the
 * Solidity type signature for the array (e.g., "uint256[]", "address[5]").
 * This is necessary because Java's type erasure prevents inferring the element
 * type name at runtime, especially for empty arrays.
 *
 * <h2>Example</h2>
 * <pre>{@code
 * // Dynamic array of uint256
 * Array<UInt> dynamicArray = new Array<>(
 *     List.of(new UInt(1), new UInt(2)),
 *     UInt.class,
 *     true,  // isDynamicLength
 *     "uint256"
 * );
 *
 * // Static array of 3 addresses
 * Array<io.brane.core.abi.Address> staticArray = new Array<>(
 *     List.of(addr1, addr2, addr3),
 *     io.brane.core.abi.Address.class,
 *     false,  // isDynamicLength
 *     "address"
 * );
 * }</pre>
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
        values = List.copyOf(values);
    }

    @Override
    public int byteSize() {
        if (isDynamic()) {
            return 32;
        }
        // Static array: length * element size
        // Edge case: empty static array (T[0]) has 0 byte size since there are no elements
        // to encode. While Solidity doesn't allow T[0] declarations, this is correct per
        // the ABI spec where head size = length * element_size = 0 * 32 = 0.
        if (values.isEmpty()) {
            return 0;
        }
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
