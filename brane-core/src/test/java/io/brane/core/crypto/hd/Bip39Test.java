package io.brane.core.crypto.hd;

import static org.junit.jupiter.api.Assertions.*;

import java.security.SecureRandom;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.brane.primitives.Hex;

/**
 * Tests for BIP-39 mnemonic generation, validation, and seed derivation.
 */
class Bip39Test {

    // Test vector from BIP-39 specification
    // https://github.com/trezor/python-mnemonic/blob/master/vectors.json
    private static final String VALID_12_WORD_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about";

    private static final String VALID_24_WORD_MNEMONIC =
            "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon " +
                    "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon art";

    @Test
    void testIsValidWithValid12WordMnemonic() {
        assertTrue(Bip39.isValid(VALID_12_WORD_MNEMONIC));
    }

    @Test
    void testIsValidWithValid24WordMnemonic() {
        assertTrue(Bip39.isValid(VALID_24_WORD_MNEMONIC));
    }

    @Test
    void testIsValidWithNullMnemonic() {
        assertFalse(Bip39.isValid(null));
    }

    @Test
    void testIsValidWithEmptyMnemonic() {
        assertFalse(Bip39.isValid(""));
    }

    @Test
    void testIsValidWithBlankMnemonic() {
        assertFalse(Bip39.isValid("   "));
    }

