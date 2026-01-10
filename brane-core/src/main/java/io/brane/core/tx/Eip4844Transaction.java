package io.brane.core.tx;

import io.brane.core.crypto.Signature;

/**
 * EIP-4844 blob transaction (stub for compilation).
 *
 * <p>This is a placeholder implementation to satisfy the sealed permits clause.
 * Full implementation pending.
 *
 * @since 0.2.0
 */
public record Eip4844Transaction() implements UnsignedTransaction {

    @Override
    public byte[] encodeForSigning(final long chainId) {
        throw new UnsupportedOperationException("EIP-4844 not yet implemented");
    }

    @Override
    public byte[] encodeAsEnvelope(final Signature signature) {
        throw new UnsupportedOperationException("EIP-4844 not yet implemented");
    }
}
