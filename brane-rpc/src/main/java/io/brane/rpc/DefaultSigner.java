package io.brane.rpc;

import java.math.BigInteger;

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
    public void close() {
        // To be implemented
    }
}
