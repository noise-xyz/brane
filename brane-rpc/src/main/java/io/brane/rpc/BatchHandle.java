package io.brane.rpc;

import java.util.Objects;

/**
 * A handle to the result of an individual call within a multicall batch.
 * 
 * @param <T> the type of the result data
 */
public final class BatchHandle<T> {

    private BatchResult<T> result;

    BatchHandle() {
    }

    /**
     * Returns the result of the call.
     * 
     * @return the result
     * @throws IllegalStateException if the batch has not been executed yet
     */
    public BatchResult<T> result() {
        if (result == null) {
            throw new IllegalStateException("Batch has not been executed yet.");
        }
        return result;
    }

    void complete(BatchResult<T> result) {
        this.result = Objects.requireNonNull(result);
    }
}

