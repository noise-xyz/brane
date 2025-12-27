package io.brane.core.model;

import io.brane.core.types.HexData;

/**
 * Represents the raw result of an individual call from Multicall3.
 *
 * @param success    True if the call was successful, false if it reverted.
 * @param returnData The raw bytes returned by the call (or revert reason).
 */
public record MulticallResult(
        boolean success,
        HexData returnData) {
}

