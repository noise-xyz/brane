package io.brane.core.abi;

import io.brane.core.error.AbiDecodingException;
import io.brane.core.types.Address;

import io.brane.primitives.Hex;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Decodes byte arrays into ABI types according to the Ethereum Contract ABI
 * specification.
 * 
 * <p>
 * This class provides static methods for decoding byte arrays into lists of
 * {@link AbiType} based on a provided {@link TypeSchema}. It handles both static
 * and dynamic types, resolving offsets and decoding primitive values.
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Schema: (uint256, string)
 * List<TypeSchema> schemas = List.of(
 *     new TypeSchema.UIntSchema(256),
 *     new TypeSchema.StringSchema()
 * );
 * 
 * // Decode
 * List<AbiType> decoded = AbiDecoder.decode(data, schemas);
 * UInt u = (UInt) decoded.get(0);
 * Utf8String s = (Utf8String) decoded.get(1);
 * }</pre>
 * 
 * @see AbiType
 * @see TypeSchema
 * @see AbiEncoder
 */
public final class AbiDecoder {

    private AbiDecoder() {
    }

    /**
     * Decodes a byte array into a list of ABI types based on the provided schemas.
     * 
     * <p>
     * This method expects the data to be encoded as a tuple, which is the standard
     * format for function arguments and return values.
     *
     * @param data    the byte array to decode
     * @param schemas the list of schemas defining the expected types
     * @return the list of decoded ABI types
     * @throws IllegalArgumentException if the data is too short or malformed
     */
    public static List<AbiType> decode(byte[] data, List<TypeSchema> schemas) {
        return decodeTuple(data, 0, schemas);
    }

    private static List<AbiType> decodeTuple(byte[] data, int offset, List<TypeSchema> schemas) {
        List<AbiType> results = new ArrayList<>(schemas.size());
        int currentHeadOffset = offset;

        // Calculate total static size to validate bounds
        int totalStaticSize = 0;
        for (TypeSchema schema : schemas) {
            totalStaticSize += getStaticSize(schema);
        }
        if (data.length < offset + totalStaticSize) {
            throw new IllegalArgumentException("Data too short for schema heads. Expected: " + totalStaticSize
                    + ", Available: " + (data.length - offset));
        }

        for (TypeSchema schema : schemas) {
            if (schema.isDynamic()) {
                // Dynamic type: head is 32-byte offset
                BigInteger offsetValue = decodeInt(data, currentHeadOffset);
                int relativeOffset = toIntExact(offsetValue, "dynamic type offset");
                int absoluteOffset = offset + relativeOffset;
                validateOffset(data, absoluteOffset, "dynamic type data");
                results.add(decodeDynamic(data, absoluteOffset, schema));
                currentHeadOffset += 32;
            } else {
                // Static type: read directly from head
                results.add(decodeStatic(data, currentHeadOffset, schema));
                currentHeadOffset += getStaticSize(schema);
            }
        }
        return results;
    }

    private static AbiType decodeStatic(byte[] data, int offset, TypeSchema schema) {
        return switch (schema) {
            case TypeSchema.UIntSchema s -> new UInt(s.width(), decodeUInt(data, offset));
            case TypeSchema.IntSchema s -> new Int(s.width(), decodeInt(data, offset));
            case TypeSchema.AddressSchema s ->
                new AddressType(new Address(Hex.encode(Arrays.copyOfRange(data, offset + 12, offset + 32))));
            case TypeSchema.BoolSchema s -> new Bool(decodeUInt(data, offset).equals(BigInteger.ONE));
            case TypeSchema.BytesSchema s -> {
                // Static bytesN: value is left-aligned in 32 bytes, extract only N bytes
                int size = s.size();
                yield Bytes.ofStatic(Arrays.copyOfRange(data, offset, offset + size));
            }
            case TypeSchema.ArraySchema s -> {
                // Static array: sequence of N elements
                List<TypeSchema> elemSchemas = new ArrayList<>(s.fixedLength());
                for (int i = 0; i < s.fixedLength(); i++) {
                    elemSchemas.add(s.element());
                }
                List<AbiType> elements = decodeTuple(data, offset, elemSchemas);
                @SuppressWarnings("unchecked")
                Class<AbiType> elemClass = (Class<AbiType>) (elements.isEmpty() ? AbiType.class
                        : elements.get(0).getClass());
                yield new Array<>(elements, elemClass, false, s.element().typeName());
            }
            case TypeSchema.TupleSchema s -> {
                // Static tuple: sequence of components
                List<AbiType> components = decodeTuple(data, offset, s.components());
                yield new Tuple(components);
            }
            default -> throw new IllegalArgumentException("Unknown static schema: " + schema);
        };
    }

