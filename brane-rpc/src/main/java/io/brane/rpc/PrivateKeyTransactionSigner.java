package io.brane.rpc;

import io.brane.core.crypto.Keccak256;
import io.brane.core.crypto.PrivateKey;
import io.brane.core.crypto.Signature;
import io.brane.core.tx.Eip1559Transaction;
import io.brane.core.tx.LegacyTransaction;
import io.brane.core.tx.UnsignedTransaction;
import io.brane.core.types.Address;
import io.brane.primitives.Hex;

/**
 * Transaction signer backed by a raw private key.
 * 
 * <p>
 * This implementation uses Brane's native crypto primitives for signing:
 * <ul>
 * <li>{@link PrivateKey} - secp256k1 private key with deterministic ECDSA (RFC
 * 6979)</li>
 * <li>{@link Keccak256} - Ethereum Keccak-256 hashing</li>
 * <li>{@link Signature} - ECDSA signature with EIP-155 support</li>
 * </ul>
 * 
 * <h2>Signature Encoding</h2>
 * <p>
 * For legacy transactions (EIP-155):
 * 
 * <pre>
 * v = chainId * 2 + 35 + yParity
 * </pre>
 * 
 * <p>
 * For EIP-1559 transactions:
 * 
 * <pre>
 * v = yParity (0 or 1)
 * </pre>
 * 
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * TransactionSigner signer = new PrivateKeyTransactionSigner("0x4c088...");
 * Address signerAddress = ((PrivateKeyTransactionSigner) signer).address();
 * 
 * UnsignedTransaction tx = request.toUnsignedTransaction(1); // mainnet
 * String signedHex = signer.sign(tx, 1);
 * }</pre>
 * 
 * @since 0.2.0
 */
public final class PrivateKeyTransactionSigner implements TransactionSigner {

    private final PrivateKey privateKey;
    private final Address address;

    /**
     * Creates a signer from a hex-encoded private key.
     * 
     * @param privateKeyHex the private key (with or without 0x prefix)
     * @throws IllegalArgumentException if the private key is invalid
     */
    public PrivateKeyTransactionSigner(final String privateKeyHex) {
        this.privateKey = PrivateKey.fromHex(privateKeyHex);
        this.address = privateKey.toAddress();
    }

    /**
     * Returns the Ethereum address for this signer.
     * 
     * @return the address derived from the private key
     */
    public Address address() {
        return address;
    }

    @Override
    public String sign(final UnsignedTransaction tx, final long chainId) {
        // 1. Encode transaction for signing
        final byte[] preimage = tx.encodeForSigning(chainId);

        // 2. Hash the preimage
        final byte[] messageHash = Keccak256.hash(preimage);

        // 3. Sign the hash
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
}
