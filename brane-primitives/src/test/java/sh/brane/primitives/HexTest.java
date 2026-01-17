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
    @DisplayName("decode handles empty string edge cases")
    void testDecodeEmptyEdgeCases() {
        // Empty string without prefix
        assertArrayEquals(new byte[] {}, Hex.decode(""));

        // "0x" prefix only
        assertArrayEquals(new byte[] {}, Hex.decode("0x"));

        // "0X" prefix only (uppercase)
        assertArrayEquals(new byte[] {}, Hex.decode("0X"));
    }

    @Test
    @DisplayName("decode rejects odd-length hex strings")
    void testDecodeOddLength() {
        // Single character (odd length)
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("0x1"));
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("1"));

        // Three characters (odd length)
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("0x123"));
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("123"));

        // Five characters (odd length)
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("0x12345"));
        assertThrows(IllegalArgumentException.class, () -> Hex.decode("12345"));
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

    @Test
    @DisplayName("encodeTo writes hex chars with prefix to pre-allocated buffer")
    void testEncodeToWithPrefix() {
        byte[] bytes = new byte[] {0x01, 0x23, (byte) 0xAB, (byte) 0xCD};
        char[] dest = new char[10];

        int written = Hex.encodeTo(bytes, dest, 0, true);

        assertEquals(10, written);
        assertEquals("0x0123abcd", new String(dest, 0, written));
    }

    @Test
    @DisplayName("encodeTo writes hex chars without prefix to pre-allocated buffer")
    void testEncodeToWithoutPrefix() {
        byte[] bytes = new byte[] {0x01, 0x23, (byte) 0xAB, (byte) 0xCD};
        char[] dest = new char[8];

        int written = Hex.encodeTo(bytes, dest, 0, false);

        assertEquals(8, written);
        assertEquals("0123abcd", new String(dest, 0, written));
    }

    @Test
    @DisplayName("encodeTo writes at specified offset")
    void testEncodeToWithOffset() {
        byte[] bytes = new byte[] {(byte) 0xFF};
        char[] dest = new char[10];
        dest[0] = 'A';
        dest[1] = 'B';

        int written = Hex.encodeTo(bytes, dest, 2, true);

        assertEquals(4, written);
        assertEquals("AB0xff", new String(dest, 0, 6));
    }

    @Test
    @DisplayName("encodeTo handles empty byte array")
    void testEncodeToEmpty() {
        byte[] bytes = new byte[] {};
        char[] dest = new char[2];

        int writtenWithPrefix = Hex.encodeTo(bytes, dest, 0, true);
        assertEquals(2, writtenWithPrefix);
        assertEquals("0x", new String(dest, 0, writtenWithPrefix));

        char[] destNoPrefix = new char[0];
        int writtenNoPrefix = Hex.encodeTo(bytes, destNoPrefix, 0, false);
        assertEquals(0, writtenNoPrefix);
    }

    @Test
    @DisplayName("encodeTo throws when buffer is too small")
    void testEncodeToBufferTooSmall() {
        byte[] bytes = new byte[] {0x01, 0x02};
        char[] smallDest = new char[3]; // needs 6 chars with prefix

        var ex = assertThrows(IllegalArgumentException.class, () -> Hex.encodeTo(bytes, smallDest, 0, true));
        assertTrue(ex.getMessage().contains("too small"));
    }

    @Test
    @DisplayName("encodeTo throws when offset leaves insufficient space")
    void testEncodeToInsufficientSpaceWithOffset() {
        byte[] bytes = new byte[] {0x01};
        char[] dest = new char[5]; // enough for 4 chars but offset reduces available space

        var ex = assertThrows(IllegalArgumentException.class, () -> Hex.encodeTo(bytes, dest, 3, true));
        assertTrue(ex.getMessage().contains("too small"));
    }

    @Test
    @DisplayName("encodeTo rejects null bytes")
    void testEncodeToNullBytes() {
        char[] dest = new char[10];
        assertThrows(IllegalArgumentException.class, () -> Hex.encodeTo(null, dest, 0, true));
    }

    @Test
    @DisplayName("encodeTo rejects null dest")
    void testEncodeToNullDest() {
        byte[] bytes = new byte[] {0x01};
        assertThrows(IllegalArgumentException.class, () -> Hex.encodeTo(bytes, null, 0, true));
    }

    @Test
    @DisplayName("decodeTo decodes hex into pre-allocated byte array")
    void testDecodeToBasic() {
        byte[] dest = new byte[4];

        int written = Hex.decodeTo("0x0123abcd", 0, 10, dest, 0);

        assertEquals(4, written);
        assertArrayEquals(new byte[] {0x01, 0x23, (byte) 0xAB, (byte) 0xCD}, dest);
    }

    @Test
    @DisplayName("decodeTo works without 0x prefix")
    void testDecodeToNoPrefix() {
        byte[] dest = new byte[2];

        int written = Hex.decodeTo("abcd", 0, 4, dest, 0);

        assertEquals(2, written);
        assertArrayEquals(new byte[] {(byte) 0xAB, (byte) 0xCD}, dest);
    }

    @Test
    @DisplayName("decodeTo writes at specified dest offset")
    void testDecodeToWithDestOffset() {
        byte[] dest = new byte[5];
        dest[0] = (byte) 0xAA;
        dest[1] = (byte) 0xBB;

        int written = Hex.decodeTo("0xffee", 0, 6, dest, 2);

        assertEquals(2, written);
        assertEquals((byte) 0xAA, dest[0]);
        assertEquals((byte) 0xBB, dest[1]);
        assertEquals((byte) 0xFF, dest[2]);
        assertEquals((byte) 0xEE, dest[3]);
        assertEquals((byte) 0x00, dest[4]);
    }

    @Test
    @DisplayName("decodeTo reads from specified hex offset and length")
    void testDecodeToWithHexOffset() {
        byte[] dest = new byte[2];
        // String: "prefix0x1234suffix" - we want to decode "0x1234" starting at index 6
        String input = "prefix0x1234suffix";

        int written = Hex.decodeTo(input, 6, 6, dest, 0);

        assertEquals(2, written);
        assertArrayEquals(new byte[] {0x12, 0x34}, dest);
    }

    @Test
    @DisplayName("decodeTo handles partial hex region without prefix")
    void testDecodeToPartialRegion() {
        byte[] dest = new byte[2];
        String input = "00112233aabbccdd";

        // Decode "aabb" from middle of string (offset 8, length 4)
        int written = Hex.decodeTo(input, 8, 4, dest, 0);

        assertEquals(2, written);
        assertArrayEquals(new byte[] {(byte) 0xAA, (byte) 0xBB}, dest);
    }

    @Test
    @DisplayName("decodeTo handles empty hex string")
    void testDecodeToEmpty() {
        byte[] dest = new byte[10];

        int written = Hex.decodeTo("0x", 0, 2, dest, 0);
        assertEquals(0, written);

        written = Hex.decodeTo("", 0, 0, dest, 0);
        assertEquals(0, written);
    }

    @Test
    @DisplayName("decodeTo handles case insensitive hex")
    void testDecodeToCaseInsensitive() {
        byte[] dest = new byte[4];

        int written = Hex.decodeTo("0xAbCdEf01", 0, 10, dest, 0);

        assertEquals(4, written);
        assertArrayEquals(new byte[] {(byte) 0xAB, (byte) 0xCD, (byte) 0xEF, 0x01}, dest);
    }

    @Test
    @DisplayName("decodeTo handles uppercase 0X prefix")
    void testDecodeToUppercasePrefix() {
        byte[] dest = new byte[2];

        int written = Hex.decodeTo("0Xaabb", 0, 6, dest, 0);

        assertEquals(2, written);
        assertArrayEquals(new byte[] {(byte) 0xAA, (byte) 0xBB}, dest);
    }

    @Test
    @DisplayName("decodeTo works with CharSequence (StringBuilder)")
    void testDecodeToCharSequence() {
        byte[] dest = new byte[2];
        StringBuilder sb = new StringBuilder("0x1234");

        int written = Hex.decodeTo(sb, 0, sb.length(), dest, 0);

        assertEquals(2, written);
        assertArrayEquals(new byte[] {0x12, 0x34}, dest);
    }

    @Test
    @DisplayName("decodeTo throws on null hex")
    void testDecodeToNullHex() {
        byte[] dest = new byte[10];
        assertThrows(IllegalArgumentException.class, () -> Hex.decodeTo(null, 0, 0, dest, 0));
    }

    @Test
    @DisplayName("decodeTo throws on null dest")
    void testDecodeToNullDest() {
        assertThrows(IllegalArgumentException.class, () -> Hex.decodeTo("0x1234", 0, 6, null, 0));
    }

    @Test
    @DisplayName("decodeTo throws on negative hexOffset")
    void testDecodeToNegativeHexOffset() {
        byte[] dest = new byte[10];
        var ex = assertThrows(IllegalArgumentException.class, () -> Hex.decodeTo("0x1234", -1, 6, dest, 0));
        assertTrue(ex.getMessage().contains("hexOffset"));
    }

    @Test
    @DisplayName("decodeTo throws on negative hexLength")
    void testDecodeToNegativeHexLength() {
        byte[] dest = new byte[10];
        var ex = assertThrows(IllegalArgumentException.class, () -> Hex.decodeTo("0x1234", 0, -1, dest, 0));
        assertTrue(ex.getMessage().contains("hexLength"));
    }

    @Test
    @DisplayName("decodeTo throws on negative destOffset")
    void testDecodeToNegativeDestOffset() {
        byte[] dest = new byte[10];
        var ex = assertThrows(IllegalArgumentException.class, () -> Hex.decodeTo("0x1234", 0, 6, dest, -1));
        assertTrue(ex.getMessage().contains("destOffset"));
    }

    @Test
    @DisplayName("decodeTo throws on hex region out of bounds")
    void testDecodeToHexOutOfBounds() {
        byte[] dest = new byte[10];
        var ex = assertThrows(IllegalArgumentException.class, () -> Hex.decodeTo("0x12", 0, 10, dest, 0));
        assertTrue(ex.getMessage().contains("out of bounds"));
    }

    @Test
    @DisplayName("decodeTo throws on odd hex length")
    void testDecodeToOddLength() {
        byte[] dest = new byte[10];

        // "0x123" has 3 hex chars after prefix
        var ex = assertThrows(IllegalArgumentException.class, () -> Hex.decodeTo("0x123", 0, 5, dest, 0));
        assertTrue(ex.getMessage().contains("even length"));

        // "123" without prefix
        ex = assertThrows(IllegalArgumentException.class, () -> Hex.decodeTo("123", 0, 3, dest, 0));
        assertTrue(ex.getMessage().contains("even length"));
    }

    @Test
    @DisplayName("decodeTo throws when dest buffer too small")
    void testDecodeToDestTooSmall() {
        byte[] dest = new byte[1]; // needs 2 bytes

        var ex = assertThrows(IllegalArgumentException.class, () -> Hex.decodeTo("0x1234", 0, 6, dest, 0));
        assertTrue(ex.getMessage().contains("too small"));
    }

    @Test
    @DisplayName("decodeTo throws when dest offset leaves insufficient space")
    void testDecodeToDestOffsetTooLarge() {
        byte[] dest = new byte[3]; // 3 bytes total, but offset 2 leaves only 1 byte

        var ex = assertThrows(IllegalArgumentException.class, () -> Hex.decodeTo("0x1234", 0, 6, dest, 2));
        assertTrue(ex.getMessage().contains("too small"));
    }

    @Test
    @DisplayName("decodeTo throws on invalid hex characters")
    void testDecodeToInvalidHex() {
        byte[] dest = new byte[10];

        assertThrows(IllegalArgumentException.class, () -> Hex.decodeTo("0xzz", 0, 4, dest, 0));
        assertThrows(IllegalArgumentException.class, () -> Hex.decodeTo("0x1g", 0, 4, dest, 0));
        assertThrows(IllegalArgumentException.class, () -> Hex.decodeTo("0x 1", 0, 4, dest, 0)); // space
    }

    @Test
    @DisplayName("decodeTo round-trip with encodeTo")
    void testDecodeToRoundTrip() {
        byte[] original = new byte[] {0x00, 0x7F, (byte) 0x80, (byte) 0xFF};
        char[] encoded = new char[10]; // 2 prefix + 8 hex chars
        Hex.encodeTo(original, encoded, 0, true);

        byte[] decoded = new byte[4];
        int written = Hex.decodeTo(new String(encoded), 0, 10, decoded, 0);

        assertEquals(4, written);
        assertArrayEquals(original, decoded);
    }
}
