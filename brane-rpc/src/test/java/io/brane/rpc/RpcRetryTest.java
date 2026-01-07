package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

import io.brane.core.error.RpcException;
import io.brane.rpc.exception.RetryExhaustedException;

class RpcRetryTest {

    @Test
    void retriesHeaderNotFound() {
        final AtomicInteger calls = new AtomicInteger();
        String result =
                RpcRetry.run(
                        () -> {
                            if (calls.getAndIncrement() == 0) {
                                throw new RpcException(-32000, "header not found", null, null, null);
                            }
                            return "ok";
                        },
                        3);

        assertEquals("ok", result);
        assertEquals(2, calls.get());
    }

    @Test
    void retriesIoErrors() {
        final AtomicInteger calls = new AtomicInteger();
        String result =
                RpcRetry.run(
                        () -> {
                            if (calls.getAndIncrement() == 0) {
                                throw new RuntimeException(new IOException("connection reset"));
                            }
                            return "done";
                        },
                        3);

        assertEquals("done", result);
        assertEquals(2, calls.get());
    }

    @Test
    void doesNotRetryRevert() {
        final AtomicInteger calls = new AtomicInteger();
        assertThrows(
                RpcException.class,
                () ->
                        RpcRetry.run(
                                () -> {
                                    calls.incrementAndGet();
                                    throw new RpcException(
                                            -32000,
                                            "execution reverted",
                                            "0x08c379a0deadbeef",
                                            (Long) null);
                                },
                                2));
        assertEquals(1, calls.get());
    }

    @Test
    void doesNotRetryUserError() {
        final AtomicInteger calls = new AtomicInteger();
        assertThrows(
                RpcException.class,
                () ->
                        RpcRetry.run(
                                () -> {
                                    calls.incrementAndGet();
                                    throw new RpcException(
                                            -32000, "insufficient funds for gas * price + value", null, (Long) null);
                                },
                                3));
        assertEquals(1, calls.get());
    }

    @Test
    void exhaustsRetries() {
        final AtomicInteger calls = new AtomicInteger();
        final RetryExhaustedException ex = assertThrows(
                RetryExhaustedException.class,
                () ->
                        RpcRetry.run(
                                () -> {
                                    calls.incrementAndGet();
                                    throw new RpcException(-32000, "header not found", null, null, null);
                                },
                                3));
        assertEquals(3, calls.get());
        assertEquals(3, ex.getAttemptCount());
        assertTrue(ex.getCause() instanceof RpcException);
        assertEquals(2, ex.getSuppressed().length); // 2 suppressed + 1 cause = 3 attempts
    }

    @Test
    void interruptPreservesExceptionContext() throws Exception {
        final CountDownLatch failedOnce = new CountDownLatch(1);
        final CountDownLatch inSleep = new CountDownLatch(1);
        final AtomicReference<Throwable> caughtException = new AtomicReference<>();
        final AtomicReference<Boolean> wasInterrupted = new AtomicReference<>(false);

        Thread worker = new Thread(() -> {
            try {
                RpcRetry.run(
                        () -> {
                            failedOnce.countDown();
                            inSleep.countDown();
                            throw new RpcException(-32000, "header not found", null, null, null);
                        },
                        3);
            } catch (Exception e) {
                caughtException.set(e);
                wasInterrupted.set(Thread.currentThread().isInterrupted());
            }
        });

        worker.start();
        assertTrue(failedOnce.await(5, TimeUnit.SECONDS));
        // Give time to enter sleep
        Thread.sleep(50);
        worker.interrupt();
        worker.join(5000);

        // Verify interrupt status was preserved
        assertTrue(wasInterrupted.get(), "Thread interrupt status should be preserved");

        // Verify exception was thrown with InterruptedException as suppressed
        Throwable caught = caughtException.get();
        assertTrue(caught instanceof RpcException, "Should throw RpcException");
        boolean hasInterruptedSuppressed = Arrays.stream(caught.getSuppressed())
                .anyMatch(s -> s instanceof InterruptedException);
        assertTrue(hasInterruptedSuppressed, "InterruptedException should be suppressed");
    }

    // ==================== runRpc tests ====================

