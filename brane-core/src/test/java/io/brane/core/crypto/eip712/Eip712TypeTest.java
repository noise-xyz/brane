package io.brane.core.crypto.eip712;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class Eip712TypeTest {

    @ParameterizedTest
    @ValueSource(ints = {8, 16, 24, 32, 64, 128, 256})
    void uint_validBitWidths(int bits) {
        var uint = new Eip712Type.Uint(bits);
        assertEquals(bits, uint.bits());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 9, 15, 257, 264, -8})
    void uint_invalidBitWidths(int bits) {
        var ex = assertThrows(IllegalArgumentException.class, () -> new Eip712Type.Uint(bits));
        assertTrue(ex.getMessage().contains("Invalid uint width"));
    }

    @ParameterizedTest
    @ValueSource(ints = {8, 16, 24, 32, 64, 128, 256})
    void int_validBitWidths(int bits) {
        var intType = new Eip712Type.Int(bits);
        assertEquals(bits, intType.bits());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 7, 9, 15, 257, 264, -8})
    void int_invalidBitWidths(int bits) {
        var ex = assertThrows(IllegalArgumentException.class, () -> new Eip712Type.Int(bits));
        assertTrue(ex.getMessage().contains("Invalid int width"));
    }

    @Test
    void address_creation() {
        var address = new Eip712Type.Address();
        assertNotNull(address);
    }

    @Test
    void bool_creation() {
        var bool = new Eip712Type.Bool();
        assertNotNull(bool);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 16, 20, 32})
    void fixedBytes_validLengths(int length) {
        var fixedBytes = new Eip712Type.FixedBytes(length);
        assertEquals(length, fixedBytes.length());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, 33, 64})
    void fixedBytes_invalidLengths(int length) {
        var ex = assertThrows(IllegalArgumentException.class, () -> new Eip712Type.FixedBytes(length));
        assertTrue(ex.getMessage().contains("Invalid bytes length"));
    }

    @Test
    void dynamicBytes_creation() {
        var dynamicBytes = new Eip712Type.DynamicBytes();
        assertNotNull(dynamicBytes);
    }

    @Test
    void string_creation() {
        var string = new Eip712Type.String();
        assertNotNull(string);
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
    void struct_creation() {
        var struct = new Eip712Type.Struct("Person");
        assertEquals("Person", struct.name());
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
}
