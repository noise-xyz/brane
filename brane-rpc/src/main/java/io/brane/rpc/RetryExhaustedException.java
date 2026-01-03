package io.brane.rpc;

import org.jspecify.annotations.Nullable;

import io.brane.core.error.RpcException;

/**
 * Thrown when all retry attempts have been exhausted.
 * <p>
 * This exception wraps the final failed attempt and provides context about
 * the retry history, including the number of attempts made and the total
 * time spent retrying.
 * <p>
 * Use {@link #getSuppressed()} to access all failed attempts in order.
 * The first suppressed exception is from attempt 1, the second from attempt 2, etc.
 * The cause of this exception is always the final (most recent) failure.
 * <p>
 * <strong>Example:</strong>
 * <pre>{@code
 * try {
 *     publicClient.getLatestBlock();
 * } catch (RetryExhaustedException e) {
 *     System.out.println("Failed after " + e.getAttemptCount() + " attempts");
 *     System.out.println("Total retry time: " + e.getTotalRetryDurationMs() + "ms");
 *     for (Throwable suppressed : e.getSuppressed()) {
 *         System.out.println("  - " + suppressed.getMessage());
 *     }
 *     // Access original RPC error if available
 *     if (e.getCause() instanceof RpcException rpc) {
 *         System.out.println("RPC Error: " + rpc.code() + " - " + rpc.getMessage());
 *     }
 * }
 * }</pre>
 *
 * @since 0.1.0-alpha
 */
public final class RetryExhaustedException extends RuntimeException {

    /** Explicit UID for serialization stability across class evolution. */
    private static final long serialVersionUID = 1L;

    private final int attemptCount;
    private final long totalRetryDurationMs;

    /**
     * Creates a new RetryExhaustedException.
     *
     * @param attemptCount the total number of attempts made
     * @param totalRetryDurationMs the total time spent retrying in milliseconds
     * @param cause the final failure that triggered this exception
     */
    public RetryExhaustedException(
            final int attemptCount,
            final long totalRetryDurationMs,
            final Throwable cause) {
        super(
            String.format("All %d retry attempts exhausted (total: %dms)", attemptCount, totalRetryDurationMs),
            cause
        );
        this.attemptCount = attemptCount;
        this.totalRetryDurationMs = totalRetryDurationMs;
    }

    /**
     * Returns the total number of attempts made before giving up.
     *
     * @return the number of attempts
     */
    public int getAttemptCount() {
        return attemptCount;
    }

    /**
     * Returns the total time spent retrying, including backoff delays.
     *
     * @return the total duration in milliseconds
     */
    public long getTotalRetryDurationMs() {
        return totalRetryDurationMs;
    }

    /**
     * Returns the RPC error data from the final failure, if available.
     *
     * @return the error data string, or {@code null} if not an RPC error or no data
     */
    public @Nullable String getRpcErrorData() {
        if (getCause() instanceof RpcException rpc) {
            return rpc.data();
        }
        return null;
    }

    /**
     * Returns the RPC error code from the final failure, if available.
     *
     * @return the error code, or 0 if not an RPC error
     */
    public int getRpcErrorCode() {
        if (getCause() instanceof RpcException rpc) {
            return rpc.code();
        }
        return 0;
    }
}
