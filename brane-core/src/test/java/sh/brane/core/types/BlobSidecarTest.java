// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import sh.brane.core.crypto.Kzg;
import sh.brane.core.error.KzgException;

class BlobSidecarTest {

    private Blob blob1;
    private Blob blob2;
    private KzgCommitment commitment1;
    private KzgCommitment commitment2;
    private KzgProof proof1;
    private KzgProof proof2;

    @BeforeEach
    void setUp() {
        byte[] blobData1 = new byte[Blob.SIZE];
        Arrays.fill(blobData1, (byte) 0xAA);
        blob1 = new Blob(blobData1);

        byte[] blobData2 = new byte[Blob.SIZE];
        Arrays.fill(blobData2, (byte) 0xBB);
        blob2 = new Blob(blobData2);

        byte[] commitmentData1 = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(commitmentData1, (byte) 0x11);
        commitment1 = new KzgCommitment(commitmentData1);

        byte[] commitmentData2 = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(commitmentData2, (byte) 0x22);
        commitment2 = new KzgCommitment(commitmentData2);

        byte[] proofData1 = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(proofData1, (byte) 0x33);
        proof1 = new KzgProof(proofData1);

        byte[] proofData2 = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(proofData2, (byte) 0x44);
        proof2 = new KzgProof(proofData2);
    }

    @Test
    void maxBlobsConstant() {
        assertEquals(6, BlobSidecar.MAX_BLOBS);
    }

    @Test
    void createsSingleBlobSidecar() {
        BlobSidecar sidecar = new BlobSidecar(
                List.of(blob1),
                List.of(commitment1),
                List.of(proof1));

        assertEquals(1, sidecar.size());
        assertEquals(List.of(blob1), sidecar.blobs());
        assertEquals(List.of(commitment1), sidecar.commitments());
        assertEquals(List.of(proof1), sidecar.proofs());
    }

    @Test
    void createsMultipleBlobSidecar() {
        BlobSidecar sidecar = new BlobSidecar(
                List.of(blob1, blob2),
                List.of(commitment1, commitment2),
                List.of(proof1, proof2));

        assertEquals(2, sidecar.size());
        assertEquals(List.of(blob1, blob2), sidecar.blobs());
        assertEquals(List.of(commitment1, commitment2), sidecar.commitments());
        assertEquals(List.of(proof1, proof2), sidecar.proofs());
    }

    @Test
    void rejectsNullBlobs() {
        assertThrows(NullPointerException.class, () ->
                new BlobSidecar(null, List.of(commitment1), List.of(proof1)));
    }

    @Test
    void rejectsNullCommitments() {
        assertThrows(NullPointerException.class, () ->
                new BlobSidecar(List.of(blob1), null, List.of(proof1)));
    }

    @Test
    void rejectsNullProofs() {
        assertThrows(NullPointerException.class, () ->
                new BlobSidecar(List.of(blob1), List.of(commitment1), null));
    }

    @Test
    void rejectsEmptyBlobs() {
        assertThrows(IllegalArgumentException.class, () ->
                new BlobSidecar(List.of(), List.of(), List.of()));
    }

