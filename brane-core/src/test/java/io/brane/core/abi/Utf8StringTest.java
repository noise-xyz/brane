package io.brane.core.abi;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link Utf8String}, focusing on UTF-8 byte length calculation.
 */
class Utf8StringTest {

    @Test
    void testAsciiString() {
        Utf8String str = new Utf8String("hello");
        // "hello" = 5 ASCII bytes, padded to 32 = 32 + 5 + 27 = 64 bytes
        assertEquals(64, str.contentByteSize());
    }

    @Test
    void testEmptyString() {
        Utf8String str = new Utf8String("");
        // Empty string: 32 (length) + 0 (data) + 0 (padding) = 32 bytes
        assertEquals(32, str.contentByteSize());
    }

    @Test
    void testTwoByteCJK() {
        // Characters in range 0x80-0x7FF encode to 2 bytes
        Utf8String str = new Utf8String("\u00E9"); // é (U+00E9)
        // 2 bytes, padded to 32 = 32 + 2 + 30 = 64
        assertEquals(64, str.contentByteSize());
    }

    @Test
    void testThreeByteBMP() {
        // Characters in BMP (0x800-0xFFFF) encode to 3 bytes
        Utf8String str = new Utf8String("\u4E2D"); // 中 (U+4E2D)
        // 3 bytes, padded to 32 = 32 + 3 + 29 = 64
        assertEquals(64, str.contentByteSize());
    }

    @Test
    void testSurrogatePairEmoji() {
        // Emoji like 😀 (U+1F600) requires surrogate pair, encodes to 4 UTF-8 bytes
        Utf8String str = new Utf8String("\uD83D\uDE00"); // 😀
        // 4 bytes, padded to 32 = 32 + 4 + 28 = 64
        assertEquals(64, str.contentByteSize());

        // Verify against actual UTF-8 encoding
        byte[] actual = "\uD83D\uDE00".getBytes(StandardCharsets.UTF_8);
        assertEquals(4, actual.length);
    }

    @Test
    void testMultipleSurrogatePairs() {
        // Multiple emoji: 😀😁😂 = 3 * 4 = 12 bytes
        Utf8String str = new Utf8String("\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02");
        // 12 bytes, padded to 32 = 32 + 12 + 20 = 64
        assertEquals(64, str.contentByteSize());
    }

    @Test
    void testLoneHighSurrogate() {
        // A lone high surrogate (not followed by low surrogate) should be treated as 3-byte char
        // This is malformed but we handle it gracefully
        String loneHigh = "\uD83D"; // High surrogate without low surrogate
        Utf8String str = new Utf8String(loneHigh);
        // 3 bytes (treated as BMP), padded to 32 = 32 + 3 + 29 = 64
        assertEquals(64, str.contentByteSize());
    }

    @Test
    void testLoneLowSurrogate() {
        // A lone low surrogate should be treated as 3-byte char
        String loneLow = "\uDE00"; // Low surrogate without high surrogate
        Utf8String str = new Utf8String(loneLow);
        // 3 bytes (treated as BMP), padded to 32 = 32 + 3 + 29 = 64
        assertEquals(64, str.contentByteSize());
    }

    @Test
    void testHighSurrogateAtEndOfString() {
        // High surrogate at end of string (no following char) should be 3 bytes
        String str = "abc\uD83D"; // "abc" + lone high surrogate
        Utf8String utf8 = new Utf8String(str);
        // 3 (abc) + 3 (lone surrogate) = 6 bytes, padded to 32 = 32 + 6 + 26 = 64
        assertEquals(64, utf8.contentByteSize());
    }

    @Test
    void testHighSurrogateFollowedByNonSurrogate() {
        // High surrogate followed by non-surrogate char should treat surrogate as 3 bytes
        String str = "\uD83Da"; // High surrogate + 'a'
        Utf8String utf8 = new Utf8String(str);
        // 3 (lone surrogate) + 1 ('a') = 4 bytes, padded to 32 = 32 + 4 + 28 = 64
        assertEquals(64, utf8.contentByteSize());
    }

    @Test
    void testMixedContent() {
        // Mix of ASCII, BMP, and surrogate pairs
        String str = "Hi \u4E2D\uD83D\uDE00!"; // "Hi 中😀!"
        Utf8String utf8 = new Utf8String(str);
        // H=1, i=1, space=1, 中=3, 😀=4, !=1 = 11 bytes
        // padded to 32 = 32 + 11 + 21 = 64
        assertEquals(64, utf8.contentByteSize());

        // Verify against actual UTF-8 encoding
        byte[] actual = str.getBytes(StandardCharsets.UTF_8);
        assertEquals(11, actual.length);
    }

    @Test
    void testContentByteSizeMatchesActualEncoding() {
        // Comprehensive test comparing our calculation with actual UTF-8 encoding
        String[] testStrings = {
                "",
                "hello",
                "Hello, 世界!",
                "\uD83D\uDE00\uD83D\uDE01\uD83D\uDE02", // 😀😁😂
                "abc\u00E9\u4E2D\uD83D\uDE00xyz", // Mixed
                "a".repeat(32), // Exactly 32 bytes
                "a".repeat(33), // 33 bytes (needs padding)
        };

        for (String test : testStrings) {
            Utf8String utf8 = new Utf8String(test);
            byte[] actualBytes = test.getBytes(StandardCharsets.UTF_8);
            int actualLen = actualBytes.length;
            int remainder = actualLen % 32;
            int padding = remainder == 0 ? 0 : 32 - remainder;
            int expected = 32 + actualLen + padding;

            assertEquals(expected, utf8.contentByteSize(),
                    "contentByteSize mismatch for: " + test);
        }
    }

    @Test
    void testNullValueThrows() {
        assertThrows(NullPointerException.class, () -> new Utf8String(null));
    }

    @Test
    void testTypeName() {
        assertEquals("string", new Utf8String("test").typeName());
    }

    @Test
    void testIsDynamic() {
        assertTrue(new Utf8String("test").isDynamic());
    }

    @Test
    void testByteSize() {
        // byteSize is always 32 (the offset pointer size)
        assertEquals(32, new Utf8String("test").byteSize());
        assertEquals(32, new Utf8String("").byteSize());
    }
}
