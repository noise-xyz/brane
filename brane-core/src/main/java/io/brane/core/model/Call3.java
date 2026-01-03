package io.brane.core.model;

import java.util.Objects;

import io.brane.core.types.Address;
import io.brane.core.types.HexData;

/**
 * Represents a single call in a Multicall3 aggregate3 request.
 *
 * @param target       the contract address to call (required)
 * @param allowFailure if true, the batch will not revert if this specific call fails
 * @param callData     the encoded function data (required, may be empty)
 * @since 0.1.0-alpha
 */
public record Call3(
        Address target,
        boolean allowFailure,
        HexData callData) {

    /**
     * Validates required fields.
     *
     * @throws NullPointerException if target or callData is null
     */
    public Call3 {
        Objects.requireNonNull(target, "target cannot be null");
        Objects.requireNonNull(callData, "callData cannot be null");
    }
}
