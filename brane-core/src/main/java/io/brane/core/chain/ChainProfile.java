package io.brane.core.chain;

import io.brane.core.types.Wei;

/**
 * Configuration profile for an Ethereum-compatible blockchain.
 *
 * <p>
 * A chain profile encapsulates network-specific settings that configure how
 * transactions are built and submitted. Each profile defines the chain ID for
 * replay protection, an optional default RPC endpoint, and transaction format preferences.
 *
 * <p>
 * <strong>Field constraints:</strong>
 * <ul>
 * <li>{@code chainId} - must be positive (greater than 0)</li>
 * <li>{@code defaultRpcUrl} - optional; if provided, must be non-empty</li>
 * <li>{@code defaultPriorityFeePerGas} - optional; used for EIP-1559 transactions</li>
 * </ul>
 *
 * @param chainId                  the unique chain identifier (must be &gt; 0)
 * @param defaultRpcUrl            the default RPC endpoint URL (optional, may be null)
 * @param supportsEip1559          true if the chain supports EIP-1559 transactions
 * @param defaultPriorityFeePerGas the default priority fee (tip) for EIP-1559 transactions (optional)
 *
 * @see ChainProfiles
 */
public record ChainProfile(
        long chainId, String defaultRpcUrl, boolean supportsEip1559, Wei defaultPriorityFeePerGas) {

    /**
     * Validates all fields meet their constraints.
     *
     * @throws IllegalArgumentException if chainId is not positive or defaultRpcUrl is non-null but empty
     */
    public ChainProfile {
        if (chainId <= 0) {
            throw new IllegalArgumentException("chainId must be positive, got: " + chainId);
        }
        if (defaultRpcUrl != null && defaultRpcUrl.isBlank()) {
            throw new IllegalArgumentException("defaultRpcUrl cannot be empty");
        }
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
