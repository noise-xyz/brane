package io.brane.core.chain;

import io.brane.core.types.Wei;

import java.util.Objects;

/**
 * Configuration profile for an Ethereum-compatible blockchain.
 *
 * <p>
 * A chain profile encapsulates network-specific settings that configure how
 * transactions are built and submitted. Each profile defines the chain ID for
 * replay protection, a default RPC endpoint, and transaction format preferences.
 *
 * <p>
 * <strong>Field constraints:</strong>
 * <ul>
 * <li>{@code chainId} - must be positive (greater than 0)</li>
 * <li>{@code defaultRpcUrl} - must be non-null and non-empty</li>
 * <li>{@code defaultPriorityFeePerGas} - must be non-null (required for EIP-1559)</li>
 * </ul>
 *
 * @param chainId                  the unique chain identifier (must be &gt; 0)
 * @param defaultRpcUrl            the default RPC endpoint URL (required)
 * @param supportsEip1559          true if the chain supports EIP-1559 transactions
 * @param defaultPriorityFeePerGas the default priority fee (tip) for EIP-1559 transactions (required)
 *
 * @see ChainProfiles
 */
public record ChainProfile(
        long chainId, String defaultRpcUrl, boolean supportsEip1559, Wei defaultPriorityFeePerGas) {

    /**
     * Validates all fields meet their constraints.
     *
     * @throws IllegalArgumentException if chainId is not positive or defaultRpcUrl is empty
     * @throws NullPointerException if defaultRpcUrl or defaultPriorityFeePerGas is null
     */
    public ChainProfile {
        if (chainId <= 0) {
            throw new IllegalArgumentException("chainId must be positive, got: " + chainId);
        }
        Objects.requireNonNull(defaultRpcUrl, "defaultRpcUrl cannot be null");
        if (defaultRpcUrl.isBlank()) {
            throw new IllegalArgumentException("defaultRpcUrl cannot be empty");
        }
        Objects.requireNonNull(defaultPriorityFeePerGas, "defaultPriorityFeePerGas cannot be null");
    }

    /**
     * Creates a new ChainProfile with the specified settings.
     *
     * <p>This factory method is provided alongside the canonical constructor for API consistency
     * and to allow for potential future enhancements (e.g., caching common profiles, validation
     * in a central location, or parameter transformation). Both the factory method and constructor
     * are valid ways to create instances; the factory method is preferred in application code
     * for readability.
     *
     * @param chainId                  the unique chain identifier (must be &gt; 0)
     * @param defaultRpcUrl            the default RPC endpoint URL
     * @param supportsEip1559          true if the chain supports EIP-1559
     * @param defaultPriorityFeePerGas the default priority fee for EIP-1559
     * @return a new ChainProfile instance
     */
    public static ChainProfile of(
            final long chainId,
            final String defaultRpcUrl,
            final boolean supportsEip1559,
            final Wei defaultPriorityFeePerGas) {
        return new ChainProfile(chainId, defaultRpcUrl, supportsEip1559, defaultPriorityFeePerGas);
    }
}
