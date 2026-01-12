// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.crypto;

import java.util.List;
import java.util.Map;

import io.brane.core.crypto.eip712.Eip712Domain;
import io.brane.core.crypto.eip712.TypedData;
import io.brane.core.crypto.eip712.TypedDataField;
import io.brane.core.crypto.eip712.TypedDataSigner;
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
     * @throws NullPointerException if tx is null
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
     * @param message the raw message bytes to sign (must not be null)
     * @return the EIP-191 compatible signature
     * @throws NullPointerException if message is null
     */
    Signature signMessage(byte[] message);

    /**
     * Signs typed data according to EIP-712.
     *
     * @param domain      the EIP-712 domain
     * @param primaryType the primary type name
     * @param types       type definitions
     * @param message     message data
     * @return signature with v=27 or v=28
     */
    default Signature signTypedData(
            Eip712Domain domain,
            String primaryType,
            Map<String, List<TypedDataField>> types,
            Map<String, Object> message) {
        return TypedDataSigner.signTypedData(domain, primaryType, types, message, this);
    }

    /**
     * Signs typed data according to EIP-712.
     * <p>
     * Convenience overload for the type-safe {@link TypedData} API.
     *
     * @param typedData the typed data to sign
     * @return signature with v=27 or v=28
     */
    default Signature signTypedData(TypedData<?> typedData) {
        return typedData.sign(this);
    }
}
