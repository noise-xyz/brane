// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.abi;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Address;
import io.brane.primitives.Hex;

class AbiEncoderTest {

    @Test
    void encodeUInt() {
        AbiType uint = new UInt(256, BigInteger.valueOf(123));
        byte[] encoded = AbiEncoder.encode(List.of(uint));
        // 123 = 0x7b
        String expected = "000000000000000000000000000000000000000000000000000000000000007b";
        assertEquals(expected, Hex.encodeNoPrefix(encoded));
    }

    @Test
    void encodeAddress() {
        Address addr = new Address("0x0000000000000000000000000000000000000123");
        AbiType address = new AddressType(addr);
        byte[] encoded = AbiEncoder.encode(List.of(address));
        String expected = "0000000000000000000000000000000000000000000000000000000000000123";
        assertEquals(expected, Hex.encodeNoPrefix(encoded));
    }

    @Test
    void encodeBool() {
        AbiType b = new Bool(true);
        byte[] encoded = AbiEncoder.encode(List.of(b));
        String expected = "0000000000000000000000000000000000000000000000000000000000000001";
        assertEquals(expected, Hex.encodeNoPrefix(encoded));
    }

    @Test
    void encodeString() {
        AbiType str = new Utf8String("Hello");
        byte[] encoded = AbiEncoder.encode(List.of(str));
        // Offset: 32 (0x20)
        // Length: 5 (0x05)
        // Data: "Hello" (0x48656c6c6f) + padding
        String expected = "0000000000000000000000000000000000000000000000000000000000000020" + // Offset
                "0000000000000000000000000000000000000000000000000000000000000005" + // Length
                "48656c6c6f000000000000000000000000000000000000000000000000000000"; // Data + Padding
        assertEquals(expected, Hex.encodeNoPrefix(encoded));
    }

    @Test
    void encodeTuple() {
        // (uint256, string)
        AbiType uint = new UInt(256, BigInteger.valueOf(1));
        AbiType str = new Utf8String("a");
        byte[] encoded = AbiEncoder.encode(List.of(uint, str));

        // Head:
        // uint: 1 (32 bytes)
        // string: offset 64 (0x40) (32 bytes)
        // Tail:
        // string length: 1 (32 bytes)
        // string data: "a" (0x61) + padding (32 bytes)

        String expected = "0000000000000000000000000000000000000000000000000000000000000001" + // uint
                "0000000000000000000000000000000000000000000000000000000000000040" + // string offset
                "0000000000000000000000000000000000000000000000000000000000000001" + // string length
                "6100000000000000000000000000000000000000000000000000000000000000"; // string data

        assertEquals(expected, Hex.encodeNoPrefix(encoded));
    }

    @Test
    void encodeNestedTuple() {
        // (uint256, (uint256, string))
        AbiType innerUint = new UInt(256, BigInteger.valueOf(2));
        AbiType innerStr = new Utf8String("b");
        AbiType innerTuple = new Tuple(List.of(innerUint, innerStr));

        AbiType outerUint = new UInt(256, BigInteger.valueOf(1));

        byte[] encoded = AbiEncoder.encode(List.of(outerUint, innerTuple));

        // Head:
        // uint: 1 (32 bytes)
        // tuple offset: 64 (0x40) (32 bytes)

        // Tail (Tuple):
        // uint: 2 (32 bytes)
        // string offset: 64 (0x40) (relative to tuple start) -> 32 bytes

        // Tail (String inside Tuple):
        // length: 1 (32 bytes)
        // data: "b" (0x62) + padding (32 bytes)

        String expected =
                // Outer Head
                "0000000000000000000000000000000000000000000000000000000000000001" + // outer uint
                        "0000000000000000000000000000000000000000000000000000000000000040" + // inner tuple offset

                        // Inner Tuple Data
                        "0000000000000000000000000000000000000000000000000000000000000002" + // inner uint
                        "0000000000000000000000000000000000000000000000000000000000000040" + // inner string offset

                        // Inner String Data
                        "0000000000000000000000000000000000000000000000000000000000000001" + // length
                        "6200000000000000000000000000000000000000000000000000000000000000"; // data

        assertEquals(expected, Hex.encodeNoPrefix(encoded));
    }

    @Test
    void encodeInt() {
        // -1 (int8) -> padded to 32 bytes of FF
        AbiType i = new Int(8, BigInteger.valueOf(-1));
        byte[] encoded = AbiEncoder.encode(List.of(i));
        String expected = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
        assertEquals(expected, Hex.encodeNoPrefix(encoded));
    }

    @Test
    void encodeBytesStatic() {
        // bytes3: 0x123456 -> 0x123456...00
        AbiType b = Bytes.ofStatic(Hex.decode("0x123456"));
        byte[] encoded = AbiEncoder.encode(List.of(b));
        String expected = "1234560000000000000000000000000000000000000000000000000000000000";
        assertEquals(expected, Hex.encodeNoPrefix(encoded));
    }

    @Test
    void encodeArray() {
        // uint[] [1, 2]
        UInt u1 = new UInt(256, BigInteger.valueOf(1));
        UInt u2 = new UInt(256, BigInteger.valueOf(2));
        AbiType array = new Array<>(List.of(u1, u2), UInt.class, true, "uint256");

        byte[] encoded = AbiEncoder.encode(List.of(array));

        // Head: offset 32 (0x20)
        // Tail:
        // Length: 2
        // Element 1: 1
        // Element 2: 2

        String expected = "0000000000000000000000000000000000000000000000000000000000000020" + // Offset
                "0000000000000000000000000000000000000000000000000000000000000002" + // Length
                "0000000000000000000000000000000000000000000000000000000000000001" + // Element 1
                "0000000000000000000000000000000000000000000000000000000000000002"; // Element 2

        assertEquals(expected, Hex.encodeNoPrefix(encoded));
    }

    @Test
    void encodeIntOutOfBounds() {
        // int8 range: -128 to 127
        // -129 is out of bounds
        BigInteger tooSmall = BigInteger.valueOf(-129);
        try {
            new Int(8, tooSmall);
            throw new RuntimeException("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }

        // 128 is out of bounds
        BigInteger tooLarge = BigInteger.valueOf(128);
        try {
            new Int(8, tooLarge);
            throw new RuntimeException("Should have thrown IllegalArgumentException");
        } catch (IllegalArgumentException e) {
            // Expected
        }
    }
}
