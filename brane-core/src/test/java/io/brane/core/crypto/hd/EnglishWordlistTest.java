package io.brane.core.crypto.hd;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for BIP-39 English wordlist.
 */
class EnglishWordlistTest {

    @Test
    void testGetFirstWord() {
        assertEquals("abandon", EnglishWordlist.getWord(0));
    }

    @Test
    void testGetLastWord() {
        assertEquals("zoo", EnglishWordlist.getWord(2047));
    }

    @Test
    void testGetWordAtKnownIndices() {
        // Verify some well-known words at specific indices
        assertEquals("ability", EnglishWordlist.getWord(1));
        assertEquals("able", EnglishWordlist.getWord(2));
        assertEquals("zero", EnglishWordlist.getWord(2045));
        assertEquals("zone", EnglishWordlist.getWord(2046));
    }

    @Test
    void testGetIndexOfFirstWord() {
        assertEquals(0, EnglishWordlist.getIndex("abandon"));
    }

    @Test
    void testGetIndexOfLastWord() {
        assertEquals(2047, EnglishWordlist.getIndex("zoo"));
    }

    @Test
    void testGetIndexOfKnownWords() {
        assertEquals(1, EnglishWordlist.getIndex("ability"));
        assertEquals(2, EnglishWordlist.getIndex("able"));
        assertEquals(2045, EnglishWordlist.getIndex("zero"));
        assertEquals(2046, EnglishWordlist.getIndex("zone"));
    }

    @Test
    void testRoundTrip() {
        // Test that getWord and getIndex are inverses
        for (int i = 0; i < 2048; i++) {
            String word = EnglishWordlist.getWord(i);
            assertEquals(i, EnglishWordlist.getIndex(word), "Round trip failed for index " + i);
        }
    }

    @Test
    void testGetWordNegativeIndex() {
        assertThrows(IndexOutOfBoundsException.class, () -> EnglishWordlist.getWord(-1));
    }

    @Test
    void testGetWordIndexTooLarge() {
        assertThrows(IndexOutOfBoundsException.class, () -> EnglishWordlist.getWord(2048));
    }

    @Test
    void testGetIndexUnknownWord() {
        assertThrows(IllegalArgumentException.class, () -> EnglishWordlist.getIndex("notaword"));
    }

    @Test
    void testGetIndexEmptyString() {
        assertThrows(IllegalArgumentException.class, () -> EnglishWordlist.getIndex(""));
    }

    @Test
    void testWordsAreLowercase() {
        // BIP-39 specifies words should be lowercase
        for (int i = 0; i < 2048; i++) {
            String word = EnglishWordlist.getWord(i);
            assertEquals(word.toLowerCase(), word, "Word at index " + i + " should be lowercase");
        }
    }

    @Test
    void testWordsHaveNoWhitespace() {
        for (int i = 0; i < 2048; i++) {
            String word = EnglishWordlist.getWord(i);
            assertEquals(word.trim(), word, "Word at index " + i + " should have no leading/trailing whitespace");
            assertFalse(word.contains(" "), "Word at index " + i + " should contain no spaces");
        }
    }
}
