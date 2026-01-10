package io.brane.core.error;

/**
 * Exception for KZG commitment failures.
 *
 * <p>KZG (Kate-Zaverucha-Goldberg) commitments are used in EIP-4844 blob
 * transactions for data availability sampling.
 *
 * @since 0.2.0
 */
public final class KzgException extends BraneException {

    /**
     * Categorizes the type of KZG failure.
     */
    public enum Kind {
        /** Invalid blob data format or content. */
        INVALID_BLOB,
        /** Invalid KZG proof. */
        INVALID_PROOF,
        /** Trusted setup initialization or loading error. */
        SETUP_ERROR,
        /** Failed to compute KZG commitment. */
        COMMITMENT_ERROR,
        /** Failed to compute or verify KZG proof. */
        PROOF_ERROR
    }

    /** The category of KZG failure. */
    private final Kind kind;

    /**
     * Creates a new KzgException with the specified kind and message.
     *
     * @param kind the category of KZG failure
     * @param message the detail message
     */
    public KzgException(final Kind kind, final String message) {
        super(message);
        this.kind = kind;
    }

    /**
     * Creates a new KzgException with the specified kind, message, and cause.
     *
     * @param kind the category of KZG failure
     * @param message the detail message
     * @param cause the underlying cause
     */
    public KzgException(final Kind kind, final String message, final Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    /**
     * Returns the kind of KZG failure.
     *
     * @return the failure kind
     */
    public Kind kind() {
        return kind;
    }

    // ═══════════════════════════════════════════════════════════════
    // Factory methods for specific error conditions
    // ═══════════════════════════════════════════════════════════════

    /**
     * Creates an exception for invalid blob data.
     *
     * @param message description of the blob validation failure
     * @return a new KzgException with kind INVALID_BLOB
     */
    public static KzgException invalidBlob(final String message) {
        return new KzgException(Kind.INVALID_BLOB, message);
    }

    /**
     * Creates an exception for invalid KZG proof.
     *
     * @param message description of the proof validation failure
     * @return a new KzgException with kind INVALID_PROOF
     */
    public static KzgException invalidProof(final String message) {
        return new KzgException(Kind.INVALID_PROOF, message);
    }

    /**
     * Creates an exception for trusted setup errors.
     *
     * @param message description of the setup failure
     * @return a new KzgException with kind SETUP_ERROR
     */
    public static KzgException setupError(final String message) {
        return new KzgException(Kind.SETUP_ERROR, message);
    }

    /**
     * Creates an exception for trusted setup errors with a cause.
     *
     * @param message description of the setup failure
     * @param cause the underlying cause
     * @return a new KzgException with kind SETUP_ERROR
     */
    public static KzgException setupError(final String message, final Throwable cause) {
        return new KzgException(Kind.SETUP_ERROR, message, cause);
    }

    /**
     * Creates an exception for commitment computation errors.
     *
     * @param message description of the commitment failure
     * @return a new KzgException with kind COMMITMENT_ERROR
     */
    public static KzgException commitmentError(final String message) {
        return new KzgException(Kind.COMMITMENT_ERROR, message);
    }

    /**
     * Creates an exception for proof computation or verification errors.
     *
     * @param message description of the proof failure
     * @return a new KzgException with kind PROOF_ERROR
     */
    public static KzgException proofError(final String message) {
        return new KzgException(Kind.PROOF_ERROR, message);
    }
}
