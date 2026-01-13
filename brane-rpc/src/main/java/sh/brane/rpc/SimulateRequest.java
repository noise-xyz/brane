// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import sh.brane.core.types.Address;

/**
 * Represents a request to simulate a batch of calls using {@code eth_simulateV1}.
 * <p>
 * This allows simulating multiple transactions in sequence with state overrides,
 * making it powerful for gas estimation, dry-run validation, and debugging.
 * <p>
 * Example usage:
 * <pre>{@code
 * SimulateRequest request = SimulateRequest.builder()
 *     .account(senderAddress)
 *     .call(SimulateCall.builder()
 *         .to(contractAddress)
 *         .data(encodedFunctionCall)
 *         .build())
 *     .blockTag(BlockTag.LATEST)
 *     .stateOverride(address, AccountOverride.builder()
 *         .balance(Wei.fromEther("1000"))
 *         .build())
 *     .traceAssetChanges(true)
 *     .build();
 * }</pre>
 *
 * @param account default sender address for all calls (can be overridden per-call)
 * @param calls list of calls to simulate (required, non-empty)
 * @param blockTag block reference (defaults to LATEST if null)
 * @param stateOverrides state modifications (map from address to override)
 * @param traceAssetChanges enable asset change tracking
 * @param traceTransfers enable transfer tracing
 * @param validation enable validation mode
 * @param fetchTokenMetadata fetch token decimals/symbol via eth_call (NOT YET IMPLEMENTED)
 * @since 0.2.0
 */
