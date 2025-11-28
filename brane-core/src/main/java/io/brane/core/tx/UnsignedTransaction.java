package io.brane.core.tx;

import io.brane.core.crypto.Signature;

/**
 * Unsigned Ethereum transaction.
 * 
 * <p>
 * This is a sealed interface that represents a transaction before it has been
 * signed. Implementations include:
 * <ul>
 * <li>{@link LegacyTransaction} - EIP-155 transactions with gasPrice</li>
 * <li>{@link Eip1559Transaction} - EIP-1559 transactions with dynamic fees</li>
 * </ul>
 * 
 * <h2>Usage</h2>
 * 
 * <pre>{@code
 * // Create transaction
 * UnsignedTransaction tx = new LegacyTransaction(...);
 * 
 * // Encode for signing
 * byte[] preimage = tx.encodeForSigning(chainId);
 * byte[] messageHash = Keccak256.hash(preimage);
 * 
 * // Sign
 * Signature signature = privateKey.sign(messageHash);
 * 
 * // Encode for broadcast
 * byte[] envelope = tx.encodeAsEnvelope(signature);
 * String hex = Hex.encode(envelope);
 * }</pre>
 * 
 * @since 0.2.0
 */
public sealed interface UnsignedTransaction permits LegacyTransaction, Eip1559Transaction {

    /**
     * Encodes the transaction for signing (pre-signature RLP encoding).
     * 
     * <p>
     * For EIP-155 (legacy): RLP([nonce, gasPrice, gasLimit, to, value, data,
     * chainId, 0, 0])
     * <br>
     * For EIP-1559: 0x02 || RLP([chainId, nonce, maxPriorityFee, maxFee, gasLimit,
     * to, value, data, accessList])
     * 
     * @param chainId the chain ID for EIP-155 encoding
     * @return bytes to hash and sign
     */
    byte[] encodeForSigning(long chainId);

    /**
     * Encodes the signed transaction as a network-ready envelope.
     * 
     * <p>
     * For EIP-155 (legacy): RLP([nonce, gasPrice, gasLimit, to, value, data, v, r,
     * s])
     * <br>
     * For EIP-1559: 0x02 || RLP([chainId, nonce, maxPriorityFee, maxFee, gasLimit,
     * to, value, data, accessList, yParity, r, s])
     * 
     * @param signature the signature to include
     * @return bytes ready for eth_sendRawTransaction
     */
    byte[] encodeAsEnvelope(Signature signature);
}
