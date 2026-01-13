// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.tx;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.brane.core.crypto.Keccak256;
import io.brane.core.crypto.PrivateKey;
import io.brane.core.crypto.Signature;
import io.brane.core.model.AccessListEntry;
import io.brane.core.types.Address;
import io.brane.core.types.Blob;
import io.brane.core.types.BlobSidecar;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.KzgCommitment;
import io.brane.core.types.KzgProof;
import io.brane.core.types.Wei;
import io.brane.primitives.Hex;

/**
 * Tests for Eip4844Transaction with EIP-2718 typed envelope.
 */
class Eip4844TransactionTest {

    private static final String TEST_PRIVATE_KEY = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";

    // A valid versioned hash with version byte 0x01 (KZG commitment)
    private static final Hash BLOB_HASH_1 = new Hash("0x01" + "00".repeat(31));
    private static final Hash BLOB_HASH_2 = new Hash("0x01" + "11".repeat(31));

    @Test
    void testCreateEip4844Transaction() {
        final Eip4844Transaction tx = new Eip4844Transaction(
                1L, // chainId
                0L, // nonce
                Wei.of(2000000000L), // 2 gwei priority fee
                Wei.of(100000000000L), // 100 gwei max fee
                21000L,
                Address.fromBytes(hexToBytes("70997970c51812dc3a010c7d01b50e0d17dc79c8")),
                Wei.of(1000000000000000000L), // 1 ether
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L), // 1 gwei blob fee
                List.of(BLOB_HASH_1));

