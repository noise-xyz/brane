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

    // ==================== Static Factory Methods ====================

    /**
     * Creates a read-only client connected to the specified RPC endpoint.
     *
     * <p>This is the simplest way to create a Brane client for read-only operations.
     * The returned client supports all query operations but cannot send transactions.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * Brane client = Brane.connect("https://eth-mainnet.g.alchemy.com/v2/...");
     * BigInteger balance = client.getBalance(address);
     * }</pre>
     *
     * @param rpcUrl the HTTP/HTTPS RPC endpoint URL
     * @return a new read-only {@link Reader} client
     * @since 0.1.0
     */
    static Reader connect(String rpcUrl) {
        return builder().rpcUrl(rpcUrl).buildReader();
    }

    /**
     * Creates a signing-capable client connected to the specified RPC endpoint.
     *
     * <p>The returned client supports all read operations plus transaction signing
     * and sending capabilities.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * Signer signer = PrivateKey.fromHex("0x...");
     * Brane.Signer client = Brane.connect("https://eth-mainnet.g.alchemy.com/v2/...", signer);
     * Hash txHash = client.sendTransaction(request);
     * }</pre>
     *
     * @param rpcUrl the HTTP/HTTPS RPC endpoint URL
     * @param signer the signer for transaction signing
     * @return a new signing-capable {@link Signer} client
     * @since 0.1.0
     */
    static Signer connect(String rpcUrl, io.brane.core.crypto.Signer signer) {
        return builder().rpcUrl(rpcUrl).signer(signer).buildSigner();
    }

    /**
     * Creates a new builder for configuring a Brane client.
     *
     * <p>Use the builder for advanced configuration such as custom providers,
     * WebSocket endpoints, chain profiles, and retry settings.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * Brane client = Brane.builder()
     *     .rpcUrl("https://eth-mainnet.g.alchemy.com/v2/...")
     *     .chain(ChainProfiles.MAINNET)
     *     .retries(5)
     *     .build();
     * }</pre>
     *
     * @return a new builder instance
     * @since 0.1.0
     */
    static Builder builder() {
        return new Builder();
    }

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
        private io.brane.core.crypto.@Nullable Signer signer;
        private @Nullable ChainProfile chain;
        private int retries = 3;
        private @Nullable RpcRetryConfig retryConfig;

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

        /**
         * Sets the signer for transaction signing.
         *
         * <p>If a signer is provided, {@link #build()} will return a {@link Brane.Signer}
         * instance capable of sending transactions. Without a signer, {@link #build()}
         * returns a {@link Brane.Reader} instance limited to read-only operations.
         *
         * @param signer the signer instance for transaction signing
         * @return this builder for chaining
         * @since 0.1.0
         */
        public Builder signer(io.brane.core.crypto.Signer signer) {
            this.signer = signer;
            return this;
        }

        /**
         * Sets the chain profile for network-specific configuration.
         *
         * <p>The chain profile provides network-specific settings such as whether
         * EIP-1559 transactions are supported. If not set, the client will operate
         * without network-specific optimizations.
         *
         * @param chain the chain profile
         * @return this builder for chaining
         * @since 0.1.0
         * @see io.brane.core.chain.ChainProfiles
         */
        public Builder chain(ChainProfile chain) {
            this.chain = chain;
            return this;
        }

        /**
         * Sets the maximum number of retry attempts for transient RPC failures.
         *
         * <p>When an RPC call fails with a transient error (e.g., network timeout,
         * rate limiting), the client will retry up to this many times before
         * throwing an exception.
         *
         * <p>Default is 3 retries.
         *
         * @param retries the maximum number of retries (must be &gt;= 0)
         * @return this builder for chaining
         * @throws IllegalArgumentException if retries is negative
         * @since 0.1.0
         */
        public Builder retries(int retries) {
            if (retries < 0) {
                throw new IllegalArgumentException("retries must be >= 0, got: " + retries);
            }
            this.retries = retries;
            return this;
        }

        /**
         * Sets the retry configuration for backoff timing.
         *
         * <p>This configuration controls the exponential backoff behavior when
         * retrying failed RPC calls, including base delay, maximum delay, and
         * jitter parameters.
         *
         * <p>If not set, {@link RpcRetryConfig#defaults()} is used.
         *
         * @param retryConfig the retry configuration
         * @return this builder for chaining
         * @since 0.1.0
         * @see RpcRetryConfig
         */
        public Builder retryConfig(RpcRetryConfig retryConfig) {
            this.retryConfig = retryConfig;
            return this;
        }

        /**
         * Builds a {@link Brane} client based on the configured options.
         *
         * <p>This method returns:
         * <ul>
         *   <li>A {@link Brane.Signer} if a signer was configured via {@link #signer(Signer)}</li>
         *   <li>A {@link Brane.Reader} if no signer was configured</li>
         * </ul>
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * // Returns Reader (no signer)
         * Brane reader = Brane.builder()
         *     .rpcUrl("https://eth.example.com")
         *     .build();
         *
         * // Returns Signer (with signer)
         * Brane signer = Brane.builder()
         *     .rpcUrl("https://eth.example.com")
         *     .signer(mySigner)
         *     .build();
         * }</pre>
         *
         * @return a new {@link Brane} client instance
         * @throws IllegalStateException if neither rpcUrl nor provider is configured
         * @since 0.1.0
         */
        public Brane build() {
            if (signer != null) {
                return buildSigner();
            }
            return buildReader();
        }

        /**
         * Builds a read-only {@link Brane.Reader} client.
         *
         * <p>Use this method when you only need read operations and want to ensure
         * the returned type is {@link Reader} at compile time.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * Brane.Reader reader = Brane.builder()
         *     .rpcUrl("https://eth.example.com")
         *     .chain(ChainProfiles.MAINNET)
         *     .buildReader();
         * }</pre>
         *
         * @return a new read-only {@link Brane.Reader} instance
         * @throws IllegalStateException if neither rpcUrl nor provider is configured
         * @since 0.1.0
         */
        public Reader buildReader() {
            validateProviderConfig();
            BraneProvider resolvedProvider = provider != null ? provider : BraneProvider.http(rpcUrl);
            RpcRetryConfig resolvedRetryConfig = retryConfig != null ? retryConfig : RpcRetryConfig.defaults();
            return new DefaultReader(resolvedProvider, chain, retries, resolvedRetryConfig);
        }

        /**
         * Builds a signing-capable {@link Brane.Signer} client.
         *
         * <p>Use this method when you need transaction signing capability and want
         * to ensure the returned type is {@link Signer} at compile time.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * Brane.Signer signer = Brane.builder()
         *     .rpcUrl("https://eth.example.com")
         *     .signer(PrivateKey.fromHex("0x..."))
         *     .chain(ChainProfiles.MAINNET)
         *     .buildSigner();
         * }</pre>
         *
         * @return a new signing-capable {@link Brane.Signer} instance
         * @throws IllegalStateException if neither rpcUrl nor provider is configured
         * @throws IllegalStateException if no signer is configured
         * @since 0.1.0
         */
        public Signer buildSigner() {
            validateProviderConfig();
            if (signer == null) {
                throw new IllegalStateException(
                        "Cannot build Signer without a signer. Call signer() before buildSigner().");
            }
            return new DefaultSigner();
        }

        private void validateProviderConfig() {
            if (provider == null && rpcUrl == null) {
                throw new IllegalStateException(
                        "Either rpcUrl or provider must be configured. Call rpcUrl() or provider().");
            }
        }
    }
}
