package io.brane.core.model;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Represents the before/after balance change for a token during simulation.
 * <p>
 * Used in {@link AssetChange} when {@code traceAssetChanges=true}.
 *
 * @param pre balance before the simulation
 * @param post balance after the simulation
 * @param diff the change amount (post - pre, can be negative)
 * @since 0.1.0-alpha
 */
public record AssetValue(
        BigInteger pre,   // Balance before
        BigInteger post,  // Balance after
        BigInteger diff   // Change amount (can be negative)
) {

    /**
     * Compact constructor with validation.
     */
    public AssetValue {
        Objects.requireNonNull(pre, "pre cannot be null");
        Objects.requireNonNull(post, "post cannot be null");
        Objects.requireNonNull(diff, "diff cannot be null");
    }
}
