package io.brane.rpc;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import io.brane.core.types.Address;

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
 * @param fetchTokenMetadata fetch token decimals/symbol via eth_call
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

        calls = List.copyOf(calls);

        if (stateOverrides != null) {
            stateOverrides = Map.copyOf(stateOverrides);
        }
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
         */
        public Builder fetchTokenMetadata(boolean fetchTokenMetadata) {
            this.fetchTokenMetadata = fetchTokenMetadata;
            return this;
        }

        /**
         * Builds the {@link SimulateRequest} instance.
         *
         * @return a new SimulateRequest
         * @throws NullPointerException if calls is null
         * @throws IllegalArgumentException if calls is empty
         */
        public SimulateRequest build() {
            return new SimulateRequest(
                    account,
                    calls != null ? calls : List.of(),
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
