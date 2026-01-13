// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.model;

import java.util.Objects;

import sh.brane.core.types.HexData;

/**
 * Represents the raw result of an individual call from Multicall3.
 *
 * @param success    true if the call was successful, false if it reverted
 * @param returnData the raw bytes returned by the call (or revert reason, required)
 * @since 0.1.0-alpha
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
