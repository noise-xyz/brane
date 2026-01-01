package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.brane.core.error.RpcException;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;

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
}
