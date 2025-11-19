package io.brane.core;

/**
 * Base runtime exception for any Brane specific failures.
 */
public sealed class BraneException extends RuntimeException
        permits RpcException, RevertException {

    public BraneException(final String message) {
        super(message);
    }

    public BraneException(final String message, final Throwable cause) {
        super(message, cause);
    }
}
