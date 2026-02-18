// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.abi;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import sh.brane.core.error.AbiDecodingException;
import sh.brane.core.types.Address;
import sh.brane.primitives.Hex;

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

    /**
     * Size of an ABI slot in bytes. All ABI types occupy multiples of 32 bytes.
     */
    private static final int SLOT_SIZE = 32;

    /**
     * Number of padding bytes before an address in a 32-byte ABI slot.
     * Addresses are 20 bytes, so they are left-padded with 12 zero bytes (32 - 20 = 12).
     */
    private static final int ADDRESS_PADDING_BYTES = 12;

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
                currentHeadOffset += SLOT_SIZE;
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
                new AddressType(new Address(Hex.encode(data, offset + ADDRESS_PADDING_BYTES, 20)));
            case TypeSchema.BoolSchema s -> new Bool(decodeUInt(data, offset).equals(BigInteger.ONE));
            case TypeSchema.BytesSchema s -> {
                // Static bytesN: value is left-aligned in 32 bytes, extract only N bytes
                int size = s.size();
                yield Bytes.ofStatic(data, offset, size);
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
            // StringSchema is always dynamic, so it should never reach decodeStatic
            case TypeSchema.StringSchema s ->
                throw new IllegalStateException("StringSchema is always dynamic and should not be decoded as static");
        };
    }

    // Helper to calculate static size of a schema
    private static int getStaticSize(TypeSchema schema) {
        if (schema.isDynamic())
            return 32;
        return switch (schema) {
            case TypeSchema.TupleSchema t -> {
                int sum = 0;
                for (TypeSchema c : t.components()) {
                    sum += getStaticSize(c);
                }
                yield sum;
            }
            case TypeSchema.ArraySchema a -> a.fixedLength() * getStaticSize(a.element());
            // All other static types are 32 bytes (single slot)
            case TypeSchema.UIntSchema s -> 32;
            case TypeSchema.IntSchema s -> 32;
            case TypeSchema.AddressSchema s -> 32;
            case TypeSchema.BoolSchema s -> 32;
            case TypeSchema.BytesSchema s -> 32;
            // StringSchema is always dynamic, handled by early return above
            case TypeSchema.StringSchema s -> 32;
        };
    }

    private static AbiType decodeDynamic(byte[] data, int offset, TypeSchema schema) {
        return switch (schema) {
            case TypeSchema.BytesSchema s -> {
                // Length + Data
                BigInteger lengthValue = decodeInt(data, offset);
                int length = toIntExact(lengthValue, "bytes length");
                int dataOffset = offset + 32;
                // Only validate if length > 0 to avoid underflow when length == 0
                if (length > 0) {
                    validateOffset(data, dataOffset + length - 1, "bytes data");
                }
                // Dynamic bytes still need a copy since Bytes.of wraps via Hex.encode
                byte[] bytes = Arrays.copyOfRange(data, dataOffset, dataOffset + length);
                yield Bytes.of(bytes);
            }
            case TypeSchema.StringSchema s -> {
                BigInteger lengthValue = decodeInt(data, offset);
                int length = toIntExact(lengthValue, "string length");
                int dataOffset = offset + 32;
                // Only validate if length > 0 to avoid underflow when length == 0
                if (length > 0) {
                    validateOffset(data, dataOffset + length - 1, "string data");
                }
                yield new Utf8String(new String(data, dataOffset, length, StandardCharsets.UTF_8));
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
            // These types are always static (isDynamic() returns false), so they should never
            // reach decodeDynamic. Include them for exhaustiveness to get compile-time warnings
            // if new types are added to the sealed hierarchy.
            case TypeSchema.UIntSchema s ->
                throw new IllegalStateException("UIntSchema is always static and should not be decoded as dynamic");
            case TypeSchema.IntSchema s ->
                throw new IllegalStateException("IntSchema is always static and should not be decoded as dynamic");
            case TypeSchema.AddressSchema s ->
                throw new IllegalStateException("AddressSchema is always static and should not be decoded as dynamic");
            case TypeSchema.BoolSchema s ->
                throw new IllegalStateException("BoolSchema is always static and should not be decoded as dynamic");
        };
    }

    private static BigInteger decodeUInt(byte[] data, int offset) {
        return new BigInteger(1, data, offset, 32);
    }

    private static BigInteger decodeInt(byte[] data, int offset) {
        return new BigInteger(data, offset, 32);
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
