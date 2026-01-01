package io.brane.rpc;

import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Type-safe representation of an eth_call request.
 * <p>
 * This record provides a strongly-typed alternative to raw Map-based call objects,
 * ensuring correct types are used for addresses, data, and values.
 * <p>
 * <strong>Usage:</strong>
 * <pre>{@code
 * // Minimal call - just target and data
 * CallRequest request = CallRequest.builder()
 *     .to(contractAddress)
 *     .data(encodedFunctionCall)
 *     .build();
 *
 * // Full call with all parameters
 * CallRequest request = CallRequest.builder()
 *     .from(senderAddress)
 *     .to(contractAddress)
 *     .data(encodedFunctionCall)
 *     .value(Wei.fromEther("1.0"))
 *     .gas(BigInteger.valueOf(100_000))
 *     .build();
 *
 * // Execute the call
 * HexData result = publicClient.call(request, BlockTag.LATEST);
 * }</pre>
 *
 * @param from the sender address (optional, for msg.sender in the call)
 * @param to the target contract address (required)
 * @param data the encoded function call data (optional)
 * @param value the wei value to send (optional)
 * @param gas the gas limit (optional)
 * @param gasPrice the gas price for legacy calls (optional)
 * @param maxFeePerGas the max fee per gas for EIP-1559 calls (optional)
 * @param maxPriorityFeePerGas the max priority fee per gas for EIP-1559 calls (optional)
 * @since 0.1.0-alpha
 */
public record CallRequest(
        Address from,
        Address to,
        HexData data,
        Wei value,
        BigInteger gas,
        BigInteger gasPrice,
        BigInteger maxFeePerGas,
        BigInteger maxPriorityFeePerGas
) {
    /**
     * Creates a new CallRequest with validation.
     *
     * @throws IllegalArgumentException if both legacy (gasPrice) and EIP-1559
     *         (maxFeePerGas/maxPriorityFeePerGas) gas fields are set
     */
    public CallRequest {
        Objects.requireNonNull(to, "Target address (to) is required for eth_call");

        // Validate mutually exclusive gas pricing fields
        boolean hasLegacyGas = gasPrice != null;
        boolean hasEip1559Gas = maxFeePerGas != null || maxPriorityFeePerGas != null;
        if (hasLegacyGas && hasEip1559Gas) {
            throw new IllegalArgumentException(
                    "Cannot set both legacy gasPrice and EIP-1559 gas fields " +
                    "(maxFeePerGas/maxPriorityFeePerGas). Use one or the other.");
        }
    }

    /**
     * Converts this request to a Map suitable for JSON-RPC serialization.
     *
     * @return a map containing the call parameters
     */
    public Map<String, Object> toMap() {
        final Map<String, Object> map = new LinkedHashMap<>();
        if (from != null) {
            map.put("from", from.value());
        }
        map.put("to", to.value());
        if (data != null) {
            map.put("data", data.value());
        }
        if (value != null) {
            map.put("value", "0x" + value.value().toString(16));
        }
        if (gas != null) {
            map.put("gas", "0x" + gas.toString(16));
        }
        if (gasPrice != null) {
            map.put("gasPrice", "0x" + gasPrice.toString(16));
        }
        if (maxFeePerGas != null) {
            map.put("maxFeePerGas", "0x" + maxFeePerGas.toString(16));
        }
        if (maxPriorityFeePerGas != null) {
            map.put("maxPriorityFeePerGas", "0x" + maxPriorityFeePerGas.toString(16));
        }
        return map;
    }

    /**
     * Returns a new builder for creating CallRequest instances.
     *
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Creates a simple call request with just target address and data.
     *
     * @param to the target contract address
     * @param data the encoded function call data
     * @return a new CallRequest
     */
    public static CallRequest of(Address to, HexData data) {
        return new CallRequest(null, to, data, null, null, null, null, null);
    }

    /**
     * Returns the sender address if present.
     *
     * @return an Optional containing the from address, or empty if not set
     */
    public Optional<Address> fromOpt() {
        return Optional.ofNullable(from);
    }

    /**
     * Returns the call data if present.
     *
     * @return an Optional containing the data, or empty if not set
     */
    public Optional<HexData> dataOpt() {
        return Optional.ofNullable(data);
    }

    /**
     * Returns the value if present.
     *
     * @return an Optional containing the value, or empty if not set
     */
    public Optional<Wei> valueOpt() {
        return Optional.ofNullable(value);
    }

    /**
     * Builder for creating CallRequest instances.
     */
    public static final class Builder {
        private Address from;
        private Address to;
        private HexData data;
        private Wei value;
        private BigInteger gas;
        private BigInteger gasPrice;
        private BigInteger maxFeePerGas;
        private BigInteger maxPriorityFeePerGas;

        private Builder() {}

        /**
         * Sets the sender address (for msg.sender in the call context).
         *
         * @param from the sender address
         * @return this builder
         */
        public Builder from(Address from) {
            this.from = from;
            return this;
        }

        /**
         * Sets the target contract address.
         *
         * @param to the target address (required)
         * @return this builder
         */
        public Builder to(Address to) {
            this.to = to;
            return this;
        }

        /**
         * Sets the encoded function call data.
         *
         * @param data the call data
         * @return this builder
         */
        public Builder data(HexData data) {
            this.data = data;
            return this;
        }

        /**
         * Sets the wei value to send.
         *
         * @param value the value in wei
         * @return this builder
         */
        public Builder value(Wei value) {
            this.value = value;
            return this;
        }

        /**
         * Sets the gas limit.
         *
         * @param gas the gas limit
         * @return this builder
         */
        public Builder gas(BigInteger gas) {
            this.gas = gas;
            return this;
        }

        /**
         * Sets the gas price (for legacy calls).
         *
         * @param gasPrice the gas price
         * @return this builder
         */
        public Builder gasPrice(BigInteger gasPrice) {
            this.gasPrice = gasPrice;
            return this;
        }

        /**
         * Sets the max fee per gas (for EIP-1559 calls).
         *
         * @param maxFeePerGas the max fee per gas
         * @return this builder
         */
        public Builder maxFeePerGas(BigInteger maxFeePerGas) {
            this.maxFeePerGas = maxFeePerGas;
            return this;
        }

        /**
         * Sets the max priority fee per gas (for EIP-1559 calls).
         *
         * @param maxPriorityFeePerGas the max priority fee per gas
         * @return this builder
         */
        public Builder maxPriorityFeePerGas(BigInteger maxPriorityFeePerGas) {
            this.maxPriorityFeePerGas = maxPriorityFeePerGas;
            return this;
        }

        /**
         * Builds the CallRequest.
         *
         * @return the new CallRequest
         * @throws NullPointerException if to address is not set
         */
        public CallRequest build() {
            return new CallRequest(from, to, data, value, gas, gasPrice, maxFeePerGas, maxPriorityFeePerGas);
        }
    }
}
