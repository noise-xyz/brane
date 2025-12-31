package io.brane.core.error;

/**
 * Thrown when a transaction is rejected due to an invalid sender/signature.
 *
 * @since 0.1.0-alpha
 */
public final class InvalidSenderException extends TxnException {

    public InvalidSenderException(final String message) {
        super(message);
    }

    public InvalidSenderException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