    @Test
    void testIsValidWithInvalidWordCount() {
        // 11 words - invalid
        assertFalse(Bip39.isValid(
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"));
    }

    @Test
    void testIsValidWithInvalidWord() {
        // "notaword" is not in the BIP-39 wordlist
        assertFalse(Bip39.isValid(
                "notaword abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"));
    }

    @Test
    void testIsValidWithInvalidChecksum() {
        // Valid words but invalid checksum (changed last word)
        assertFalse(Bip39.isValid(
                "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon"));
    }

    @Test
    void testIsValidWithExtraWhitespace() {
        // Extra whitespace should be handled
        assertTrue(Bip39.isValid(
                "abandon  abandon   abandon abandon abandon abandon abandon abandon abandon abandon abandon about"));
    }

    @Test
    void testIsValidWithLeadingTrailingWhitespace() {
        // Leading and trailing whitespace should be handled via trim()
        assertTrue(Bip39.isValid(
                "  abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about  "));
    }

    @ParameterizedTest
    @ValueSource(ints = {12, 15, 18, 21, 24})
    void testGenerateValidWordCounts(int wordCount) {
        SecureRandom random = new SecureRandom();
        String mnemonic = Bip39.generate(wordCount, random);

        assertNotNull(mnemonic);
        String[] words = mnemonic.split(" ");
        assertEquals(wordCount, words.length);
        assertTrue(Bip39.isValid(mnemonic), "Generated mnemonic should be valid");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 11, 13, 25, 100})
    void testGenerateInvalidWordCounts(int wordCount) {
        SecureRandom random = new SecureRandom();
        assertThrows(IllegalArgumentException.class, () -> Bip39.generate(wordCount, random));
    }

    @Test
    void testGenerateProducesDifferentMnemonics() {
        SecureRandom random = new SecureRandom();
        String mnemonic1 = Bip39.generate(12, random);
        String mnemonic2 = Bip39.generate(12, random);

        // With 128 bits of entropy, collision is astronomically unlikely
        assertNotEquals(mnemonic1, mnemonic2);
    }

    @Test
    void testToSeedWithEmptyPassphrase() {
        // BIP-39 test vector
        // Mnemonic: "abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about"
        // Passphrase: ""
        // Expected seed (first 32 bytes hex): 5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc19a5ac40b389cd370d086206dec8aa6c43daea6690f20ad3d8d48b2d2ce9e38e4
        byte[] seed = Bip39.toSeed(VALID_12_WORD_MNEMONIC, "");

        assertEquals(64, seed.length);
        String seedHex = Hex.encode(seed);
        assertEquals(
                "0x5eb00bbddcf069084889a8ab9155568165f5c453ccb85e70811aaed6f6da5fc19a5ac40b389cd370d086206dec8aa6c43daea6690f20ad3d8d48b2d2ce9e38e4",
                seedHex.toLowerCase());
    }

    @Test
    void testToSeedWithPassphrase() {
        // BIP-39 test vector with passphrase "TREZOR"
        byte[] seed = Bip39.toSeed(VALID_12_WORD_MNEMONIC, "TREZOR");

        assertEquals(64, seed.length);
        String seedHex = Hex.encode(seed);
        assertEquals(
                "0xc55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04",
                seedHex.toLowerCase());
    }

    @Test
    void testToSeedWithNullMnemonic() {
        assertThrows(IllegalArgumentException.class, () -> Bip39.toSeed(null, ""));
    }

    @Test
    void testToSeedWithNullPassphrase() {
        assertThrows(IllegalArgumentException.class, () -> Bip39.toSeed(VALID_12_WORD_MNEMONIC, null));
    }

    @Test
    void testToSeedDifferentPassphrasesDifferentSeeds() {
        byte[] seed1 = Bip39.toSeed(VALID_12_WORD_MNEMONIC, "passphrase1");
        byte[] seed2 = Bip39.toSeed(VALID_12_WORD_MNEMONIC, "passphrase2");

        assertFalse(java.util.Arrays.equals(seed1, seed2));
    }

    @Test
    void testToSeedWithUnicodePassphrase() {
        // NFKD normalization should handle Unicode properly
        // é (U+00E9) normalizes to e + combining acute accent (U+0065 U+0301)
        byte[] seed1 = Bip39.toSeed(VALID_12_WORD_MNEMONIC, "\u00E9"); // precomposed é
        byte[] seed2 = Bip39.toSeed(VALID_12_WORD_MNEMONIC, "e\u0301"); // decomposed é

        assertArrayEquals(seed1, seed2, "NFKD normalization should produce same seed");
    }

    @Test
    void testToSeedWith24WordMnemonic() {
        // BIP-39 test vector for 24-word "abandon...abandon art" mnemonic with passphrase "TREZOR"
        // Source: https://github.com/trezor/python-mnemonic/blob/master/vectors.json
        byte[] seed = Bip39.toSeed(VALID_24_WORD_MNEMONIC, "TREZOR");

        assertEquals(64, seed.length);
        String seedHex = Hex.encode(seed);
        assertEquals(
                "0xbda85446c68413707090a52022edd26a1c9462295029f2e60cd7c4f2bbd3097170af7a4d73245cafa9c3cca8d561a7c3de6f5d4a10be8ed2a5e608d68f92fcc8",
                seedHex.toLowerCase());
    }

    @Test
    void testIsValidWith15WordMnemonic() {
        // Generate and validate a 15-word mnemonic
        SecureRandom random = new SecureRandom();
        String mnemonic = Bip39.generate(15, random);
        assertTrue(Bip39.isValid(mnemonic));
    }

    @Test
    void testIsValidWith18WordMnemonic() {
        // Generate and validate an 18-word mnemonic
        SecureRandom random = new SecureRandom();
        String mnemonic = Bip39.generate(18, random);
        assertTrue(Bip39.isValid(mnemonic));
    }

    @Test
    void testIsValidWith21WordMnemonic() {
        // Generate and validate a 21-word mnemonic
        SecureRandom random = new SecureRandom();
        String mnemonic = Bip39.generate(21, random);
        assertTrue(Bip39.isValid(mnemonic));
    }

    @Test
    void testGenerateAllWordsInWordlist() {
        SecureRandom random = new SecureRandom();
        String mnemonic = Bip39.generate(12, random);
        String[] words = mnemonic.split(" ");

        for (String word : words) {
            assertTrue(EnglishWordlist.contains(word), "Word should be in wordlist: " + word);
        }
    }
}
