package io.brane.core.abi;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Represents a Solidity tuple (sequence of types).
 *
 * @param components the list of component types
 */
public record Tuple(List<AbiType> components) implements AbiType {
    public Tuple {
        Objects.requireNonNull(components, "components cannot be null");
        components = List.copyOf(components);
    }

    @Override
    public int byteSize() {
        if (isDynamic()) {
            return 32;
        }
        // Static tuple: sum of component sizes
        return components.stream().mapToInt(AbiType::byteSize).sum();
    }

    @Override
    public boolean isDynamic() {
        return components.stream().anyMatch(AbiType::isDynamic);
    }

    @Override
    public String typeName() {
        return components.stream()
                .map(AbiType::typeName)
                .collect(Collectors.joining(",", "(", ")"));
    }

    @Override
    public int contentByteSize() {
        if (!isDynamic()) {
            return 0;
        }
        int size = 0;
        for (AbiType c : components) {
            size += c.byteSize();
            if (c.isDynamic()) {
                size += c.contentByteSize();
            }
        }
        return size;
    }
}
