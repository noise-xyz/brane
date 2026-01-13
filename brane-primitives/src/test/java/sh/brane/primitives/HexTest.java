// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.primitives;

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

    @Test
    @DisplayName("encodeByte produces correct 0x-prefixed hex for single bytes")
    void testEncodeByte() {
        // Boundary values
        assertEquals("0x00", Hex.encodeByte(0x00));
        assertEquals("0x7f", Hex.encodeByte(0x7F));
        assertEquals("0x80", Hex.encodeByte(0x80));
        assertEquals("0xff", Hex.encodeByte(0xFF));

        // Common values
        assertEquals("0x01", Hex.encodeByte(0x01));
        assertEquals("0x0f", Hex.encodeByte(0x0F));
        assertEquals("0x10", Hex.encodeByte(0x10));
        assertEquals("0xab", Hex.encodeByte(0xAB));

        // RLP-relevant prefixes
        assertEquals("0xb7", Hex.encodeByte(0xB7)); // Long string prefix base
        assertEquals("0xc0", Hex.encodeByte(0xC0)); // Empty list
        assertEquals("0xf7", Hex.encodeByte(0xF7)); // Long list prefix base
    }

    @Test
    @DisplayName("encodeByte rejects out-of-range values")
    void testEncodeByteInvalid() {
        assertThrows(IllegalArgumentException.class, () -> Hex.encodeByte(-1));
        assertThrows(IllegalArgumentException.class, () -> Hex.encodeByte(256));
        assertThrows(IllegalArgumentException.class, () -> Hex.encodeByte(0x100));
        assertThrows(IllegalArgumentException.class, () -> Hex.encodeByte(Integer.MAX_VALUE));
    }

    @Test
    @DisplayName("toBytes accepts String input with or without 0x prefix")
    void testToBytesString() {
        assertArrayEquals(new byte[] {}, Hex.toBytes("0x"));
        assertArrayEquals(new byte[] {}, Hex.toBytes(""));
        assertArrayEquals(new byte[] {0x0A, (byte) 0xBC}, Hex.toBytes("0x0abc"));
        assertArrayEquals(new byte[] {0x0A, (byte) 0xBC}, Hex.toBytes("0abc"));
        assertArrayEquals(new byte[] {(byte) 0xFF}, Hex.toBytes("0xFF"));
    }

    @Test
    @DisplayName("toBytes passes through byte[] unchanged")
    void testToBytesByteArray() {
        byte[] original = new byte[] {0x01, 0x02, 0x03};
        assertSame(original, Hex.toBytes(original)); // Same reference, no copy

        byte[] empty = new byte[] {};
        assertSame(empty, Hex.toBytes(empty));
    }

    @Test
    @DisplayName("toBytes rejects null and unsupported types")
    void testToBytesInvalid() {
        assertThrows(IllegalArgumentException.class, () -> Hex.toBytes(null));
        assertThrows(IllegalArgumentException.class, () -> Hex.toBytes(123));
        assertThrows(IllegalArgumentException.class, () -> Hex.toBytes(new Object()));

        // Invalid hex string should still throw
        assertThrows(IllegalArgumentException.class, () -> Hex.toBytes("0xzz"));
    }

    @Test
    @DisplayName("toBytes round-trip with encode")
    void testToBytesRoundTrip() {
        byte[] original = new byte[] {0x00, 0x7F, (byte) 0x80, (byte) 0xFF};
        String hex = Hex.encode(original);
        assertArrayEquals(original, Hex.toBytes(hex));

        // Also works with encodeNoPrefix
        String noPrefix = Hex.encodeNoPrefix(original);
        assertArrayEquals(original, Hex.toBytes(noPrefix));
    }
}
