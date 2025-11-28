package io.brane.rpc;

import io.brane.core.tx.UnsignedTransaction;

/**
 * Signs transactions for submission to the Ethereum network.
 * 
 * <p>
 * Implementations are responsible for:
 * <ul>
 * <li>Encoding the transaction for signing (preimage)</li>
 * <li>Hashing the preimage</li>
 * <li>Creating a valid ECDSA signature</li>
 * <li>Encoding the signed transaction as a hex string ready for broadcast</li>
 * </ul>
 * 
 * <h2>Usage Example</h2>
 * 
 * <pre>{@code
 * TransactionSigner signer = new PrivateKeyTransactionSigner("0x123...");
 * UnsignedTransaction tx = request.toUnsignedTransaction(chainId);
 * String signedHex = signer.sign(tx, chainId);
 * provider.sendRawTransaction(signedHex);
 * }</pre>
 * 
 * @see PrivateKeyTransactionSigner
 * @since 0.2.0
 */
@FunctionalInterface
public interface TransactionSigner {

    /**
     * Signs a transaction and returns the hex-encoded signed envelope.
     * 
     * @param tx      the unsigned transaction to sign
     * @param chainId the chain ID for EIP-155 protection
     * @return hex-encoded signed transaction ready for
     *         {@code eth_sendRawTransaction}
     */
    String sign(UnsignedTransaction tx, long chainId);
}
