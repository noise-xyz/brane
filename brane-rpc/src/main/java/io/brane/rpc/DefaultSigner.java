package io.brane.rpc;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
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
 * Default implementation of {@link Brane.Signer} for full blockchain operations.
 *
 * <p>This implementation provides access to all read operations via delegation to
 * an internal {@link DefaultReader}, plus transaction signing and sending capabilities
 * using the configured {@link Signer} and {@link SmartGasStrategy}.
 *
 * @since 0.1.0
 */
final class DefaultSigner implements Brane.Signer {

    private final DefaultReader reader;
    private final io.brane.core.crypto.Signer signer;
    private final SmartGasStrategy gasStrategy;

    /**
     * Creates a new DefaultSigner with the specified configuration.
     *
     * @param provider    the RPC provider for blockchain communication
     * @param signer      the signer for transaction signing
     * @param chain       the chain profile for network-specific settings (may be null)
     * @param maxRetries  the maximum number of retry attempts for transient failures
     * @param retryConfig the retry configuration for backoff timing
     */
    DefaultSigner(
            final BraneProvider provider,
            final io.brane.core.crypto.Signer signer,
            final @Nullable ChainProfile chain,
            final int maxRetries,
            final RpcRetryConfig retryConfig) {
        this.reader = new DefaultReader(provider, chain, maxRetries, retryConfig);
        this.signer = signer;
        // SmartGasStrategy will be initialized in P3-07 when sendTransaction is implemented
        this.gasStrategy = null;
    }

    @Override
    public BigInteger chainId() {
        return reader.chainId();
    }

    @Override
    public BigInteger getBalance(final Address address) {
        return reader.getBalance(address);
    }

    @Override
    public @Nullable BlockHeader getLatestBlock() {
        return reader.getLatestBlock();
    }

    @Override
    public @Nullable BlockHeader getBlockByNumber(final long blockNumber) {
        return reader.getBlockByNumber(blockNumber);
    }

    @Override
    public @Nullable Transaction getTransactionByHash(final Hash hash) {
        return reader.getTransactionByHash(hash);
    }

    @Override
    public @Nullable TransactionReceipt getTransactionReceipt(final Hash hash) {
        return reader.getTransactionReceipt(hash);
    }

    @Override
    public HexData call(final CallRequest request) {
        return reader.call(request);
    }

    @Override
    public HexData call(final CallRequest request, final BlockTag blockTag) {
        return reader.call(request, blockTag);
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
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public boolean canSubscribe() {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public void close() {
        // To be implemented
    }

    @Override
    public Hash sendTransaction(final TransactionRequest request) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public TransactionReceipt sendTransactionAndWait(
            final TransactionRequest request, final long timeoutMillis, final long pollIntervalMillis) {
        throw new UnsupportedOperationException("Not implemented yet");
    }

    @Override
    public io.brane.core.crypto.Signer signer() {
        throw new UnsupportedOperationException("Not implemented yet");
    }
}
