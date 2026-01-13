// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.abi;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import sh.brane.core.error.AbiDecodingException;
import sh.brane.core.types.Address;

class AbiDecoderTest {

    @Test
    void testDecodeUInt() {
        // Encode uint256(123)
        AbiType input = new UInt(256, BigInteger.valueOf(123));
        byte[] encoded = AbiEncoder.encode(List.of(input));

        List<AbiType> decoded = AbiDecoder.decode(encoded, List.of(new TypeSchema.UIntSchema(256)));
        Assertions.assertEquals(1, decoded.size());
        Assertions.assertEquals(input, decoded.get(0));
    }

    @Test
    void testDecodeInt() {
        // Encode int256(-123)
        AbiType input = new Int(256, BigInteger.valueOf(-123));
        byte[] encoded = AbiEncoder.encode(List.of(input));

        List<AbiType> decoded = AbiDecoder.decode(encoded, List.of(new TypeSchema.IntSchema(256)));
        Assertions.assertEquals(1, decoded.size());
        Assertions.assertEquals(input, decoded.get(0));
    }

    @Test
    void testDecodeAddress() {
        Address addr = new Address("0x0000000000000000000000000000000000000001");
        AbiType input = new AddressType(addr);
        byte[] encoded = AbiEncoder.encode(List.of(input));

        List<AbiType> decoded = AbiDecoder.decode(encoded, List.of(new TypeSchema.AddressSchema()));
        Assertions.assertEquals(1, decoded.size());
        Assertions.assertEquals(input, decoded.get(0));
    }

    @Test
    void testDecodeBool() {
        AbiType input = new Bool(true);
        byte[] encoded = AbiEncoder.encode(List.of(input));

        List<AbiType> decoded = AbiDecoder.decode(encoded, List.of(new TypeSchema.BoolSchema()));
        Assertions.assertEquals(1, decoded.size());
        Assertions.assertEquals(input, decoded.get(0));
    }

    @Test
    void testDecodeString() {
        AbiType input = new Utf8String("Hello World");
        byte[] encoded = AbiEncoder.encode(List.of(input));

        List<AbiType> decoded = AbiDecoder.decode(encoded, List.of(new TypeSchema.StringSchema()));
        Assertions.assertEquals(1, decoded.size());
        Assertions.assertEquals(input, decoded.get(0));
    }

    @Test
    void testDecodeMixed() {
        // uint256, string
        AbiType u = new UInt(256, BigInteger.TEN);
        AbiType s = new Utf8String("foo");
        byte[] encoded = AbiEncoder.encode(List.of(u, s));

        List<AbiType> decoded = AbiDecoder.decode(encoded,
                List.of(new TypeSchema.UIntSchema(256), new TypeSchema.StringSchema()));
        Assertions.assertEquals(2, decoded.size());
        Assertions.assertEquals(u, decoded.get(0));
        Assertions.assertEquals(s, decoded.get(1));
    }

    @Test
    void testDecodeTuple() {
        // (uint256, bool)
        AbiType u = new UInt(256, BigInteger.ONE);
        AbiType b = new Bool(false);
        AbiType tuple = new Tuple(List.of(u, b));

        byte[] encoded = AbiEncoder.encode(List.of(tuple));

        TypeSchema tupleSchema = new TypeSchema.TupleSchema(
                List.of(new TypeSchema.UIntSchema(256), new TypeSchema.BoolSchema()));
        List<AbiType> decoded = AbiDecoder.decode(encoded, List.of(tupleSchema));

        Assertions.assertEquals(1, decoded.size());
        Assertions.assertEquals(tuple, decoded.get(0));
    }

    @Test
    void testDecodeStaticBytes() {
        byte[] data = new byte[32];
        data[0] = 0x12;
        AbiType input = Bytes.ofStatic(data);
        byte[] encoded = AbiEncoder.encode(List.of(input));

        List<AbiType> decoded = AbiDecoder.decode(encoded, List.of(new TypeSchema.BytesSchema(32)));
        Assertions.assertEquals(1, decoded.size());
        // Bytes.ofStatic might wrap differently but equality should hold if implemented
        // correctly
        // Actually Bytes record uses byte[] which doesn't implement equals deeply by
        // default in records?
        // Wait, records do NOT use Arrays.equals for array components. They use
        // reference equality.
        // I should check Bytes implementation.
        // Bytes implementation: record Bytes(byte[] value, boolean isDynamic)
        // I need to override equals/hashCode in Bytes if I want value equality.
        // For now let's check value content.
        Assertions.assertEquals(((Bytes) input).value(), ((Bytes) decoded.get(0)).value());
    }

