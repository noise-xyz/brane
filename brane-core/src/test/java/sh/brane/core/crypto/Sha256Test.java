// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.crypto;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

/**
 * Tests for Sha256 hashing against known test vectors.
 */
class Sha256Test {

    @Test
    void testEmptyString() {
        // Empty string hash is a well-known SHA-256 constant
        final byte[] hash = Sha256.hash(new byte[0]);

        final String expected = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";
        final String actual = bytesToHex(hash);

        assertEquals(expected, actual, "Empty string hash should match SHA-256 spec");
    }

    @Test
    void testHelloWorld() {
        final byte[] input = "hello world".getBytes(StandardCharsets.UTF_8);
        final byte[] hash = Sha256.hash(input);

        // Known SHA-256 hash
        final String expected = "b94d27b9934d3e08a52e52d7da7dabfac484efe37a5380ee9088f7ace2efcde9";
        final String actual = bytesToHex(hash);

        assertEquals(expected, actual);
    }

    @Test
    void testAbc() {
        // "abc" is a standard NIST test vector
        final byte[] input = "abc".getBytes(StandardCharsets.UTF_8);
        final byte[] hash = Sha256.hash(input);

        final String expected = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        final String actual = bytesToHex(hash);

        assertEquals(expected, actual, "SHA-256('abc') should match NIST test vector");
    }

    @Test
    void testMultipleInputs() {
        final byte[] part1 = "hello".getBytes(StandardCharsets.UTF_8);
        final byte[] part2 = " world".getBytes(StandardCharsets.UTF_8);

        final byte[] hashConcatenated = Sha256.hash(part1, part2);
        final byte[] hashSingle = Sha256.hash("hello world".getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(hashSingle, hashConcatenated, "Multi-part hash should equal single hash");
    }

    @Test
    void testHashLength() {
        final byte[] hash = Sha256.hash("test".getBytes(StandardCharsets.UTF_8));
        assertEquals(32, hash.length, "SHA-256 always produces 32 bytes");
    }

    @Test
    void testNullInput() {
        assertThrows(NullPointerException.class, () -> Sha256.hash((byte[]) null));
    }

    @Test
    void testNullInMultipleInputs() {
        final byte[] validInput = "test".getBytes(StandardCharsets.UTF_8);
        assertThrows(NullPointerException.class, () -> Sha256.hash(validInput, null));
    }

    @Test
    void testDeterministic() {
        final byte[] input = "deterministic test".getBytes(StandardCharsets.UTF_8);
        final byte[] hash1 = Sha256.hash(input);
        final byte[] hash2 = Sha256.hash(input);

        assertArrayEquals(hash1, hash2, "Same input must produce same hash");
    }

    @Test
    void testCleanupAllowsSubsequentHashing() {
        // Verify cleanup() removes ThreadLocal but allows subsequent hashing
        final byte[] input = "cleanup test".getBytes(StandardCharsets.UTF_8);

        // Hash before cleanup
        final byte[] hashBefore = Sha256.hash(input);

        // Cleanup should not throw
        assertDoesNotThrow(Sha256::cleanup);

        // Hash after cleanup should work and produce same result
        final byte[] hashAfter = Sha256.hash(input);
        assertArrayEquals(hashBefore, hashAfter, "Hash should be same after cleanup");
    }

    @Test
    void testCleanupIdempotent() {
        // Cleanup should be safe to call multiple times
        assertDoesNotThrow(() -> {
            Sha256.cleanup();
            Sha256.cleanup();
            Sha256.cleanup();
        });
    }

    @Test
    void testCleanupWithoutPriorHash() {
        // Cleanup should be safe even without prior hash call
        // Note: This test may run after other tests, so we just verify it doesn't throw
        assertDoesNotThrow(Sha256::cleanup);
    }

    // Helper method

    private static String bytesToHex(byte[] bytes) {
        final StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