    @Test
    void runRpcRetriesRetryableErrorResponses() {
        final AtomicInteger calls = new AtomicInteger();
        JsonRpcResponse result = RpcRetry.runRpc(
                () -> {
                    if (calls.getAndIncrement() == 0) {
                        // First call returns a retryable error response (rate limit)
                        return new JsonRpcResponse(
                                "2.0", null,
                                new JsonRpcError(-32000, "rate limit exceeded", null),
                                "1");
                    }
                    // Second call succeeds
                    return new JsonRpcResponse("2.0", "success", null, "2");
                },
                3);

        assertEquals("success", result.result());
        assertEquals(2, calls.get());
    }

    @Test
    void runRpcDoesNotRetryNonRetryableErrorResponses() {
        final AtomicInteger calls = new AtomicInteger();
        JsonRpcResponse result = RpcRetry.runRpc(
                () -> {
                    calls.incrementAndGet();
                    // Return a non-retryable error (revert with data)
                    return new JsonRpcResponse(
                            "2.0", null,
                            new JsonRpcError(-32000, "execution reverted", "0x08c379a0deadbeef"),
                            "1");
                },
                3);

        // Should return the error response without retrying
        assertTrue(result.hasError());
        assertEquals(1, calls.get());
    }

    @Test
    void runRpcSuccessOnFirstCall() {
        final AtomicInteger calls = new AtomicInteger();
        JsonRpcResponse result = RpcRetry.runRpc(
                () -> {
                    calls.incrementAndGet();
                    return new JsonRpcResponse("2.0", "immediate success", null, "1");
                },
                3);

        assertEquals("immediate success", result.result());
        assertEquals(1, calls.get());
    }

    @Test
    void runRpcExhaustsRetriesOnRetryableError() {
        final AtomicInteger calls = new AtomicInteger();
        final RetryExhaustedException ex = assertThrows(
                RetryExhaustedException.class,
                () -> RpcRetry.runRpc(
                        () -> {
                            calls.incrementAndGet();
                            // Always return a retryable error
                            return new JsonRpcResponse(
                                    "2.0", null,
                                    new JsonRpcError(-32603, "internal error", null),
                                    "1");
                        },
                        3));

        assertEquals(3, calls.get());
        assertEquals(3, ex.getAttemptCount());
    }

    @Test
    void runRpcRetriesExceptionsLikeRun() {
        final AtomicInteger calls = new AtomicInteger();
        JsonRpcResponse result = RpcRetry.runRpc(
                () -> {
                    if (calls.getAndIncrement() == 0) {
                        throw new RpcException(-32000, "timeout", null, null, null);
                    }
                    return new JsonRpcResponse("2.0", "success", null, "1");
                },
                3);

        assertEquals("success", result.result());
        assertEquals(2, calls.get());
    }

    /**
     * HIGH-6 Verification: Test if isRetryableRpcError handles null data correctly.
     *
     * The TODO claims there's a null safety issue with e.data().
     * Let's verify by testing with null data.
     */
    @Test
    void verifyHigh6_nullDataHandledCorrectly() {
        // Create an RpcException with null data
        RpcException exWithNullData = new RpcException(-32000, "header not found", null, null, null);

        // This should NOT throw NPE - the code should handle null data
        final AtomicInteger calls = new AtomicInteger();
        String result = RpcRetry.run(
                () -> {
                    if (calls.getAndIncrement() == 0) {
                        throw exWithNullData;
                    }
                    return "success";
                },
                3);

        assertEquals("success", result);
        assertEquals(2, calls.get()); // Should have retried once

        System.out.println("HIGH-6 Verification: null data is handled correctly - NOT A BUG");
        System.out.println("  isLikelyRevert checks 'data != null' first (short-circuit evaluation)");
    }

    @Test
    void verifyHigh6_nullMessageHandledCorrectly() {
        // Create an RpcException with null message - this SHOULD return false (not retryable)
        // because isRetryableRpcError checks: if (e == null || e.getMessage() == null) return false;
        RpcException exWithNullMessage = new RpcException(-32000, null, null, null, null);

        final AtomicInteger calls = new AtomicInteger();
        assertThrows(RpcException.class, () -> {
            RpcRetry.run(
                    () -> {
                        calls.incrementAndGet();
                        throw exWithNullMessage;
                    },
                    3);
        });

        // Should NOT retry because null message is not retryable
        assertEquals(1, calls.get());
        System.out.println("HIGH-6 Verification: null message correctly makes error non-retryable");
    }

