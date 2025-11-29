package io.brane.core.abi;

import io.brane.core.types.Address;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

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

        List<AbiType> decoded = AbiDecoder.decode(encoded, List.of(new TypeSchema.BytesSchema(false)));
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
        AbiType array = new Array<>(List.of(e1, e2), UInt.class, true);

        byte[] encoded = AbiEncoder.encode(List.of(array));

        TypeSchema arraySchema = new TypeSchema.ArraySchema(new TypeSchema.UIntSchema(256), -1);
        List<AbiType> decoded = AbiDecoder.decode(encoded, List.of(arraySchema));

        Assertions.assertEquals(1, decoded.size());
        Array<?> decodedArray = (Array<?>) decoded.get(0);
        Assertions.assertEquals(2, decodedArray.values().size());
        Assertions.assertEquals(e1, decodedArray.values().get(0));
        Assertions.assertEquals(e2, decodedArray.values().get(1));
    }
}
