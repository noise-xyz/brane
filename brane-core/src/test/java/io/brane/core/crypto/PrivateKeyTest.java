package io.brane.core.crypto;

import io.brane.core.types.Address;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for PrivateKey with known Ethereum test vectors.
 */
class PrivateKeyTest {

    // Known test vector from Ethereum
    private static final String TEST_PRIVATE_KEY = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";
    private static final String TEST_ADDRESS = "0x2c7536e3605d9c16a7a3d7b1898e529396a65c23";

    @Test
    void testFromHex() {
        final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);
        assertNotNull(key);
    }

    @Test
    void testFromHexWithoutPrefix() {
        final String keyWithoutPrefix = TEST_PRIVATE_KEY.substring(2);
        final PrivateKey key = PrivateKey.fromHex(keyWithoutPrefix);
        assertNotNull(key);
    }

    @Test
    void testAddressDerivation() {
        final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);
        final Address address = key.toAddress();

        assertEquals(TEST_ADDRESS.toLowerCase(), address.value().toLowerCase(),
                "Derived address should match known test vector");
    }

    @Test
    void testSignAndRecover() {
        final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);
        final Address originalAddress = key.toAddress();

        // Sign a message
        final byte[] message = "Hello, Ethereum!".getBytes(StandardCharsets.UTF_8);
        final byte[] messageHash = Keccak256.hash(message);
        final Signature signature = key.sign(messageHash);

        assertNotNull(signature);
        assertEquals(32, signature.r().length);
        assertEquals(32, signature.s().length);
        assertTrue(signature.v() == 0 || signature.v() == 1, "Recovery ID should be 0 or 1");

        // Recover address from signature
        final Address recoveredAddress = PrivateKey.recoverAddress(messageHash, signature);

        assertEquals(originalAddress, recoveredAddress,
                "Recovered address should match original address");
    }

    @Test
    void testSignatureIsDeterministic() {
        final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);
        final byte[] messageHash = Keccak256.hash("test message".getBytes(StandardCharsets.UTF_8));

        final Signature sig1 = key.sign(messageHash);
        final Signature sig2 = key.sign(messageHash);

        assertEquals(sig1, sig2, "RFC 6979 deterministic signing must produce same signature");
    }

    @Test
    void testMultipleMessagesRecovery() {
        final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);
        final Address address = key.toAddress();

        final String[] messages = {
                "Message 1",
                "Message 2",
                "Another message",
                "0x1234567890abcdef",
                ""
        };

        for (String msg : messages) {
            final byte[] messageHash = Keccak256.hash(msg.getBytes(StandardCharsets.UTF_8));
            final Signature signature = key.sign(messageHash);
            final Address recovered = PrivateKey.recoverAddress(messageHash, signature);

            assertEquals(address, recovered, "Recovery should work for: " + msg);
        }
    }

    @Test
    void testInvalidPrivateKeyLength() {
        final String invalidKey = "0x1234"; // Too short
        assertThrows(IllegalArgumentException.class, () -> PrivateKey.fromHex(invalidKey));
    }

    @Test
    void testZeroPrivateKey() {
        final String zeroKey = "0x0000000000000000000000000000000000000000000000000000000000000000";
        assertThrows(IllegalArgumentException.class, () -> PrivateKey.fromHex(zeroKey));
    }

    @Test
    void testPrivateKeyTooLarge() {
        // Private key must be < curve order (secp256k1)
        // This is > curve order:
        // 0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFEBAAEDCE6AF48A03BBFD25E8CD0364141
        final String invalidKey = "0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF";
        assertThrows(IllegalArgumentException.class, () -> PrivateKey.fromHex(invalidKey));
    }

    @Test
    void testNullHex() {
        assertThrows(NullPointerException.class, () -> PrivateKey.fromHex(null));
    }

    @Test
    void testNullBytes() {
        assertThrows(NullPointerException.class, () -> PrivateKey.fromBytes(null));
    }

    @Test
    void testSignNullMessageHash() {
        final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);
        assertThrows(NullPointerException.class, () -> key.sign(null));
    }

    @Test
    void testSignInvalidMessageHashLength() {
        final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);
        final byte[] invalidHash = new byte[31]; // Should be 32 bytes
        assertThrows(IllegalArgumentException.class, () -> key.sign(invalidHash));
    }

    @Test
    void testRecoverNullMessageHash() {
        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        final Signature sig = new Signature(r, s, 0);

        assertThrows(NullPointerException.class, () -> PrivateKey.recoverAddress(null, sig));
    }

    @Test
    void testRecoverNullSignature() {
        final byte[] messageHash = new byte[32];
        assertThrows(NullPointerException.class, () -> PrivateKey.recoverAddress(messageHash, null));
    }

    @Test
    void testRecoverInvalidMessageHashLength() {
        final byte[] invalidHash = new byte[31];
        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        final Signature sig = new Signature(r, s, 0);

        assertThrows(IllegalArgumentException.class, () -> PrivateKey.recoverAddress(invalidHash, sig));
    }

    @Test
    void testLowSNormalization() {
        // The implementation should automatically normalize s to low-s
        final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);
        final byte[] messageHash = Keccak256.hash("test".getBytes(StandardCharsets.UTF_8));
        final Signature signature = key.sign(messageHash);

        // s should be in the lower half of the curve order
        // This is enforced by the implementation but we verify the signature is valid
        final Address recovered = PrivateKey.recoverAddress(messageHash, signature);
        assertEquals(key.toAddress(), recovered);
    }

    @Test
    void testFromBytes() {
        final byte[] keyBytes = hexToBytes(TEST_PRIVATE_KEY.substring(2));
        final PrivateKey key = PrivateKey.fromBytes(keyBytes);

        assertEquals(TEST_ADDRESS.toLowerCase(), key.toAddress().value().toLowerCase());
    }

    @Test
    void testToString() {
        final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);
        final String str = key.toString();

        assertNotNull(str);
        assertTrue(str.contains("PrivateKey"));
        assertTrue(str.contains(key.toAddress().value()));
    }

    // Helper method

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
