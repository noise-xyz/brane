package io.brane.core.error;

/**
 * Thrown when ABI outputs cannot be decoded.
 *
 * @since 0.1.0-alpha
 */
public final class AbiDecodingException extends BraneException {

    public AbiDecodingException(final String message) {
        super(message);
    }

    public AbiDecodingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
