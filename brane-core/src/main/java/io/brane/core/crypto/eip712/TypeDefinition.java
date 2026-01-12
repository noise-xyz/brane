// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.crypto.eip712;

import java.lang.reflect.RecordComponent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;

/**
 * Defines the EIP-712 type structure for a Java type.
 *
 * <p>This record encapsulates the primary type name, type definitions for all
 * struct types, and a function to extract field values from a message object.
 *
 * <p>Example usage for ERC-2612 Permit:
 * <pre>{@code
 * record Permit(Address owner, Address spender, BigInteger value, BigInteger nonce, BigInteger deadline) {
 *     public static final TypeDefinition<Permit> DEFINITION = TypeDefinition.forRecord(
 *         Permit.class,
 *         "Permit",
 *         Map.of("Permit", List.of(
 *             TypedDataField.of("owner", "address"),
 *             TypedDataField.of("spender", "address"),
 *             TypedDataField.of("value", "uint256"),
 *             TypedDataField.of("nonce", "uint256"),
 *             TypedDataField.of("deadline", "uint256")
 *         ))
 *     );
 * }
 * }</pre>
 *
 * @param <T> the Java type this definition maps
 * @param primaryType the primary type name (e.g., "Permit", "Mail")
 * @param types map of type names to their field definitions
 * @param extractor function to extract field values from a message object
 */
public record TypeDefinition<T>(
    String primaryType,
    Map<String, List<TypedDataField>> types,
    Function<T, Map<String, Object>> extractor
) {
    public TypeDefinition {
        Objects.requireNonNull(primaryType, "primaryType");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(extractor, "extractor");
        if (primaryType.isBlank()) {
            throw new IllegalArgumentException("primaryType cannot be blank");
        }
        if (!types.containsKey(primaryType)) {
            throw new IllegalArgumentException("types must contain primaryType: " + primaryType);
        }
    }

    /**
     * Creates a definition for a record type using reflection.
     *
     * <p>The extractor function is automatically generated to read
     * record component values using reflection. Component names must
     * match the field names in the type definition.
     *
     * @param <T> the record type
     * @param recordClass the record class
     * @param primaryType the primary type name for EIP-712 encoding
     * @param types map of type names to their field definitions
     * @return a new TypeDefinition with reflection-based extraction
     * @throws IllegalArgumentException if recordClass is not a record
     */
    public static <T extends Record> TypeDefinition<T> forRecord(
            Class<T> recordClass,
            String primaryType,
            Map<String, List<TypedDataField>> types) {
        Objects.requireNonNull(recordClass, "recordClass");
        if (!recordClass.isRecord()) {
            throw new IllegalArgumentException("Class must be a record: " + recordClass.getName());
        }

        Function<T, Map<String, Object>> extractor = record -> {
            var result = new LinkedHashMap<String, Object>();
            for (RecordComponent component : recordClass.getRecordComponents()) {
                try {
                    Object value = component.getAccessor().invoke(record);
                    result.put(component.getName(), value);
                } catch (ReflectiveOperationException e) {
                    throw new RuntimeException(
                        "Failed to extract field '" + component.getName() + "' from " + recordClass.getName(), e);
                }
            }
            return result;
        };

        return new TypeDefinition<>(primaryType, types, extractor);
    }
}
