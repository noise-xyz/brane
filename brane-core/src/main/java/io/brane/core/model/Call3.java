package io.brane.core.model;

import io.brane.core.types.Address;
import io.brane.core.types.HexData;

/**
 * Represents a single call in a Multicall3 aggregate3 request.
 *
 * @param target       The contract address to call.
 * @param allowFailure If true, the batch will not revert if this specific call fails.
 * @param callData     The encoded function data.
 */
public record Call3(
        Address target,
        boolean allowFailure,
        HexData callData) {
}

