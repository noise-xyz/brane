package io.brane.rpc;

import java.math.BigInteger;
import java.util.List;

import org.jspecify.annotations.Nullable;

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
 * @since 0.1.0
 */
final class DefaultSigner implements Brane.Signer {

    @Override
    public BigInteger chainId() {
        throw new UnsupportedOperationException("Not implemented yet");
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
    public void close() {
        // To be implemented
    }
}
