package io.brane.core.error;

/**
 * Base runtime exception for Brane-specific failures.
 */
public sealed class BraneException extends RuntimeException
        permits AbiDecodingException,
                AbiEncodingException,
                RevertException,
                RpcException,
                TxnException {

    public BraneException(final String message) {
        super(message);
    }

    public BraneException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
