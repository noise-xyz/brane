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

    public KzgException(final String message) {
        super(message);
    }

    public KzgException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
