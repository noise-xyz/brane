package io.brane.rpc;

import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * Internal utility for retrying RPC calls with exponential backoff.
 * 
 * <p>
 * This class implements a retry strategy for transient RPC failures:
 * <ul>
 * <li><strong>Retryable errors:</strong> Network timeouts, connection resets,
 * temporary node unavailability</li>
 * <li><strong>Non-retryable errors:</strong> Reverts, insufficient funds,
 * invalid parameters</li>
 * <li><strong>Backoff:</strong> Linear backoff (200ms × attempt number)</li>
 * </ul>
 * 
 * <p>
 * <strong>Retry Conditions:</strong>
 * <ul>
 * <li>✅ "header not found" - Block not yet propagated</li>
 * <li>✅ "timeout" - Network or node timeout</li>
 * <li>✅ "connection reset" - Network hiccup</li>
 * <li>✅ "underpriced" / "nonce too low" - Mempool issues</li>
 * <li>❌ Revert data (0x...) - Smart contract reverted</li>
 * <li>❌ "insufficient funds" - User error</li>
 * </ul>
 * 
 * <p>
 * <strong>Backoff Strategy:</strong>
 * <ul>
 * <li>Attempt 1: No delay</li>
 * <li>Attempt 2: 200ms delay</li>
 * <li>Attempt 3: 400ms delay</li>
 * <li>Total delay for 3 attempts: 600ms</li>
 * </ul>
 * 
 * <p>
 * <strong>Thread Interruption:</strong> If the calling thread is interrupted
 * during backoff, the retry loop terminates and throws the last encountered
 * exception.
 * 
 * @see RpcException
 * @see RevertException
 */
final class RpcRetry {

    private RpcRetry() {
    }

    /**
     * Executes the supplier with retry on transient failures.
     * 
     * <p>
     * Only retries on network errors and specific RPC errors.
     * Reverts and user errors are thrown immediately without retry.
     * 
     * @param <T>         the return type
     * @param supplier    the operation to retry
     * @param maxAttempts maximum number of attempts (must be >= 1)
     * @return the result from the supplier
     * @throws RpcException             if all retries fail or error is
     *                                  non-retryable
     * @throws RevertException          if the operation reverts (never retried)
     * @throws IllegalArgumentException if maxAttempts < 1
     */
    static <T> T run(final Supplier<T> supplier, final int maxAttempts) {
        Objects.requireNonNull(supplier, "supplier");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        RpcException lastException = null;
        IOException lastIo = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (RevertException e) {
                throw e;
            } catch (RpcException e) {
                lastException = e;
                if (!isRetryableRpcError(e) || attempt == maxAttempts) {
                    throw e;
                }
            } catch (RuntimeException e) {
                final IOException io = unwrapIo(e);
                if (io == null || attempt == maxAttempts) {
                    throw e;
                }
                lastIo = io;
            }

            final long delayMillis = backoff(attempt);
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (lastException != null) {
                    throw lastException;
                }
                if (lastIo != null) {
                    throw new RuntimeException(lastIo);
                }
                throw new RuntimeException("Interrupted while retrying", e);
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        if (lastIo != null) {
            throw new RuntimeException(lastIo);
        }
        throw new IllegalStateException("Retry finished without result or exception");
    }

    static boolean isRetryableRpcError(final RpcException e) {
        if (e == null || e.getMessage() == null) {
            return false;
        }
        final String message = e.getMessage().toLowerCase(Locale.ROOT);
        if (isLikelyRevert(e.data())) {
            return false;
        }
        if (message.contains("insufficient funds")) {
            return false;
        }
        return message.contains("header not found")
                || message.contains("timeout")
                || message.contains("connection reset")
                || message.contains("temporary unavailable")
                || message.contains("try again")
                || message.contains("underpriced")
                || message.contains("nonce too low");
    }

    private static long backoff(final int attempt) {
        final long base = Duration.ofMillis(200).toMillis();
        return base * attempt;
    }

    private static boolean isLikelyRevert(final String data) {
        return data != null && data.startsWith("0x") && data.length() > 10;
    }

    private static IOException unwrapIo(final RuntimeException e) {
        Throwable current = e;
        while (current != null) {
            if (current instanceof IOException io) {
                return io;
            }
            current = current.getCause();
        }
        return null;
    }
}
