package io.brane.core.model;

import io.brane.core.types.HexData;

import java.util.Objects;

/**
 * Represents the raw result of an individual call from Multicall3.
 *
 * @param success    true if the call was successful, false if it reverted
 * @param returnData the raw bytes returned by the call (or revert reason, required)
 */
public record MulticallResult(
        boolean success,
        HexData returnData) {

    /**
     * Validates required fields.
     *
     * @throws NullPointerException if returnData is null
     */
    public MulticallResult {
        Objects.requireNonNull(returnData, "returnData cannot be null");
    }
}

