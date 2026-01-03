package io.brane.core.crypto;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Address;

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
    void testFromBytesZerosInputArray() {
        // Security behavior: fromBytes zeros the input array after use
        final byte[] keyBytes = hexToBytes(TEST_PRIVATE_KEY.substring(2));

        // Verify the bytes are non-zero before creating key
        boolean hasNonZero = false;
        for (byte b : keyBytes) {
            if (b != 0) {
                hasNonZero = true;
                break;
            }
        }
        assertTrue(hasNonZero, "Test key should have non-zero bytes");

        // Create the key
        final PrivateKey key = PrivateKey.fromBytes(keyBytes);
        assertNotNull(key);

        // Verify the input array has been zeroed
        for (byte b : keyBytes) {
            assertEquals(0, b, "Input array should be zeroed after fromBytes()");
        }
    }

    @Test
    void testToString() {
        final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);
        final String str = key.toString();

        assertNotNull(str);
        assertTrue(str.contains("PrivateKey"));
        assertTrue(str.contains(key.toAddress().value()));
    }

    @Test
    void testDestroyable() {
        final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);

        // Key should not be destroyed initially
        assertFalse(key.isDestroyed());

        // Destroy the key
        key.destroy();

        // Key should now be destroyed
        assertTrue(key.isDestroyed());
    }

    @Test
    void testDestroyedKeyThrowsOnToAddress() {
        final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);
        key.destroy();

        assertThrows(IllegalStateException.class, key::toAddress);
    }

    @Test
    void testDestroyedKeyThrowsOnSign() {
        final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);
        key.destroy();

        final byte[] messageHash = Keccak256.hash("test".getBytes(StandardCharsets.UTF_8));
        assertThrows(IllegalStateException.class, () -> key.sign(messageHash));
    }

    @Test
    void testDestroyedKeyToString() {
        final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);
        key.destroy();

        assertEquals("PrivateKey[destroyed]", key.toString());
    }

    @Test
    void testDestroyIsIdempotent() {
        final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);

        // Multiple destroy calls should be safe
        key.destroy();
        key.destroy();
        key.destroy();

        assertTrue(key.isDestroyed());
    }

    @Test
    void testConcurrentSignAndDestroy() throws InterruptedException {
        // Test that concurrent sign and destroy operations don't cause NPE
        // Either the sign completes successfully, or it throws IllegalStateException
        final int iterations = 100;

        for (int i = 0; i < iterations; i++) {
            final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);
            final byte[] messageHash = Keccak256.hash("test".getBytes(StandardCharsets.UTF_8));

            final CountDownLatch startLatch = new CountDownLatch(1);
            final AtomicInteger successCount = new AtomicInteger(0);
            final AtomicInteger destroyedCount = new AtomicInteger(0);
            final AtomicInteger unexpectedErrorCount = new AtomicInteger(0);

            final ExecutorService executor = Executors.newFixedThreadPool(2);

            // Thread 1: Try to sign
            executor.submit(() -> {
                try {
                    startLatch.await();
                    key.sign(messageHash);
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    // Expected if key was destroyed
                    destroyedCount.incrementAndGet();
                } catch (Exception e) {
                    // Unexpected - would indicate race condition (NPE, etc.)
                    unexpectedErrorCount.incrementAndGet();
                }
            });

            // Thread 2: Destroy the key
            executor.submit(() -> {
                try {
                    startLatch.await();
                    key.destroy();
                } catch (Exception e) {
                    unexpectedErrorCount.incrementAndGet();
                }
            });

            // Start both threads simultaneously
            startLatch.countDown();

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            // Should never have unexpected errors (like NPE from race condition)
            assertEquals(0, unexpectedErrorCount.get(),
                    "No unexpected errors should occur during concurrent sign/destroy");

            // Either sign succeeded or was rejected because key was destroyed
            assertEquals(1, successCount.get() + destroyedCount.get(),
                    "Sign should either succeed or throw IllegalStateException");
        }
    }

    @Test
    void testConcurrentToAddressAndDestroy() throws InterruptedException {
        // Test that concurrent toAddress and destroy operations don't cause NPE
        final int iterations = 100;

        for (int i = 0; i < iterations; i++) {
            final PrivateKey key = PrivateKey.fromHex(TEST_PRIVATE_KEY);

            final CountDownLatch startLatch = new CountDownLatch(1);
            final AtomicInteger successCount = new AtomicInteger(0);
            final AtomicInteger destroyedCount = new AtomicInteger(0);
            final AtomicInteger unexpectedErrorCount = new AtomicInteger(0);

            final ExecutorService executor = Executors.newFixedThreadPool(2);

            // Thread 1: Try to get address
            executor.submit(() -> {
                try {
                    startLatch.await();
                    key.toAddress();
                    successCount.incrementAndGet();
                } catch (IllegalStateException e) {
                    // Expected if key was destroyed
                    destroyedCount.incrementAndGet();
                } catch (Exception e) {
                    // Unexpected - would indicate race condition (NPE, etc.)
                    unexpectedErrorCount.incrementAndGet();
                }
            });

            // Thread 2: Destroy the key
            executor.submit(() -> {
                try {
                    startLatch.await();
                    key.destroy();
                } catch (Exception e) {
                    unexpectedErrorCount.incrementAndGet();
                }
            });

            // Start both threads simultaneously
            startLatch.countDown();

            executor.shutdown();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

            // Should never have unexpected errors (like NPE from race condition)
            assertEquals(0, unexpectedErrorCount.get(),
                    "No unexpected errors should occur during concurrent toAddress/destroy");

            // Either toAddress succeeded or was rejected because key was destroyed
            assertEquals(1, successCount.get() + destroyedCount.get(),
                    "toAddress should either succeed or throw IllegalStateException");
        }
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
