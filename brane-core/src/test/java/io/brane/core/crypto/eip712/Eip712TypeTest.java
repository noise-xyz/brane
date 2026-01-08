package io.brane.core.crypto.eip712;

import static org.junit.jupiter.api.Assertions.*;

import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

class Eip712TypeTest {

    // All valid bit widths for uint/int: 8, 16, 24, ..., 256
    static IntStream allValidBitWidths() {
        return IntStream.iterate(8, b -> b <= 256, b -> b + 8);
    }

    @ParameterizedTest
    @MethodSource("allValidBitWidths")
    void uint_allValidBitWidths(int bits) {
        var uint = new Eip712Type.Uint(bits);
        assertEquals(bits, uint.bits());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 9, 15, 257, 264, -8, -256, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void uint_invalidBitWidths(int bits) {
        var ex = assertThrows(IllegalArgumentException.class, () -> new Eip712Type.Uint(bits));
        assertTrue(ex.getMessage().contains("Invalid uint width"));
    }

    @Test
    void uint_equality() {
        assertEquals(new Eip712Type.Uint(256), new Eip712Type.Uint(256));
        assertNotEquals(new Eip712Type.Uint(256), new Eip712Type.Uint(128));
        assertEquals(new Eip712Type.Uint(8).hashCode(), new Eip712Type.Uint(8).hashCode());
    }

    @ParameterizedTest
    @MethodSource("allValidBitWidths")
    void int_allValidBitWidths(int bits) {
        var intType = new Eip712Type.Int(bits);
        assertEquals(bits, intType.bits());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 9, 15, 257, 264, -8, -256, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void int_invalidBitWidths(int bits) {
        var ex = assertThrows(IllegalArgumentException.class, () -> new Eip712Type.Int(bits));
        assertTrue(ex.getMessage().contains("Invalid int width"));
    }

    @Test
    void int_equality() {
        assertEquals(new Eip712Type.Int(256), new Eip712Type.Int(256));
        assertNotEquals(new Eip712Type.Int(256), new Eip712Type.Int(128));
        assertEquals(new Eip712Type.Int(8).hashCode(), new Eip712Type.Int(8).hashCode());
    }

    @Test
    void address_creation() {
        var address = new Eip712Type.Address();
        assertNotNull(address);
    }

    @Test
    void address_equality() {
        assertEquals(new Eip712Type.Address(), new Eip712Type.Address());
        assertEquals(new Eip712Type.Address().hashCode(), new Eip712Type.Address().hashCode());
    }

    @Test
    void bool_creation() {
        var bool = new Eip712Type.Bool();
        assertNotNull(bool);
    }

    @Test
    void bool_equality() {
        assertEquals(new Eip712Type.Bool(), new Eip712Type.Bool());
        assertEquals(new Eip712Type.Bool().hashCode(), new Eip712Type.Bool().hashCode());
    }

    // All valid FixedBytes lengths: 1, 2, ..., 32
    static IntStream allValidFixedBytesLengths() {
        return IntStream.rangeClosed(1, 32);
    }

    @ParameterizedTest
    @MethodSource("allValidFixedBytesLengths")
    void fixedBytes_allValidLengths(int length) {
        var fixedBytes = new Eip712Type.FixedBytes(length);
        assertEquals(length, fixedBytes.length());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 33, 64, -32, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void fixedBytes_invalidLengths(int length) {
        var ex = assertThrows(IllegalArgumentException.class, () -> new Eip712Type.FixedBytes(length));
        assertTrue(ex.getMessage().contains("Invalid bytes length"));
    }

    @Test
    void fixedBytes_equality() {
        assertEquals(new Eip712Type.FixedBytes(32), new Eip712Type.FixedBytes(32));
        assertNotEquals(new Eip712Type.FixedBytes(32), new Eip712Type.FixedBytes(20));
        assertEquals(new Eip712Type.FixedBytes(1).hashCode(), new Eip712Type.FixedBytes(1).hashCode());
    }

    @Test
    void dynamicBytes_creation() {
        var dynamicBytes = new Eip712Type.DynamicBytes();
        assertNotNull(dynamicBytes);
    }

    @Test
    void dynamicBytes_equality() {
        assertEquals(new Eip712Type.DynamicBytes(), new Eip712Type.DynamicBytes());
        assertEquals(new Eip712Type.DynamicBytes().hashCode(), new Eip712Type.DynamicBytes().hashCode());
    }

    @Test
    void string_creation() {
        var string = new Eip712Type.String();
        assertNotNull(string);
    }

    @Test
    void string_equality() {
        assertEquals(new Eip712Type.String(), new Eip712Type.String());
        assertEquals(new Eip712Type.String().hashCode(), new Eip712Type.String().hashCode());
    }

    @Test
    void array_dynamicLength() {
        var array = new Eip712Type.Array(new Eip712Type.Uint(256), null);
        assertEquals(new Eip712Type.Uint(256), array.elementType());
        assertNull(array.fixedLength());
    }

    @Test
    void array_fixedLength() {
        var array = new Eip712Type.Array(new Eip712Type.Address(), 10);
        assertEquals(new Eip712Type.Address(), array.elementType());
        assertEquals(10, array.fixedLength());
    }

    @Test
    void array_nestedArray() {
        var inner = new Eip712Type.Array(new Eip712Type.Uint(8), 5);
        var outer = new Eip712Type.Array(inner, null);
        assertInstanceOf(Eip712Type.Array.class, outer.elementType());
    }

    @Test
    void array_equality() {
        var array1 = new Eip712Type.Array(new Eip712Type.Uint(256), null);
        var array2 = new Eip712Type.Array(new Eip712Type.Uint(256), null);
        var array3 = new Eip712Type.Array(new Eip712Type.Uint(256), 5);
        assertEquals(array1, array2);
        assertNotEquals(array1, array3);
        assertEquals(array1.hashCode(), array2.hashCode());
    }

    @Test
    void array_withStructElementType() {
        var array = new Eip712Type.Array(new Eip712Type.Struct("Person"), null);
        assertEquals(new Eip712Type.Struct("Person"), array.elementType());
    }

    @Test
    void struct_creation() {
        var struct = new Eip712Type.Struct("Person");
        assertEquals("Person", struct.name());
    }

    @Test
    void struct_equality() {
        assertEquals(new Eip712Type.Struct("Person"), new Eip712Type.Struct("Person"));
        assertNotEquals(new Eip712Type.Struct("Person"), new Eip712Type.Struct("Mail"));
        assertEquals(new Eip712Type.Struct("Test").hashCode(), new Eip712Type.Struct("Test").hashCode());
    }

    @Test
    void sealedInterface_allPermitsAreValid() {
        // Verify all permitted types are valid implementations
        Eip712Type[] types = {
            new Eip712Type.Uint(256),
            new Eip712Type.Int(128),
            new Eip712Type.Address(),
            new Eip712Type.Bool(),
            new Eip712Type.FixedBytes(32),
            new Eip712Type.DynamicBytes(),
            new Eip712Type.String(),
            new Eip712Type.Array(new Eip712Type.Uint(256), null),
            new Eip712Type.Struct("Test")
        };
        assertEquals(9, types.length);
    }

    @Test
    void differentTypes_notEqual() {
        // Ensure different type variants are not equal to each other
        assertNotEquals(new Eip712Type.Uint(256), new Eip712Type.Int(256));
        assertNotEquals(new Eip712Type.Address(), new Eip712Type.Bool());
        assertNotEquals(new Eip712Type.DynamicBytes(), new Eip712Type.String());
        assertNotEquals(new Eip712Type.FixedBytes(32), new Eip712Type.Uint(256));
    }
}
