// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.tx;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.brane.core.crypto.Keccak256;
import io.brane.core.crypto.PrivateKey;
import io.brane.core.crypto.Signature;
import io.brane.core.model.AccessListEntry;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.primitives.Hex;

/**
 * Integration test for transaction encoding, signing, and roundtrip validation.
 *
 * <p>
 * This test validates the complete flow:
 * <ol>
 * <li>Create transaction from TransactionRequest</li>
 * <li>Encode for signing</li>
 * <li>Sign with PrivateKey</li>
 * <li>Encode as envelope</li>
 * <li>Verify the envelope is valid hex ready for broadcast</li>
 * </ol>
 */
class TransactionIntegrationTest {

    private static final String TEST_PRIVATE_KEY = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";
    private static final long CHAIN_ID = 1; // Mainnet

    @Test
    void testLegacyTransactionFullFlow() {
        // 1. Create TransactionRequest
        final TransactionRequest request = new TransactionRequest(
                Address.fromBytes(hexToBytes("2c7536e3605d9c16a7a3d7b1898e529396a65c23")), // from
                Address.fromBytes(hexToBytes("70997970c51812dc3a010c7d01b50e0d17dc79c8")), // to
                Wei.of(1000000000000000000L), // 1 ether
                21000L, // gasLimit
                Wei.gwei(20), // gasPrice
                null, // maxPriorityFeePerGas (legacy)
                null, // maxFeePerGas (legacy)
                0L, // nonce
                HexData.EMPTY, // data
                false, // isEip1559
                null // accessList
        );

        // 2. Convert to UnsignedTransaction
        final UnsignedTransaction tx = request.toUnsignedTransaction(CHAIN_ID);
        assertInstanceOf(LegacyTransaction.class, tx);

        // 3. Encode for signing
        final byte[] preimage = tx.encodeForSigning(CHAIN_ID);
        assertNotNull(preimage);
        assertTrue(preimage.length > 0);

        // 4. Sign
        final PrivateKey privateKey = PrivateKey.fromHex(TEST_PRIVATE_KEY);
        final byte[] messageHash = Keccak256.hash(preimage);
        final Signature baseSig = privateKey.sign(messageHash);

        // Create EIP-155 signature
        final int v = (int) (CHAIN_ID * 2 + 35 + baseSig.v());
        final Signature eip155Sig = new Signature(baseSig.r(), baseSig.s(), v);

        // 5. Encode as envelope
        final byte[] envelope = tx.encodeAsEnvelope(eip155Sig);
        assertNotNull(envelope);
        assertTrue(envelope.length > 0);

        // 6. Verify hex encoding
        final String hexEnvelope = Hex.encode(envelope);
        assertTrue(hexEnvelope.startsWith("0x"));
        assertTrue(hexEnvelope.length() > 10);

        System.out.println("✓ Legacy transaction signed successfully");
        System.out.println("  Envelope: " + hexEnvelope.substring(0, Math.min(66, hexEnvelope.length())) + "...");
    }

    @Test
    void testEip1559TransactionFullFlow() {
        // 1. Create TransactionRequest
        final TransactionRequest request = new TransactionRequest(
                Address.fromBytes(hexToBytes("2c7536e3605d9c16a7a3d7b1898e529396a65c23")), // from
                Address.fromBytes(hexToBytes("70997970c51812dc3a010c7d01b50e0d17dc79c8")), // to
                Wei.of(1000000000000000000L), // 1 ether
                21000L, // gasLimit
                null, // gasPrice (EIP-1559)
                Wei.gwei(2), // maxPriorityFeePerGas
                Wei.gwei(100), // maxFeePerGas
                0L, // nonce
                HexData.EMPTY, // data
                true, // isEip1559
                List.of() // accessList
        );

        // 2. Convert to UnsignedTransaction
        final UnsignedTransaction tx = request.toUnsignedTransaction(CHAIN_ID);
        assertInstanceOf(Eip1559Transaction.class, tx);

        // 3. Encode for signing
        final byte[] preimage = tx.encodeForSigning(CHAIN_ID);
        assertNotNull(preimage);
        assertTrue(preimage.length > 0);
        assertEquals(0x02, preimage[0], "EIP-1559 must start with type byte 0x02");

        // 4. Sign
        final PrivateKey privateKey = PrivateKey.fromHex(TEST_PRIVATE_KEY);
        final byte[] messageHash = Keccak256.hash(preimage);
        final Signature signature = privateKey.sign(messageHash);

        // For EIP-1559, v is just yParity (0 or 1)
        assertTrue(signature.v() == 0 || signature.v() == 1);

        // 5. Encode as envelope
        final byte[] envelope = tx.encodeAsEnvelope(signature);
        assertNotNull(envelope);
        assertTrue(envelope.length > 0);
        assertEquals(0x02, envelope[0], "EIP-1559 envelope must start with type byte 0x02");

        // 6. Verify hex encoding
        final String hexEnvelope = Hex.encode(envelope);
        assertTrue(hexEnvelope.startsWith("0x02"));

        System.out.println("✓ EIP-1559 transaction signed successfully");
        System.out.println("  Envelope: " + hexEnvelope.substring(0, Math.min(66, hexEnvelope.length())) + "...");
    }

