package io.brane.rpc;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.brane.core.chain.ChainProfile;
import io.brane.core.crypto.Signer;

import io.brane.core.model.AccessListWithGas;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.LogEntry;
import io.brane.core.model.Transaction;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;

/**
 * Unified entry point for interacting with Ethereum/EVM blockchains.
 *
 * <p>This sealed interface provides a type-safe hierarchy for blockchain clients:
 * <ul>
 *   <li>{@link Reader} - Read-only operations (queries, calls, subscriptions)</li>
 *   <li>{@link Signer} - Full operations including transaction signing and sending</li>
 * </ul>
 *
 * <p><strong>Usage with pattern matching:</strong>
 * <pre>{@code
 * Brane client = Brane.connect("https://eth.example.com");
 * switch (client) {
 *     case Brane.Reader r -> System.out.println("Read-only client");
 *     case Brane.Signer s -> System.out.println("Signing client");
 * }
 * }</pre>
 *
 * @since 0.1.0
 */
public sealed interface Brane extends AutoCloseable permits Brane.Reader, Brane.Signer {

    /**
     * Returns the chain ID of the connected network.
     *
     * @return the chain ID
     * @since 0.1.0
     */
    BigInteger chainId();

    /**
     * Retrieves the balance of an account.
     *
     * @param address the account address
     * @return the balance in Wei
     * @since 0.1.0
     */
    BigInteger getBalance(Address address);

    /**
     * Retrieves the latest block header.
     *
     * @return the latest block header, or {@code null} if not found
     * @since 0.1.0
     */
    @Nullable BlockHeader getLatestBlock();

    /**
     * Retrieves a block header by its number.
     *
     * @param blockNumber the block number
     * @return the block header, or {@code null} if not found
     * @since 0.1.0
     */
    @Nullable BlockHeader getBlockByNumber(long blockNumber);

    /**
     * Retrieves a transaction by its hash.
     *
     * @param hash the transaction hash
     * @return the transaction, or {@code null} if not found
     * @since 0.1.0
     */
    @Nullable Transaction getTransactionByHash(Hash hash);

    /**
     * Retrieves a transaction receipt by the transaction hash.
     *
     * @param hash the transaction hash
     * @return the transaction receipt, or {@code null} if not found or pending
     * @since 0.1.0
     */
    @Nullable TransactionReceipt getTransactionReceipt(Hash hash);

    /**
     * Executes a read-only call on the blockchain at the latest block.
     *
     * <p>This is a convenience method equivalent to calling
     * {@code call(request, BlockTag.LATEST)}.
     *
     * @param request the type-safe call request
     * @return the raw hex return value as HexData
     * @since 0.1.0
     */
    HexData call(CallRequest request);

    /**
     * Executes a read-only call on the blockchain at the specified block.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * CallRequest request = CallRequest.builder()
     *     .to(contractAddress)
     *     .data(encodedFunctionCall)
     *     .build();
     * HexData result = client.call(request, BlockTag.LATEST);
     * }</pre>
     *
     * @param request  the type-safe call request
     * @param blockTag the block tag (e.g., BlockTag.LATEST, BlockTag.PENDING)
     * @return the raw hex return value as HexData
     * @since 0.1.0
     */
    HexData call(CallRequest request, BlockTag blockTag);

    /**
     * Retrieves logs matching the given filter.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * LogFilter filter = LogFilter.byContract(contractAddress, List.of(transferEventTopic));
     * List<LogEntry> logs = client.getLogs(filter);
     * for (LogEntry log : logs) {
     *     System.out.println("Log from block " + log.blockNumber());
     * }
     * }</pre>
     *
     * @param filter the log filter criteria
     * @return a list of matching logs
     * @since 0.1.0
     */
    List<LogEntry> getLogs(LogFilter filter);

    /**
     * Estimates the gas required to execute a transaction.
     *
     * <p>The estimate may be higher than the actual gas used due to EVM execution
     * variance and state changes between estimation and actual execution.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * TransactionRequest request = TransactionRequest.builder()
     *     .from(senderAddress)
     *     .to(contractAddress)
     *     .data(encodedFunctionCall)
     *     .build();
     * BigInteger gasEstimate = client.estimateGas(request);
     * }</pre>
     *
     * @param request the transaction request to estimate
     * @return the estimated gas in Wei
     * @since 0.1.0
     */
    BigInteger estimateGas(TransactionRequest request);

    /**
     * Creates an access list for a transaction.
     *
     * <p>An access list specifies the addresses and storage keys a transaction will access,
     * allowing for gas savings on EIP-2930/EIP-1559 transactions.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * TransactionRequest request = TransactionRequest.builder()
     *     .from(senderAddress)
     *     .to(contractAddress)
     *     .data(encodedFunctionCall)
     *     .build();
     * AccessListWithGas result = client.createAccessList(request);
     * System.out.println("Gas estimate: " + result.gasUsed());
     * }</pre>
     *
     * @param request the transaction request to analyze
     * @return the access list and estimated gas
     * @since 0.1.0
     */
    AccessListWithGas createAccessList(TransactionRequest request);

