// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.tx;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.brane.core.crypto.Keccak256;
import io.brane.core.crypto.PrivateKey;
import io.brane.core.crypto.Signature;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.primitives.Hex;

/**
 * Tests for LegacyTransaction with EIP-155 encoding.
 * Uses known test vectors from Ethereum specification.
 */
class LegacyTransactionTest {

    // Known Ethereum test vector for EIP-155
    private static final String TEST_PRIVATE_KEY = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";

    @Test
    void testCreateLegacyTransaction() {
        final LegacyTransaction tx = new LegacyTransaction(
                0L,
                Wei.of(50000000000L), // 50 gwei
                21000L,
                Address.fromBytes(hexToBytes("70997970c51812dc3a010c7d01b50e0d17dc79c8")),
                Wei.of(1000000000000000000L), // 1 ether
                HexData.EMPTY);

        assertNotNull(tx);
        assertEquals(0L, tx.nonce());
        assertEquals(21000L, tx.gasLimit());
    }

    @Test
    void testEncodeForSigningEip155() {
        final LegacyTransaction tx = new LegacyTransaction(
                9L,
                Wei.of(20000000000L), // 20 gwei
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(1000000000000000000L), // 1 ether
                HexData.EMPTY);

        final long chainId = 1; // mainnet
        final byte[] preimage = tx.encodeForSigning(chainId);

        assertNotNull(preimage);
        assertTrue(preimage.length > 0);

        // Preimage should include chainId, 0, 0 at the end (EIP-155)
        // We can verify by checking it's a valid RLP list with 9 items
    }

