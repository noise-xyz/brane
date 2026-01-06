package io.brane.rpc;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.brane.core.model.AssetChange;
import io.brane.core.model.AssetToken;
import io.brane.core.model.AssetValue;
import io.brane.core.types.Address;
import io.brane.rpc.internal.RpcUtils;

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

    /**
     * Parses a simulation result from a JSON-RPC response map.
     *
     * @param map the raw map from the JSON-RPC response
     * @return a new SimulateResult instance
     */
    @SuppressWarnings("unchecked")
    public static SimulateResult fromMap(Map<String, Object> map) {
        List<Map<String, Object>> rawResults = (List<Map<String, Object>>) map.get("results");
        if (rawResults == null && map.containsKey("calls")) {
            // Some implementations put 'calls' at the top level
            rawResults = (List<Map<String, Object>>) map.get("calls");
        }

        if (rawResults == null) {
            return new SimulateResult(List.of(), null);
        }

        List<CallResult> results = rawResults.stream()
                .map(CallResult::fromMap)
                .toList();

        List<AssetChange> assetChanges = null;
        if (map.containsKey("assetChanges") && map.get("assetChanges") != null) {
            List<Map<String, Object>> rawChanges = (List<Map<String, Object>>) map.get("assetChanges");
            assetChanges = rawChanges.stream()
                    .map(SimulateResult::parseAssetChange)
                    .toList();
        }

        return new SimulateResult(results, assetChanges);
    }

    /**
     * Parses a simulation result from a JSON-RPC response list (direct array of blocks).
     *
     * @param list the raw list from the JSON-RPC response
     * @return a new SimulateResult instance
     */
    @SuppressWarnings("unchecked")
    public static SimulateResult fromList(List<Map<String, Object>> list) {
        if (list.isEmpty()) {
            return new SimulateResult(List.of(), null);
        }

        // In the block-based response format, results for all calls are usually
        // in the 'calls' array of the first simulated block if we sent them all in one block request.
        Map<String, Object> firstBlock = list.get(0);
        List<Map<String, Object>> rawResults = (List<Map<String, Object>>) firstBlock.get("calls");

        if (rawResults == null) {
            return new SimulateResult(List.of(), null);
        }

        List<CallResult> results = rawResults.stream()
                .map(CallResult::fromMap)
                .toList();

        // Asset changes might be in the block result if requested
        List<AssetChange> assetChanges = null;
        if (firstBlock.containsKey("assetChanges") && firstBlock.get("assetChanges") != null) {
            List<Map<String, Object>> rawChanges = (List<Map<String, Object>>) firstBlock.get("assetChanges");
            assetChanges = rawChanges.stream()
                    .map(SimulateResult::parseAssetChange)
                    .toList();
        }

        return new SimulateResult(results, assetChanges);
    }

    @SuppressWarnings("unchecked")
    private static AssetChange parseAssetChange(Map<String, Object> map) {
        Address tokenAddr = new Address(String.valueOf(map.get("token")));
        Integer decimals = map.get("decimals") != null ? ((Number) map.get("decimals")).intValue() : null;
        String symbol = (String) map.get("symbol");
        AssetToken token = new AssetToken(tokenAddr, decimals, symbol);

        Map<String, Object> rawValue = (Map<String, Object>) map.get("value");
        if (rawValue == null) {
            throw new IllegalArgumentException("assetChange entry missing required 'value' field");
        }
        BigInteger pre = RpcUtils.decodeHexBigInteger(String.valueOf(rawValue.get("pre")));
        BigInteger post = RpcUtils.decodeHexBigInteger(String.valueOf(rawValue.get("post")));
        BigInteger diff = RpcUtils.decodeHexBigInteger(String.valueOf(rawValue.get("diff")));
        AssetValue value = new AssetValue(pre, post, diff);

        return new AssetChange(token, value);
    }
}
