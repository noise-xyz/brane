package io.brane.core.crypto;

import io.brane.core.tx.UnsignedTransaction;
import io.brane.core.types.Address;

/**
 * Transaction signer backed by a raw private key.
 * <p>
 * This implementation uses Brane's native crypto primitives for signing.
 */
public final class PrivateKeySigner implements Signer {

    private final PrivateKey privateKey;
    private final Address address;

    /**
     * Creates a signer from a hex-encoded private key.
     *
     * @param privateKeyHex the private key (with or without 0x prefix)
     * @throws IllegalArgumentException if the private key is invalid
     */
    public PrivateKeySigner(final String privateKeyHex) {
        this.privateKey = PrivateKey.fromHex(privateKeyHex);
        this.address = privateKey.toAddress();
    }

    @Override
    public Address address() {
        return address;
    }

    @Override
    public Signature signTransaction(final UnsignedTransaction tx, final long chainId) {
        // 1. Encode transaction for signing
        final byte[] preimage = tx.encodeForSigning(chainId);

        // 2. Hash the preimage
        final byte[] messageHash = Keccak256.hash(preimage);

        // 3. Sign the hash
        return privateKey.signFast(messageHash);
    }

    @Override
    public Signature signMessage(final byte[] message) {
        java.util.Objects.requireNonNull(message, "message cannot be null");
        // EIP-191 style signing (Ethereum Signed Message)
        // \x19Ethereum Signed Message:\n + length + message
        byte[] prefix = ("\u0019Ethereum Signed Message:\n" + message.length)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] prefixedMessage = new byte[prefix.length + message.length];
        System.arraycopy(prefix, 0, prefixedMessage, 0, prefix.length);
        System.arraycopy(message, 0, prefixedMessage, prefix.length, message.length);

        Signature sig = privateKey.signFast(Keccak256.hash(prefixedMessage));

        // Adjust v to be 27 or 28 for EIP-191 compatibility
        int v = sig.v() + 27;
        return new Signature(sig.r(), sig.s(), v);
    }

    /**
     * Signs a raw 32-byte hash directly without any prefixing.
     *
     * <p>This is used for EIP-712 typed data signing where the hash
     * already includes the appropriate prefix (0x19 0x01).
     *
     * @param hash the 32-byte hash to sign
     * @return signature with v=0 or v=1
     * @throws IllegalArgumentException if hash is not 32 bytes
     */
    public Signature signRawHash(final byte[] hash) {
        java.util.Objects.requireNonNull(hash, "hash cannot be null");
        if (hash.length != 32) {
            throw new IllegalArgumentException("Hash must be 32 bytes, got " + hash.length);
        }
        return privateKey.signFast(hash);
    }
}
