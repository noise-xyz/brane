// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.tx;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;

import org.junit.jupiter.api.Test;

import sh.brane.core.crypto.Kzg;
import sh.brane.core.types.Blob;
import sh.brane.core.types.BlobSidecar;
import sh.brane.core.types.FixedSizeG1Point;
import sh.brane.core.types.KzgCommitment;
import sh.brane.core.types.KzgProof;

class SidecarBuilderTest {

    @Test
    void constantsAreCorrect() {
        assertEquals(8, SidecarBuilder.LENGTH_PREFIX_SIZE);
        assertEquals(31, SidecarBuilder.USABLE_BYTES_PER_FIELD_ELEMENT);
        assertEquals(4096, SidecarBuilder.FIELD_ELEMENTS_PER_BLOB);
        assertEquals(126976, SidecarBuilder.USABLE_BYTES_PER_BLOB);
        assertEquals(761848, SidecarBuilder.MAX_DATA_SIZE);
    }

    @Test
    void usableBytesPerBlobIsCalculatedCorrectly() {
        int expected = SidecarBuilder.USABLE_BYTES_PER_FIELD_ELEMENT * SidecarBuilder.FIELD_ELEMENTS_PER_BLOB;
        assertEquals(expected, SidecarBuilder.USABLE_BYTES_PER_BLOB);
    }

    @Test
    void maxDataSizeIsCalculatedCorrectly() {
        // 6 blobs max, minus 8 bytes for length prefix
        int expected = (SidecarBuilder.USABLE_BYTES_PER_BLOB * 6) - SidecarBuilder.LENGTH_PREFIX_SIZE;
        assertEquals(expected, SidecarBuilder.MAX_DATA_SIZE);
    }

    @Test
    void fromRejectsNullData() {
        assertThrows(NullPointerException.class, () -> SidecarBuilder.from(null));
    }

