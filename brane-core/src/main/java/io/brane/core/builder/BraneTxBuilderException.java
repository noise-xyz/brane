package io.brane.core.builder;

/** Runtime exception thrown when a transaction builder is in an invalid state. */
public final class BraneTxBuilderException extends RuntimeException {
    public BraneTxBuilderException(final String message) {
        super(message);
    }

    public BraneTxBuilderException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
