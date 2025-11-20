package io.brane.core.chain;

public final class ChainProfile {
    public final long chainId;
    public final String defaultRpcUrl;
    public final boolean supportsEip1559;

    private ChainProfile(final long chainId, final String defaultRpcUrl, final boolean supportsEip1559) {
        this.chainId = chainId;
        this.defaultRpcUrl = defaultRpcUrl;
        this.supportsEip1559 = supportsEip1559;
    }

    public static ChainProfile of(final long chainId, final String defaultRpcUrl, final boolean supportsEip1559) {
        return new ChainProfile(chainId, defaultRpcUrl, supportsEip1559);
    }

    @Override
    public String toString() {
        return "ChainProfile{"
                + "chainId=" + chainId
                + ", defaultRpcUrl='" + defaultRpcUrl + '\''
                + ", supportsEip1559=" + supportsEip1559
                + '}';
    }
}
