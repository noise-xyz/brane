package io.brane.kzg;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import io.brane.core.crypto.Kzg;
import io.brane.core.types.Blob;
import io.brane.core.types.KzgCommitment;
import io.brane.core.types.KzgProof;

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
        assertEquals(KzgCommitment.SIZE, commitment.toBytes().length);
        assertEquals(48, commitment.toBytes().length);
    }

    @Test
    void computeProofReturns48ByteProof() {
        Blob blob = createValidBlob((byte) 0x42);
        KzgCommitment commitment = kzg.blobToCommitment(blob);

        KzgProof proof = kzg.computeProof(blob, commitment);

        assertNotNull(proof);
        assertEquals(KzgProof.SIZE, proof.toBytes().length);
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
}
