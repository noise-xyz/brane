package io.brane.core.crypto;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for Keccak256 hashing against known Ethereum test vectors.
 */
class Keccak256Test {

    @Test
    void testEmptyString() {
        // Empty string hash is a well-known Ethereum constant
        final byte[] hash = Keccak256.hash(new byte[0]);

        final String expected = "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470";
        final String actual = bytesToHex(hash);

        assertEquals(expected, actual, "Empty string hash should match Ethereum spec");
    }

    @Test
    void testHelloWorld() {
        final byte[] input = "hello world".getBytes(StandardCharsets.UTF_8);
        final byte[] hash = Keccak256.hash(input);

        // Known hash from Ethereum tooling
        final String expected = "47173285a8d7341e5e972fc677286384f802f8ef42a5ec5f03bbfa254cb01fad";
        final String actual = bytesToHex(hash);

        assertEquals(expected, actual);
    }

    @Test
    void testEthereumAddressDerivation() {
        // Testing the hash used in address derivation
        // Public key (uncompressed, without 0x04 prefix):
        // x: 0x79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798
        // y: 0x483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8

        final byte[] pubKeyNoPrefix = hexToBytes(
                "79be667ef9dcbbac55a06295ce870b07029bfcdb2dce28d959f2815b16f81798" +
                        "483ada7726a3c4655da4fbfc0e1108a8fd17b448a68554199c47d08ffb10d4b8");
        final byte[] hash = Keccak256.hash(pubKeyNoPrefix);

        // Last 20 bytes should give us a valid address
        assertNotNull(hash);
        assertEquals(32, hash.length);
    }

    @Test
    void testMultipleInputs() {
        final byte[] part1 = "hello".getBytes(StandardCharsets.UTF_8);
        final byte[] part2 = " world".getBytes(StandardCharsets.UTF_8);

        final byte[] hashConcatenated = Keccak256.hash(part1, part2);
        final byte[] hashSingle = Keccak256.hash("hello world".getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(hashSingle, hashConcatenated, "Multi-part hash should equal single hash");
    }

    @Test
    void testHashLength() {
        final byte[] hash = Keccak256.hash("test".getBytes(StandardCharsets.UTF_8));
        assertEquals(32, hash.length, "Keccak256 always produces 32 bytes");
    }

    @Test
    void testNullInput() {
        assertThrows(NullPointerException.class, () -> Keccak256.hash((byte[]) null));
    }

    @Test
    void testNullInMultipleInputs() {
        final byte[] validInput = "test".getBytes(StandardCharsets.UTF_8);
        assertThrows(NullPointerException.class, () -> Keccak256.hash(validInput, null));
    }

    @Test
    void testDeterministic() {
        final byte[] input = "deterministic test".getBytes(StandardCharsets.UTF_8);
        final byte[] hash1 = Keccak256.hash(input);
        final byte[] hash2 = Keccak256.hash(input);

        assertArrayEquals(hash1, hash2, "Same input must produce same hash");
    }

    // Helper methods

    private static String bytesToHex(byte[] bytes) {
        final StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private static byte[] hexToBytes(String hex) {
        final int len = hex.length();
        final byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
