package io.brane.core.crypto;

import java.util.List;
import java.util.Map;

import javax.security.auth.Destroyable;

import io.brane.core.crypto.eip712.Eip712Domain;
import io.brane.core.crypto.eip712.TypedDataField;
import io.brane.core.tx.UnsignedTransaction;
import io.brane.core.types.Address;

/**
 * Transaction signer backed by a raw private key.
 * <p>
 * This implementation uses Brane's native crypto primitives for signing.
 * <p>
 * Implements {@link Destroyable} to allow clearing sensitive key material
 * from memory when no longer needed. Call {@link #destroy()} when the signer
 * is no longer required.
 */
public final class PrivateKeySigner implements Signer, Destroyable {

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

    /**
     * Creates a signer from an existing private key.
     *
     * <p>Package-private to support HD wallet derivation without hex roundtrip.
     *
     * @param privateKey the private key (must not be null)
     * @throws NullPointerException if privateKey is null
     */
    PrivateKeySigner(final PrivateKey privateKey) {
        this.privateKey = java.util.Objects.requireNonNull(privateKey, "privateKey cannot be null");
        this.address = privateKey.toAddress();
    }

    /**
     * Creates a signer from an existing private key.
     *
     * <p>This factory method is intended for HD wallet derivation in the
     * {@code io.brane.core.crypto.hd} package, avoiding hex encoding roundtrips.
     *
     * @param privateKey the private key (must not be null)
     * @return a new signer backed by the given private key
     * @throws NullPointerException if privateKey is null
     */
    public static PrivateKeySigner fromPrivateKey(final PrivateKey privateKey) {
        return new PrivateKeySigner(privateKey);
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

    @Override
    public Signature signTypedData(
            Eip712Domain domain,
            String primaryType,
            Map<String, List<TypedDataField>> types,
            Map<String, Object> message) {

        // Compute the final EIP-712 hash using TypedDataSigner
        // This hash is: keccak256("\x19\x01" || domainSeparator || hashStruct(message))
        byte[] finalHash = io.brane.core.crypto.eip712.TypedDataSigner
                .hashTypedData(domain, primaryType, types, message)
                .toBytes();

        // Sign using signFast for consistency with signMessage
        // signFast returns v=0 or v=1 (recovery ID)
        Signature sig = privateKey.signFast(finalHash);

        // Adjust v to 27 or 28 for EIP-712/EIP-191 compatibility
        return new Signature(sig.r(), sig.s(), sig.v() + 27);
    }

    /**
     * Destroys the underlying private key material.
     * <p>
     * After calling this method, any attempt to use this signer will throw
     * an {@link IllegalStateException}.
     */
    @Override
    public void destroy() {
        privateKey.destroy();
    }

    /**
     * Returns whether the underlying private key has been destroyed.
     *
     * @return true if {@link #destroy()} has been called
     */
    @Override
    public boolean isDestroyed() {
        return privateKey.isDestroyed();
    }
}
