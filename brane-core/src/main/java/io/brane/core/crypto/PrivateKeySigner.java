package io.brane.core.crypto;

import io.brane.core.tx.Eip1559Transaction;
import io.brane.core.tx.LegacyTransaction;
import io.brane.core.tx.UnsignedTransaction;
import io.brane.core.types.Address;
import io.brane.primitives.Hex;

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
    public String signTransaction(final UnsignedTransaction tx, final long chainId) {
        // 1. Encode transaction for signing
        final byte[] preimage = tx.encodeForSigning(chainId);

        // 2. Hash the preimage
        final byte[] messageHash = Keccak256.hash(preimage);

        // 3. Sign the hash
        final Signature baseSig = privateKey.signFast(messageHash);

        // 4. Create appropriately encoded signature based on transaction type
        final Signature signature;
        if (tx instanceof LegacyTransaction) {
            // For legacy transactions, use EIP-155 encoding: v = chainId * 2 + 35 + yParity
            final int v = (int) (chainId * 2 + 35 + baseSig.v());
            signature = new Signature(baseSig.r(), baseSig.s(), v);
        } else if (tx instanceof Eip1559Transaction) {
            // For EIP-1559, v is just yParity (0 or 1)
            signature = baseSig;
        } else {
            throw new IllegalArgumentException("Unsupported transaction type: " + tx.getClass().getName());
        }

        // 5. Encode as signed envelope
        final byte[] envelope = tx.encodeAsEnvelope(signature);

        // 6. Convert to hex string
        return Hex.encode(envelope);
    }

    @Override
    public Signature signMessage(final byte[] message) {
        // EIP-191 style signing (Ethereum Signed Message)
        // \x19Ethereum Signed Message:\n + length + message
        // For simplicity in this core implementation, we sign the hash directly or the
        // raw message?
        // Usually signMessage implies EIP-191.
        // Let's implement standard EIP-191 hashing here.

        byte[] prefix = ("\u0019Ethereum Signed Message:\n" + message.length)
                .getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] prefixedMessage = new byte[prefix.length + message.length];
        System.arraycopy(prefix, 0, prefixedMessage, 0, prefix.length);
        System.arraycopy(message, 0, prefixedMessage, prefix.length, message.length);

        return privateKey.signFast(Keccak256.hash(prefixedMessage));
    }
}