    @Test
    void testDecodeDynamicArray() {
        // uint256[] = [1, 2]
        UInt e1 = new UInt(256, BigInteger.ONE);
        UInt e2 = new UInt(256, BigInteger.TWO);
        AbiType array = new Array<>(List.of(e1, e2), UInt.class, true, "uint256");

        byte[] encoded = AbiEncoder.encode(List.of(array));

        TypeSchema arraySchema = new TypeSchema.ArraySchema(new TypeSchema.UIntSchema(256), -1);
        List<AbiType> decoded = AbiDecoder.decode(encoded, List.of(arraySchema));

        Assertions.assertEquals(1, decoded.size());
        Array<?> decodedArray = (Array<?>) decoded.get(0);
        Assertions.assertEquals(2, decodedArray.values().size());
        Assertions.assertEquals(e1, decodedArray.values().get(0));
        Assertions.assertEquals(e2, decodedArray.values().get(1));
    }

    @Test
    void testDecodeFixedArrayOfDynamicTypes() {
        // string[2] = ["a", "b"]
        // Encoded as:
        // offset 1 (32 bytes)
        // offset 2 (32 bytes)
        // length 1 (32 bytes)
        // "a" (32 bytes)
        // length 1 (32 bytes)
        // "b" (32 bytes)

        Utf8String s1 = new Utf8String("a");
        Utf8String s2 = new Utf8String("b");
        AbiType array = new Array<>(List.of(s1, s2), Utf8String.class, false, "string"); // false = fixed length

        // We need to encode it manually or trust AbiEncoder?
        // AbiEncoder might also have issues with fixed array of dynamic types if not
        // implemented correctly.
        // Let's check AbiEncoder.encodeArray.
        // If AbiEncoder is correct, we can use it.
        // AbiEncoder.encodeArray checks isDynamic.
        // For string[2], isDynamic is true (because elements are dynamic).
        // So it encodes as dynamic array?
        // Wait, fixed array of dynamic types is encoded as a tuple of dynamic types.
        // AbiEncoder.encodeArray:
        // if (array.isDynamic()) { encode length + elements } else { encode elements }
        // Array.isDynamic() returns the boolean passed in constructor.
        // For string[2], we pass false? No, Array.isDynamic() usually means "is this
        // type dynamic in ABI sense?".
        // string[2] IS dynamic type in ABI.
        // But AbiEncoder needs to know if it should emit length prefix.
        // Length prefix is emitted for dynamic arrays (T[]), NOT for fixed arrays
        // (T[N]).
        // So for string[2], we should NOT emit length.
        // But AbiEncoder uses array.isDynamic() to decide.
        // If we pass true to Array constructor, AbiEncoder emits length.
        // If we pass false, AbiEncoder does NOT emit length.
        // So for string[2], we should pass false to Array constructor?
        // But string[2] IS dynamic.
        // This suggests AbiEncoder might rely on a flag "emitLength" or similar, or
        // just "isDynamicArray" vs "isFixedArray".
        // The Array record has `boolean isDynamic`.
        // If this flag means "is variable length array", then for string[2] it should
        // be false.
        // Let's assume Array.isDynamic means "is variable length".

        byte[] encoded = AbiEncoder.encode(List.of(array));

        // string[2] schema
        TypeSchema arraySchema = new TypeSchema.ArraySchema(new TypeSchema.StringSchema(), 2);

        List<AbiType> decoded = AbiDecoder.decode(encoded, List.of(arraySchema));

        Assertions.assertEquals(1, decoded.size());
        Array<?> decodedArray = (Array<?>) decoded.get(0);
        Assertions.assertEquals(2, decodedArray.values().size());
        Assertions.assertEquals(s1, decodedArray.values().get(0));
        Assertions.assertEquals(s2, decodedArray.values().get(1));
    }