    // Helper to calculate static size of a schema
    private static int getStaticSize(TypeSchema schema) {
        if (schema.isDynamic())
            return 32;
        return switch (schema) {
            case TypeSchema.TupleSchema t -> t.components().stream().mapToInt(AbiDecoder::getStaticSize).sum();
            case TypeSchema.ArraySchema a -> a.fixedLength() * getStaticSize(a.element());
            default -> 32;
        };
    }

    private static AbiType decodeDynamic(byte[] data, int offset, TypeSchema schema) {
        return switch (schema) {
            case TypeSchema.BytesSchema s -> {
                // Length + Data
                BigInteger lengthValue = decodeInt(data, offset);
                int length = toIntExact(lengthValue, "bytes length");
                int dataOffset = offset + 32;
                validateOffset(data, dataOffset + length - 1, "bytes data");
                byte[] bytes = Arrays.copyOfRange(data, dataOffset, dataOffset + length);
                yield Bytes.of(bytes);
            }
            case TypeSchema.StringSchema s -> {
                BigInteger lengthValue = decodeInt(data, offset);
                int length = toIntExact(lengthValue, "string length");
                int dataOffset = offset + 32;
                validateOffset(data, dataOffset + length - 1, "string data");
                byte[] bytes = Arrays.copyOfRange(data, dataOffset, dataOffset + length);
                yield new Utf8String(new String(bytes, StandardCharsets.UTF_8));
            }
            case TypeSchema.ArraySchema s -> {
                // Dynamic array: Length + Elements
                // OR Fixed array of dynamic types: Elements (no length prefix)
                int length;
                int elemOffset;
                if (s.fixedLength() != -1) {
                    length = s.fixedLength();
                    elemOffset = offset;
                } else {
                    BigInteger lengthValue = decodeInt(data, offset);
                    length = toIntExact(lengthValue, "array length");
                    elemOffset = offset + 32;
                }

                // Construct a schema list of 'length' elements
                List<TypeSchema> elemSchemas = new ArrayList<>(length);
                for (int i = 0; i < length; i++) {
                    elemSchemas.add(s.element());
                }

                // Decode as a tuple (sequence of elements)
                // Note: decodeTuple expects offset 0 relative to the slice?
                // No, decodeTuple takes absolute offset.
                // We can use decodeTuple logic but we need to be careful about offsets.
                // Elements in dynamic array are packed like a tuple.

                List<AbiType> elements = decodeTuple(data, elemOffset, elemSchemas);
                // We need the class of the element type for Array record.
                // This is a bit hacky, we can infer from first element or pass Object.class
                @SuppressWarnings("unchecked")
                Class<AbiType> elemClass = (Class<AbiType>) (elements.isEmpty() ? AbiType.class
                        : elements.get(0).getClass());
                yield new Array<>(elements, elemClass, true, s.element().typeName());
            }
            case TypeSchema.TupleSchema s -> {
                // Dynamic tuple is encoded as a tuple at the offset
                List<AbiType> components = decodeTuple(data, offset, s.components());
                yield new Tuple(components);
            }
            default -> throw new IllegalArgumentException("Unknown dynamic schema: " + schema);
        };
    }

    private static BigInteger decodeUInt(byte[] data, int offset) {
        byte[] slice = Arrays.copyOfRange(data, offset, offset + 32);
        return new BigInteger(1, slice);
    }

    private static BigInteger decodeInt(byte[] data, int offset) {
        byte[] slice = Arrays.copyOfRange(data, offset, offset + 32);
        return new BigInteger(slice);
    }

    /**
     * Converts a BigInteger to int, throwing AbiDecodingException if it doesn't fit.
     *
     * @param value   the value to convert
     * @param context description of what the value represents (for error messages)
     * @return the int value
     * @throws AbiDecodingException if the value exceeds Integer.MAX_VALUE
     */
    private static int toIntExact(BigInteger value, String context) {
        try {
            return value.intValueExact();
        } catch (ArithmeticException e) {
            throw new AbiDecodingException(context + " too large for int: " + value, e);
        }
    }

    /**
     * Validates that an offset is within the data bounds.
     *
     * @param data    the byte array
     * @param offset  the offset to validate
     * @param context description of what the offset points to (for error messages)
     * @throws AbiDecodingException if the offset is out of bounds
     */
    private static void validateOffset(byte[] data, int offset, String context) {
        if (offset < 0 || offset >= data.length) {
            throw new AbiDecodingException(
                    context + " offset out of bounds: " + offset + " (data length: " + data.length + ")");
        }
    }
}
