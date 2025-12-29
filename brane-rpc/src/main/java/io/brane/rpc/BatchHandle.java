package io.brane.rpc;

import java.util.Objects;

/**
 * A handle to the result of an individual call within a Multicall3 batch.
 * 
 * <p>
 * After calling {@link MulticallBatch#execute()}, the result can be retrieved
 * via {@link #result()}. Before execution, calling {@link #result()} will throw
 * an {@link IllegalStateException}.
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

    private volatile BatchResult<T> result;

    BatchHandle() {
    }

    /**
     * Returns whether the batch has been executed and this handle has a result.
     * 
     * @return true if the result is available, false if the batch hasn't been
     *         executed
     */
    public boolean isComplete() {
        return result != null;
    }

    /**
     * Returns the result of the call.
     * 
     * @return the result containing data, success status, and any revert reason
     * @throws IllegalStateException if the batch has not been executed yet
     */
    public BatchResult<T> result() {
        if (result == null) {
            throw new IllegalStateException("Batch has not been executed yet.");
        }
        return result;
    }

    void complete(BatchResult<T> result) {
        if (this.result != null) {
            throw new IllegalStateException("BatchHandle has already been completed");
        }
        this.result = Objects.requireNonNull(result);
    }
}
