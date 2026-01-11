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
 * <h2>Thread Safety</h2>
 * Implementations of this interface MUST be thread-safe. All methods may be
 * called concurrently from multiple threads without external synchronization.
 * Implementations should document any internal synchronization mechanisms
 * or state management that ensures thread safety.
 *
 * @since 0.2.0
 */
public interface Kzg {

    /**
     * Computes a KZG commitment for the given blob.
     * <p>
     * The commitment is a 48-byte compressed G1 point on the BLS12-381 curve
     * that uniquely represents the blob data.
     *
     * @param blob the blob to commit to
     * @return the KZG commitment for the blob
     * @throws KzgException if commitment computation fails
     * @throws NullPointerException if blob is null
     */
    KzgCommitment blobToCommitment(Blob blob);

    /**
     * Computes a KZG proof for the given blob and commitment.
     * <p>
     * The proof enables verification that the blob corresponds to the
     * given commitment without requiring the full blob data.
     *
     * @param blob the blob data
     * @param commitment the KZG commitment for the blob
     * @return the KZG proof
     * @throws KzgException if proof computation fails
     * @throws NullPointerException if any argument is null
     */
    KzgProof computeProof(Blob blob, KzgCommitment commitment);

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
