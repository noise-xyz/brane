package io.brane.core.error;

/**
 * Base class for transaction-related failures (signing, validation, send).
 */
public non-sealed class TxnException extends BraneException {

    public TxnException(final String message) {
        super(message);
    }

    public TxnException(final String message, final Throwable cause) {
        super(message, cause);
    }

    public boolean isInvalidSender() {
        final String msg = getMessage();
        return msg != null && msg.toLowerCase().contains("invalid sender");
    }

    public boolean isChainIdMismatch() {
        final String msg = getMessage();
        return msg != null && msg.toLowerCase().contains("chain id mismatch");
    }
}