    @Test
    void testEncodeAsEnvelope() {
        final LegacyTransaction tx = new LegacyTransaction(
                0L,
                Wei.of(20000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(1000000000000000000L),
                HexData.EMPTY);

        // Create a dummy signature for testing
        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;
        final Signature signature = new Signature(r, s, 37); // v=37 for chainId=1, yParity=0

        final byte[] envelope = tx.encodeAsEnvelope(signature);

        assertNotNull(envelope);
        assertTrue(envelope.length > 0);

        // Envelope should be RLP list with 9 items (6 tx fields + 3 signature fields)
    }

    @Test
    void testSignAndEncode() {
        final PrivateKey privateKey = PrivateKey.fromHex(TEST_PRIVATE_KEY);

        final LegacyTransaction tx = new LegacyTransaction(
                0L,
                Wei.of(20000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(1000000000000000000L),
                HexData.EMPTY);

        final long chainId = 1;
        final byte[] preimage = tx.encodeForSigning(chainId);
        final byte[] messageHash = Keccak256.hash(preimage);
        final Signature baseSig = privateKey.sign(messageHash);

        // Create EIP-155 signature (v = chainId * 2 + 35 + yParity)
        final int v = (int) (chainId * 2 + 35 + baseSig.v());
        final Signature eip155Sig = new Signature(baseSig.r(), baseSig.s(), v);

        final byte[] envelope = tx.encodeAsEnvelope(eip155Sig);

        assertNotNull(envelope);
        assertTrue(envelope.length > 0);

        // Envelope should be valid RLP and ready to broadcast
        final String hex = Hex.encode(envelope);
        assertTrue(hex.startsWith("0x"));
    }

    @Test
    void testContractCreation() {
        // Contract creation: to = null
        final LegacyTransaction tx = new LegacyTransaction(
                0L,
                Wei.of(20000000000L),
                53000L, // More gas for contract creation
                null, // null recipient = contract creation
                Wei.of(0),
                HexData.fromBytes(new byte[] { 0x60, 0x60, 0x60, 0x40 }) // Simple bytecode
        );

        assertNotNull(tx);
        assertNull(tx.to());

        final byte[] preimage = tx.encodeForSigning(1);
        assertNotNull(preimage);
        // Should encode empty address bytes for contract creation
    }

    @Test
    void testNonceValidation() {
        assertThrows(IllegalArgumentException.class, () -> new LegacyTransaction(
                -1L, // Invalid negative nonce
                Wei.of(20000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(0),
                HexData.EMPTY));
    }

    @Test
    void testGasLimitValidation() {
        assertThrows(IllegalArgumentException.class, () -> new LegacyTransaction(
                0L,
                Wei.of(20000000000L),
                0L, // Invalid zero gas limit
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(0),
                HexData.EMPTY));
    }

    @Test
    void testNullValidation() {
        assertThrows(NullPointerException.class, () -> new LegacyTransaction(
                0L,
                null, // Null gasPrice
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(0),
                HexData.EMPTY));

        assertThrows(NullPointerException.class, () -> new LegacyTransaction(
                0L,
                Wei.of(20000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                null, // Null value
                HexData.EMPTY));

        assertThrows(NullPointerException.class, () -> new LegacyTransaction(
                0L,
                Wei.of(20000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(0),
                null // Null data
        ));
    }

    @Test
    void testInvalidVValue_throwsException() {
        final LegacyTransaction tx = new LegacyTransaction(
                0L,
                Wei.of(20000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(1000000000000000000L),
                HexData.EMPTY);

        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;

        // v=0 is yParity, not EIP-155 encoded - should fail
        final Signature rawSig0 = new Signature(r, s, 0);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tx.encodeAsEnvelope(rawSig0));
        assertTrue(exception.getMessage().contains("EIP-155 encoded"));
        assertTrue(exception.getMessage().contains(">= 35"));

        // v=1 is also yParity - should fail
        final Signature rawSig1 = new Signature(r, s, 1);
        assertThrows(IllegalArgumentException.class, () -> tx.encodeAsEnvelope(rawSig1));

        // v=27 is pre-EIP-155 legacy format - should fail
        final Signature legacySig27 = new Signature(r, s, 27);
        assertThrows(IllegalArgumentException.class, () -> tx.encodeAsEnvelope(legacySig27));

        // v=28 is also pre-EIP-155 - should fail
        final Signature legacySig28 = new Signature(r, s, 28);
        assertThrows(IllegalArgumentException.class, () -> tx.encodeAsEnvelope(legacySig28));

        // v=35 is minimum EIP-155 (chainId=0, yParity=0) - should work
        final Signature minEip155 = new Signature(r, s, 35);
        assertDoesNotThrow(() -> tx.encodeAsEnvelope(minEip155));

        // v=37 is mainnet (chainId=1, yParity=0) - should work
        final Signature mainnetSig = new Signature(r, s, 37);
        assertDoesNotThrow(() -> tx.encodeAsEnvelope(mainnetSig));

        // v=38 is mainnet (chainId=1, yParity=1) - should work
        final Signature mainnetSig1 = new Signature(r, s, 38);
        assertDoesNotThrow(() -> tx.encodeAsEnvelope(mainnetSig1));
    }

    @Test
    void testEip155VEncodingForDifferentChainIds() {
        final LegacyTransaction tx = new LegacyTransaction(
                0L,
                Wei.of(20000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(1000000000000000000L),
                HexData.EMPTY);

        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;

        // Test various chain IDs with proper EIP-155 encoding
        // chainId=1 (mainnet): v = 1*2+35+0 = 37 or 1*2+35+1 = 38
        assertDoesNotThrow(() -> tx.encodeAsEnvelope(new Signature(r, s, 37)));
        assertDoesNotThrow(() -> tx.encodeAsEnvelope(new Signature(r, s, 38)));

        // chainId=137 (polygon): v = 137*2+35+0 = 309 or 137*2+35+1 = 310
        assertDoesNotThrow(() -> tx.encodeAsEnvelope(new Signature(r, s, 309)));
        assertDoesNotThrow(() -> tx.encodeAsEnvelope(new Signature(r, s, 310)));

        // chainId=42161 (arbitrum): v = 42161*2+35+0 = 84357
        assertDoesNotThrow(() -> tx.encodeAsEnvelope(new Signature(r, s, 84357)));
    }

    @Test
    void testDeterministicEncoding() {
        final LegacyTransaction tx = new LegacyTransaction(
                5L,
                Wei.of(25000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(500000000000000000L),
                HexData.EMPTY);

        final byte[] preimage1 = tx.encodeForSigning(1);
        final byte[] preimage2 = tx.encodeForSigning(1);

        assertArrayEquals(preimage1, preimage2, "Encoding must be deterministic");
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
