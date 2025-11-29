package io.brane.core.abi;

import java.util.List;
import java.util.Objects;

/**
 * Defines the schema (expected type structure) for ABI decoding.
 * 
 * <p>
 * Unlike {@link AbiType}, which represents a concrete value, {@code TypeSchema}
 * represents the <em>type</em> of a value. This is necessary for decoding
 * because
 * the ABI encoding format is ambiguous without a schema (e.g., dynamic arrays
 * look like tuples, and static types are just 32 bytes).
 * 
 * <p>
 * Schemas are used by {@link AbiDecoder} to interpret byte arrays.
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
    }

    record AddressSchema() implements TypeSchema {
        @Override
        public boolean isDynamic() {
            return false;
        }
    }

    record BoolSchema() implements TypeSchema {
        @Override
        public boolean isDynamic() {
            return false;
        }
    }

    record BytesSchema(boolean isDynamic) implements TypeSchema {
        @Override
        public boolean isDynamic() {
            return isDynamic;
        }
    }

    record StringSchema() implements TypeSchema {
        @Override
        public boolean isDynamic() {
            return true;
        }
    }

    record ArraySchema(TypeSchema element, int fixedLength) implements TypeSchema {
        public ArraySchema {
            Objects.requireNonNull(element, "element schema cannot be null");
        }

        @Override
        public boolean isDynamic() {
            return fixedLength == -1 || element.isDynamic();
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
    }
}
