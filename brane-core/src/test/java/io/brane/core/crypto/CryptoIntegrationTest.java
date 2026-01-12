// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.crypto;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Address;

/**
 * Integration test demonstrating complete crypto primitive usage.
 */
class CryptoIntegrationTest {

    @Test
    void testCompleteSignAndVerifyFlow() {
        // Create private key
        final PrivateKey privateKey = PrivateKey.fromHex(
                "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318");

        // Get address
        final Address address = privateKey.toAddress();
        assertEquals("0x2c7536e3605d9c16a7a3d7b1898e529396a65c23", address.value());

        // Create message and hash it
        final String message = "Welcome to Ethereum!";
        final byte[] messageBytes = message.getBytes(StandardCharsets.UTF_8);
        final byte[] messageHash = Keccak256.hash(messageBytes);

        // Sign
        final Signature signature = privateKey.sign(messageHash);

        // Verify signature properties
        assertNotNull(signature);
        assertEquals(32, signature.r().length);
        assertEquals(32, signature.s().length);
        assertTrue(signature.v() == 0 || signature.v() == 1);
        assertFalse(signature.isEip155(), "Simple signature should not be EIP-155");

        // Recover address from signature
        final Address recoveredAddress = PrivateKey.recoverAddress(messageHash, signature);

        // Verify it matches
        assertEquals(address, recoveredAddress);
    }

    @Test
    void testMultipleKeysAndMessages() {
        // Test with 3 different keys and messages
        final String[] privateKeys = {
                "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318",
                "0x1234567890123456789012345678901234567890123456789012345678901234",
                "0xabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcdefabcd"
        };

        final String[] messages = {
                "Message 1",
                "Different message",
                ""
        };

        for (String keyHex : privateKeys) {
            final PrivateKey key = PrivateKey.fromHex(keyHex);
            final Address expectedAddress = key.toAddress();

            for (String msg : messages) {
                final byte[] hash = Keccak256.hash(msg.getBytes(StandardCharsets.UTF_8));
                final Signature sig = key.sign(hash);
                final Address recovered = PrivateKey.recoverAddress(hash, sig);

                assertEquals(expectedAddress, recovered,
                        "Recovery failed for key=" + keyHex + ", msg=" + msg);
            }
        }
    }

    @Test
    void testEip155SignatureEncoding() {
        final PrivateKey key = PrivateKey.fromHex(
                "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318");

        final byte[] messageHash = Keccak256.hash("test".getBytes(StandardCharsets.UTF_8));
        final Signature baseSig = key.sign(messageHash);

        // Simulate EIP-155 encoding for mainnet (chainId=1)
        final long chainId = 1;
        final int eip155V = (int) (chainId * 2 + 35 + baseSig.v());

        final Signature eip155Sig = new Signature(baseSig.r(), baseSig.s(), eip155V);

        assertTrue(eip155Sig.isEip155());
        assertEquals(baseSig.v(), eip155Sig.getRecoveryId(chainId));
    }

    @Test
    void testDeterministicSigning() {
        final PrivateKey key = PrivateKey.fromHex(
                "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318");

        final byte[] messageHash = Keccak256.hash("deterministic test".getBytes(StandardCharsets.UTF_8));

        // Sign same message 10 times
        Signature firstSig = null;
        for (int i = 0; i < 10; i++) {
            final Signature sig = key.sign(messageHash);
            if (firstSig == null) {
                firstSig = sig;
            } else {
                assertEquals(firstSig, sig, "RFC 6979 deterministic signing must be consistent");
            }
        }
    }

    @Test
    void testKeccak256Consistency() {
        final String input = "test data";
        final byte[] bytes = input.getBytes(StandardCharsets.UTF_8);

        // Hash multiple times
        final byte[] hash1 = Keccak256.hash(bytes);
        final byte[] hash2 = Keccak256.hash(bytes);
        final byte[] hash3 = Keccak256.hash(input.getBytes(StandardCharsets.UTF_8));

        assertArrayEquals(hash1, hash2);
        assertArrayEquals(hash2, hash3);
    }

    @Test
    void testAddressDerivationIsConsistent() {
        final PrivateKey key = PrivateKey.fromHex(
                "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318");

        // Derive address multiple times
        final Address addr1 = key.toAddress();
        final Address addr2 = key.toAddress();
        final Address addr3 = key.toAddress();

        assertEquals(addr1, addr2);
        assertEquals(addr2, addr3);
    }
}
