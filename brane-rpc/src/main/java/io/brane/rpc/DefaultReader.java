package io.brane.rpc;

import java.math.BigInteger;

import org.jspecify.annotations.Nullable;

import io.brane.core.model.BlockHeader;
import io.brane.core.types.Address;

/**
 * Default implementation of {@link Brane.Reader} for read-only blockchain operations.
 *
 * @since 0.1.0
 */
final class DefaultReader implements Brane.Reader {

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
    public void close() {
        // To be implemented
    }
}
