package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.brane.core.error.RpcException;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
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
                                            null);
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
                                            -32000, "insufficient funds for gas * price + value", null, null);
                                },
                                3));
        assertEquals(1, calls.get());
    }

    @Test
    void exhaustsRetries() {
        final AtomicInteger calls = new AtomicInteger();
        assertThrows(
                RpcException.class,
                () ->
                        RpcRetry.run(
                                () -> {
                                    calls.incrementAndGet();
                                    throw new RpcException(-32000, "header not found", null, null, null);
                                },
                                3));
        assertEquals(3, calls.get());
    }
}