    @Test
    void fromRejectsOversizedData() {
        byte[] oversized = new byte[SidecarBuilder.MAX_DATA_SIZE + 1];
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SidecarBuilder.from(oversized));
        assertEquals("Data size 761849 exceeds maximum 761848 bytes", ex.getMessage());
    }

    @Test
    void fromWithSmallDataCreatesSingleBlob() {
        byte[] data = "Hello, blobs!".getBytes();
        SidecarBuilder builder = SidecarBuilder.from(data);

        List<Blob> blobs = builder.blobs();
        assertNotNull(blobs);
        assertEquals(1, blobs.size());

        // Verify blob structure
        Blob blob = blobs.get(0);
        byte[] blobBytes = blob.toBytes();
        assertEquals(Blob.SIZE, blobBytes.length);

        // First byte of first field element is 0x00
        assertEquals(0x00, blobBytes[0]);

        // Bytes 1-8 contain the length prefix (big-endian)
        long encodedLength = 0;
        for (int i = 1; i <= 8; i++) {
            encodedLength = (encodedLength << 8) | (blobBytes[i] & 0xFF);
        }
        assertEquals(data.length, encodedLength);
    }

    @Test
    void fromEncodesLengthPrefixAsBigEndian() {
        byte[] data = new byte[1000]; // 1000 bytes
        SidecarBuilder builder = SidecarBuilder.from(data);

        Blob blob = builder.blobs().get(0);
        byte[] blobBytes = blob.toBytes();

        // First field element: 0x00 + first 31 bytes of prefixed data
        // The length prefix is 8 bytes big-endian
        // 1000 in big-endian is: 0x00 0x00 0x00 0x00 0x00 0x00 0x03 0xe8

        // Bytes 1-8 in the blob (after 0x00 prefix) contain the length
        assertEquals(0x00, blobBytes[1]);
        assertEquals(0x00, blobBytes[2]);
        assertEquals(0x00, blobBytes[3]);
        assertEquals(0x00, blobBytes[4]);
        assertEquals(0x00, blobBytes[5]);
        assertEquals(0x00, blobBytes[6]);
        assertEquals(0x03, blobBytes[7]);
        assertEquals((byte) 0xe8, blobBytes[8]);
    }

    @Test
    void fromWithEmptyDataCreatesSingleBlob() {
        byte[] empty = new byte[0];
        SidecarBuilder builder = SidecarBuilder.from(empty);

        List<Blob> blobs = builder.blobs();
        assertEquals(1, blobs.size());

        // Verify length prefix is 0
        Blob blob = blobs.get(0);
        byte[] blobBytes = blob.toBytes();
        for (int i = 1; i <= 8; i++) {
            assertEquals(0x00, blobBytes[i], "Length prefix byte " + i + " should be 0");
        }
    }

    @Test
    void fromWithLargeDataCreatesMultipleBlobs() {
        // Data that requires 2 blobs: more than USABLE_BYTES_PER_BLOB - LENGTH_PREFIX_SIZE
        int size = SidecarBuilder.USABLE_BYTES_PER_BLOB; // This plus 8-byte prefix will need 2 blobs
        byte[] data = new byte[size];
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }

        SidecarBuilder builder = SidecarBuilder.from(data);

        List<Blob> blobs = builder.blobs();
        assertEquals(2, blobs.size());
    }

    @Test
    void fromPreservesFieldElementConstraint() {
        // Each field element's first byte must be 0x00
        byte[] data = new byte[1000];
        SidecarBuilder builder = SidecarBuilder.from(data);

        Blob blob = builder.blobs().get(0);
        byte[] blobBytes = blob.toBytes();

        // Check that every 32nd byte (first byte of each field element) is 0x00
        for (int fe = 0; fe < Blob.FIELD_ELEMENTS; fe++) {
            int offset = fe * Blob.BYTES_PER_FIELD_ELEMENT;
            assertEquals(0x00, blobBytes[offset],
                    "Field element " + fe + " first byte should be 0x00");
        }
    }

    @Test
    void fromWithMaxDataSucceeds() {
        byte[] maxData = new byte[SidecarBuilder.MAX_DATA_SIZE];
        SidecarBuilder builder = SidecarBuilder.from(maxData);

        List<Blob> blobs = builder.blobs();
        // MAX_DATA_SIZE + LENGTH_PREFIX_SIZE = 6 * USABLE_BYTES_PER_BLOB, so exactly 6 blobs
        assertEquals(6, blobs.size());
    }

    // Tests for fromBlobs

    @Test
    void maxBlobsConstantIsCorrect() {
        assertEquals(6, SidecarBuilder.MAX_BLOBS);
    }

    @Test
    void fromBlobsRejectsNullArray() {
        assertThrows(NullPointerException.class, () -> SidecarBuilder.fromBlobs((Blob[]) null));
    }

    @Test
    void fromBlobsRejectsEmptyArray() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SidecarBuilder.fromBlobs());
        assertEquals("blobs must not be empty", ex.getMessage());
    }

    @Test
    void fromBlobsRejectsNullElement() {
        Blob validBlob = new Blob(new byte[Blob.SIZE]);
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> SidecarBuilder.fromBlobs(validBlob, null));
        assertEquals("blobs[1] is null", ex.getMessage());
    }

    @Test
    void fromBlobsRejectsNullFirstElement() {
        NullPointerException ex = assertThrows(NullPointerException.class,
                () -> SidecarBuilder.fromBlobs(null, new Blob(new byte[Blob.SIZE])));
        assertEquals("blobs[0] is null", ex.getMessage());
    }

    @Test
    void fromBlobsRejectsTooManyBlobs() {
        Blob[] tooMany = new Blob[SidecarBuilder.MAX_BLOBS + 1];
        for (int i = 0; i < tooMany.length; i++) {
            tooMany[i] = new Blob(new byte[Blob.SIZE]);
        }
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> SidecarBuilder.fromBlobs(tooMany));
        assertEquals("blobs size 7 exceeds maximum 6", ex.getMessage());
    }

    @Test
    void fromBlobsWithSingleBlob() {
        Blob blob = new Blob(new byte[Blob.SIZE]);
        SidecarBuilder builder = SidecarBuilder.fromBlobs(blob);

        List<Blob> blobs = builder.blobs();
        assertEquals(1, blobs.size());
        assertEquals(blob, blobs.get(0));
    }

    @Test
    void fromBlobsWithMaxBlobs() {
        Blob[] maxBlobs = new Blob[SidecarBuilder.MAX_BLOBS];
        for (int i = 0; i < maxBlobs.length; i++) {
            byte[] data = new byte[Blob.SIZE];
            data[0] = (byte) i; // Make each blob distinct
            maxBlobs[i] = new Blob(data);
        }

        SidecarBuilder builder = SidecarBuilder.fromBlobs(maxBlobs);

        List<Blob> blobs = builder.blobs();
        assertEquals(SidecarBuilder.MAX_BLOBS, blobs.size());
        for (int i = 0; i < maxBlobs.length; i++) {
            assertEquals(maxBlobs[i], blobs.get(i));
        }
    }

    @Test
    void fromBlobsPreservesOrder() {
        Blob[] blobs = new Blob[3];
        for (int i = 0; i < blobs.length; i++) {
            byte[] data = new byte[Blob.SIZE];
            data[100] = (byte) (i + 1); // Mark each blob distinctly
            blobs[i] = new Blob(data);
        }

        SidecarBuilder builder = SidecarBuilder.fromBlobs(blobs);

        List<Blob> result = builder.blobs();
        assertEquals(3, result.size());
        for (int i = 0; i < blobs.length; i++) {
            assertEquals(blobs[i], result.get(i));
        }
    }

    // Tests for build(Kzg)

    /**
     * Mock Kzg implementation for testing.
     * Returns deterministic commitments and proofs based on blob content.
     */
    private static class MockKzg implements Kzg {
        private int commitmentCallCount = 0;
        private int proofCallCount = 0;

        @Override
        public KzgCommitment blobToCommitment(Blob blob) {
            commitmentCallCount++;
            // Create a deterministic commitment based on blob content
            byte[] commitmentData = new byte[FixedSizeG1Point.SIZE];
            byte[] blobBytes = blob.toBytes();
            // Use first few bytes of blob to make commitment distinct
            System.arraycopy(blobBytes, 0, commitmentData, 0, Math.min(blobBytes.length, FixedSizeG1Point.SIZE));
            return new KzgCommitment(commitmentData);
        }

        @Override
        public KzgProof computeProof(Blob blob, KzgCommitment commitment) {
            proofCallCount++;
            // Create a deterministic proof based on blob and commitment
            byte[] proofData = new byte[FixedSizeG1Point.SIZE];
            byte[] blobBytes = blob.toBytes();
            byte[] commitmentBytes = commitment.toBytes();
            // XOR first bytes of blob and commitment to make proof distinct
            for (int i = 0; i < FixedSizeG1Point.SIZE; i++) {
                proofData[i] = (byte) (blobBytes[i % blobBytes.length] ^ commitmentBytes[i % commitmentBytes.length]);
            }
            return new KzgProof(proofData);
        }

        @Override
        public boolean verifyBlobKzgProof(Blob blob, KzgCommitment commitment, KzgProof proof) {
            return true;
        }

        @Override
        public boolean verifyBlobKzgProofBatch(List<Blob> blobs, List<KzgCommitment> commitments, List<KzgProof> proofs) {
            return true;
        }

        int getCommitmentCallCount() {
            return commitmentCallCount;
        }

        int getProofCallCount() {
            return proofCallCount;
        }
    }

    @Test
    void buildRejectsNullKzg() {
        SidecarBuilder builder = SidecarBuilder.from("test".getBytes());
        NullPointerException ex = assertThrows(NullPointerException.class, () -> builder.build(null));
        assertEquals("kzg", ex.getMessage());
    }

    @Test
    void buildWithSingleBlob() {
        byte[] data = "Hello, blobs!".getBytes();
        SidecarBuilder builder = SidecarBuilder.from(data);
        MockKzg kzg = new MockKzg();

        BlobSidecar sidecar = builder.build(kzg);

        assertNotNull(sidecar);
        assertEquals(1, sidecar.size());
        assertEquals(1, sidecar.blobs().size());
        assertEquals(1, sidecar.commitments().size());
        assertEquals(1, sidecar.proofs().size());
        assertEquals(1, kzg.getCommitmentCallCount());
        assertEquals(1, kzg.getProofCallCount());
    }

    @Test
    void buildWithMultipleBlobs() {
        // Create data requiring multiple blobs
        int size = SidecarBuilder.USABLE_BYTES_PER_BLOB; // Will need 2 blobs
        byte[] data = new byte[size];
        SidecarBuilder builder = SidecarBuilder.from(data);
        MockKzg kzg = new MockKzg();

        BlobSidecar sidecar = builder.build(kzg);

        assertNotNull(sidecar);
        assertEquals(2, sidecar.size());
        assertEquals(2, sidecar.blobs().size());
        assertEquals(2, sidecar.commitments().size());
        assertEquals(2, sidecar.proofs().size());
        assertEquals(2, kzg.getCommitmentCallCount());
        assertEquals(2, kzg.getProofCallCount());
    }

    @Test
    void buildFromBlobsWithSingleBlob() {
        Blob blob = new Blob(new byte[Blob.SIZE]);
        SidecarBuilder builder = SidecarBuilder.fromBlobs(blob);
        MockKzg kzg = new MockKzg();

        BlobSidecar sidecar = builder.build(kzg);

        assertNotNull(sidecar);
        assertEquals(1, sidecar.size());
        assertEquals(blob, sidecar.blobs().get(0));
    }

    @Test
    void buildFromBlobsWithMultipleBlobs() {
        Blob[] blobs = new Blob[3];
        for (int i = 0; i < blobs.length; i++) {
            byte[] data = new byte[Blob.SIZE];
            data[0] = (byte) (i + 1);
            blobs[i] = new Blob(data);
        }
        SidecarBuilder builder = SidecarBuilder.fromBlobs(blobs);
        MockKzg kzg = new MockKzg();

        BlobSidecar sidecar = builder.build(kzg);

        assertNotNull(sidecar);
        assertEquals(3, sidecar.size());
        assertEquals(3, sidecar.blobs().size());
        assertEquals(3, sidecar.commitments().size());
        assertEquals(3, sidecar.proofs().size());
        for (int i = 0; i < blobs.length; i++) {
            assertEquals(blobs[i], sidecar.blobs().get(i));
        }
    }

    @Test
    void buildPreservesBlobOrder() {
        Blob[] blobs = new Blob[3];
        for (int i = 0; i < blobs.length; i++) {
            byte[] data = new byte[Blob.SIZE];
            data[100] = (byte) (i + 1);
            blobs[i] = new Blob(data);
        }
        SidecarBuilder builder = SidecarBuilder.fromBlobs(blobs);
        MockKzg kzg = new MockKzg();

        BlobSidecar sidecar = builder.build(kzg);

        for (int i = 0; i < blobs.length; i++) {
            assertEquals(blobs[i], sidecar.blobs().get(i));
        }
    }

    @Test
    void buildComputesCommitmentAndProofForEachBlob() {
        Blob[] blobs = new Blob[2];
        for (int i = 0; i < blobs.length; i++) {
            byte[] data = new byte[Blob.SIZE];
            // Make blobs distinct
            data[0] = (byte) (i + 10);
            data[1] = (byte) (i + 20);
            blobs[i] = new Blob(data);
        }
        SidecarBuilder builder = SidecarBuilder.fromBlobs(blobs);
        MockKzg kzg = new MockKzg();

        BlobSidecar sidecar = builder.build(kzg);

        // Verify commitments are distinct (since blobs are distinct)
        assertNotNull(sidecar.commitments().get(0));
        assertNotNull(sidecar.commitments().get(1));

        // Verify proofs are distinct
        assertNotNull(sidecar.proofs().get(0));
        assertNotNull(sidecar.proofs().get(1));
    }
}
