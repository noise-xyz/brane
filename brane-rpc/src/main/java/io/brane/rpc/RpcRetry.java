package io.brane.rpc;

import io.brane.core.RevertDecoder;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import java.io.IOException;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.function.Supplier;

final class RpcRetry {

    private RpcRetry() {}

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
