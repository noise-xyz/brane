package io.brane.primitives;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class HexTest {
    @Test
    @DisplayName("Encoding empty and single bytes")
    void testEncodeBasic() {
        assertEquals("0x", Hex.encode(new byte[] {}));
        assertEquals("0x00", Hex.encode(new byte[] {0x00}));
        assertEquals("0xff", Hex.encode(new byte[] {(byte) 0xFF}));
    }

    @Test
    void testEncodeMultipleBytes() {
        byte[] bytes = new byte[] {0x01, 0x23, (byte) 0xAB, (byte) 0xCD};
        assertEquals("0x0123abcd", Hex.encode(bytes));
        assertEquals("0123abcd", Hex.encodeNoPrefix(bytes));
    }

    @Test
    void testDecodeBasic() {
        assertArrayEquals(new byte[] {}, Hex.decode("0x"));
        assertArrayEquals(new byte[] {0}, Hex.decode("0x00"));
        assertArrayEquals(new byte[] {(byte) 0xFF}, Hex.decode("0xff"));
    }

    @Test
    void testDecodeCaseInsensitivity() {
        byte[] expected = new byte[] {0x0A, (byte) 0xBC, (byte) 0xDE, (byte) 0xF0};
        assertArrayEquals(expected, Hex.decode("0x0AbCdEf0"));
        assertArrayEquals(expected, Hex.decode("0X0aBcDeF0"));
        assertArrayEquals(expected, Hex.decode("0aBcDeF0"));
    }

    @Test
    void testCleanPrefix() {
        assertEquals("1234", Hex.cleanPrefix("0x1234"));
        assertEquals("1234", Hex.cleanPrefix("1234"));
    }

    @Test
    void testHasPrefix() {
        assertTrue(Hex.hasPrefix("0x1"));
        assertTrue(Hex.hasPrefix("0Xff"));
        assertFalse(Hex.hasPrefix("1"));
        assertFalse(Hex.hasPrefix(""));
        assertFalse(Hex.hasPrefix(null));
    }

    @Test
    void testDecodeInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () -> Hex.decode(null));
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("0x1"));
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("0xz1"));
        assertThrows(IllegalArgumentException.class, () -> Hex.cleanPrefix(null));
        assertThrows(IllegalArgumentException.class, () -> Hex.encodeNoPrefix(null));
    }

    @Test
    void testRoundTrip() {
        byte[] values1 = new byte[] {};
        byte[] values2 = new byte[] {0x00, 0x01, 0x7F, (byte) 0x80, (byte) 0xFF};
        assertArrayEquals(values1, Hex.decode(Hex.encode(values1)));
        assertArrayEquals(values2, Hex.decode(Hex.encode(values2)));
    }
}