    @Test
    void testEip1559WithAccessList() {
        final Address contractAddr = Address.fromBytes(hexToBytes("a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"));
        final Hash storageKey = new Hash("0x0000000000000000000000000000000000000000000000000000000000000001");
        final AccessListEntry entry = new AccessListEntry(contractAddr, List.of(storageKey));

        final TransactionRequest request = new TransactionRequest(
                Address.fromBytes(hexToBytes("2c7536e3605d9c16a7a3d7b1898e529396a65c23")),
                contractAddr,
                Wei.of(0),
                100000L,
                null,
                Wei.gwei(2),
                Wei.gwei(100),
                0L,
                HexData.fromBytes(new byte[] { 0x01, 0x02, 0x03, 0x04 }),
                true,
                List.of(entry));

        final UnsignedTransaction tx = request.toUnsignedTransaction(CHAIN_ID);
        final PrivateKey privateKey = PrivateKey.fromHex(TEST_PRIVATE_KEY);

        final byte[] preimage = tx.encodeForSigning(CHAIN_ID);
        final byte[] messageHash = Keccak256.hash(preimage);
        final Signature signature = privateKey.sign(messageHash);

        final byte[] envelope = tx.encodeAsEnvelope(signature);
        assertNotNull(envelope);

        System.out.println("✓ EIP-1559 with access list signed successfully");
    }

    @Test
    void testContractDeployment() {
        // Contract deployment: to = null
        final TransactionRequest request = new TransactionRequest(
                Address.fromBytes(hexToBytes("2c7536e3605d9c16a7a3d7b1898e529396a65c23")),
                null, // Contract deployment
                Wei.of(0),
                500000L,
                Wei.gwei(20),
                null,
                null,
                0L,
                HexData.fromBytes(new byte[] { 0x60, 0x60, 0x60, 0x40 }), // Simple bytecode
                false,
                null);

        final UnsignedTransaction tx = request.toUnsignedTransaction(CHAIN_ID);
        final PrivateKey privateKey = PrivateKey.fromHex(TEST_PRIVATE_KEY);

        final byte[] preimage = tx.encodeForSigning(CHAIN_ID);
        final byte[] messageHash = Keccak256.hash(preimage);
        final Signature baseSig = privateKey.sign(messageHash);

        final int v = (int) (CHAIN_ID * 2 + 35 + baseSig.v());
        final Signature eip155Sig = new Signature(baseSig.r(), baseSig.s(), v);

        final byte[] envelope = tx.encodeAsEnvelope(eip155Sig);
        assertNotNull(envelope);

        System.out.println("✓ Contract deployment transaction signed successfully");
    }

    @Test
    void testTransactionRequestValidation() {
        final TransactionRequest incompleteRequest = new TransactionRequest(
                Address.fromBytes(hexToBytes("2c7536e3605d9c16a7a3d7b1898e529396a65c23")),
                Address.fromBytes(hexToBytes("70997970c51812dc3a010c7d01b50e0d17dc79c8")),
                Wei.of(1000000000000000000L),
                null, // gasLimit not set
                Wei.gwei(20),
                null,
                null,
                0L,
                HexData.EMPTY,
                false,
                null);

        assertThrows(IllegalStateException.class, () -> {
            incompleteRequest.toUnsignedTransaction(CHAIN_ID);
        }, "Should throw when gasLimit is null");
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
