package io.brane.core.crypto;

import io.brane.core.tx.UnsignedTransaction;
import io.brane.core.types.Address;

/**
 * Defines a signer capable of signing transactions and providing its address.
 * <p>
 * This interface unifies the signing capabilities, allowing for various
 * implementations
 * such as local private keys, external KMS, or hardware wallets.
 */
public interface Signer {

    /**
     * Returns the address associated with this signer.
     *
     * @return the Ethereum address
     */
    Address address();

    /**
     * Signs a transaction and returns the raw signature.
     * <p>
     * The client is responsible for assembling the final signed transaction
     * envelope.
     *
     * @param tx      the unsigned transaction to sign
     * @param chainId the chain ID for replay protection
     * @return the raw signature of the transaction hash
     */
    Signature signTransaction(UnsignedTransaction tx, long chainId);

    /**
     * Signs a raw message according to EIP-191 (personal_sign).
     * <p>
     * The implementation is responsible for prefixing the message with
     * {@code "\u0019Ethereum Signed Message:\n" + message.length} before hashing
     * and signing.
     * The returned signature should have a {@code v} value of 27 or 28.
     *
     * @param message the raw message bytes to sign
     * @return the EIP-191 compatible signature
     */
    Signature signMessage(byte[] message);
}
