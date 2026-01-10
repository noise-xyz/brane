package io.brane.core.crypto;

import io.brane.core.error.KzgException;
import io.brane.core.types.Blob;
import io.brane.core.types.KzgCommitment;
import io.brane.core.types.KzgProof;

/**
 * Interface for KZG (Kate-Zaverucha-Goldberg) commitment operations.
 * <p>
 * KZG commitments are used in EIP-4844 blob transactions to enable
 * data availability sampling. Implementations of this interface provide
 * cryptographic operations for computing and verifying blob commitments.
 * <p>
 * Typical implementations will use a trusted setup ceremony output
 * (e.g., from the Ethereum KZG ceremony) to initialize the cryptographic
 * parameters.
 *
 * @since 0.2.0
 */
public interface Kzg {

    /**
     * Verifies that a KZG proof is valid for the given blob and commitment.
     * <p>
     * This method checks that the proof correctly proves the blob data
     * corresponds to the given commitment.
     *
     * @param blob the blob data
     * @param commitment the KZG commitment
     * @param proof the KZG proof to verify
     * @return {@code true} if the proof is valid, {@code false} otherwise
     * @throws KzgException if verification fails due to invalid inputs or internal error
     * @throws NullPointerException if any argument is null
     */
    boolean verifyBlobKzgProof(Blob blob, KzgCommitment commitment, KzgProof proof);

    /**
     * Verifies multiple blob-commitment-proof tuples in a batch.
     * <p>
     * This method is more efficient than verifying each tuple individually
     * when multiple blobs need to be verified.
     *
     * @param blobs the blob data
     * @param commitments the KZG commitments
     * @param proofs the KZG proofs
     * @return {@code true} if all proofs are valid, {@code false} if any is invalid
     * @throws KzgException if verification fails due to invalid inputs or internal error
     * @throws IllegalArgumentException if the lists have different sizes
     * @throws NullPointerException if any argument is null
     */
    boolean verifyBlobKzgProofBatch(
            java.util.List<Blob> blobs,
            java.util.List<KzgCommitment> commitments,
            java.util.List<KzgProof> proofs);
}
