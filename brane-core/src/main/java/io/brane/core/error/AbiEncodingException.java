package io.brane.core.error;

/**
 * Thrown when ABI inputs cannot be encoded.
 */
public final class AbiEncodingException extends BraneException {

    public AbiEncodingException(final String message) {
        super(message);
    }

    public AbiEncodingException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
