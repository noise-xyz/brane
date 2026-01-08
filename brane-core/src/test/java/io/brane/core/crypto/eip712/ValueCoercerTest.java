package io.brane.core.crypto.eip712;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import io.brane.core.error.Eip712Exception;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;

class ValueCoercerTest {

    // ═══════════════════════════════════════════════════════════════
    // coerce() - null handling
    // ═══════════════════════════════════════════════════════════════

    @Test
    void coerce_nullValue_throws() {
        var type = new Eip712Type.Uint(256);
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(null, type));
        assertTrue(ex.getMessage().contains("Invalid value"));
    }

    // ═══════════════════════════════════════════════════════════════
    // coerce() - Address type
    // ═══════════════════════════════════════════════════════════════

    @Test
    void coerceAddress_fromAddress() {
        var addr = new Address("0x1234567890123456789012345678901234567890");
        var result = ValueCoercer.coerce(addr, new Eip712Type.Address());
        assertEquals(addr, result);
    }

    @Test
    void coerceAddress_fromString() {
        var addrStr = "0x1234567890123456789012345678901234567890";
        var result = ValueCoercer.coerce(addrStr, new Eip712Type.Address());
        assertInstanceOf(Address.class, result);
        assertEquals(new Address(addrStr), result);
    }

    @Test
    void coerceAddress_fromStringLowercase() {
        var addrStr = "0xabcdefabcdefabcdefabcdefabcdefabcdefabcd";
        var result = ValueCoercer.coerce(addrStr, new Eip712Type.Address());
        assertInstanceOf(Address.class, result);
    }

    @Test
    void coerceAddress_fromStringMixedCase() {
        var addrStr = "0xAbCdEf1234567890AbCdEf1234567890AbCdEf12";
        var result = ValueCoercer.coerce(addrStr, new Eip712Type.Address());
        assertInstanceOf(Address.class, result);
    }

    @Test
    void coerceAddress_invalidType_throws() {
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(12345, new Eip712Type.Address()));
        assertTrue(ex.getMessage().contains("Invalid value"));
        assertTrue(ex.getMessage().contains("address"));
    }

    @Test
    void coerceAddress_invalidString_throws() {
        assertThrows(RuntimeException.class,
            () -> ValueCoercer.coerce("invalid", new Eip712Type.Address()));
    }

    // ═══════════════════════════════════════════════════════════════
    // coerce() - Uint types
    // ═══════════════════════════════════════════════════════════════

    @Test
    void coerceUint_fromBigInteger() {
        var bi = BigInteger.valueOf(12345);
        var result = ValueCoercer.coerce(bi, new Eip712Type.Uint(256));
        assertEquals(bi, result);
    }

    @Test
    void coerceUint_fromLong() {
        var result = ValueCoercer.coerce(12345L, new Eip712Type.Uint(256));
        assertEquals(BigInteger.valueOf(12345), result);
    }

    @Test
    void coerceUint_fromInteger() {
        var result = ValueCoercer.coerce(12345, new Eip712Type.Uint(256));
        assertEquals(BigInteger.valueOf(12345), result);
    }

    @Test
    void coerceUint_fromDecimalString() {
        var result = ValueCoercer.coerce("12345", new Eip712Type.Uint(256));
        assertEquals(BigInteger.valueOf(12345), result);
    }

    @Test
    void coerceUint_fromHexString() {
        var result = ValueCoercer.coerce("0xff", new Eip712Type.Uint(256));
        assertEquals(BigInteger.valueOf(255), result);
    }

    @Test
    void coerceUint_fromHexStringUppercase() {
        var result = ValueCoercer.coerce("0XFF", new Eip712Type.Uint(256));
        assertEquals(BigInteger.valueOf(255), result);
    }

    @Test
    void coerceUint_zero() {
        var result = ValueCoercer.coerce(BigInteger.ZERO, new Eip712Type.Uint(8));
        assertEquals(BigInteger.ZERO, result);
    }

    @Test
    void coerceUint8_maxValue() {
        var maxUint8 = BigInteger.valueOf(255);
        var result = ValueCoercer.coerce(maxUint8, new Eip712Type.Uint(8));
        assertEquals(maxUint8, result);
    }

    @Test
    void coerceUint256_maxValue() {
        var maxUint256 = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);
        var result = ValueCoercer.coerce(maxUint256, new Eip712Type.Uint(256));
        assertEquals(maxUint256, result);
    }

    @Test
    void coerceUint_negative_throws() {
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(BigInteger.valueOf(-1), new Eip712Type.Uint(256)));
        assertTrue(ex.getMessage().contains("must be non-negative"));
    }

    @Test
    void coerceUint8_exceedsBitWidth_throws() {
        var tooLarge = BigInteger.valueOf(256);
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(tooLarge, new Eip712Type.Uint(8)));
        assertTrue(ex.getMessage().contains("exceeds bit width"));
    }

    @Test
    void coerceUint_invalidType_throws() {
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(new Object(), new Eip712Type.Uint(256)));
        assertTrue(ex.getMessage().contains("Invalid value"));
        assertTrue(ex.getMessage().contains("integer"));
    }

    static IntStream allValidUintBitWidths() {
        return IntStream.iterate(8, b -> b <= 256, b -> b + 8);
    }

    @ParameterizedTest
    @MethodSource("allValidUintBitWidths")
    void coerceUint_boundaryMaxValues(int bits) {
        var maxValue = BigInteger.ONE.shiftLeft(bits).subtract(BigInteger.ONE);
        var result = ValueCoercer.coerce(maxValue, new Eip712Type.Uint(bits));
        assertEquals(maxValue, result);
    }

    @ParameterizedTest
    @MethodSource("allValidUintBitWidths")
    void coerceUint_overflowByOne_throws(int bits) {
        var overflow = BigInteger.ONE.shiftLeft(bits);
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(overflow, new Eip712Type.Uint(bits)));
        assertTrue(ex.getMessage().contains("exceeds bit width"));
    }

    // ═══════════════════════════════════════════════════════════════
    // coerce() - Int types
    // ═══════════════════════════════════════════════════════════════

    @Test
    void coerceInt_fromBigInteger() {
        var bi = BigInteger.valueOf(-12345);
        var result = ValueCoercer.coerce(bi, new Eip712Type.Int(256));
        assertEquals(bi, result);
    }

    @Test
    void coerceInt_fromLong() {
        var result = ValueCoercer.coerce(-12345L, new Eip712Type.Int(256));
        assertEquals(BigInteger.valueOf(-12345), result);
    }

    @Test
    void coerceInt_fromInteger() {
        var result = ValueCoercer.coerce(-12345, new Eip712Type.Int(256));
        assertEquals(BigInteger.valueOf(-12345), result);
    }

    @Test
    void coerceInt_fromDecimalString() {
        var result = ValueCoercer.coerce("-12345", new Eip712Type.Int(256));
        assertEquals(BigInteger.valueOf(-12345), result);
    }

    @Test
    void coerceInt_fromPositiveHexString() {
        var result = ValueCoercer.coerce("0x7f", new Eip712Type.Int(8));
        assertEquals(BigInteger.valueOf(127), result);
    }

    @Test
    void coerceInt_zero() {
        var result = ValueCoercer.coerce(BigInteger.ZERO, new Eip712Type.Int(8));
        assertEquals(BigInteger.ZERO, result);
    }

    @Test
    void coerceInt8_maxValue() {
        var maxInt8 = BigInteger.valueOf(127);
        var result = ValueCoercer.coerce(maxInt8, new Eip712Type.Int(8));
        assertEquals(maxInt8, result);
    }

    @Test
    void coerceInt8_minValue() {
        var minInt8 = BigInteger.valueOf(-128);
        var result = ValueCoercer.coerce(minInt8, new Eip712Type.Int(8));
        assertEquals(minInt8, result);
    }

    @Test
    void coerceInt256_maxValue() {
        var maxInt256 = BigInteger.ONE.shiftLeft(255).subtract(BigInteger.ONE);
        var result = ValueCoercer.coerce(maxInt256, new Eip712Type.Int(256));
        assertEquals(maxInt256, result);
    }

    @Test
    void coerceInt256_minValue() {
        var minInt256 = BigInteger.ONE.shiftLeft(255).negate();
        var result = ValueCoercer.coerce(minInt256, new Eip712Type.Int(256));
        assertEquals(minInt256, result);
    }

    @Test
    void coerceInt8_exceedsMax_throws() {
        var tooLarge = BigInteger.valueOf(128);
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(tooLarge, new Eip712Type.Int(8)));
        assertTrue(ex.getMessage().contains("out of signed range"));
    }

    @Test
    void coerceInt8_exceedsMin_throws() {
        var tooSmall = BigInteger.valueOf(-129);
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(tooSmall, new Eip712Type.Int(8)));
        assertTrue(ex.getMessage().contains("out of signed range"));
    }

    @Test
    void coerceInt_invalidType_throws() {
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(new Object(), new Eip712Type.Int(256)));
        assertTrue(ex.getMessage().contains("Invalid value"));
        assertTrue(ex.getMessage().contains("integer"));
    }

    static IntStream allValidIntBitWidths() {
        return IntStream.iterate(8, b -> b <= 256, b -> b + 8);
    }

    @ParameterizedTest
    @MethodSource("allValidIntBitWidths")
    void coerceInt_boundaryMaxValues(int bits) {
        var maxValue = BigInteger.ONE.shiftLeft(bits - 1).subtract(BigInteger.ONE);
        var result = ValueCoercer.coerce(maxValue, new Eip712Type.Int(bits));
        assertEquals(maxValue, result);
    }

    @ParameterizedTest
    @MethodSource("allValidIntBitWidths")
    void coerceInt_boundaryMinValues(int bits) {
        var minValue = BigInteger.ONE.shiftLeft(bits - 1).negate();
        var result = ValueCoercer.coerce(minValue, new Eip712Type.Int(bits));
        assertEquals(minValue, result);
    }

    @ParameterizedTest
    @MethodSource("allValidIntBitWidths")
    void coerceInt_overflowMaxByOne_throws(int bits) {
        var overflow = BigInteger.ONE.shiftLeft(bits - 1);
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(overflow, new Eip712Type.Int(bits)));
        assertTrue(ex.getMessage().contains("out of signed range"));
    }

    @ParameterizedTest
    @MethodSource("allValidIntBitWidths")
    void coerceInt_overflowMinByOne_throws(int bits) {
        var underflow = BigInteger.ONE.shiftLeft(bits - 1).negate().subtract(BigInteger.ONE);
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(underflow, new Eip712Type.Int(bits)));
        assertTrue(ex.getMessage().contains("out of signed range"));
    }

    // ═══════════════════════════════════════════════════════════════
    // coerce() - Bool type
    // ═══════════════════════════════════════════════════════════════

    @Test
    void coerceBool_fromBooleanTrue() {
        var result = ValueCoercer.coerce(true, new Eip712Type.Bool());
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void coerceBool_fromBooleanFalse() {
        var result = ValueCoercer.coerce(false, new Eip712Type.Bool());
        assertEquals(Boolean.FALSE, result);
    }

    @Test
    void coerceBool_fromStringTrue() {
        var result = ValueCoercer.coerce("true", new Eip712Type.Bool());
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void coerceBool_fromStringFalse() {
        var result = ValueCoercer.coerce("false", new Eip712Type.Bool());
        assertEquals(Boolean.FALSE, result);
    }

    @Test
    void coerceBool_fromStringUppercase() {
        // Boolean.parseBoolean is case-insensitive for "true"
        var result = ValueCoercer.coerce("TRUE", new Eip712Type.Bool());
        assertEquals(Boolean.TRUE, result);
    }

    @Test
    void coerceBool_fromInvalidString_returnsFalse() {
        // Boolean.parseBoolean returns false for any non-"true" string
        var result = ValueCoercer.coerce("invalid", new Eip712Type.Bool());
        assertEquals(Boolean.FALSE, result);
    }

    @Test
    void coerceBool_invalidType_throws() {
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(12345, new Eip712Type.Bool()));
        assertTrue(ex.getMessage().contains("Invalid value"));
        assertTrue(ex.getMessage().contains("bool"));
    }

    // ═══════════════════════════════════════════════════════════════
    // coerce() - FixedBytes types
    // ═══════════════════════════════════════════════════════════════

    @Test
    void coerceFixedBytes_fromHexData() {
        var data = new HexData("0x1234567890123456789012345678901234567890123456789012345678901234");
        var result = ValueCoercer.coerce(data, new Eip712Type.FixedBytes(32));
        assertEquals(data, result);
    }

    @Test
    void coerceFixedBytes_fromByteArray() {
        var bytes = new byte[32];
        bytes[0] = 0x12;
        bytes[31] = 0x34;
        var result = ValueCoercer.coerce(bytes, new Eip712Type.FixedBytes(32));
        assertInstanceOf(HexData.class, result);
        assertEquals(32, ((HexData) result).toBytes().length);
    }

    @Test
    void coerceFixedBytes_fromString() {
        var hexStr = "0x1234567890123456789012345678901234567890123456789012345678901234";
        var result = ValueCoercer.coerce(hexStr, new Eip712Type.FixedBytes(32));
        assertInstanceOf(HexData.class, result);
        assertEquals(32, ((HexData) result).toBytes().length);
    }

    @Test
    void coerceFixedBytes1_fromString() {
        var hexStr = "0xff";
        var result = ValueCoercer.coerce(hexStr, new Eip712Type.FixedBytes(1));
        assertInstanceOf(HexData.class, result);
        assertEquals(1, ((HexData) result).toBytes().length);
    }

    static IntStream allValidBytesLengths() {
        return IntStream.rangeClosed(1, 32);
    }

    @ParameterizedTest
    @MethodSource("allValidBytesLengths")
    void coerceFixedBytes_allValidLengths(int length) {
        var bytes = new byte[length];
        var result = ValueCoercer.coerce(bytes, new Eip712Type.FixedBytes(length));
        assertInstanceOf(HexData.class, result);
        assertEquals(length, ((HexData) result).toBytes().length);
    }

    @Test
    void coerceFixedBytes_wrongLength_throws() {
        var bytes = new byte[16];
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(bytes, new Eip712Type.FixedBytes(32)));
        assertTrue(ex.getMessage().contains("expected 32 bytes, got 16"));
    }

    @Test
    void coerceFixedBytes_tooShort_throws() {
        var hexStr = "0x1234";
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(hexStr, new Eip712Type.FixedBytes(32)));
        assertTrue(ex.getMessage().contains("expected 32 bytes"));
    }

    @Test
    void coerceFixedBytes_invalidType_throws() {
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(12345, new Eip712Type.FixedBytes(32)));
        assertTrue(ex.getMessage().contains("Invalid value"));
        assertTrue(ex.getMessage().contains("bytes"));
    }

    // ═══════════════════════════════════════════════════════════════
    // coerce() - DynamicBytes type
    // ═══════════════════════════════════════════════════════════════

    @Test
    void coerceDynamicBytes_fromHexData() {
        var data = new HexData("0x1234");
        var result = ValueCoercer.coerce(data, new Eip712Type.DynamicBytes());
        assertEquals(data, result);
    }

    @Test
    void coerceDynamicBytes_fromByteArray() {
        var bytes = new byte[]{0x12, 0x34, 0x56};
        var result = ValueCoercer.coerce(bytes, new Eip712Type.DynamicBytes());
        assertInstanceOf(HexData.class, result);
        assertArrayEquals(bytes, ((HexData) result).toBytes());
    }

    @Test
    void coerceDynamicBytes_fromString() {
        var hexStr = "0x1234567890abcdef";
        var result = ValueCoercer.coerce(hexStr, new Eip712Type.DynamicBytes());
        assertInstanceOf(HexData.class, result);
    }

    @Test
    void coerceDynamicBytes_empty() {
        var result = ValueCoercer.coerce("0x", new Eip712Type.DynamicBytes());
        assertInstanceOf(HexData.class, result);
        assertEquals(0, ((HexData) result).toBytes().length);
    }

    @Test
    void coerceDynamicBytes_emptyByteArray() {
        var result = ValueCoercer.coerce(new byte[0], new Eip712Type.DynamicBytes());
        assertInstanceOf(HexData.class, result);
        assertEquals(0, ((HexData) result).toBytes().length);
    }

    @Test
    void coerceDynamicBytes_invalidType_throws() {
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(12345, new Eip712Type.DynamicBytes()));
        assertTrue(ex.getMessage().contains("Invalid value"));
        assertTrue(ex.getMessage().contains("bytes"));
    }

    // ═══════════════════════════════════════════════════════════════
    // coerce() - String type
    // ═══════════════════════════════════════════════════════════════

    @Test
    void coerceString_fromString() {
        var str = "Hello, World!";
        var result = ValueCoercer.coerce(str, new Eip712Type.String());
        assertEquals(str, result);
    }

    @Test
    void coerceString_empty() {
        var result = ValueCoercer.coerce("", new Eip712Type.String());
        assertEquals("", result);
    }

    @Test
    void coerceString_withUnicode() {
        var str = "Hello, 世界! 🌍";
        var result = ValueCoercer.coerce(str, new Eip712Type.String());
        assertEquals(str, result);
    }

    @Test
    void coerceString_invalidType_throws() {
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(12345, new Eip712Type.String()));
        assertTrue(ex.getMessage().contains("Invalid value"));
        assertTrue(ex.getMessage().contains("string"));
    }

    // ═══════════════════════════════════════════════════════════════
    // coerce() - Array types
    // ═══════════════════════════════════════════════════════════════

    @Test
    void coerceArray_dynamicArray() {
        var list = List.of(BigInteger.ONE, BigInteger.TWO, BigInteger.TEN);
        var arrayType = new Eip712Type.Array(new Eip712Type.Uint(256), null);
        var result = ValueCoercer.coerce(list, arrayType);
        assertEquals(list, result);
    }

    @Test
    void coerceArray_emptyDynamicArray() {
        var list = List.of();
        var arrayType = new Eip712Type.Array(new Eip712Type.Address(), null);
        var result = ValueCoercer.coerce(list, arrayType);
        assertEquals(list, result);
    }

    @Test
    void coerceArray_fixedArray_correctLength() {
        var list = List.of("a", "b", "c");
        var arrayType = new Eip712Type.Array(new Eip712Type.String(), 3);
        var result = ValueCoercer.coerce(list, arrayType);
        assertEquals(list, result);
    }

    @Test
    void coerceArray_fixedArray_wrongLength_throws() {
        var list = List.of("a", "b");
        var arrayType = new Eip712Type.Array(new Eip712Type.String(), 3);
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(list, arrayType));
        assertTrue(ex.getMessage().contains("expected 3 elements, got 2"));
    }

    @Test
    void coerceArray_fixedArray_tooManyElements_throws() {
        var list = List.of("a", "b", "c", "d");
        var arrayType = new Eip712Type.Array(new Eip712Type.String(), 3);
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(list, arrayType));
        assertTrue(ex.getMessage().contains("expected 3 elements, got 4"));
    }

    @Test
    void coerceArray_nestedArray() {
        var list = List.of(List.of(BigInteger.ONE, BigInteger.TWO), List.of(BigInteger.TEN));
        var innerType = new Eip712Type.Array(new Eip712Type.Uint(256), null);
        var arrayType = new Eip712Type.Array(innerType, null);
        var result = ValueCoercer.coerce(list, arrayType);
        assertEquals(list, result);
    }

    @Test
    void coerceArray_invalidType_throws() {
        var arrayType = new Eip712Type.Array(new Eip712Type.Uint(256), null);
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce("not a list", arrayType));
        assertTrue(ex.getMessage().contains("Invalid value"));
        assertTrue(ex.getMessage().contains("array"));
    }

    @Test
    void coerceArray_invalidType_number_throws() {
        var arrayType = new Eip712Type.Array(new Eip712Type.Uint(256), null);
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(12345, arrayType));
        assertTrue(ex.getMessage().contains("Invalid value"));
        assertTrue(ex.getMessage().contains("array"));
    }

    // ═══════════════════════════════════════════════════════════════
    // coerce() - Struct type
    // ═══════════════════════════════════════════════════════════════

    @Test
    void coerceStruct_fromMap() {
        var map = Map.of("name", "Alice", "wallet", "0x1234567890123456789012345678901234567890");
        var result = ValueCoercer.coerce(map, new Eip712Type.Struct("Person"));
        assertEquals(map, result);
    }

    @Test
    void coerceStruct_emptyMap() {
        var map = Map.of();
        var result = ValueCoercer.coerce(map, new Eip712Type.Struct("Empty"));
        assertEquals(map, result);
    }

    @Test
    void coerceStruct_invalidType_throws() {
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce("not a map", new Eip712Type.Struct("Person")));
        assertTrue(ex.getMessage().contains("Invalid value"));
        assertTrue(ex.getMessage().contains("struct"));
    }

    @Test
    void coerceStruct_invalidType_list_throws() {
        var ex = assertThrows(Eip712Exception.class,
            () -> ValueCoercer.coerce(List.of("a", "b"), new Eip712Type.Struct("Person")));
        assertTrue(ex.getMessage().contains("Invalid value"));
        assertTrue(ex.getMessage().contains("struct"));
    }

    // ═══════════════════════════════════════════════════════════════
    // coerce() - Edge cases for toBigInteger
    // ═══════════════════════════════════════════════════════════════

    @Test
    void coerceUint_fromShort() {
        var result = ValueCoercer.coerce((short) 100, new Eip712Type.Uint(256));
        assertEquals(BigInteger.valueOf(100), result);
    }

    @Test
    void coerceUint_fromByte() {
        var result = ValueCoercer.coerce((byte) 50, new Eip712Type.Uint(256));
        assertEquals(BigInteger.valueOf(50), result);
    }

    @Test
    void coerceUint_fromDouble() {
        // Double is truncated to long value
        var result = ValueCoercer.coerce(123.9, new Eip712Type.Uint(256));
        assertEquals(BigInteger.valueOf(123), result);
    }

    @Test
    void coerceUint_fromFloat() {
        var result = ValueCoercer.coerce(50.7f, new Eip712Type.Uint(256));
        assertEquals(BigInteger.valueOf(50), result);
    }

    @Test
    void coerceUint_largeHexString() {
        var hexStr = "0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff";
        var expected = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);
        var result = ValueCoercer.coerce(hexStr, new Eip712Type.Uint(256));
        assertEquals(expected, result);
    }

    @Test
    void coerceUint_largeDecimalString() {
        var maxUint256 = BigInteger.ONE.shiftLeft(256).subtract(BigInteger.ONE);
        var result = ValueCoercer.coerce(maxUint256.toString(), new Eip712Type.Uint(256));
        assertEquals(maxUint256, result);
    }

    @Test
    void coerceInt_negativeDecimalString() {
        var minInt256 = BigInteger.ONE.shiftLeft(255).negate();
        var result = ValueCoercer.coerce(minInt256.toString(), new Eip712Type.Int(256));
        assertEquals(minInt256, result);
    }

    // ═══════════════════════════════════════════════════════════════
    // coerce() - Type dispatch verification
    // ═══════════════════════════════════════════════════════════════

    static Stream<Arguments> allTypeDispatch() {
        return Stream.of(
            Arguments.of(new Eip712Type.Address(), "0x1234567890123456789012345678901234567890", Address.class),
            Arguments.of(new Eip712Type.Uint(256), BigInteger.ONE, BigInteger.class),
            Arguments.of(new Eip712Type.Int(256), BigInteger.ONE, BigInteger.class),
            Arguments.of(new Eip712Type.Bool(), true, Boolean.class),
            Arguments.of(new Eip712Type.FixedBytes(4), new byte[4], HexData.class),
            Arguments.of(new Eip712Type.DynamicBytes(), "0x1234", HexData.class),
            Arguments.of(new Eip712Type.String(), "hello", String.class),
            Arguments.of(new Eip712Type.Array(new Eip712Type.Uint(8), null), List.of(BigInteger.ONE), List.class),
            Arguments.of(new Eip712Type.Struct("Test"), Map.of("a", "b"), Map.class)
        );
    }

    @ParameterizedTest
    @MethodSource("allTypeDispatch")
    void coerce_dispatchesToCorrectHandler(Eip712Type type, Object value, Class<?> expectedClass) {
        var result = ValueCoercer.coerce(value, type);
        assertInstanceOf(expectedClass, result);
    }
}
