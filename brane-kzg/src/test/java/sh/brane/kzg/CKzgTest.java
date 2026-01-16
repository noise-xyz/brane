// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.kzg;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import sh.brane.core.crypto.Kzg;
import sh.brane.core.error.KzgException;
import sh.brane.core.types.Blob;
import sh.brane.core.types.FixedSizeG1Point;
import sh.brane.core.types.KzgCommitment;
import sh.brane.core.types.KzgProof;

class CKzgTest {

    private static final int FIELD_ELEMENT_SIZE = 32;
    private static final int FIELD_ELEMENTS_PER_BLOB = 4096;

    private static Kzg kzg;

    @BeforeAll
    static void setUp() {
        kzg = CKzg.loadFromClasspath();
    }

    /**
     * Creates a valid blob for testing.
     * Each 32-byte field element must have its high byte as 0x00
     * to be a valid BLS12-381 field element.
     */
    private static Blob createValidBlob(byte fillValue) {
        byte[] blobData = new byte[Blob.SIZE];
        for (int fe = 0; fe < FIELD_ELEMENTS_PER_BLOB; fe++) {
            int offset = fe * FIELD_ELEMENT_SIZE;
            // First byte must be 0x00 for valid field element
            blobData[offset] = 0x00;
            // Fill remaining 31 bytes with test data
            for (int i = 1; i < FIELD_ELEMENT_SIZE; i++) {
                blobData[offset + i] = fillValue;
            }
        }
        return new Blob(blobData);
    }

    @Test
    void loadFromClasspathSucceeds() {
        // Verify the kzg instance loaded in @BeforeAll is valid
        // Note: The native library only allows loading the trusted setup once,
        // so we validate that the single load succeeded
        assertNotNull(kzg);
        assertInstanceOf(CKzg.class, kzg);
    }

    @Test
    void blobToCommitmentReturns48ByteCommitment() {
        Blob blob = createValidBlob((byte) 0x42);

        KzgCommitment commitment = kzg.blobToCommitment(blob);

        assertNotNull(commitment);
        assertEquals(FixedSizeG1Point.SIZE, commitment.toBytes().length);
        assertEquals(48, commitment.toBytes().length);
    }

    @Test
    void computeProofReturns48ByteProof() {
        Blob blob = createValidBlob((byte) 0x42);
        KzgCommitment commitment = kzg.blobToCommitment(blob);

        KzgProof proof = kzg.computeProof(blob, commitment);

        assertNotNull(proof);
        assertEquals(FixedSizeG1Point.SIZE, proof.toBytes().length);
        assertEquals(48, proof.toBytes().length);
    }

    @Test
    void verifyProofReturnsTrueForValidData() {
        Blob blob = createValidBlob((byte) 0x42);
        KzgCommitment commitment = kzg.blobToCommitment(blob);
        KzgProof proof = kzg.computeProof(blob, commitment);

        boolean isValid = kzg.verifyBlobKzgProof(blob, commitment, proof);

        assertTrue(isValid);
    }

    @Test
    void verifyProofReturnsFalseForInvalidData() {
        // Create first blob and compute valid commitment/proof
        Blob blob1 = createValidBlob((byte) 0x42);
        KzgCommitment commitment1 = kzg.blobToCommitment(blob1);
        KzgProof proof1 = kzg.computeProof(blob1, commitment1);

        // Create different blob with different content
        Blob blob2 = createValidBlob((byte) 0x99);

        // Verify using mismatched blob - should return false
        boolean isValid = kzg.verifyBlobKzgProof(blob2, commitment1, proof1);

        assertFalse(isValid);
    }

    @Test
    void verifyBatchReturnsTrueForValidData() {
        Blob blob1 = createValidBlob((byte) 0x11);
        Blob blob2 = createValidBlob((byte) 0x22);
        Blob blob3 = createValidBlob((byte) 0x33);

        KzgCommitment commitment1 = kzg.blobToCommitment(blob1);
        KzgCommitment commitment2 = kzg.blobToCommitment(blob2);
        KzgCommitment commitment3 = kzg.blobToCommitment(blob3);

        KzgProof proof1 = kzg.computeProof(blob1, commitment1);
        KzgProof proof2 = kzg.computeProof(blob2, commitment2);
        KzgProof proof3 = kzg.computeProof(blob3, commitment3);

        boolean isValid = kzg.verifyBlobKzgProofBatch(
                List.of(blob1, blob2, blob3),
                List.of(commitment1, commitment2, commitment3),
                List.of(proof1, proof2, proof3));

        assertTrue(isValid);
    }

    @Test
    void verifyBatchReturnsFalseForInvalidData() {
        Blob blob1 = createValidBlob((byte) 0x11);
        Blob blob2 = createValidBlob((byte) 0x22);
        Blob wrongBlob = createValidBlob((byte) 0x99);

        KzgCommitment commitment1 = kzg.blobToCommitment(blob1);
        KzgCommitment commitment2 = kzg.blobToCommitment(blob2);

        KzgProof proof1 = kzg.computeProof(blob1, commitment1);
        KzgProof proof2 = kzg.computeProof(blob2, commitment2);

        // Use wrongBlob instead of blob2 - batch should fail
        boolean isValid = kzg.verifyBlobKzgProofBatch(
                List.of(blob1, wrongBlob),
                List.of(commitment1, commitment2),
                List.of(proof1, proof2));

        assertFalse(isValid);
    }

    @Test
    void verifyBatchReturnsTrueForEmptyLists() {
        boolean isValid = kzg.verifyBlobKzgProofBatch(
                List.of(),
                List.of(),
                List.of());

        assertTrue(isValid);
    }

    @Test
    void verifyBatchThrowsOnSizeMismatch() {
        Blob blob1 = createValidBlob((byte) 0x11);
        Blob blob2 = createValidBlob((byte) 0x22);

        KzgCommitment commitment1 = kzg.blobToCommitment(blob1);
        KzgProof proof1 = kzg.computeProof(blob1, commitment1);

        // 2 blobs but only 1 commitment and 1 proof
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                kzg.verifyBlobKzgProofBatch(
                        List.of(blob1, blob2),
                        List.of(commitment1),
                        List.of(proof1)));

        assertTrue(ex.getMessage().contains("Lists must have same size"));
    }

    // Tests for loadTrustedSetup(String)

    @Test
    void loadTrustedSetupThrowsOnNullPath() {
        assertThrows(NullPointerException.class, () -> CKzg.loadTrustedSetup(null));
    }

    @Test
    void loadTrustedSetupThrowsWhenAlreadyLoaded() throws Exception {
        // Get the path to the trusted setup from classpath resource
        var resourceUrl = CKzgTest.class.getResource("/trusted_setup.txt");
        assertNotNull(resourceUrl, "trusted_setup.txt should be on classpath");

        String path = java.nio.file.Path.of(resourceUrl.toURI()).toString();

        // The native library maintains global state and was already loaded in @BeforeAll.
        // Attempting to reload throws KzgException with "already loaded" message.
        // Note: This does NOT corrupt the global state since the native library
        // checks for existing setup before attempting to free/reload.
        KzgException ex = assertThrows(KzgException.class, () ->
                CKzg.loadTrustedSetup(path));

        assertTrue(ex.getMessage().contains("Failed to load trusted setup"));
        assertNotNull(ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("already loaded"));
    }

    // Note: Testing loadTrustedSetup with a non-existent file is intentionally omitted.
    // The native c-kzg-4844 library frees any existing trusted setup before attempting
    // to load a new one. If the load fails (e.g., file not found), the global state is
    // corrupted and all subsequent KZG operations fail. This is a limitation of the
    // native library's global state design.
}
