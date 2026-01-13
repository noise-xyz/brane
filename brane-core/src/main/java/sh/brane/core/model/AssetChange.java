// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.model;

import java.util.Objects;

/**
 * Represents a token balance change during transaction simulation.
 * <p>
 * Asset changes are only included in simulation results when
 * {@code traceAssetChanges=true} in the simulation request.
 *
 * @param token the token that changed (address and optional metadata)
 * @param value the balance change (before, after, diff)
 * @since 0.1.0-alpha
 */
public record AssetChange(
        AssetToken token,
        AssetValue value
) {

    /**
     * Compact constructor with validation.
     */
    public AssetChange {
        Objects.requireNonNull(token, "token cannot be null");
        Objects.requireNonNull(value, "value cannot be null");
    }
}
