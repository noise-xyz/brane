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
 * <li>✅ "rate limit" / "too many requests" / "429" - Provider rate limiting</li>
 * <li>✅ "internal error" / "-32603" - Transient server errors</li>
 * <li>✅ "server busy" / "overloaded" - Server capacity issues</li>
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
     * <p>
     * <strong>Exception Context:</strong> When all retries are exhausted, this method
     * throws a {@link RetryExhaustedException} that includes:
     * <ul>
     * <li>The total number of attempts made</li>
     * <li>The total time spent retrying (including backoff delays)</li>
     * <li>All failed attempts as suppressed exceptions (in order)</li>
     * </ul>
     *
     * @param <T>         the return type
     * @param supplier    the operation to retry
     * @param maxAttempts maximum number of attempts (must be >= 1)
     * @return the result from the supplier
     * @throws RpcException             if all retries fail or error is
     *                                  non-retryable
     * @throws RetryExhaustedException  if all retry attempts were exhausted
     * @throws RevertException          if the operation reverts (never retried)
     * @throws IllegalArgumentException if maxAttempts < 1
     */
    static <T> T run(final Supplier<T> supplier, final int maxAttempts) {
        Objects.requireNonNull(supplier, "supplier");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }

        final java.util.List<Throwable> failedAttempts = new java.util.ArrayList<>();
        final long startTime = System.currentTimeMillis();
        Throwable lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return supplier.get();
            } catch (RevertException e) {
                throw e;
            } catch (RpcException e) {
                lastException = e;
                failedAttempts.add(e);
                if (!isRetryableRpcError(e)) {
                    throw e;
                }
                if (attempt == maxAttempts) {
                    throw createRetryExhaustedException(failedAttempts, startTime);
                }
            } catch (RuntimeException e) {
                final IOException io = unwrapIo(e);
                if (io == null) {
                    throw e;
                }
                lastException = e;
                failedAttempts.add(e);
                if (attempt == maxAttempts) {
                    throw createRetryExhaustedException(failedAttempts, startTime);
                }
            }

            final long delayMillis = backoff(attempt);
            try {
                Thread.sleep(delayMillis);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                if (lastException != null) {
                    // Preserve InterruptedException context by adding it as suppressed
                    if (lastException instanceof RuntimeException re) {
                        re.addSuppressed(e);
                        throw re;
                    }
                    final RuntimeException wrapper = new RuntimeException(lastException);
                    wrapper.addSuppressed(e);
                    throw wrapper;
                }
                throw new RuntimeException("Interrupted while retrying", e);
            }
        }

        if (!failedAttempts.isEmpty()) {
            throw createRetryExhaustedException(failedAttempts, startTime);
        }
        throw new IllegalStateException("Retry finished without result or exception");
    }

    private static RetryExhaustedException createRetryExhaustedException(
            final java.util.List<Throwable> failedAttempts,
            final long startTime) {
        final long totalDuration = System.currentTimeMillis() - startTime;
        final Throwable lastFailure = failedAttempts.get(failedAttempts.size() - 1);

        final RetryExhaustedException exhausted = new RetryExhaustedException(
                failedAttempts.size(),
                totalDuration,
                lastFailure
        );

        // Add all previous failures as suppressed exceptions
        for (int i = 0; i < failedAttempts.size() - 1; i++) {
            exhausted.addSuppressed(failedAttempts.get(i));
        }

        return exhausted;
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
                || message.contains("nonce too low")
                // Rate limiting from RPC providers
                || message.contains("rate limit")
                || message.contains("too many requests")
                || message.contains("429")
                // Transient server errors
                || message.contains("internal error")
                || message.contains("-32603")
                || message.contains("server busy")
                || message.contains("overloaded");
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