    // ==================== RpcRetryConfig tests ====================

    @Test
    void runWithCustomConfigUsesConfiguredBackoff() {
        // Use very short backoff to verify config is applied
        RpcRetryConfig fastConfig = RpcRetryConfig.builder()
                .backoffBaseMs(1)
                .backoffMaxMs(10)
                .jitterMin(0.01)
                .jitterMax(0.02)
                .build();

        final AtomicInteger calls = new AtomicInteger();
        long startTime = System.currentTimeMillis();

        String result = RpcRetry.run(
                () -> {
                    if (calls.getAndIncrement() < 2) {
                        throw new RpcException(-32000, "header not found", null, null, null);
                    }
                    return "ok";
                },
                3,
                fastConfig);

        long elapsed = System.currentTimeMillis() - startTime;

        assertEquals("ok", result);
        assertEquals(3, calls.get());
        // With 1ms base backoff, should complete very quickly (< 100ms even with jitter)
        assertTrue(elapsed < 100, "Fast config should result in quick retries, took: " + elapsed + "ms");
    }

    @Test
    void runRpcWithCustomConfigUsesConfiguredBackoff() {
        // Use very short backoff to verify config is applied
        RpcRetryConfig fastConfig = RpcRetryConfig.builder()
                .backoffBaseMs(1)
                .backoffMaxMs(10)
                .jitterMin(0.01)
                .jitterMax(0.02)
                .build();

        final AtomicInteger calls = new AtomicInteger();
        long startTime = System.currentTimeMillis();

        JsonRpcResponse result = RpcRetry.runRpc(
                () -> {
                    if (calls.getAndIncrement() < 2) {
                        return new JsonRpcResponse(
                                "2.0", null,
                                new JsonRpcError(-32000, "rate limit exceeded", null),
                                "1");
                    }
                    return new JsonRpcResponse("2.0", "success", null, "1");
                },
                3,
                fastConfig);

        long elapsed = System.currentTimeMillis() - startTime;

        assertEquals("success", result.result());
        assertEquals(3, calls.get());
        // With 1ms base backoff, should complete very quickly (< 100ms even with jitter)
        assertTrue(elapsed < 100, "Fast config should result in quick retries, took: " + elapsed + "ms");
    }

    @Test
    void configDefaultsMatchExpectedValues() {
        RpcRetryConfig defaults = RpcRetryConfig.defaults();

        assertEquals(200, defaults.backoffBaseMs());
        assertEquals(5000, defaults.backoffMaxMs());
        assertEquals(0.10, defaults.jitterMin());
        assertEquals(0.25, defaults.jitterMax());
    }

    @Test
    void configBuilderAllowsPartialCustomization() {
        RpcRetryConfig config = RpcRetryConfig.builder()
                .backoffBaseMs(100)
                .build();

        assertEquals(100, config.backoffBaseMs());
        // Other values should be defaults
        assertEquals(5000, config.backoffMaxMs());
        assertEquals(0.10, config.jitterMin());
        assertEquals(0.25, config.jitterMax());
    }

    @Test
    void configValidatesParameters() {
        // backoffBaseMs must be > 0
        assertThrows(IllegalArgumentException.class, () ->
                new RpcRetryConfig(0, 5000, 0.10, 0.25));

        // backoffMaxMs must be >= backoffBaseMs
        assertThrows(IllegalArgumentException.class, () ->
                new RpcRetryConfig(200, 100, 0.10, 0.25));

        // jitterMin must be >= 0
        assertThrows(IllegalArgumentException.class, () ->
                new RpcRetryConfig(200, 5000, -0.1, 0.25));

        // jitterMax must be > jitterMin
        assertThrows(IllegalArgumentException.class, () ->
                new RpcRetryConfig(200, 5000, 0.25, 0.25));
    }
}