    @Test
    void rejectsExceedingMaxBlobs() {
        List<Blob> blobs = new ArrayList<>();
        List<KzgCommitment> commitments = new ArrayList<>();
        List<KzgProof> proofs = new ArrayList<>();

        for (int i = 0; i <= BlobSidecar.MAX_BLOBS; i++) {
            byte[] blobData = new byte[Blob.SIZE];
            byte[] commitmentData = new byte[FixedSizeG1Point.SIZE];
            byte[] proofData = new byte[FixedSizeG1Point.SIZE];
            blobs.add(new Blob(blobData));
            commitments.add(new KzgCommitment(commitmentData));
            proofs.add(new KzgProof(proofData));
        }

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new BlobSidecar(blobs, commitments, proofs));
        assertTrue(ex.getMessage().contains("exceeds maximum"));
    }

    @Test
    void rejectsMismatchedBlobsAndCommitmentsSize() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new BlobSidecar(
                        List.of(blob1, blob2),
                        List.of(commitment1),
                        List.of(proof1, proof2)));
        assertTrue(ex.getMessage().contains("commitments size"));
    }

    @Test
    void rejectsMismatchedBlobsAndProofsSize() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                new BlobSidecar(
                        List.of(blob1, blob2),
                        List.of(commitment1, commitment2),
                        List.of(proof1)));
        assertTrue(ex.getMessage().contains("proofs size"));
    }

    @Test
    void rejectsNullBlobElement() {
        List<Blob> blobs = new ArrayList<>();
        blobs.add(null);

        assertThrows(NullPointerException.class, () ->
                new BlobSidecar(blobs, List.of(commitment1), List.of(proof1)));
    }

    @Test
    void rejectsNullCommitmentElement() {
        List<KzgCommitment> commitments = new ArrayList<>();
        commitments.add(null);

        assertThrows(NullPointerException.class, () ->
                new BlobSidecar(List.of(blob1), commitments, List.of(proof1)));
    }

    @Test
    void rejectsNullProofElement() {
        List<KzgProof> proofs = new ArrayList<>();
        proofs.add(null);

        assertThrows(NullPointerException.class, () ->
                new BlobSidecar(List.of(blob1), List.of(commitment1), proofs));
    }

    @Test
    void defensiveCopyOnConstruction() {
        List<Blob> blobs = new ArrayList<>();
        blobs.add(blob1);
        List<KzgCommitment> commitments = new ArrayList<>();
        commitments.add(commitment1);
        List<KzgProof> proofs = new ArrayList<>();
        proofs.add(proof1);

        BlobSidecar sidecar = new BlobSidecar(blobs, commitments, proofs);

        // Modify original lists
        blobs.add(blob2);
        commitments.add(commitment2);
        proofs.add(proof2);

        // Sidecar should not be affected
        assertEquals(1, sidecar.size());
    }

    @Test
    void listsAreUnmodifiable() {
        BlobSidecar sidecar = new BlobSidecar(
                List.of(blob1),
                List.of(commitment1),
                List.of(proof1));

        assertThrows(UnsupportedOperationException.class, () ->
                sidecar.blobs().add(blob2));
        assertThrows(UnsupportedOperationException.class, () ->
                sidecar.commitments().add(commitment2));
        assertThrows(UnsupportedOperationException.class, () ->
                sidecar.proofs().add(proof2));
    }

    @Test
    void versionedHashesAreDerivedFromCommitments() {
        BlobSidecar sidecar = new BlobSidecar(
                List.of(blob1, blob2),
                List.of(commitment1, commitment2),
                List.of(proof1, proof2));

        List<Hash> hashes = sidecar.versionedHashes();

        assertEquals(2, hashes.size());
        assertEquals(commitment1.toVersionedHash(), hashes.get(0));
        assertEquals(commitment2.toVersionedHash(), hashes.get(1));
    }

    @Test
    void versionedHashesAreCached() {
        BlobSidecar sidecar = new BlobSidecar(
                List.of(blob1),
                List.of(commitment1),
                List.of(proof1));

        List<Hash> hashes1 = sidecar.versionedHashes();
        List<Hash> hashes2 = sidecar.versionedHashes();

        assertSame(hashes1, hashes2);
    }

    @Test
    void versionedHashesAreUnmodifiable() {
        BlobSidecar sidecar = new BlobSidecar(
                List.of(blob1),
                List.of(commitment1),
                List.of(proof1));

        assertThrows(UnsupportedOperationException.class, () ->
                sidecar.versionedHashes().clear());
    }

    @Test
    void validateWithValidProofs() {
        BlobSidecar sidecar = new BlobSidecar(
                List.of(blob1),
                List.of(commitment1),
                List.of(proof1));

        Kzg validKzg = new Kzg() {
            @Override
            public KzgCommitment blobToCommitment(Blob blob) {
                return commitment1;
            }

            @Override
            public KzgProof computeProof(Blob blob, KzgCommitment commitment) {
                return proof1;
            }

            @Override
            public boolean verifyBlobKzgProof(Blob blob, KzgCommitment commitment, KzgProof proof) {
                return true;
            }

            @Override
            public boolean verifyBlobKzgProofBatch(List<Blob> blobs, List<KzgCommitment> commitments, List<KzgProof> proofs) {
                return true;
            }
        };

        // Should not throw
        assertDoesNotThrow(() -> sidecar.validate(validKzg));
    }

    @Test
    void validateWithInvalidProofs() {
        BlobSidecar sidecar = new BlobSidecar(
                List.of(blob1),
                List.of(commitment1),
                List.of(proof1));

        Kzg invalidKzg = new Kzg() {
            @Override
            public KzgCommitment blobToCommitment(Blob blob) {
                return commitment1;
            }

            @Override
            public KzgProof computeProof(Blob blob, KzgCommitment commitment) {
                return proof1;
            }

            @Override
            public boolean verifyBlobKzgProof(Blob blob, KzgCommitment commitment, KzgProof proof) {
                return false;
            }

            @Override
            public boolean verifyBlobKzgProofBatch(List<Blob> blobs, List<KzgCommitment> commitments, List<KzgProof> proofs) {
                return false;
            }
        };

        KzgException ex = assertThrows(KzgException.class, () ->
                sidecar.validate(invalidKzg));
        assertEquals(KzgException.Kind.INVALID_PROOF, ex.kind());
    }

    @Test
    void validateRejectsNullKzg() {
        BlobSidecar sidecar = new BlobSidecar(
                List.of(blob1),
                List.of(commitment1),
                List.of(proof1));

        assertThrows(NullPointerException.class, () -> sidecar.validate(null));
    }

    @Test
    void validateHashesWithMatchingHashes() {
        BlobSidecar sidecar = new BlobSidecar(
                List.of(blob1, blob2),
                List.of(commitment1, commitment2),
                List.of(proof1, proof2));

        List<Hash> expectedHashes = List.of(
                commitment1.toVersionedHash(),
                commitment2.toVersionedHash());

        // Should not throw
        assertDoesNotThrow(() -> sidecar.validateHashes(expectedHashes));
    }

    @Test
    void validateHashesWithMismatchedSize() {
        BlobSidecar sidecar = new BlobSidecar(
                List.of(blob1, blob2),
                List.of(commitment1, commitment2),
                List.of(proof1, proof2));

        List<Hash> expectedHashes = List.of(commitment1.toVersionedHash());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                sidecar.validateHashes(expectedHashes));
        assertTrue(ex.getMessage().contains("size"));
    }

    @Test
    void validateHashesWithMismatchedContent() {
        BlobSidecar sidecar = new BlobSidecar(
                List.of(blob1),
                List.of(commitment1),
                List.of(proof1));

        // Use a different commitment's hash
        List<Hash> expectedHashes = List.of(commitment2.toVersionedHash());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                sidecar.validateHashes(expectedHashes));
        assertTrue(ex.getMessage().contains("does not match"));
    }

    @Test
    void validateHashesRejectsNull() {
        BlobSidecar sidecar = new BlobSidecar(
                List.of(blob1),
                List.of(commitment1),
                List.of(proof1));

        assertThrows(NullPointerException.class, () -> sidecar.validateHashes(null));
    }

    @Test
    void equalsAndHashCode() {
        BlobSidecar sidecar1 = new BlobSidecar(
                List.of(blob1),
                List.of(commitment1),
                List.of(proof1));
        BlobSidecar sidecar2 = new BlobSidecar(
                List.of(blob1),
                List.of(commitment1),
                List.of(proof1));
        BlobSidecar sidecar3 = new BlobSidecar(
                List.of(blob2),
                List.of(commitment2),
                List.of(proof2));

        // Reflexive
        assertEquals(sidecar1, sidecar1);

        // Symmetric
        assertEquals(sidecar1, sidecar2);
        assertEquals(sidecar2, sidecar1);

        // Different content
        assertNotEquals(sidecar1, sidecar3);

        // HashCode consistency
        assertEquals(sidecar1.hashCode(), sidecar2.hashCode());

        // Null and other type
        assertNotEquals(sidecar1, null);
        assertNotEquals(sidecar1, "not a sidecar");
    }

    @Test
    void toStringContainsInfo() {
        BlobSidecar sidecar = new BlobSidecar(
                List.of(blob1, blob2),
                List.of(commitment1, commitment2),
                List.of(proof1, proof2));

        String str = sidecar.toString();
        assertTrue(str.contains("BlobSidecar"));
        assertTrue(str.contains("2"));
    }
}