        assertNotNull(tx);
        assertEquals(1L, tx.chainId());
        assertEquals(0L, tx.nonce());
        assertEquals(21000L, tx.gasLimit());
        assertEquals(1, tx.blobVersionedHashes().size());
    }

    @Test
    void testEncodeForSigningStartsWithType03Byte() {
        final Eip4844Transaction tx = new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(1000000000000000000L),
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(BLOB_HASH_1));

        final byte[] preimage = tx.encodeForSigning(1);

        assertNotNull(preimage);
        assertTrue(preimage.length > 0);
        assertEquals(0x03, preimage[0], "EIP-4844 transactions must start with type 0x03");
    }

    @Test
    void testEncodeForSigningContainsRlpEncodedFields() {
        final Eip4844Transaction tx = new Eip4844Transaction(
                1L, // chainId
                5L, // nonce
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(1000000000000000000L),
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(BLOB_HASH_1));

        final byte[] preimage = tx.encodeForSigning(1);

        // Verify it starts with type byte
        assertEquals(0x03, preimage[0]);

        // The rest should be valid RLP
        // RLP list starts with 0xf8 or higher for long lists
        assertTrue((preimage[1] & 0xFF) >= 0xc0, "Second byte should indicate RLP list");
    }

    @Test
    void testEncodeAsEnvelopeStartsWithType() {
        final Eip4844Transaction tx = new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(1000000000000000000L),
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(BLOB_HASH_1));

        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;
        final Signature signature = new Signature(r, s, 0); // yParity = 0

        final byte[] envelope = tx.encodeAsEnvelope(signature);

        assertNotNull(envelope);
        assertTrue(envelope.length > 0);
        assertEquals(0x03, envelope[0], "EIP-4844 envelope must start with type 0x03");
    }

    @Test
    void testSignAndEncode() {
        final PrivateKey privateKey = PrivateKey.fromHex(TEST_PRIVATE_KEY);

        final Eip4844Transaction tx = new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(1000000000000000000L),
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(BLOB_HASH_1));

        final byte[] preimage = tx.encodeForSigning(1);
        final byte[] messageHash = Keccak256.hash(preimage);
        final Signature signature = privateKey.sign(messageHash);

        // For EIP-4844, v is just yParity (0 or 1), not EIP-155 encoded
        assertTrue(signature.v() == 0 || signature.v() == 1);

        final byte[] envelope = tx.encodeAsEnvelope(signature);

        assertNotNull(envelope);
        assertTrue(envelope.length > 0);

        final String hex = Hex.encode(envelope);
        assertTrue(hex.startsWith("0x03"), "Hex should start with 0x03");
    }

    @Test
    void testWithAccessList() {
        final Address contractAddr = Address.fromBytes(hexToBytes("a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"));
        final Hash storageKey1 = new Hash("0x0000000000000000000000000000000000000000000000000000000000000001");
        final Hash storageKey2 = new Hash("0x0000000000000000000000000000000000000000000000000000000000000002");

        final AccessListEntry entry = new AccessListEntry(contractAddr, List.of(storageKey1, storageKey2));

        final Eip4844Transaction tx = new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                100000L, // More gas for complex interaction
                contractAddr,
                Wei.ZERO,
                HexData.fromBytes(new byte[] { 0x01, 0x02, 0x03, 0x04 }),
                List.of(entry),
                Wei.of(1000000000L),
                List.of(BLOB_HASH_1));

        assertNotNull(tx);
        assertEquals(1, tx.accessList().size());

        final byte[] preimage = tx.encodeForSigning(1);
        assertNotNull(preimage);
        // Access list should be encoded in the preimage
    }

    @Test
    void testMultipleBlobHashes() {
        final Eip4844Transaction tx = new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.ZERO,
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(BLOB_HASH_1, BLOB_HASH_2));

        assertEquals(2, tx.blobVersionedHashes().size());

        final byte[] preimage = tx.encodeForSigning(1);
        assertNotNull(preimage);
        assertEquals(0x03, preimage[0]);
    }

    @Test
    void testChainIdMismatchThrows() {
        final Eip4844Transaction tx = new Eip4844Transaction(
                1L, // chainId = 1
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.ZERO,
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(BLOB_HASH_1));

        // Passing different chainId should throw
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tx.encodeForSigning(2));
        assertTrue(exception.getMessage().contains("chainId"));
    }

    @Test
    void testChainIdValidation() {
        assertThrows(IllegalArgumentException.class, () -> new Eip4844Transaction(
                0L, // Invalid chainId = 0
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.ZERO,
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(BLOB_HASH_1)));
    }

    @Test
    void testNonceValidation() {
        assertThrows(IllegalArgumentException.class, () -> new Eip4844Transaction(
                1L,
                -1L, // Invalid negative nonce
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.ZERO,
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(BLOB_HASH_1)));
    }

    @Test
    void testGasLimitValidation() {
        assertThrows(IllegalArgumentException.class, () -> new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                0L, // Invalid zero gas limit
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.ZERO,
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(BLOB_HASH_1)));
    }

    @Test
    void testToAddressRequired() {
        // EIP-4844 requires to address (no contract creation)
        assertThrows(NullPointerException.class, () -> new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                null, // null to address should throw for EIP-4844
                Wei.ZERO,
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(BLOB_HASH_1)));
    }

    @Test
    void testBlobHashesRequired() {
        // Must have at least 1 blob hash
        assertThrows(IllegalArgumentException.class, () -> new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.ZERO,
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of())); // Empty blob hashes
    }

    @Test
    void testMaxBlobHashes() {
        // Maximum 6 blob hashes allowed
        final List<Hash> sevenHashes = List.of(
                BLOB_HASH_1, BLOB_HASH_1, BLOB_HASH_1, BLOB_HASH_1,
                BLOB_HASH_1, BLOB_HASH_1, BLOB_HASH_1);

        assertThrows(IllegalArgumentException.class, () -> new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.ZERO,
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                sevenHashes)); // 7 hashes - too many
    }

    @Test
    void testDeterministicEncoding() {
        final Eip4844Transaction tx = new Eip4844Transaction(
                1L,
                5L,
                Wei.of(2500000000L),
                Wei.of(95000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(500000000000000000L),
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(BLOB_HASH_1));

        final byte[] preimage1 = tx.encodeForSigning(1);
        final byte[] preimage2 = tx.encodeForSigning(1);

        assertArrayEquals(preimage1, preimage2, "Encoding must be deterministic");
    }

    @Test
    void testInvalidVValue_throwsException() {
        final Eip4844Transaction tx = new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(1000000000000000000L),
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(BLOB_HASH_1));

        // Create signature with EIP-155 encoded v (invalid for EIP-4844)
        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;

        // v=27 is legacy pre-EIP-155 format, not valid for EIP-4844
        final Signature legacySignature = new Signature(r, s, 27);
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> tx.encodeAsEnvelope(legacySignature));
        assertTrue(exception.getMessage().contains("yParity (0 or 1)"));
        assertTrue(exception.getMessage().contains("27"));

        // v=28 is also invalid
        final Signature legacySignature28 = new Signature(r, s, 28);
        assertThrows(IllegalArgumentException.class, () -> tx.encodeAsEnvelope(legacySignature28));

        // v=37 is EIP-155 mainnet, also invalid for EIP-4844
        final Signature eip155Signature = new Signature(r, s, 37);
        assertThrows(IllegalArgumentException.class, () -> tx.encodeAsEnvelope(eip155Signature));

        // v=0 and v=1 should work (yParity values)
        final Signature validSig0 = new Signature(r, s, 0);
        assertDoesNotThrow(() -> tx.encodeAsEnvelope(validSig0));

        final Signature validSig1 = new Signature(r, s, 1);
        assertDoesNotThrow(() -> tx.encodeAsEnvelope(validSig1));
    }

    @Test
    void testBlobHashesAreImmutable() {
        final java.util.ArrayList<Hash> mutableHashes = new java.util.ArrayList<>();
        mutableHashes.add(BLOB_HASH_1);

        final Eip4844Transaction tx = new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.ZERO,
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                mutableHashes);

        // Blob hashes should be immutable - attempting to modify should fail
        assertThrows(UnsupportedOperationException.class, () -> tx.blobVersionedHashes().add(BLOB_HASH_2));
    }

    @Test
    void testAccessListIsImmutable() {
        final Address contractAddr = Address.fromBytes(hexToBytes("a0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"));
        final Hash storageKey = new Hash("0x0000000000000000000000000000000000000000000000000000000000000001");
        final java.util.ArrayList<AccessListEntry> accessList = new java.util.ArrayList<>();
        accessList.add(new AccessListEntry(contractAddr, List.of(storageKey)));

        final Eip4844Transaction tx = new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                contractAddr,
                Wei.ZERO,
                HexData.EMPTY,
                accessList,
                Wei.of(1000000000L),
                List.of(BLOB_HASH_1));

        // Access list should be immutable - attempting to modify should fail
        assertThrows(UnsupportedOperationException.class, () -> tx.accessList().add(
                new AccessListEntry(contractAddr, List.of())));
    }

    @Test
    void testEncodeAsNetworkWrapperStartsWithType() {
        // Create a KzgCommitment that produces BLOB_HASH_1 when converted to versioned hash
        // For testing, we create a commitment whose SHA-256 hash starts with 0x01
        // Since KzgCommitment.toVersionedHash() replaces first byte with 0x01, we need to
        // create matching commitment
        final byte[] commitmentBytes = new byte[48];
        // Set bytes so SHA-256 hash bytes 1-31 match BLOB_HASH_1 bytes 1-31
        // For this test, we create a sidecar whose versioned hash matches
        final KzgCommitment commitment = createCommitmentForHash(BLOB_HASH_1);
        final Hash versionedHash = commitment.toVersionedHash();

        final Eip4844Transaction tx = new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(1000000000000000000L),
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(versionedHash));

        final BlobSidecar sidecar = new BlobSidecar(
                List.of(new Blob(new byte[Blob.SIZE])),
                List.of(commitment),
                List.of(new KzgProof(new byte[48])));

        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;
        final Signature signature = new Signature(r, s, 0);

        final byte[] wrapper = tx.encodeAsNetworkWrapper(signature, sidecar);

        assertNotNull(wrapper);
        assertTrue(wrapper.length > 0);
        assertEquals(0x03, wrapper[0], "Network wrapper must start with type 0x03");
    }

    @Test
    void testEncodeAsNetworkWrapperWithMultipleBlobs() {
        final KzgCommitment commitment1 = createCommitmentForHash(BLOB_HASH_1);
        final KzgCommitment commitment2 = createCommitmentForHash(BLOB_HASH_2);

        final Eip4844Transaction tx = new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.ZERO,
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(commitment1.toVersionedHash(), commitment2.toVersionedHash()));

        final BlobSidecar sidecar = new BlobSidecar(
                List.of(new Blob(new byte[Blob.SIZE]), new Blob(new byte[Blob.SIZE])),
                List.of(commitment1, commitment2),
                List.of(new KzgProof(new byte[48]), new KzgProof(new byte[48])));

        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;
        final Signature signature = new Signature(r, s, 1);

        final byte[] wrapper = tx.encodeAsNetworkWrapper(signature, sidecar);

        assertNotNull(wrapper);
        assertEquals(0x03, wrapper[0]);
        // Wrapper should be significantly larger than envelope due to blob data
        final byte[] envelope = tx.encodeAsEnvelope(signature);
        assertTrue(wrapper.length > envelope.length,
                "Network wrapper should be larger than envelope due to blob data");
    }

    @Test
    void testEncodeAsNetworkWrapperValidatesHashMismatch() {
        // Create a sidecar whose versioned hashes don't match
        final KzgCommitment commitment = createCommitmentForHash(BLOB_HASH_1);
        final Hash mismatchedHash = commitment.toVersionedHash();

        // Transaction uses BLOB_HASH_2, but sidecar produces hash from commitment
        final Eip4844Transaction tx = new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.ZERO,
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(BLOB_HASH_2)); // Different from commitment's versioned hash

        final BlobSidecar sidecar = new BlobSidecar(
                List.of(new Blob(new byte[Blob.SIZE])),
                List.of(commitment),
                List.of(new KzgProof(new byte[48])));

        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;
        final Signature signature = new Signature(r, s, 0);

        // Should throw because sidecar's versioned hashes don't match transaction's
        assertThrows(IllegalArgumentException.class,
                () -> tx.encodeAsNetworkWrapper(signature, sidecar));
    }

    @Test
    void testEncodeAsNetworkWrapperNullSignature() {
        final KzgCommitment commitment = createCommitmentForHash(BLOB_HASH_1);

        final Eip4844Transaction tx = new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.ZERO,
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(commitment.toVersionedHash()));

        final BlobSidecar sidecar = new BlobSidecar(
                List.of(new Blob(new byte[Blob.SIZE])),
                List.of(commitment),
                List.of(new KzgProof(new byte[48])));

        assertThrows(NullPointerException.class,
                () -> tx.encodeAsNetworkWrapper(null, sidecar));
    }

    @Test
    void testEncodeAsNetworkWrapperNullSidecar() {
        final Eip4844Transaction tx = new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.ZERO,
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(BLOB_HASH_1));

        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;
        final Signature signature = new Signature(r, s, 0);

        assertThrows(NullPointerException.class,
                () -> tx.encodeAsNetworkWrapper(signature, null));
    }

    @Test
    void testEncodeAsNetworkWrapperInvalidYParity() {
        final KzgCommitment commitment = createCommitmentForHash(BLOB_HASH_1);

        final Eip4844Transaction tx = new Eip4844Transaction(
                1L,
                0L,
                Wei.of(2000000000L),
                Wei.of(100000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.ZERO,
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(commitment.toVersionedHash()));

        final BlobSidecar sidecar = new BlobSidecar(
                List.of(new Blob(new byte[Blob.SIZE])),
                List.of(commitment),
                List.of(new KzgProof(new byte[48])));

        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;
        final Signature invalidSig = new Signature(r, s, 27); // Invalid yParity

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> tx.encodeAsNetworkWrapper(invalidSig, sidecar));
        assertTrue(exception.getMessage().contains("yParity"));
    }

    @Test
    void testEncodeAsNetworkWrapperDeterministic() {
        final KzgCommitment commitment = createCommitmentForHash(BLOB_HASH_1);

        final Eip4844Transaction tx = new Eip4844Transaction(
                1L,
                5L,
                Wei.of(2500000000L),
                Wei.of(95000000000L),
                21000L,
                Address.fromBytes(hexToBytes("3535353535353535353535353535353535353535")),
                Wei.of(500000000000000000L),
                HexData.EMPTY,
                List.of(),
                Wei.of(1000000000L),
                List.of(commitment.toVersionedHash()));

        final BlobSidecar sidecar = new BlobSidecar(
                List.of(new Blob(new byte[Blob.SIZE])),
                List.of(commitment),
                List.of(new KzgProof(new byte[48])));

        final byte[] r = new byte[32];
        final byte[] s = new byte[32];
        r[0] = 0x01;
        s[0] = 0x02;
        final Signature signature = new Signature(r, s, 0);

        final byte[] wrapper1 = tx.encodeAsNetworkWrapper(signature, sidecar);
        final byte[] wrapper2 = tx.encodeAsNetworkWrapper(signature, sidecar);

        assertArrayEquals(wrapper1, wrapper2, "Network wrapper encoding must be deterministic");
    }

    /**
     * Helper to create a KzgCommitment that produces a specific versioned hash.
     * Since toVersionedHash() computes SHA-256 and replaces byte 0 with 0x01,
     * we need to find bytes that hash to the expected result.
     * For simplicity in tests, we just create a commitment and use its actual versioned hash.
     */
    private static KzgCommitment createCommitmentForHash(Hash expectedHash) {
        // In real use, the commitment would come from KZG operations
        // For testing, we create deterministic commitments based on the expected hash
        // The important thing is that the versioned hash matches what the tx expects
        byte[] commitmentBytes = new byte[48];
        // Use the hash bytes to seed the commitment (just for test determinism)
        byte[] hashBytes = Hex.decode(expectedHash.value());
        System.arraycopy(hashBytes, 0, commitmentBytes, 0, Math.min(hashBytes.length, 48));
        return new KzgCommitment(commitmentBytes);
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
