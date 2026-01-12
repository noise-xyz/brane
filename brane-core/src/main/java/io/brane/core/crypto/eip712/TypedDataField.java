// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.crypto.eip712;

import java.util.Objects;

/**
 * A single field in a struct type definition.
 *
 * @param name the field name
 * @param type the Solidity type (e.g., "address", "uint256", "Person")
 */
public record TypedDataField(String name, String type) {
    public TypedDataField {
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(type, "type");
        if (name.isBlank()) throw new IllegalArgumentException("name cannot be blank");
        if (type.isBlank()) throw new IllegalArgumentException("type cannot be blank");
    }

    public static TypedDataField of(String name, String type) {
        return new TypedDataField(name, type);
    }
}
