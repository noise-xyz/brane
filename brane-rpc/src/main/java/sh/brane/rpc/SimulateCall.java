// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;


import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

import sh.brane.core.types.Address;
import sh.brane.core.types.HexData;
import sh.brane.core.types.Wei;
import sh.brane.rpc.internal.RpcUtils;

/**
 * Represents a single call in a transaction simulation batch.
 * <p>
 * Similar to {@link CallRequest}, but used specifically for {@code eth_simulateV1}
 * where multiple calls can be simulated in sequence with state changes preserved between calls.
 *
 * @since 0.2.0
 */
public record SimulateCall(
        @Nullable Address from,          // Override account for this specific call
        Address to,                       // Target address (required)
        @Nullable HexData data,          // Raw calldata
        @Nullable Wei value,             // ETH value to send
        @Nullable BigInteger gas,        // Gas limit override
        @Nullable BigInteger gasPrice,   // Legacy gas price
        @Nullable BigInteger maxFeePerGas,
        @Nullable BigInteger maxPriorityFeePerGas
) {

    /**
     * Compact constructor with validation.
     */
    public SimulateCall {
        Objects.requireNonNull(to, "Target address (to) is required for SimulateCall");

        boolean hasLegacyGas = gasPrice != null;
        boolean hasEip1559Gas = maxFeePerGas != null || maxPriorityFeePerGas != null;

        if (hasLegacyGas && hasEip1559Gas) {
            throw new IllegalArgumentException(
                    "Cannot set both legacy gasPrice and EIP-1559 gas fields " +
                    "(maxFeePerGas/maxPriorityFeePerGas) in the same SimulateCall"
            );
        }
    }

    /**
     * Converts this call to a map suitable for JSON-RPC serialization.
     *
     * @return a map representation of this call
     */
    public Map<String, Object> toMap() {
        var map = new LinkedHashMap<String, Object>();
        RpcUtils.putIfPresent(map, "from", from, Address::value);
        map.put("to", to.value());
        RpcUtils.putIfPresent(map, "data", data, HexData::value);
        RpcUtils.putIfPresent(map, "value", value, Wei::toHexString);
        RpcUtils.putIfPresent(map, "gas", gas, RpcUtils::toQuantityHex);
        RpcUtils.putIfPresent(map, "gasPrice", gasPrice, RpcUtils::toQuantityHex);
        RpcUtils.putIfPresent(map, "maxFeePerGas", maxFeePerGas, RpcUtils::toQuantityHex);
        RpcUtils.putIfPresent(map, "maxPriorityFeePerGas", maxPriorityFeePerGas, RpcUtils::toQuantityHex);
        return map;
    }

    /**
     * Creates a simple simulate call with just target address and data.
     * <p>
     * This is a convenience factory method for the common case where only
     * the target address and calldata are needed, matching the pattern
     * used by {@link CallRequest#of(Address, HexData)}.
     *
     * @param to the target contract address
     * @param data the encoded function call data
     * @return a new SimulateCall
     * @since 0.2.0
     */
    public static SimulateCall of(Address to, HexData data) {
        return new SimulateCall(null, to, data, null, null, null, null, null);
    }

    /**
     * Creates a builder for constructing {@link SimulateCall} instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Builder for {@link SimulateCall}.
     */
    public static final class Builder {
        private @Nullable Address from;
        private @Nullable Address to;
        private @Nullable HexData data;
        private @Nullable Wei value;
        private @Nullable BigInteger gas;
        private @Nullable BigInteger gasPrice;
        private @Nullable BigInteger maxFeePerGas;
        private @Nullable BigInteger maxPriorityFeePerGas;

        private Builder() {}

        /**
         * Sets the sender address for this call (optional override).
         */
        public Builder from(Address from) {
            this.from = from;
            return this;
        }

        /**
         * Sets the target address for this call (required).
         */
        public Builder to(Address to) {
            this.to = to;
            return this;
        }

        /**
         * Sets the calldata for this call.
         */
        public Builder data(HexData data) {
            this.data = data;
            return this;
        }

        /**
         * Sets the ETH value to send with this call.
         */
        public Builder value(Wei value) {
            this.value = value;
            return this;
        }

        /**
         * Sets the gas limit for this call.
         */
        public Builder gas(BigInteger gas) {
            this.gas = gas;
            return this;
        }

        /**
         * Sets the legacy gas price (pre-EIP-1559).
         * Cannot be used with {@link #maxFeePerGas} or {@link #maxPriorityFeePerGas}.
         */
        public Builder gasPrice(BigInteger gasPrice) {
            this.gasPrice = gasPrice;
            return this;
        }

        /**
         * Sets the maximum fee per gas (EIP-1559).
         * Cannot be used with {@link #gasPrice}.
         */
        public Builder maxFeePerGas(BigInteger maxFeePerGas) {
            this.maxFeePerGas = maxFeePerGas;
            return this;
        }

        /**
         * Sets the maximum priority fee per gas (EIP-1559).
         * Cannot be used with {@link #gasPrice}.
         */
        public Builder maxPriorityFeePerGas(BigInteger maxPriorityFeePerGas) {
            this.maxPriorityFeePerGas = maxPriorityFeePerGas;
            return this;
        }

        /**
         * Builds the {@link SimulateCall} instance.
         *
         * @return a new SimulateCall
         * @throws NullPointerException if {@code to} is not set
         * @throws IllegalArgumentException if both legacy and EIP-1559 gas fields are set
         */
        public SimulateCall build() {
            return new SimulateCall(from, to, data, value, gas, gasPrice, maxFeePerGas, maxPriorityFeePerGas);
        }
    }
}
