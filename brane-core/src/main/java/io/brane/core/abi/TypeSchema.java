package io.brane.core.abi;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Defines the schema (expected type structure) for ABI decoding.
 * 
 * <p>
 * Unlike {@link AbiType}, which represents a concrete value, {@code TypeSchema}
 * represents the <em>type</em> of a value. This is necessary for decoding because
 * the ABI encoding format is ambiguous without a schema (e.g., dynamic arrays
 * look like tuples, and static types are just 32 bytes).
 * 
 * <p>
 * Schemas are used by {@link AbiDecoder} to interpret byte arrays.
 * 
 * <h2>Example</h2>
 * <pre>{@code
 * // Schema for: (uint256, string[])
 * TypeSchema schema = new TypeSchema.TupleSchema(List.of(
 *     new TypeSchema.UIntSchema(256),
 *     new TypeSchema.ArraySchema(new TypeSchema.StringSchema(), -1)
 * ));
 * }</pre>
 */
public sealed interface TypeSchema permits
        TypeSchema.UIntSchema,
        TypeSchema.IntSchema,
        TypeSchema.AddressSchema,
        TypeSchema.BoolSchema,
        TypeSchema.BytesSchema,
        TypeSchema.StringSchema,
        TypeSchema.ArraySchema,
        TypeSchema.TupleSchema {

    boolean isDynamic();

    /**
     * Returns the Solidity type name for this schema.
     *
     * @return the type name (e.g., "uint256", "address", "bytes32[]")
     */
    String typeName();

    default int headSize() {
        return 32;
    }

    record UIntSchema(int width) implements TypeSchema {
        public UIntSchema {
            if (width % 8 != 0 || width < 8 || width > 256) {
                throw new IllegalArgumentException("Invalid uint width: " + width);
            }
        }

        @Override
        public boolean isDynamic() {
            return false;
        }

        @Override
        public String typeName() {
            return "uint" + width;
        }
    }

    record IntSchema(int width) implements TypeSchema {
        public IntSchema {
            if (width % 8 != 0 || width < 8 || width > 256) {
                throw new IllegalArgumentException("Invalid int width: " + width);
            }
        }

        @Override
        public boolean isDynamic() {
            return false;
        }

        @Override
        public String typeName() {
            return "int" + width;
        }
    }

    record AddressSchema() implements TypeSchema {
        @Override
        public boolean isDynamic() {
            return false;
        }

        @Override
        public String typeName() {
            return "address";
        }
    }

    record BoolSchema() implements TypeSchema {
        @Override
        public boolean isDynamic() {
            return false;
        }

        @Override
        public String typeName() {
            return "bool";
        }
    }

    /**
     * Schema for Solidity bytes or bytesN types.
     *
     * @param size the byte size for static types (1-32), or -1 for dynamic bytes
     */
    record BytesSchema(int size) implements TypeSchema {
        /** Size value indicating dynamic bytes type. */
        public static final int DYNAMIC = -1;

        public BytesSchema {
            if (size != DYNAMIC && (size < 1 || size > 32)) {
                throw new IllegalArgumentException("bytesN size must be 1-32 or DYNAMIC (-1), got: " + size);
            }
        }

        @Override
        public boolean isDynamic() {
            return size == DYNAMIC;
        }

        @Override
        public String typeName() {
            return size == DYNAMIC ? "bytes" : "bytes" + size;
        }
    }

    record StringSchema() implements TypeSchema {
        @Override
        public boolean isDynamic() {
            return true;
        }

        @Override
        public String typeName() {
            return "string";
        }
    }

    record ArraySchema(TypeSchema element, int fixedLength) implements TypeSchema {
        /**
         * Validates the array schema.
         *
         * @throws NullPointerException if element is null
         * @throws IllegalArgumentException if fixedLength is less than -1
         */
        public ArraySchema {
            Objects.requireNonNull(element, "element schema cannot be null");
            if (fixedLength < -1) {
                throw new IllegalArgumentException(
                        "fixedLength must be >= -1 (use -1 for dynamic arrays), got: " + fixedLength);
            }
        }

        @Override
        public boolean isDynamic() {
            return fixedLength == -1 || element.isDynamic();
        }

        @Override
        public String typeName() {
            String elemType = element.typeName();
            return fixedLength == -1 ? elemType + "[]" : elemType + "[" + fixedLength + "]";
        }
    }

    record TupleSchema(List<TypeSchema> components) implements TypeSchema {
        public TupleSchema {
            Objects.requireNonNull(components, "components cannot be null");
        }

        @Override
        public boolean isDynamic() {
            return components.stream().anyMatch(TypeSchema::isDynamic);
        }

        @Override
        public String typeName() {
            return components.stream()
                    .map(TypeSchema::typeName)
                    .collect(Collectors.joining(",", "(", ")"));
        }
    }
}
