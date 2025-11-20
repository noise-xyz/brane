package io.brane.rpc;

import io.brane.core.chain.ChainProfile;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.LogEntry;
import io.brane.core.model.Transaction;
import io.brane.core.types.Hash;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class BranePublicClient implements PublicClient {
    private final PublicClient delegate;
    private final ChainProfile profile;

    private BranePublicClient(final PublicClient delegate, final ChainProfile profile) {
        this.delegate = delegate;
        this.profile = profile;
    }

    public ChainProfile profile() {
        return profile;
    }

    public static Builder forChain(final ChainProfile profile) {
        return new Builder(profile);
    }

    @Override
    public BlockHeader getLatestBlock() {
        return delegate.getLatestBlock();
    }

    @Override
    public BlockHeader getBlockByNumber(final long blockNumber) {
        return delegate.getBlockByNumber(blockNumber);
    }

    @Override
    public Transaction getTransactionByHash(final Hash hash) {
        return delegate.getTransactionByHash(hash);
    }

    @Override
    public String call(final Map<String, Object> callObject, final String blockTag) {
        return delegate.call(callObject, blockTag);
    }

    @Override
    public List<LogEntry> getLogs(final LogFilter filter) {
        return delegate.getLogs(filter);
    }

    public static final class Builder {
        private final ChainProfile profile;
        private String rpcUrlOverride;

        private Builder(final ChainProfile profile) {
            this.profile = Objects.requireNonNull(profile, "profile");
        }

        public Builder withRpcUrl(final String rpcUrl) {
            this.rpcUrlOverride = rpcUrl;
            return this;
        }

        public BranePublicClient build() {
            final String rpcUrl =
                    rpcUrlOverride != null ? rpcUrlOverride : profile.defaultRpcUrl;

            if (rpcUrl == null || rpcUrl.isBlank()) {
                throw new IllegalStateException(
                        "No RPC URL configured for chainId="
                                + profile.chainId
                                + ". Either set a defaultRpcUrl in ChainProfile or call withRpcUrl(...).");
            }

            final BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
            final PublicClient publicClient = PublicClient.from(provider);
            return new BranePublicClient(publicClient, profile);
        }
    }
}
