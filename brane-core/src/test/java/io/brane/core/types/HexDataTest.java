package io.brane.core.types;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HexDataTest {

    @Test
    void acceptsEvenLength() {
        HexData data = new HexData("0x1234abcd");
        assertEquals("0x1234abcd", data.value());
    }

    @Test
    void rejectsOddLength() {
        assertThrows(IllegalArgumentException.class, () -> new HexData("0x123"));
    }

    @Test
    void emptyConstant() {
        assertEquals("0x", HexData.EMPTY.value());
        assertArrayEquals(new byte[0], HexData.EMPTY.toBytes());
    }

    @Test
    void roundTripBytes() {
        byte[] bytes = new byte[] {(byte) 0x01, (byte) 0xFF};
        HexData data = HexData.fromBytes(bytes);
        assertArrayEquals(bytes, data.toBytes());
    }
}
