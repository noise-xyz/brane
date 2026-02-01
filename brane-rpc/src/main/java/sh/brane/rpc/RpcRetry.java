// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;

import java.io.IOException;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import sh.brane.core.error.RevertException;
import sh.brane.core.error.RpcException;
import sh.brane.rpc.exception.RetryExhaustedException;
import sh.brane.rpc.internal.RpcUtils;

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
 * <li><strong>Backoff:</strong> Exponential backoff (200ms × 2^(attempt-1), max 5s) with 10-25% random jitter</li>
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
 * <li>Attempt 2: 200ms + 10-25% jitter (220-250ms)</li>
 * <li>Attempt 3: 400ms + 10-25% jitter (440-500ms)</li>
 * <li>Attempt 4: 800ms + 10-25% jitter (880-1000ms)</li>
 * <li>Attempt 5+: Capped at 5000ms + 10-25% jitter (5500-6250ms)</li>
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
     * Executes the supplier with retry on transient failures using default configuration.
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
        return run(supplier, maxAttempts, DEFAULT_CONFIG);
    }

    /**
     * Executes the supplier with retry on transient failures using custom configuration.
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
     * @param config      retry configuration for backoff timing
     * @return the result from the supplier
     * @throws RpcException             if all retries fail or error is
     *                                  non-retryable
     * @throws RetryExhaustedException  if all retry attempts were exhausted
     * @throws RevertException          if the operation reverts (never retried)
     * @throws IllegalArgumentException if maxAttempts < 1
     */
    static <T> T run(final Supplier<T> supplier, final int maxAttempts, final RpcRetryConfig config) {
        Objects.requireNonNull(supplier, "supplier");
        return executeWithRetry(supplier, maxAttempts, config, result -> null);
    }

    private static RetryExhaustedException createRetryExhaustedException(
            final java.util.List<Throwable> failedAttempts,
            final long startTime) {
        final long totalDuration = System.currentTimeMillis() - startTime;
        final Throwable lastFailure = failedAttempts.getLast();

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

    /**
     * Common retry loop logic shared by run() and runRpc().
     *
     * @param <T>            the return type
     * @param supplier       the operation to retry
     * @param maxAttempts    maximum number of attempts
     * @param config         retry configuration
     * @param resultChecker  function that checks the result and returns an RpcException if the result
     *                       should trigger a retry, or null if the result is acceptable
     * @return the result from the supplier
     */
    private static <T> T executeWithRetry(
            final Supplier<T> supplier,
            final int maxAttempts,
            final RpcRetryConfig config,
            final java.util.function.Function<T, RpcException> resultChecker) {
        Objects.requireNonNull(config, "config");
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }

        // Lazy-initialized on first failure to avoid allocation on success path
        java.util.List<Throwable> failedAttempts = null;
        final long startTime = System.currentTimeMillis();
        Throwable lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                final T result = supplier.get();

                // Check if result should trigger a retry
                final RpcException retryableError = resultChecker.apply(result);
                if (retryableError != null) {
                    lastException = retryableError;
                    if (failedAttempts == null) {
                        failedAttempts = new java.util.ArrayList<>();
                    }
                    failedAttempts.add(retryableError);
                    if (attempt == maxAttempts) {
                        throw createRetryExhaustedException(failedAttempts, startTime);
                    }
                } else {
                    return result;
                }
            } catch (RevertException e) {
                throw e;
            } catch (RpcException e) {
                lastException = e;
                if (failedAttempts == null) {
                    failedAttempts = new java.util.ArrayList<>();
                }
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
                if (failedAttempts == null) {
                    failedAttempts = new java.util.ArrayList<>();
                }
                failedAttempts.add(e);
                if (attempt == maxAttempts) {
                    throw createRetryExhaustedException(failedAttempts, startTime);
                }
            }

            final long delayMillis = backoff(attempt, config);
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

        if (failedAttempts != null && !failedAttempts.isEmpty()) {
            throw createRetryExhaustedException(failedAttempts, startTime);
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

    /** Default retry configuration. */
    private static final RpcRetryConfig DEFAULT_CONFIG = RpcRetryConfig.defaults();

    private static long backoff(final int attempt, final RpcRetryConfig config) {
        // Exponential backoff: base * 2^(attempt-1), capped at max
        final long delay = config.backoffBaseMs() * (1L << (attempt - 1));
        final long cappedDelay = Math.min(delay, config.backoffMaxMs());
        // Add random jitter to prevent thundering herd
        final double jitter = ThreadLocalRandom.current().nextDouble(config.jitterMin(), config.jitterMax());
        return cappedDelay + (long) (cappedDelay * jitter);
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

    /**
     * Executes an RPC call with retry on both exceptions AND retryable RPC error responses
     * using default configuration.
     *
     * <p>
     * Unlike {@link #run}, this method also retries when the response contains a retryable
     * error (e.g., rate limiting, transient server errors). Non-retryable errors like
     * reverts or invalid parameters are not retried.
     *
     * @param supplier    the operation returning a JSON-RPC response
     * @param maxAttempts maximum number of attempts (must be >= 1)
     * @return the response (may contain non-retryable error)
     * @throws RpcException             if all retries fail or error is non-retryable
     * @throws RetryExhaustedException  if all retry attempts were exhausted
     * @throws IllegalArgumentException if maxAttempts < 1
     */
    static JsonRpcResponse runRpc(final Supplier<JsonRpcResponse> supplier, final int maxAttempts) {
        return runRpc(supplier, maxAttempts, DEFAULT_CONFIG);
    }

    /**
     * Executes an RPC call with retry on both exceptions AND retryable RPC error responses
     * using custom configuration.
     *
     * <p>
     * Unlike {@link #run}, this method also retries when the response contains a retryable
     * error (e.g., rate limiting, transient server errors). Non-retryable errors like
     * reverts or invalid parameters are not retried.
     *
     * @param supplier    the operation returning a JSON-RPC response
     * @param maxAttempts maximum number of attempts (must be >= 1)
     * @param config      retry configuration for backoff timing
     * @return the response (may contain non-retryable error)
     * @throws RpcException             if all retries fail or error is non-retryable
     * @throws RetryExhaustedException  if all retry attempts were exhausted
     * @throws IllegalArgumentException if maxAttempts < 1
     */
    static JsonRpcResponse runRpc(
            final Supplier<JsonRpcResponse> supplier,
            final int maxAttempts,
            final RpcRetryConfig config) {
        Objects.requireNonNull(supplier, "supplier");
        return executeWithRetry(supplier, maxAttempts, config, RpcRetry::checkRetryableResponse);
    }

    /**
     * Checks if a JSON-RPC response contains a retryable error.
     *
     * @param response the response to check
     * @return an RpcException if the error is retryable and should be retried, null otherwise
     */
    private static RpcException checkRetryableResponse(final JsonRpcResponse response) {
        if (!response.hasError()) {
            return null;
        }
        final JsonRpcError err = response.error();
        final RpcException rpcEx = new RpcException(
                err.code(),
                err.message(),
                RpcUtils.extractErrorData(err.data()),
                (Long) null,
                (Throwable) null);

        // If not retryable, return null to accept the response as-is
        if (!isRetryableRpcError(rpcEx)) {
            return null;
        }
        // Return the exception to trigger a retry
        return rpcEx;
    }
}
