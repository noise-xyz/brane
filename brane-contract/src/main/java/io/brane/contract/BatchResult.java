package io.brane.contract;

/**
 * The result of an individual call within a multicall batch.
 * 
 * @param <T>          the type of the result data
 * @param data         the decoded result data (null if successful and returns void,
 *                     or if unsuccessful)
 * @param success      true if the call was successful
 * @param revertReason human-readable reason for the failure (null if successful)
 */
public record BatchResult<T>(
        T data,
        boolean success,
        String revertReason) {
}