    /**
     * Simulates a batch of transactions using {@code eth_simulateV1}.
     *
     * <p>This allows simulating multiple transactions in sequence with state overrides,
     * making it useful for gas estimation, dry-run validation, and debugging.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * SimulateRequest request = SimulateRequest.builder()
     *     .account(senderAddress)
     *     .call(SimulateCall.builder()
     *         .to(contractAddress)
     *         .data(encodedFunctionCall)
     *         .build())
     *     .traceAssetChanges(true)
     *     .build();
     * SimulateResult result = client.simulate(request);
     * for (CallResult callResult : result.results()) {
     *     System.out.println("Success: " + callResult.success());
     * }
     * }</pre>
     *
     * @param request the simulation request
     * @return the simulation result containing per-call results and optional asset changes
     * @since 0.1.0
     */
    SimulateResult simulate(SimulateRequest request);

    /**
     * Creates a new batch for executing multiple calls in a single RPC request.
     *
     * <p>The returned {@link MulticallBatch} collects multiple contract calls and executes
     * them together using the Multicall3 contract, reducing network overhead.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * MulticallBatch batch = client.batch();
     * ERC20 token = batch.bind(ERC20.class, tokenAddress, ERC20_ABI);
     * BatchHandle<BigInteger> balance1 = batch.add(token.balanceOf(addr1));
     * BatchHandle<BigInteger> balance2 = batch.add(token.balanceOf(addr2));
     * batch.execute();
     * System.out.println("Balance 1: " + balance1.get().data());
     * System.out.println("Balance 2: " + balance2.get().data());
     * }</pre>
     *
     * @return a new batch instance
     * @since 0.1.0
     */
    MulticallBatch batch();

    /**
     * Subscribes to new block headers.
     *
     * <p>This method establishes a WebSocket subscription to receive real-time notifications
     * whenever a new block is added to the chain. The provided callback is invoked with each
     * new {@link BlockHeader} as it becomes available.
     *
     * <p><strong>Requirements:</strong> This method requires a WebSocket-capable provider.
     * If called on an HTTP-only client, an {@link UnsupportedOperationException} will be thrown.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * Subscription sub = client.onNewHeads(header -> {
     *     System.out.println("New block: " + header.number());
     * });
     * // Later, when done listening:
     * sub.unsubscribe();
     * }</pre>
     *
     * @param callback the consumer to receive new block headers
     * @return a subscription handle that can be used to unsubscribe
     * @throws UnsupportedOperationException if the underlying provider does not support subscriptions
     * @since 0.1.0
     */
    Subscription onNewHeads(Consumer<BlockHeader> callback);

    /**
     * Subscribes to log events matching the given filter.
     *
     * <p>This method establishes a WebSocket subscription to receive real-time notifications
     * for log events that match the specified filter criteria. The provided callback is invoked
     * with each matching {@link LogEntry} as it is emitted.
     *
     * <p><strong>Requirements:</strong> This method requires a WebSocket-capable provider.
     * If called on an HTTP-only client, an {@link UnsupportedOperationException} will be thrown.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * LogFilter filter = LogFilter.byContract(contractAddress, List.of(transferEventTopic));
     * Subscription sub = client.onLogs(filter, log -> {
     *     System.out.println("Log from block " + log.blockNumber() + ": " + log.data());
     * });
     * // Later, when done listening:
     * sub.unsubscribe();
     * }</pre>
     *
     * @param filter   the log filter criteria
     * @param callback the consumer to receive matching log entries
     * @return a subscription handle that can be used to unsubscribe
     * @throws UnsupportedOperationException if the underlying provider does not support subscriptions
     * @since 0.1.0
     */
    Subscription onLogs(LogFilter filter, Consumer<LogEntry> callback);

    // ==================== Metadata Methods ====================

    /**
     * Returns the chain profile associated with this client, if configured.
     *
     * <p>The chain profile provides network-specific configuration such as whether
     * EIP-1559 transactions are supported and default gas settings.
     *
     * @return an optional containing the chain profile, or empty if not configured
     * @since 0.1.0
     */
    Optional<ChainProfile> chain();

    /**
     * Returns whether this client has transaction signing capability.
     *
     * <p>This is a convenience method for type checking. Clients implementing
     * {@link Signer} return {@code true}; clients implementing only {@link Reader}
     * return {@code false}.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * if (client.canSign()) {
     *     Brane.Signer signer = (Brane.Signer) client;
     *     signer.sendTransaction(request);
     * }
     * }</pre>
     *
     * @return {@code true} if this client can sign transactions, {@code false} otherwise
     * @since 0.1.0
     */
    default boolean canSign() {
        return this instanceof Signer;
    }

    /**
     * Returns whether this client supports real-time subscriptions.
     *
     * <p>Subscription support requires a WebSocket-capable provider. HTTP-only
     * providers do not support subscriptions.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * if (client.canSubscribe()) {
     *     client.onNewHeads(header -> System.out.println("New block: " + header.number()));
     * }
     * }</pre>
     *
     * @return {@code true} if subscriptions are supported, {@code false} otherwise
     * @since 0.1.0
     */
    boolean canSubscribe();

