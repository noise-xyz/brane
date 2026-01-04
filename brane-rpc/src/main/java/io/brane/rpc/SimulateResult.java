package io.brane.rpc;

import java.util.List;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.brane.core.model.AssetChange;

/**
 * Represents the result of a transaction simulation batch.
 * <p>
 * Contains per-call results (in the same order as the input calls) and
 * optional asset change information if {@code traceAssetChanges=true}.
 *
 * @param results per-call results (same order as input)
 * @param assetChanges token balance changes (null if traceAssetChanges=false)
 * @see SimulateRequest
 */
public record SimulateResult(
        List<CallResult> results,
        @Nullable List<AssetChange> assetChanges
) {

    /**
     * Compact constructor with validation and defensive copy.
     */
    public SimulateResult {
        Objects.requireNonNull(results, "results cannot be null");
        results = List.copyOf(results);

        if (assetChanges != null) {
            assetChanges = List.copyOf(assetChanges);
        }
    }
}
