package io.brane.rpc;

import io.brane.core.chain.ChainProfile;
import io.brane.core.model.AccessListWithGas;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.LogEntry;
import io.brane.core.model.Transaction;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import java.util.List;
import io.brane.rpc.Subscription;
import java.util.Map;
import java.util.Objects;

/**
 * High-level {@link PublicClient} implementation with built-in chain
 * configuration.
 * 
 * <p>
 * This is the recommended way to create a {@link PublicClient} for well-known
 * chains.
 * It combines:
 * <ul>
 * <li>Chain-specific configuration ({@link ChainProfile})</li>
 * <li>Automatic RPC URL configuration (with override support)</li>
 * <li>Fluent builder API for easy setup</li>
 * </ul>
 * 
 * <p>
 * <strong>Thread Safety:</strong> This class is immutable and thread-safe once
 * built.
 * 
 * <p>
 * <strong>Usage Example:</strong>
 * 
 * <pre>{@code
 * // Use default RPC URL from chain profile
 * PublicClient client = BranePublicClient.forChain(ChainProfiles.MAINNET)
 *         .build();
 * 
 * // Override RPC URL (e.g., use Infura or Alchemy)
 * PublicClient client = BranePublicClient.forChain(ChainProfiles.MAINNET)
 *         .withRpcUrl("https://mainnet.infura.io/v3/YOUR_KEY")
 *         .build();
 * 
 * // Access chain profile
 * BranePublicClient braneClient = (BranePublicClient) client;
 * System.out.println("Chain ID: " + braneClient.profile().chainId());
 * }</pre>
 * 
 * @see PublicClient
 * @see ChainProfile
 */
public final class BranePublicClient implements PublicClient {
    private final PublicClient delegate;
    private final ChainProfile profile;

    private BranePublicClient(final PublicClient delegate, final ChainProfile profile) {
        this.delegate = delegate;
        this.profile = profile;
    }

    /**
     * Returns the chain profile for this client.
     * 
     * @return the chain configuration
     */
    public ChainProfile profile() {
        return profile;
    }

    /**
     * Creates a new builder for the specified chain.
     * 
     * @param profile the chain profile (e.g., {@code ChainProfiles.MAINNET})
     * @return a new builder instance
     */
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
    public HexData call(final CallRequest request, final BlockTag blockTag) {
        return delegate.call(request, blockTag);
    }

    @SuppressWarnings("deprecation")
    @Override
    @Deprecated(since = "0.1.0-alpha", forRemoval = true)
    public String call(final Map<String, Object> callObject, final String blockTag) {
        return delegate.call(callObject, blockTag);
    }

    @Override
    public List<LogEntry> getLogs(final LogFilter filter) {
        return delegate.getLogs(filter);
    }

    @Override
    public java.math.BigInteger getChainId() {
        return delegate.getChainId();
    }

    @Override
    public java.math.BigInteger getBalance(final io.brane.core.types.Address address) {
        return delegate.getBalance(address);
    }

    @Override
    public Subscription subscribeToNewHeads(java.util.function.Consumer<io.brane.core.model.BlockHeader> callback) {
        return delegate.subscribeToNewHeads(callback);
    }

    @Override
    public Subscription subscribeToLogs(LogFilter filter,
            java.util.function.Consumer<io.brane.core.model.LogEntry> callback) {
        return delegate.subscribeToLogs(filter, callback);
    }

    @Override
    public AccessListWithGas createAccessList(final TransactionRequest request) {
        return delegate.createAccessList(request);
    }

    @Override
    public MulticallBatch createBatch() {
        return delegate.createBatch();
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
            final String rpcUrl = rpcUrlOverride != null ? rpcUrlOverride : profile.defaultRpcUrl();

            if (rpcUrl == null || rpcUrl.isBlank()) {
                throw new IllegalStateException(
                        "No RPC URL configured for chainId="
                                + profile.chainId()
                                + ". Either set a defaultRpcUrl in ChainProfile or call withRpcUrl(...).");
            }

            final BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
            final PublicClient publicClient = PublicClient.from(provider);
            return new BranePublicClient(publicClient, profile);
        }
    }
}
