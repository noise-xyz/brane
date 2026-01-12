// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * A handle to the result of an individual call within a Multicall3 batch.
 *
 * <p>
 * After calling {@link MulticallBatch#execute()}, the result can be retrieved
 * via {@link #result()}. Before execution, calling {@link #result()} will throw
 * an {@link IllegalStateException}.
 *
 * <p>
 * <strong>Thread Safety:</strong> This class is thread-safe. The completion
 * operation uses atomic compare-and-set to ensure that a handle can only be
 * completed once, even if {@link #complete(BatchResult)} is called concurrently.
 *
 * <p>
 * <strong>Example:</strong>
 *
 * <pre>{@code
 * BatchHandle<BigInteger> handle = batch.add(token.balanceOf(user));
 * batch.execute();
 *
 * if (handle.result().success()) {
 *     BigInteger balance = handle.result().data();
 * }
 * }</pre>
 *
 * @param <T> the type of the result data
 * @since 0.1.0
 */
public final class BatchHandle<T> {

    private final AtomicReference<BatchResult<T>> result = new AtomicReference<>();

    BatchHandle() {
    }

    /**
     * Returns whether the batch has been executed and this handle has a result.
     *
     * @return true if the result is available, false if the batch hasn't been
     *         executed
     */
    public boolean isComplete() {
        return result.get() != null;
    }

    /**
     * Returns the result of the call.
     *
     * @return the result containing data, success status, and any revert reason
     * @throws IllegalStateException if the batch has not been executed yet
     */
    public BatchResult<T> result() {
        final BatchResult<T> r = result.get();
        if (r == null) {
            throw new IllegalStateException("Batch has not been executed yet.");
        }
        return r;
    }

    /**
     * Attempts to complete this handle with the given result.
     *
     * <p>This method is thread-safe and idempotent. If called concurrently, only the first
     * call will succeed; subsequent calls will return {@code false} without modifying state.
     * This design allows safe completion from multiple sources (e.g., timeout handler and
     * normal completion) without requiring external synchronization.
     *
     * @param result the result to set
     * @return {@code true} if this call completed the handle, {@code false} if already completed
     * @throws NullPointerException if result is null
     */
    boolean complete(final BatchResult<T> result) {
        Objects.requireNonNull(result, "result");
        return this.result.compareAndSet(null, result);
    }
}
