package io.brane.rpc;

import java.math.BigInteger;

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
    public void close() {
        // To be implemented
    }
}
