package io.brane.core.types;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.brane.core.crypto.Kzg;
import io.brane.core.error.KzgException;

/**
 * Represents the sidecar data for EIP-4844 blob transactions.
 * <p>
 * A blob sidecar contains the blobs, their KZG commitments, and proofs that
 * are transmitted alongside a blob transaction. The sidecar data is not
 * included in the execution payload but is available for data availability
 * verification.
 * <p>
 * Each blob transaction can contain up to {@value #MAX_BLOBS} blobs. The
 * number of blobs, commitments, and proofs must all be equal.
 * <p>
 * This class is immutable and thread-safe.
 *
 * @since 0.2.0
 */
public final class BlobSidecar {

    /**
     * Maximum number of blobs per transaction (EIP-4844).
     */
    public static final int MAX_BLOBS = 6;

    private final List<Blob> blobs;
    private final List<KzgCommitment> commitments;
    private final List<KzgProof> proofs;

    /**
     * Cached versioned hashes. Computed lazily and cached for performance.
     * Uses volatile for safe publication across threads.
     */
    private volatile List<Hash> versionedHashes;

    /**
     * Creates a new blob sidecar with the given blobs, commitments, and proofs.
     *
     * @param blobs the blob data
     * @param commitments the KZG commitments corresponding to each blob
     * @param proofs the KZG proofs corresponding to each blob
     * @throws NullPointerException if any argument is null or contains null elements
     * @throws IllegalArgumentException if the lists are empty, have different sizes,
     *         or exceed {@value #MAX_BLOBS} elements
     */
    public BlobSidecar(
            final List<Blob> blobs,
            final List<KzgCommitment> commitments,
            final List<KzgProof> proofs) {
        Objects.requireNonNull(blobs, "blobs");
        Objects.requireNonNull(commitments, "commitments");
        Objects.requireNonNull(proofs, "proofs");

        if (blobs.isEmpty()) {
            throw new IllegalArgumentException("blobs must not be empty");
        }
        if (blobs.size() > MAX_BLOBS) {
            throw new IllegalArgumentException(
                    "blobs size " + blobs.size() + " exceeds maximum " + MAX_BLOBS);
        }
        if (blobs.size() != commitments.size()) {
            throw new IllegalArgumentException(
                    "blobs size " + blobs.size() + " does not match commitments size " + commitments.size());
        }
        if (blobs.size() != proofs.size()) {
            throw new IllegalArgumentException(
                    "blobs size " + blobs.size() + " does not match proofs size " + proofs.size());
        }

        // Validate no null elements before making defensive copies
        for (int i = 0; i < blobs.size(); i++) {
            if (blobs.get(i) == null) {
                throw new NullPointerException("blobs[" + i + "] is null");
            }
            if (commitments.get(i) == null) {
                throw new NullPointerException("commitments[" + i + "] is null");
            }
            if (proofs.get(i) == null) {
                throw new NullPointerException("proofs[" + i + "] is null");
            }
        }

        // Make defensive copies
        this.blobs = List.copyOf(blobs);
        this.commitments = List.copyOf(commitments);
        this.proofs = List.copyOf(proofs);
    }

    /**
     * Returns the blobs in this sidecar.
     *
     * @return an unmodifiable list of blobs
     */
    public List<Blob> blobs() {
        return blobs;
    }

    /**
     * Returns the KZG commitments in this sidecar.
     *
     * @return an unmodifiable list of commitments
     */
    public List<KzgCommitment> commitments() {
        return commitments;
    }

    /**
     * Returns the KZG proofs in this sidecar.
     *
     * @return an unmodifiable list of proofs
     */
    public List<KzgProof> proofs() {
        return proofs;
    }

    /**
     * Returns the versioned hashes derived from the commitments.
     * <p>
     * The versioned hashes are used in the transaction's blob_versioned_hashes
     * field and are derived from the KZG commitments using SHA-256 with a
     * version prefix.
     * <p>
     * The result is cached for subsequent calls. This uses a benign race pattern
     * (like {@link String#hashCode()}). Multiple threads may compute the hashes
     * simultaneously on first access, but all will produce the same result since
     * the computation is idempotent.
     *
     * @return an unmodifiable list of versioned hashes
     */
    public List<Hash> versionedHashes() {
        List<Hash> result = versionedHashes;
        if (result == null) {
            var hashes = new ArrayList<Hash>(commitments.size());
            for (KzgCommitment commitment : commitments) {
                hashes.add(commitment.toVersionedHash());
            }
            result = List.copyOf(hashes);
            versionedHashes = result;
        }
        return result;
    }

    /**
     * Returns the number of blobs in this sidecar.
     *
     * @return the number of blobs
     */
    public int size() {
        return blobs.size();
    }

    /**
     * Validates that all proofs are correct for their corresponding blobs
     * and commitments using the provided KZG implementation.
     *
     * @param kzg the KZG implementation to use for verification
     * @throws KzgException if any proof is invalid
     * @throws NullPointerException if kzg is null
     */
    public void validate(final Kzg kzg) {
        Objects.requireNonNull(kzg, "kzg");
        boolean valid = kzg.verifyBlobKzgProofBatch(blobs, commitments, proofs);
        if (!valid) {
            throw KzgException.invalidProof("Blob KZG proof verification failed");
        }
    }

    /**
     * Validates that the versioned hashes match the expected values.
     * <p>
     * This is used to verify that the sidecar corresponds to a specific
     * blob transaction by checking that the versioned hashes derived from
     * the commitments match those in the transaction.
     *
     * @param expectedHashes the expected versioned hashes from the transaction
     * @throws IllegalArgumentException if the hashes do not match
     * @throws NullPointerException if expectedHashes is null
     */
    public void validateHashes(final List<Hash> expectedHashes) {
        Objects.requireNonNull(expectedHashes, "expectedHashes");
        List<Hash> actual = versionedHashes();
        if (actual.size() != expectedHashes.size()) {
            throw new IllegalArgumentException(
                    "versioned hashes size " + actual.size() +
                    " does not match expected size " + expectedHashes.size());
        }
        for (int i = 0; i < actual.size(); i++) {
            if (!actual.get(i).equals(expectedHashes.get(i))) {
                throw new IllegalArgumentException(
                        "versioned hash at index " + i + " does not match: expected " +
                        expectedHashes.get(i).value() + " but got " + actual.get(i).value());
            }
        }
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        BlobSidecar that = (BlobSidecar) o;
        return blobs.equals(that.blobs) &&
               commitments.equals(that.commitments) &&
               proofs.equals(that.proofs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(blobs, commitments, proofs);
    }

    @Override
    public String toString() {
        return "BlobSidecar[size=" + blobs.size() + "]";
    }
}
