package io.brane.core.chain;

import io.brane.core.types.Wei;

public record ChainProfile(
        long chainId, String defaultRpcUrl, boolean supportsEip1559, Wei defaultPriorityFeePerGas) {

    public static ChainProfile of(
            final long chainId,
            final String defaultRpcUrl,
            final boolean supportsEip1559,
            final Wei defaultPriorityFeePerGas) {
        return new ChainProfile(chainId, defaultRpcUrl, supportsEip1559, defaultPriorityFeePerGas);
    }
}
