package io.brane.rpc;

import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;

/**
 * Represents an active impersonation session for a specific address on a test node.
 * <p>
 * Impersonation allows sending transactions from any address without possessing
 * its private key. This is useful for testing scenarios where you need to simulate
 * actions from specific addresses (e.g., whales, DAO contracts, or protocol admins).
 * <p>
 * <strong>Important:</strong> This feature is only available on test nodes (Anvil,
 * Hardhat, Ganache) and will fail on production networks.
 * <p>
 * The session automatically stops impersonation when closed, either explicitly via
 * {@link #close()} or when used with try-with-resources.
 * <p>
 * <strong>Usage:</strong>
 * <pre>{@code
 * // Impersonate a whale address for testing
 * Address whale = Address.from("0x...");
 *
 * try (ImpersonationSession session = tester.impersonate(whale)) {
 *     // Send transaction as the whale
 *     TransactionRequest request = TransactionRequest.builder()
 *         .to(recipient)
 *         .value(Wei.fromEther("100"))
 *         .build();
 *
 *     TransactionReceipt receipt = session.sendTransactionAndWait(request);
 *     System.out.println("Transfer confirmed in block " + receipt.blockNumber());
 * }
 * // Impersonation automatically stopped
 * }</pre>
 *
 * @see Brane.Tester
 * @since 0.2.0
 */
public interface ImpersonationSession extends AutoCloseable {

    /**
     * Returns the address being impersonated in this session.
     *
     * @return the impersonated address, never {@code null}
     * @since 0.2.0
     */
    Address address();

    /**
     * Sends a transaction from the impersonated address.
     * <p>
     * The transaction's {@code from} field is automatically set to the impersonated
     * address. Any existing {@code from} value in the request is ignored.
     * <p>
     * This method returns immediately after the transaction is submitted to the
     * mempool. Use {@link #sendTransactionAndWait(TransactionRequest)} if you need
     * to wait for confirmation.
     *
     * @param request the transaction request (from address is automatically set)
     * @return the transaction hash
     * @throws io.brane.core.error.RpcException if the RPC call fails
     * @since 0.2.0
     */
    Hash sendTransaction(TransactionRequest request);

    /**
     * Sends a transaction from the impersonated address and waits for confirmation.
     * <p>
     * Uses default timeout (60 seconds) and poll interval (1 second).
     *
     * @param request the transaction request (from address is automatically set)
     * @return the transaction receipt once confirmed
     * @throws io.brane.core.error.RpcException if the RPC call fails or times out
     * @throws io.brane.core.error.RevertException if the transaction reverts
     * @since 0.2.0
     */
    TransactionReceipt sendTransactionAndWait(TransactionRequest request);

    /**
     * Sends a transaction from the impersonated address and waits for confirmation
     * with custom timeout settings.
     *
     * @param request            the transaction request (from address is automatically set)
     * @param timeoutMillis      maximum time to wait for confirmation, in milliseconds
     * @param pollIntervalMillis how often to poll for the receipt, in milliseconds
     * @return the transaction receipt once confirmed
     * @throws io.brane.core.error.RpcException if the RPC call fails or times out
     * @throws io.brane.core.error.RevertException if the transaction reverts
     * @since 0.2.0
     */
    TransactionReceipt sendTransactionAndWait(
            TransactionRequest request, long timeoutMillis, long pollIntervalMillis);

    /**
     * Stops the impersonation session.
     * <p>
     * After calling this method, the impersonated address can no longer be used
     * to send transactions without proper signing. This method is idempotent;
     * calling it multiple times has no additional effect.
     * <p>
     * This method does not throw exceptions. Any errors during cleanup are logged
     * but not propagated.
     *
     * @since 0.2.0
     */
    @Override
    void close();
}
