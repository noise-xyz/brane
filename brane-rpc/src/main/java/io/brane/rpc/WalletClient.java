package io.brane.rpc;

import io.brane.core.model.TransactionRequest;
import io.brane.core.error.RpcException;
import io.brane.core.error.RevertException;
import io.brane.core.error.ChainMismatchException;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.types.Hash;

/**
 * A client for signing and sending transactions.
 * <p>
 * This client wraps a {@link PublicClient} and adds transaction management
 * capabilities:
 * <ul>
 * <li>Gas estimation via {@code eth_estimateGas}</li>
 * <li>Nonce fetching via {@code eth_getTransactionCount}</li>
 * <li>Gas price/fee calculation for both legacy and EIP-1559 transactions</li>
 * <li>Transaction signing</li>
 * <li>Transaction submission via {@code eth_sendRawTransaction}</li>
 * </ul>
 * 
 * <p>
 * <strong>Transaction Lifecycle:</strong>
 * <ol>
 * <li>Create a {@link TransactionRequest} with at minimum a {@code from}
 * address</li>
 * <li>Call {@link #sendTransaction} to submit → returns transaction hash
 * immediately</li>
 * <li>Optionally call {@link #sendTransactionAndWait} to submit and wait for
 * confirmation</li>
 * </ol>
 * 
 * <p>
 * <strong>Usage Example:</strong>
 * 
 * <pre>{@code
 * WalletClient client = BraneWalletClient.forChain(ChainProfiles.MAINNET)
 *         .withPrivateKey("0x...")
 *         .build();
 * 
 * // Simple transfer
 * TransactionRequest transfer = new TransactionRequest(
 *         myAddress, // from
 *         recipientAddress, // to
 *         Wei.fromEther("0.1"), // value
 *         null, null, null, // gas fields (auto-filled)
 *         null, null, // nonce, data (auto-filled)
 *         true, // EIP-1559
 *         null // access list
 * );
 * 
 * // Send and wait for confirmation
 * TransactionReceipt receipt = client.sendTransactionAndWait(
 *         transfer,
 *         60_000, // timeout: 60 seconds
 *         1_000 // poll every 1 second
 * );
 * 
 * if (receipt.status()) {
 *     System.out.println("✓ Transaction confirmed in block " + receipt.blockNumber());
 * }
 * }</pre>
 * 
 * @see TransactionRequest
 * @see TransactionReceipt
 * @see DefaultWalletClient
 */
public interface WalletClient {

    /**
     * Submits a transaction to the blockchain and returns immediately.
     * 
     * <p>
     * This method:
     * <ol>
     * <li>Fills in missing gas/nonce fields if null in the request</li>
     * <li>Signs the transaction using the configured signer</li>
     * <li>Broadcasts via {@code eth_sendRawTransaction}</li>
     * <li>Returns the transaction hash without waiting for confirmation</li>
     * </ol>
     * 
     * <p>
     * <strong>Note:</strong> The transaction is submitted but NOT confirmed.
     * Use {@link #sendTransactionAndWait} if you need to wait for confirmation.
     * 
     * @param request the transaction request with at minimum a {@code from}
     *                address;
     *                all other fields are optional and will be auto-filled
     * @return the transaction hash of the submitted transaction
     * @throws RpcException           if the RPC call fails (network error,
     *                                insufficient funds, etc.)
     * @throws ChainMismatchException if the configured chain ID doesn't match the
     *                                node
     */
    Hash sendTransaction(TransactionRequest request);

    /**
     * Submits a transaction and waits for it to be confirmed in a block.
     * 
     * <p>
     * This method combines {@link #sendTransaction} with polling for the receipt.
     * It will:
     * <ol>
     * <li>Submit the transaction (same as {@link #sendTransaction})</li>
     * <li>Poll {@code eth_getTransactionReceipt} at the specified interval</li>
     * <li>Return the receipt once the transaction is included in a block</li>
     * <li>Throw an exception if timeout is reached or transaction reverts</li>
     * </ol>
     * 
     * <p>
     * <strong>Revert Detection:</strong> If the transaction is included but reverts
     * ({@code status = false}), this method will decode the revert reason and throw
     * a {@link RevertException} with details.
     * 
     * @param request            the transaction request
     * @param timeoutMillis      maximum time to wait for confirmation, in
     *                           milliseconds
     * @param pollIntervalMillis how often to poll for the receipt, in milliseconds
     * @return the transaction receipt once confirmed
     * @throws RpcException           if the RPC call fails
     * @throws RevertException        if the transaction reverts
     * @throws ChainMismatchException if the configured chain ID doesn't match
     * @throws RuntimeException       if the timeout is exceeded before confirmation
     */
    TransactionReceipt sendTransactionAndWait(
            TransactionRequest request, long timeoutMillis, long pollIntervalMillis);
}