public record SimulateRequest(
        @Nullable Address account,
        List<SimulateCall> calls,
        @Nullable BlockTag blockTag,
        @Nullable Map<Address, AccountOverride> stateOverrides,
        boolean traceAssetChanges,
        boolean traceTransfers,
        boolean validation,
        boolean fetchTokenMetadata
) {

    /**
     * Compact constructor with validation and defensive copies.
     */
    public SimulateRequest {
        Objects.requireNonNull(calls, "calls cannot be null");

        if (calls.isEmpty()) {
            throw new IllegalArgumentException("calls must contain at least one call");
        }

        if (fetchTokenMetadata) {
            throw new UnsupportedOperationException(
                    "fetchTokenMetadata is not yet implemented - token metadata is only "
                            + "available if the RPC node includes it natively in eth_simulateV1 response");
        }

        calls = List.copyOf(calls);

        if (stateOverrides != null) {
            stateOverrides = Map.copyOf(stateOverrides);
        }
    }

    /**
     * Converts this request to a map suitable for JSON-RPC serialization.
     * <p>
     * This method constructs the first parameter (payload object) for {@code eth_simulateV1}.
     * Note that the block tag is passed as a separate second parameter in the RPC call.
     *
     * @return a map representation of the simulation payload
     */
    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();

        // 1. blockStateCalls (Array of objects, each containing a list of calls)
        var blockStateCall = new LinkedHashMap<String, Object>();

        // Map calls and apply default account if not present
        // Create a new map to avoid mutating the map returned by call.toMap()
        List<Map<String, Object>> mappedCalls = calls.stream().map(call -> {
            Map<String, Object> originalMap = call.toMap();
            if (account != null && !originalMap.containsKey("from")) {
                var callMap = new LinkedHashMap<String, Object>();
                callMap.put("from", account.value());
                callMap.putAll(originalMap);
                return callMap;
            }
            return originalMap;
        }).toList();

        blockStateCall.put("calls", mappedCalls);

        // Add global state overrides as direct child of blockStateCall (per eth_simulateV1 spec)
        if (stateOverrides != null && !stateOverrides.isEmpty()) {
            var overrides = new LinkedHashMap<String, Map<String, Object>>();
            stateOverrides.forEach((addr, override) -> overrides.put(addr.value(), override.toMap()));
            blockStateCall.put("stateOverrides", overrides);
        }

        map.put("blockStateCalls", List.of(blockStateCall));

        // 2. Flags at top level
        if (traceAssetChanges) {
            map.put("traceAssetChanges", true);
        }
        if (traceTransfers) {
            map.put("traceTransfers", true);
        }
        // Always serialize validation flag so users can explicitly disable it
        map.put("validation", validation);

        return map;
    }

    /**
     * Creates a builder for constructing {@link SimulateRequest} instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link SimulateRequest}.
     */
    public static final class Builder {
        private @Nullable Address account;
        private @Nullable List<SimulateCall> calls;
        private @Nullable BlockTag blockTag;
        private @Nullable Map<Address, AccountOverride> stateOverrides;
        private boolean traceAssetChanges = false;
        private boolean traceTransfers = false;
        private boolean validation = true;  // Default to true for safety
        private boolean fetchTokenMetadata = false;

        private Builder() {}

        /**
         * Sets the default sender address for all calls.
         * Can be overridden per-call with {@link SimulateCall#from()}.
         */
        public Builder account(Address account) {
            this.account = account;
            return this;
        }

        /**
         * Sets the list of calls to simulate.
         *
         * @param calls the calls to simulate (must be non-empty)
         */
        public Builder calls(List<SimulateCall> calls) {
            this.calls = calls;
            return this;
        }

        /**
         * Adds a single call to the simulation.
         *
         * @param call the call to add
         */
        public Builder call(SimulateCall call) {
            if (this.calls == null) {
                this.calls = new ArrayList<>();
            }
            this.calls.add(call);
            return this;
        }

        /**
         * Sets the block reference for the simulation.
         * Defaults to {@link BlockTag#LATEST} if not specified.
         *
         * @param blockTag the block tag
         */
        public Builder blockTag(BlockTag blockTag) {
            this.blockTag = blockTag;
            return this;
        }

        /**
         * Sets the state overrides map.
         *
         * @param stateOverrides map from address to account override
         */
        public Builder stateOverrides(Map<Address, AccountOverride> stateOverrides) {
            this.stateOverrides = stateOverrides;
            return this;
        }

        /**
         * Adds a single state override for an account.
         *
         * @param address the address to override
         * @param override the account override configuration
         */
        public Builder stateOverride(Address address, AccountOverride override) {
            if (this.stateOverrides == null) {
                this.stateOverrides = new LinkedHashMap<>();
            }
            this.stateOverrides.put(address, override);
            return this;
        }

        /**
         * Enables or disables asset change tracking.
         * When enabled, {@link SimulateResult#assetChanges()} will contain token balance changes.
         * Default: {@code false}
         */
        public Builder traceAssetChanges(boolean traceAssetChanges) {
            this.traceAssetChanges = traceAssetChanges;
            return this;
        }

        /**
         * Enables or disables transfer tracing.
         * Default: {@code false}
         */
        public Builder traceTransfers(boolean traceTransfers) {
            this.traceTransfers = traceTransfers;
            return this;
        }

        /**
         * Enables or disables validation mode.
         * Default: {@code true}
         */
        public Builder validation(boolean validation) {
            this.validation = validation;
            return this;
        }

        /**
         * Enables or disables fetching token metadata (decimals/symbol).
         * When enabled, additional {@code eth_call} requests will be made to fetch
         * token metadata for asset changes. This adds latency but provides richer data.
         * Default: {@code false}
         *
         * <p><b>Note:</b> This feature is not yet implemented. Setting this to {@code true}
         * currently has no effect. Token metadata will be populated only if the RPC node
         * includes it natively in the {@code eth_simulateV1} response.
         */
        public Builder fetchTokenMetadata(boolean fetchTokenMetadata) {
            this.fetchTokenMetadata = fetchTokenMetadata;
            return this;
        }

        /**
         * Builds the {@link SimulateRequest} instance.
         *
         * @return a new SimulateRequest
         * @throws IllegalStateException if no calls have been added
         */
        public SimulateRequest build() {
            if (calls == null || calls.isEmpty()) {
                throw new IllegalStateException(
                        "SimulateRequest requires at least one call. "
                                + "Use .call(SimulateCall) or .calls(List<SimulateCall>) to add calls.");
            }
            return new SimulateRequest(
                    account,
                    calls,
                    blockTag,
                    stateOverrides,
                    traceAssetChanges,
                    traceTransfers,
                    validation,
                    fetchTokenMetadata
            );
        }
    }
}
