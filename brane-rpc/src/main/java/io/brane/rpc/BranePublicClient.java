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
import java.util.concurrent.atomic.AtomicBoolean;

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
 * <strong>Resource Management:</strong> This class implements {@link AutoCloseable}
 * and must be closed when done to release the underlying provider resources.
 * Use try-with-resources for automatic cleanup:
 *
 * <pre>{@code
 * try (BranePublicClient client = BranePublicClient.forChain(ChainProfiles.MAINNET).build()) {
 *     // Use client...
 * }
 * }</pre>
 *
 * <p>
 * <strong>Usage Example:</strong>
 *
 * <pre>{@code
 * // Use default RPC URL from chain profile
 * BranePublicClient client = BranePublicClient.forChain(ChainProfiles.MAINNET)
 *         .build();
 *
 * // Override RPC URL (e.g., use Infura or Alchemy)
 * BranePublicClient client = BranePublicClient.forChain(ChainProfiles.MAINNET)
 *         .withRpcUrl("https://mainnet.infura.io/v3/YOUR_KEY")
 *         .build();
 *
 * // Access chain profile
 * System.out.println("Chain ID: " + client.profile().chainId());
 *
 * // Close when done
 * client.close();
 * }</pre>
 *
 * @see PublicClient
 * @see ChainProfile
 */
public final class BranePublicClient implements PublicClient, AutoCloseable {
    private final PublicClient delegate;
    private final ChainProfile profile;
    private final BraneProvider provider;
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private BranePublicClient(final PublicClient delegate, final ChainProfile profile, final BraneProvider provider) {
        this.delegate = delegate;
        this.profile = profile;
        this.provider = provider;
    }

    /**
     * Closes the underlying provider and releases resources.
     *
     * <p>After calling this method, the client should not be used.
     * This method is idempotent and can be called multiple times safely.
     */
    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return; // Already closed
        }
        provider.close();
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
     * Checks if the client is closed and throws if so.
     *
     * @throws IllegalStateException if the client has been closed
     */
    private void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("Client is closed");
        }
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
        ensureOpen();
        return delegate.getLatestBlock();
    }

    @Override
    public BlockHeader getBlockByNumber(final long blockNumber) {
        ensureOpen();
        return delegate.getBlockByNumber(blockNumber);
    }

    @Override
    public Transaction getTransactionByHash(final Hash hash) {
        ensureOpen();
        return delegate.getTransactionByHash(hash);
    }

    @Override
    public HexData call(final CallRequest request, final BlockTag blockTag) {
        ensureOpen();
        return delegate.call(request, blockTag);
    }

    @SuppressWarnings("deprecation")
    @Override
    @Deprecated(since = "0.1.0-alpha", forRemoval = true)
    public String call(final Map<String, Object> callObject, final String blockTag) {
        ensureOpen();
        return delegate.call(callObject, blockTag);
    }

    @Override
    public List<LogEntry> getLogs(final LogFilter filter) {
        ensureOpen();
        return delegate.getLogs(filter);
    }

    @Override
    public java.math.BigInteger getChainId() {
        ensureOpen();
        return delegate.getChainId();
    }

    @Override
    public java.math.BigInteger getBalance(final io.brane.core.types.Address address) {
        ensureOpen();
        return delegate.getBalance(address);
    }

    @Override
    public Subscription subscribeToNewHeads(java.util.function.Consumer<io.brane.core.model.BlockHeader> callback) {
        ensureOpen();
        return delegate.subscribeToNewHeads(callback);
    }

    @Override
    public Subscription subscribeToLogs(LogFilter filter,
            java.util.function.Consumer<io.brane.core.model.LogEntry> callback) {
        ensureOpen();
        return delegate.subscribeToLogs(filter, callback);
    }

    @Override
    public AccessListWithGas createAccessList(final TransactionRequest request) {
        ensureOpen();
        return delegate.createAccessList(request);
    }

    @Override
    public MulticallBatch createBatch() {
        ensureOpen();
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

        /**
         * Builds a new {@link BranePublicClient} instance.
         *
         * <p>This method creates an HTTP provider and wraps it in a public client.
         * If client creation fails after the provider is created, the provider is
         * automatically closed to prevent resource leaks.
         *
         * @return a new BranePublicClient instance
         * @throws IllegalStateException if no RPC URL is configured
         */
        public BranePublicClient build() {
            final String rpcUrl = rpcUrlOverride != null ? rpcUrlOverride : profile.defaultRpcUrl();

            if (rpcUrl == null || rpcUrl.isBlank()) {
                throw new IllegalStateException(
                        "No RPC URL configured for chainId="
                                + profile.chainId()
                                + ". Either set a defaultRpcUrl in ChainProfile or call withRpcUrl(...).");
            }

            final BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
            try {
                final PublicClient publicClient = PublicClient.from(provider);
                return new BranePublicClient(publicClient, profile, provider);
            } catch (RuntimeException | Error e) {
                // Close provider to prevent resource leak if client creation fails
                provider.close();
                throw e;
            }
        }
    }
}
