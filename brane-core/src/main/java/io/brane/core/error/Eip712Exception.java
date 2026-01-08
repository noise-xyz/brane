package io.brane.core.error;

/**
 * Thrown when EIP-712 typed data encoding or signing fails.
 *
 * @since 0.1.0-alpha
 */
public final class Eip712Exception extends BraneException {

    public Eip712Exception(final String message) {
        super(message);
    }

    public Eip712Exception(final String message, final Throwable cause) {
        super(message, cause);
    }
}
