package io.brane.core.crypto.hd;

import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Arrays;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * BIP-39 mnemonic seed phrase generation and validation.
 *
 * <p>
 * This class provides methods for generating, validating, and deriving seeds from
 * BIP-39 mnemonic phrases using the English wordlist.
 *
 * @see <a href="https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki">BIP-39</a>
 */
final class Bip39 {

    private static final int PBKDF2_ITERATIONS = 2048;
    private static final int SEED_LENGTH_BYTES = 64;
    private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA512";
    private static final String SALT_PREFIX = "mnemonic";

    private Bip39() {
        // Utility class
    }

    /**
     * Validates a mnemonic phrase.
     *
     * <p>
     * A valid mnemonic must:
     * <ul>
     * <li>Have a valid word count (12, 15, 18, 21, or 24 words)</li>
     * <li>Contain only words from the BIP-39 English wordlist</li>
     * <li>Have a valid checksum</li>
     * </ul>
     *
     * @param mnemonic the mnemonic phrase (space-separated words)
     * @return true if the mnemonic is valid, false otherwise
     */
    static boolean isValid(String mnemonic) {
        if (mnemonic == null || mnemonic.isBlank()) {
            return false;
        }

        String normalized = normalizeNfkd(mnemonic);
        String[] words = normalized.split("\\s+");

        // Valid word counts: 12, 15, 18, 21, 24
        int wordCount = words.length;
        if (wordCount != 12 && wordCount != 15 && wordCount != 18 && wordCount != 21 && wordCount != 24) {
            return false;
        }

        // Check all words are in the wordlist
        for (String word : words) {
            if (!EnglishWordlist.contains(word)) {
                return false;
            }
        }

        // Verify checksum
        return verifyChecksum(words);
    }

    /**
     * Generates a new mnemonic phrase with the specified word count.
     *
     * @param wordCount the number of words (12, 15, 18, 21, or 24)
     * @param random    the source of randomness
     * @return a new mnemonic phrase
     * @throws IllegalArgumentException if wordCount is not valid
     */
    static String generate(int wordCount, SecureRandom random) {
        if (wordCount != 12 && wordCount != 15 && wordCount != 18 && wordCount != 21 && wordCount != 24) {
            throw new IllegalArgumentException(
                    "Word count must be 12, 15, 18, 21, or 24, got " + wordCount);
        }

        // Calculate entropy bits: wordCount * 11 bits = entropy + checksum
        // checksum = entropy / 32
        // So: wordCount * 11 = entropy + entropy/32 = 33*entropy/32
        // entropy = wordCount * 11 * 32 / 33
        int entropyBits = wordCount * 11 * 32 / 33;
        int entropyBytes = entropyBits / 8;

        byte[] entropy = new byte[entropyBytes];
        random.nextBytes(entropy);

        try {
            return entropyToMnemonic(entropy);
        } finally {
            Arrays.fill(entropy, (byte) 0);
        }
    }

    /**
     * Derives a 64-byte seed from a mnemonic phrase and passphrase using PBKDF2-HMAC-SHA512.
     *
     * <p>
     * Both the mnemonic and passphrase are NFKD-normalized before processing, as required
     * by BIP-39.
     *
     * @param mnemonic   the mnemonic phrase
     * @param passphrase the passphrase (can be empty string, but not null)
     * @return 64-byte seed suitable for HD key derivation
     * @throws IllegalArgumentException if mnemonic is null or passphrase is null
     */
    static byte[] toSeed(String mnemonic, String passphrase) {
        if (mnemonic == null) {
            throw new IllegalArgumentException("Mnemonic cannot be null");
        }
        if (passphrase == null) {
            throw new IllegalArgumentException("Passphrase cannot be null");
        }

        // NFKD normalization as required by BIP-39
        String normalizedMnemonic = normalizeNfkd(mnemonic);
        String normalizedPassphrase = normalizeNfkd(passphrase);

        // Salt is "mnemonic" + passphrase
        String salt = SALT_PREFIX + normalizedPassphrase;

        try {
            var spec = new PBEKeySpec(
                    normalizedMnemonic.toCharArray(),
                    salt.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                    PBKDF2_ITERATIONS,
                    SEED_LENGTH_BYTES * 8);

            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            byte[] seed = factory.generateSecret(spec).getEncoded();
            spec.clearPassword();
            return seed;
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("PBKDF2 derivation failed", e);
        }
    }

    /**
     * Converts entropy bytes to a mnemonic phrase.
     */
    private static String entropyToMnemonic(byte[] entropy) {
        int entropyBits = entropy.length * 8;
        int checksumBits = entropyBits / 32;
        int totalBits = entropyBits + checksumBits;
        int wordCount = totalBits / 11;

        // Calculate SHA-256 checksum
        byte[] hash = sha256(entropy);

        // Combine entropy and checksum into a bit array
        boolean[] bits = new boolean[totalBits];

        // Add entropy bits
        for (int i = 0; i < entropyBits; i++) {
            bits[i] = (entropy[i / 8] & (1 << (7 - (i % 8)))) != 0;
        }

        // Add checksum bits
        for (int i = 0; i < checksumBits; i++) {
            bits[entropyBits + i] = (hash[i / 8] & (1 << (7 - (i % 8)))) != 0;
        }

        // Convert to words
        var words = new StringBuilder();
        for (int i = 0; i < wordCount; i++) {
            int index = 0;
            for (int j = 0; j < 11; j++) {
                if (bits[i * 11 + j]) {
                    index |= (1 << (10 - j));
                }
            }
            if (i > 0) {
                words.append(' ');
            }
            words.append(EnglishWordlist.getWord(index));
        }

        return words.toString();
    }

    /**
     * Verifies the checksum of a mnemonic.
     */
    private static boolean verifyChecksum(String[] words) {
        int wordCount = words.length;
        int totalBits = wordCount * 11;
        int checksumBits = wordCount / 3;
        int entropyBits = totalBits - checksumBits;
        int entropyBytes = entropyBits / 8;

        // Convert words to bits
        boolean[] bits = new boolean[totalBits];
        for (int i = 0; i < wordCount; i++) {
            int index = EnglishWordlist.getIndex(words[i]);
            for (int j = 0; j < 11; j++) {
                bits[i * 11 + j] = (index & (1 << (10 - j))) != 0;
            }
        }

        // Extract entropy
        byte[] entropy = new byte[entropyBytes];
        for (int i = 0; i < entropyBits; i++) {
            if (bits[i]) {
                entropy[i / 8] |= (1 << (7 - (i % 8)));
            }
        }

        // Calculate expected checksum
        byte[] hash = sha256(entropy);

        // Compare checksums
        for (int i = 0; i < checksumBits; i++) {
            boolean expectedBit = (hash[i / 8] & (1 << (7 - (i % 8)))) != 0;
            if (bits[entropyBits + i] != expectedBit) {
                return false;
            }
        }

        return true;
    }

    /**
     * Computes SHA-256 hash of the input.
     */
    private static byte[] sha256(byte[] input) {
        try {
            return java.security.MessageDigest.getInstance("SHA-256").digest(input);
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    /**
     * Applies NFKD normalization to a string.
     */
    private static String normalizeNfkd(String input) {
        return Normalizer.normalize(input, Normalizer.Form.NFKD);
    }
}