    /**
     * Read-only client for blockchain queries.
     *
     * <p>Provides access to all read operations without transaction signing capability.
     *
     * @since 0.1.0
     */
    sealed interface Reader extends Brane permits DefaultReader {
    }

    /**
     * Full-featured client with transaction signing capability.
     *
     * <p>Extends all read operations with the ability to sign and send transactions.
     *
     * @since 0.1.0
     */
    sealed interface Signer extends Brane permits DefaultSigner {

        /**
         * Submits a transaction to the blockchain and returns immediately.
         *
         * <p>This method:
         * <ol>
         *   <li>Fills in missing gas/nonce fields if null in the request</li>
         *   <li>Signs the transaction using the configured signer</li>
         *   <li>Broadcasts via {@code eth_sendRawTransaction}</li>
         *   <li>Returns the transaction hash without waiting for confirmation</li>
         * </ol>
         *
         * <p><strong>Note:</strong> The transaction is submitted but NOT confirmed.
         * Use {@link #sendTransactionAndWait} if you need to wait for confirmation.
         *
         * @param request the transaction request with at minimum a {@code from} address;
         *                all other fields are optional and will be auto-filled
         * @return the transaction hash of the submitted transaction
         * @since 0.1.0
         */
        Hash sendTransaction(TransactionRequest request);

        /**
         * Submits a transaction and waits for it to be confirmed in a block.
         *
         * <p>This method combines {@link #sendTransaction} with polling for the receipt.
         * It will:
         * <ol>
         *   <li>Submit the transaction (same as {@link #sendTransaction})</li>
         *   <li>Poll {@code eth_getTransactionReceipt} at the specified interval</li>
         *   <li>Return the receipt once the transaction is included in a block</li>
         *   <li>Throw an exception if timeout is reached or transaction reverts</li>
         * </ol>
         *
         * @param request            the transaction request
         * @param timeoutMillis      maximum time to wait for confirmation, in milliseconds
         * @param pollIntervalMillis how often to poll for the receipt, in milliseconds
         * @return the transaction receipt once confirmed
         * @since 0.1.0
         */
        TransactionReceipt sendTransactionAndWait(
                TransactionRequest request, long timeoutMillis, long pollIntervalMillis);

        /**
         * Returns the signer instance used by this client.
         *
         * @return the signer
         * @since 0.1.0
         */
        io.brane.core.crypto.Signer signer();
    }

    /**
     * Builder for creating {@link Brane} client instances.
     *
     * <p>The builder supports multiple configuration options:
     * <ul>
     *   <li>{@link #rpcUrl(String)} - HTTP/HTTPS RPC endpoint (required if no provider)</li>
     *   <li>{@link #wsUrl(String)} - WebSocket endpoint for subscriptions (optional)</li>
     *   <li>{@link #provider(BraneProvider)} - Custom provider (alternative to rpcUrl)</li>
     * </ul>
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * // Simple HTTP client
     * Brane client = Brane.builder()
     *     .rpcUrl("https://eth.example.com")
     *     .build();
     *
     * // With custom provider
     * Brane client = Brane.builder()
     *     .provider(myProvider)
     *     .build();
     * }</pre>
     *
     * @since 0.1.0
     */
    final class Builder {

        private @Nullable String rpcUrl;
        private @Nullable String wsUrl;
        private @Nullable BraneProvider provider;

        /**
         * Creates a new builder instance.
         */
        Builder() {
        }

        /**
         * Sets the HTTP/HTTPS RPC endpoint URL.
         *
         * <p>This is mutually exclusive with {@link #provider(BraneProvider)}.
         * If both are set, the explicit provider takes precedence.
         *
         * @param rpcUrl the RPC endpoint URL (e.g., "https://eth.example.com")
         * @return this builder for chaining
         * @since 0.1.0
         */
        public Builder rpcUrl(String rpcUrl) {
            this.rpcUrl = rpcUrl;
            return this;
        }

        /**
         * Sets the WebSocket endpoint URL for subscriptions.
         *
         * <p>If provided, the client will use this endpoint for subscription methods
         * like {@link Brane#onNewHeads} and {@link Brane#onLogs}.
         *
         * @param wsUrl the WebSocket endpoint URL (e.g., "wss://eth.example.com/ws")
         * @return this builder for chaining
         * @since 0.1.0
         */
        public Builder wsUrl(String wsUrl) {
            this.wsUrl = wsUrl;
            return this;
        }

        /**
         * Sets a custom provider for RPC communication.
         *
         * <p>This is mutually exclusive with {@link #rpcUrl(String)}.
         * If both are set, the explicit provider takes precedence.
         *
         * @param provider the custom provider instance
         * @return this builder for chaining
         * @since 0.1.0
         */
        public Builder provider(BraneProvider provider) {
            this.provider = provider;
            return this;
        }
    }
}
