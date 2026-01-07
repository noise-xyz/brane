package io.brane.rpc;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.brane.core.chain.ChainProfile;

import io.brane.core.model.AccessListWithGas;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.LogEntry;
import io.brane.core.model.Transaction;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;

/**
 * Default implementation of {@link Brane.Reader} for read-only blockchain operations.
 *
 * <p>This implementation provides access to all query operations on the blockchain
 * without transaction signing capability. It uses the configured {@link BraneProvider}
 * for RPC communication and supports automatic retry with exponential backoff.
 *
 * @since 0.1.0
 */
final class DefaultReader implements Brane.Reader {

    private final BraneProvider provider;
    private final @Nullable ChainProfile chain;
    private final int maxRetries;
    private final RpcRetryConfig retryConfig;
    private final AtomicBoolean closed;

    /**
     * Creates a new DefaultReader with the specified configuration.
     *
     * @param provider    the RPC provider for blockchain communication
     * @param chain       the chain profile for network-specific settings (may be null)
     * @param maxRetries  the maximum number of retry attempts for transient failures
     * @param retryConfig the retry configuration for backoff timing
     */
    DefaultReader(
            final BraneProvider provider,
            final @Nullable ChainProfile chain,
            final int maxRetries,
            final RpcRetryConfig retryConfig) {
        this.provider = provider;
        this.chain = chain;
        this.maxRetries = maxRetries;
        this.retryConfig = retryConfig;
        this.closed = new AtomicBoolean(false);
    }

    @Override
    public BigInteger chainId() {
        ensureOpen();
        final JsonRpcResponse response = sendWithRetry("eth_chainId", List.of());
        final Object result = response.result();
        if (result == null) {
            throw new io.brane.core.error.RpcException(
                    0, "eth_chainId returned null", (String) null, (Throwable) null);
        }
        return io.brane.rpc.internal.RpcUtils.decodeHexBigInteger(result.toString());
    }

    @Override
    public BigInteger getBalance(final Address address) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public @Nullable BlockHeader getLatestBlock() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public @Nullable BlockHeader getBlockByNumber(final long blockNumber) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public @Nullable Transaction getTransactionByHash(final Hash hash) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public @Nullable TransactionReceipt getTransactionReceipt(final Hash hash) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public HexData call(final CallRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public HexData call(final CallRequest request, final BlockTag blockTag) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public List<LogEntry> getLogs(final LogFilter filter) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public BigInteger estimateGas(final TransactionRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public AccessListWithGas createAccessList(final TransactionRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public SimulateResult simulate(final SimulateRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public MulticallBatch batch() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Subscription onNewHeads(final Consumer<BlockHeader> callback) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Subscription onLogs(final LogFilter filter, final Consumer<LogEntry> callback) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public Optional<ChainProfile> chain() {
        return Optional.ofNullable(chain);
    }

    @Override
    public boolean canSubscribe() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            provider.close();
        }
    }

    /**
     * Returns the provider used by this reader.
     *
     * @return the RPC provider
     */
    BraneProvider provider() {
        return provider;
    }

    /**
     * Returns the maximum number of retries.
     *
     * @return the max retries
     */
    int maxRetries() {
        return maxRetries;
    }

    /**
     * Returns the retry configuration.
     *
     * @return the retry config
     */
    RpcRetryConfig retryConfig() {
        return retryConfig;
    }

    /**
     * Returns whether this reader has been closed.
     *
     * @return true if closed
     */
    boolean isClosed() {
        return closed.get();
    }

    /**
     * Ensures this reader is not closed.
     *
     * @throws IllegalStateException if this reader has been closed
     */
    void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("This reader has been closed");
        }
    }

    /**
     * Sends an RPC request with automatic retry on transient failures.
     *
     * @param method the JSON-RPC method name
     * @param params the method parameters
     * @return the JSON-RPC response
     */
    JsonRpcResponse sendWithRetry(final String method, final List<?> params) {
        return RpcRetry.runRpc(() -> provider.send(method, params), maxRetries + 1, retryConfig);
    }
}