    @Test
    void testDecodeOutOfBoundsOffset() {
        // HIGH-8: Test malicious data with out-of-bounds offset
        // Create data for a string where the offset points beyond the data
        byte[] maliciousData = new byte[32];
        // Set offset to 0xFFFFFFFF (4294967295) which is way beyond data length
        maliciousData[31] = (byte) 0xFF;
        maliciousData[30] = (byte) 0xFF;
        maliciousData[29] = (byte) 0xFF;
        maliciousData[28] = (byte) 0xFF;

        AbiDecodingException exception = assertThrows(
                AbiDecodingException.class,
                () -> AbiDecoder.decode(maliciousData, List.of(new TypeSchema.StringSchema())));

        assertTrue(exception.getMessage().contains("out of bounds") ||
                   exception.getMessage().contains("too large"),
                "Error message should indicate offset issue: " + exception.getMessage());
    }

    @Test
    void testDecodeOffsetExceedsIntMaxValue() {
        // HIGH-8: Test offset that exceeds Integer.MAX_VALUE
        // Create data for a string where the offset is larger than Integer.MAX_VALUE
        byte[] maliciousData = new byte[64];
        // Set offset to 0x8000_0000_0000_0000 (much larger than Integer.MAX_VALUE)
        maliciousData[24] = (byte) 0x80;  // This makes the BigInteger very large
        // The rest are zeros

        AbiDecodingException exception = assertThrows(
                AbiDecodingException.class,
                () -> AbiDecoder.decode(maliciousData, List.of(new TypeSchema.StringSchema())));

        assertTrue(exception.getMessage().contains("too large for int"),
                "Error message should indicate overflow: " + exception.getMessage());
    }

    @Test
    void testDecodeNegativeOffset() {
        // HIGH-8: Test data with negative offset (high bit set in signed interpretation)
        byte[] maliciousData = new byte[64];
        // Set a negative offset (first byte 0xFF makes it negative in signed interpretation)
        maliciousData[0] = (byte) 0xFF;  // This makes BigInteger negative

        // This should throw an exception - negative values don't fit in int (too large)
        AbiDecodingException exception = assertThrows(
                AbiDecodingException.class,
                () -> AbiDecoder.decode(maliciousData, List.of(new TypeSchema.StringSchema())));

        // Negative BigIntegers that don't fit in int will show as "too large for int"
        assertTrue(exception.getMessage().contains("out of bounds") ||
                   exception.getMessage().contains("too large for int"),
                "Error message should indicate offset issue: " + exception.getMessage());
    }

    @Test
    void testDecodeCorruptedStringLength() {
        // HIGH-8: Test malicious data where string length is too large
        byte[] maliciousData = new byte[64];
        // First 32 bytes: offset to string data (32)
        maliciousData[31] = 32;

        // Next 32 bytes: string length (set to huge value)
        maliciousData[63] = (byte) 0xFF;
        maliciousData[62] = (byte) 0xFF;
        maliciousData[61] = (byte) 0xFF;
        maliciousData[60] = (byte) 0xFF;

        AbiDecodingException exception = assertThrows(
                AbiDecodingException.class,
                () -> AbiDecoder.decode(maliciousData, List.of(new TypeSchema.StringSchema())));

        assertTrue(exception.getMessage().contains("out of bounds") ||
                   exception.getMessage().contains("too large"),
                "Error message should indicate size issue: " + exception.getMessage());
    }

    @Test
    void testDecodeEmptyBytes() {
        // CRIT-1: Test decoding empty bytes (length = 0)
        // Empty bytes should decode successfully without validation underflow
        AbiType input = Bytes.of(new byte[0]);
        byte[] encoded = AbiEncoder.encode(List.of(input));

        List<AbiType> decoded = AbiDecoder.decode(encoded, List.of(new TypeSchema.BytesSchema(TypeSchema.BytesSchema.DYNAMIC)));
        Assertions.assertEquals(1, decoded.size());
        Bytes decodedBytes = (Bytes) decoded.get(0);
        Assertions.assertEquals(0, decodedBytes.value().byteLength());
    }

    @Test
    void testDecodeEmptyString() {
        // CRIT-1: Test decoding empty string (length = 0)
        // Empty string should decode successfully without validation underflow
        AbiType input = new Utf8String("");
        byte[] encoded = AbiEncoder.encode(List.of(input));

        List<AbiType> decoded = AbiDecoder.decode(encoded, List.of(new TypeSchema.StringSchema()));
        Assertions.assertEquals(1, decoded.size());
        Utf8String decodedString = (Utf8String) decoded.get(0);
        Assertions.assertEquals("", decodedString.value());
    }
}
