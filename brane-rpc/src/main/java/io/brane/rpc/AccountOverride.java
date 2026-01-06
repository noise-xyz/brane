package io.brane.rpc;

import java.util.LinkedHashMap;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

/**
 * Represents state overrides for an account during transaction simulation.
 * <p>
 * Used in {@code eth_simulateV1} to temporarily modify account state (balance, nonce, code, storage)
 * for the duration of the simulation without affecting the actual blockchain state.
 * <p>
 * The address is specified as the map key in {@link SimulateRequest#stateOverrides()},
 * not as a field in this record.
 *
 * @since 0.2.0
 */
public record AccountOverride(
        @Nullable Wei balance,              // Override balance
        @Nullable Long nonce,               // Override nonce
        @Nullable HexData code,             // Override contract code
        @Nullable Map<Hash, Hash> stateDiff // Storage slot overrides
) {

    /**
     * Compact constructor with defensive copy for immutability.
     */
    public AccountOverride {
        if (stateDiff != null) {
            stateDiff = Map.copyOf(stateDiff);
        }
    }

    /**
     * Converts this override to a map suitable for JSON-RPC serialization.
     *
     * @return a map representation of this account override
     */
    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();

        if (balance != null) {
            map.put("balance", balance.toHexString());
        }
        if (nonce != null) {
            map.put("nonce", "0x" + Long.toHexString(nonce));
        }
        if (code != null) {
            map.put("code", code.value());
        }
        if (stateDiff != null && !stateDiff.isEmpty()) {
            var stateDiffMap = new LinkedHashMap<String, String>();
            stateDiff.forEach((key, value) -> stateDiffMap.put(key.value(), value.value()));
            map.put("stateDiff", stateDiffMap);
        }

        return map;
    }

    /**
     * Creates a builder for constructing {@link AccountOverride} instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link AccountOverride}.
     */
    public static final class Builder {
        private @Nullable Wei balance;
        private @Nullable Long nonce;
        private @Nullable HexData code;
        private @Nullable Map<Hash, Hash> stateDiff;

        private Builder() {}

        /**
         * Sets the balance override for this account.
         */
        public Builder balance(Wei balance) {
            this.balance = balance;
            return this;
        }

        /**
         * Sets the nonce override for this account.
         */
        public Builder nonce(long nonce) {
            this.nonce = nonce;
            return this;
        }

        /**
         * Sets the code override for this account (to override contract bytecode).
         */
        public Builder code(HexData code) {
            this.code = code;
            return this;
        }

        /**
         * Sets the storage slot overrides for this account.
         * <p>
         * The map keys are storage slot hashes, and the values are the override values.
         * Use this to temporarily modify contract storage during simulation.
         *
         * @param stateDiff a map from storage slot to override value
         */
        public Builder stateDiff(Map<Hash, Hash> stateDiff) {
            this.stateDiff = stateDiff;
            return this;
        }

        /**
         * Adds a single storage slot override.
         *
         * @param slot the storage slot hash
         * @param value the override value
         */
        public Builder putStateDiff(Hash slot, Hash value) {
            if (this.stateDiff == null) {
                this.stateDiff = new LinkedHashMap<>();
            }
            this.stateDiff.put(slot, value);
            return this;
        }

        /**
         * Builds the {@link AccountOverride} instance.
         *
         * @return a new AccountOverride
         */
        public AccountOverride build() {
            return new AccountOverride(balance, nonce, code, stateDiff);
        }
    }
}
