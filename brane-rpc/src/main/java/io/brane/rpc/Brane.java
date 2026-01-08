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
 * <p>{@code Brane} is the primary interface for all blockchain interactions in the Brane SDK.
 * It provides a modern, type-safe API for querying blockchain state, executing read-only calls,
 * subscribing to real-time events, and sending transactions.
 *
 * <h2>Client Hierarchy</h2>
 *
 * <p>This sealed interface provides a type-safe hierarchy for blockchain clients:
 * <ul>
 *   <li>{@link Reader} - Read-only operations (queries, calls, subscriptions)</li>
 *   <li>{@link Signer} - Full operations including transaction signing and sending</li>
 * </ul>
 *
 * <p>The sealed hierarchy enables exhaustive pattern matching and compile-time type safety
 * when working with different client capabilities.
 *
 * <h2>Creating Clients</h2>
 *
 * <p><strong>Simple read-only client:</strong>
 * <pre>{@code
 * // Connect to any Ethereum RPC endpoint
 * Brane client = Brane.connect("https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY");
 *
 * // Query blockchain state
 * BigInteger balance = client.getBalance(address);
 * BlockHeader block = client.getLatestBlock();
 * }</pre>
 *
 * <p><strong>Client with transaction signing:</strong>
 * <pre>{@code
 * // Create a signer from a private key
 * Signer key = PrivateKey.fromHex("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
 *
 * // Connect with signing capability
 * Brane.Signer client = Brane.connect("https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY", key);
 *
 * // Send transactions
 * TransactionRequest request = TransactionRequest.builder()
 *     .to(recipient)
 *     .value(Wei.fromEther("1.5"))
 *     .build();
 * Hash txHash = client.sendTransaction(request);
 * }</pre>
 *
 * <p><strong>Advanced configuration with builder:</strong>
 * <pre>{@code
 * Brane client = Brane.builder()
 *     .rpcUrl("https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
 *     .chain(ChainProfiles.MAINNET)  // Network-specific optimizations
 *     .retries(5)                     // Retry transient failures
 *     .build();
 * }</pre>
 *
 * <h2>Pattern Matching</h2>
 *
 * <p>The sealed hierarchy enables exhaustive pattern matching in Java 21+:
 * <pre>{@code
 * Brane client = getClientFromSomewhere();
 * switch (client) {
 *     case Brane.Reader r -> {
 *         // Read-only operations only
 *         System.out.println("Balance: " + r.getBalance(address));
 *     }
 *     case Brane.Signer s -> {
 *         // Full operations including transactions
 *         Hash hash = s.sendTransaction(request);
 *     }
 * }
 * }</pre>
 *
 * <p>Alternatively, use the {@link #canSign()} helper:
 * <pre>{@code
 * if (client.canSign()) {
 *     Brane.Signer signer = (Brane.Signer) client;
 *     signer.sendTransaction(request);
 * }
 * }</pre>
 *
 * <h2>Common Operations</h2>
 *
 * <p><strong>Reading blockchain state:</strong>
 * <pre>{@code
 * // Account balance
 * BigInteger balance = client.getBalance(address);
 *
 * // Block information
 * BlockHeader latest = client.getLatestBlock();
 * BlockHeader specific = client.getBlockByNumber(12345678);
 *
 * // Transaction details
 * Transaction tx = client.getTransactionByHash(txHash);
 * TransactionReceipt receipt = client.getTransactionReceipt(txHash);
 * }</pre>
 *
 * <p><strong>Executing contract calls:</strong>
 * <pre>{@code
 * // Build a call request
 * CallRequest request = CallRequest.builder()
 *     .to(contractAddress)
 *     .data(encodedFunctionCall)
 *     .build();
 *
 * // Execute at latest block
 * HexData result = client.call(request);
 *
 * // Or at a specific block
 * HexData historicalResult = client.call(request, BlockTag.number(12345678));
 * }</pre>
 *
 * <p><strong>Querying event logs:</strong>
 * <pre>{@code
 * LogFilter filter = LogFilter.byContract(contractAddress, List.of(transferEventTopic))
 *     .fromBlock(BlockTag.number(startBlock))
 *     .toBlock(BlockTag.LATEST);
 *
 * List<LogEntry> logs = client.getLogs(filter);
 * for (LogEntry log : logs) {
 *     System.out.println("Transfer at block " + log.blockNumber());
 * }
 * }</pre>
 *
 * <p><strong>Batching multiple calls:</strong>
 * <pre>{@code
 * MulticallBatch batch = client.batch();
 * ERC20 token = batch.bind(ERC20.class, tokenAddress, ERC20_ABI);
 *
 * BatchHandle<BigInteger> balance1 = batch.add(token.balanceOf(addr1));
 * BatchHandle<BigInteger> balance2 = batch.add(token.balanceOf(addr2));
 * BatchHandle<BigInteger> balance3 = batch.add(token.balanceOf(addr3));
 *
 * batch.execute();  // Single RPC call via Multicall3
 *
 * System.out.println("Balance 1: " + balance1.get().data());
 * System.out.println("Balance 2: " + balance2.get().data());
 * }</pre>
 *
 * <h2>Real-time Subscriptions</h2>
 *
 * <p>WebSocket-connected clients support real-time event subscriptions:
 * <pre>{@code
 * if (client.canSubscribe()) {
 *     // Subscribe to new blocks
 *     Subscription blockSub = client.onNewHeads(header -> {
 *         System.out.println("New block: " + header.number());
 *     });
 *
 *     // Subscribe to contract events
 *     LogFilter filter = LogFilter.byContract(contractAddress, List.of(eventTopic));
 *     Subscription logSub = client.onLogs(filter, log -> {
 *         System.out.println("Event: " + log.data());
 *     });
 *
 *     // Later, clean up
 *     blockSub.unsubscribe();
 *     logSub.unsubscribe();
 * }
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 *
 * <p>All {@code Brane} implementations are thread-safe. Multiple threads can safely
 * share a single client instance for concurrent operations. The underlying HTTP and
 * WebSocket providers handle connection pooling and request multiplexing internally.
 *
 * <h2>Resource Management</h2>
 *
 * <p>{@code Brane} extends {@link AutoCloseable} for proper resource cleanup:
 * <pre>{@code
 * try (Brane client = Brane.connect("https://eth.example.com")) {
 *     BigInteger balance = client.getBalance(address);
 *     // ... use client
 * }  // Automatically closes connections
 * }</pre>
 *
 * <p>Calling methods on a closed client throws {@link IllegalStateException}.
 *
 * <h2>Error Handling</h2>
 *
 * <p>All methods may throw:
 * <ul>
 *   <li>{@link io.brane.core.error.RpcException} - JSON-RPC communication failures</li>
 *   <li>{@link io.brane.core.error.RevertException} - EVM execution reverts (for calls)</li>
 *   <li>{@link IllegalStateException} - If the client has been closed</li>
 * </ul>
 *
 * <p>Transaction methods ({@link Signer#sendTransaction}, {@link Signer#sendTransactionAndWait})
 * may additionally throw:
 * <ul>
 *   <li>{@link io.brane.core.error.TxnException} - Transaction-specific failures</li>
 * </ul>
 *
 * @see Brane.Reader
 * @see Brane.Signer
 * @see Brane.Builder
 * @see BraneProvider
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
     * <p>{@code Reader} provides access to all read operations without transaction signing
     * capability. This is the appropriate client type when you only need to query blockchain
     * state, execute read-only contract calls, or subscribe to events.
     *
     * <h2>Read-Only Guarantee</h2>
     *
     * <p>A {@code Reader} instance guarantees that no state-modifying transactions can be
     * sent. This makes it safe to use in contexts where you want to prevent accidental
     * writes, such as:
     * <ul>
     *   <li>Analytics and monitoring applications</li>
     *   <li>Block explorers and indexers</li>
     *   <li>Read-only API endpoints</li>
     *   <li>Testing and debugging tools</li>
     * </ul>
     *
     * <h2>Creating a Reader</h2>
     *
     * <pre>{@code
     * // Via static factory (simplest)
     * Brane.Reader reader = Brane.connect("https://eth.example.com");
     *
     * // Via builder (with configuration)
     * Brane.Reader reader = Brane.builder()
     *     .rpcUrl("https://eth.example.com")
     *     .chain(ChainProfiles.MAINNET)
     *     .retries(5)
     *     .buildReader();
     * }</pre>
     *
     * <h2>Available Operations</h2>
     *
     * <p>All methods inherited from {@link Brane} are available:
     * <ul>
     *   <li>Balance queries: {@link #getBalance(Address)}</li>
     *   <li>Block queries: {@link #getLatestBlock()}, {@link #getBlockByNumber(long)}</li>
     *   <li>Transaction queries: {@link #getTransactionByHash(Hash)}, {@link #getTransactionReceipt(Hash)}</li>
     *   <li>Contract calls: {@link #call(CallRequest)}, {@link #call(CallRequest, BlockTag)}</li>
     *   <li>Log queries: {@link #getLogs(LogFilter)}</li>
     *   <li>Gas estimation: {@link #estimateGas(TransactionRequest)}</li>
     *   <li>Access list creation: {@link #createAccessList(TransactionRequest)}</li>
     *   <li>Simulation: {@link #simulate(SimulateRequest)}</li>
     *   <li>Batching: {@link #batch()}</li>
     *   <li>Subscriptions: {@link #onNewHeads(Consumer)}, {@link #onLogs(LogFilter, Consumer)}</li>
     * </ul>
     *
     * @see Brane
     * @see Brane.Signer
     * @since 0.1.0
     */
    sealed interface Reader extends Brane permits DefaultReader {
    }

    /**
     * Full-featured client with transaction signing capability.
     *
     * <p>{@code Signer} extends all read operations with the ability to sign and send
     * transactions to the blockchain. This is the client type to use when your application
     * needs to modify blockchain state.
     *
     * <h2>Signing Capability</h2>
     *
     * <p>A {@code Signer} client wraps a cryptographic signer (typically a
     * {@link io.brane.core.crypto.PrivateKey}) that can sign transactions. The signing
     * happens locally - private keys never leave your application.
     *
     * <h2>Creating a Signer</h2>
     *
     * <pre>{@code
     * // Create the cryptographic signer
     * Signer key = PrivateKey.fromHex("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
     *
     * // Via static factory (simplest)
     * Brane.Signer client = Brane.connect("https://eth.example.com", key);
     *
     * // Via builder (with configuration)
     * Brane.Signer client = Brane.builder()
     *     .rpcUrl("https://eth.example.com")
     *     .signer(key)
     *     .chain(ChainProfiles.MAINNET)
     *     .retries(5)
     *     .buildSigner();
     * }</pre>
     *
     * <h2>Sending Transactions</h2>
     *
     * <p><strong>Fire and forget:</strong>
     * <pre>{@code
     * TransactionRequest request = TransactionRequest.builder()
     *     .to(recipient)
     *     .value(Wei.fromEther("1.0"))
     *     .build();
     *
     * // Returns immediately after broadcast
     * Hash txHash = client.sendTransaction(request);
     * System.out.println("Transaction submitted: " + txHash);
     * }</pre>
     *
     * <p><strong>Wait for confirmation:</strong>
     * <pre>{@code
     * // Wait up to 60 seconds for confirmation (default)
     * TransactionReceipt receipt = client.sendTransactionAndWait(request);
     *
     * // Or with custom timeout
     * TransactionReceipt receipt = client.sendTransactionAndWait(request, 120_000, 2_000);
     *
     * if (receipt.status() == 1) {
     *     System.out.println("Transaction succeeded in block " + receipt.blockNumber());
     * }
     * }</pre>
     *
     * <h2>Transaction Auto-Fill</h2>
     *
     * <p>When sending transactions, the client automatically fills in missing fields:
     * <ul>
     *   <li><strong>nonce</strong>: Fetched from {@code eth_getTransactionCount}</li>
     *   <li><strong>gasLimit</strong>: Estimated via {@code eth_estimateGas}</li>
     *   <li><strong>maxFeePerGas/maxPriorityFeePerGas</strong>: Calculated from current base fee</li>
     *   <li><strong>chainId</strong>: Fetched from {@code eth_chainId}</li>
     * </ul>
     *
     * <p>You can override any of these by setting them explicitly in the request.
     *
     * <h2>Contract Interactions</h2>
     *
     * <pre>{@code
     * // Encode a contract call
     * HexData data = Abi.encodeFunctionCall("transfer(address,uint256)",
     *     recipient, Wei.fromEther("100").toBigInteger());
     *
     * TransactionRequest request = TransactionRequest.builder()
     *     .to(tokenAddress)
     *     .data(data)
     *     .build();
     *
     * TransactionReceipt receipt = client.sendTransactionAndWait(request);
     * }</pre>
     *
     * @see Brane
     * @see Brane.Reader
     * @see io.brane.core.crypto.Signer
     * @see io.brane.core.crypto.PrivateKey
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

        /** Default timeout for waiting for transaction confirmation: 60 seconds. */
        long DEFAULT_TIMEOUT_MILLIS = 60_000;

        /** Default poll interval for checking transaction receipt: 1 second. */
        long DEFAULT_POLL_INTERVAL_MILLIS = 1_000;

        /**
         * Submits a transaction and waits for it to be confirmed in a block using default settings.
         *
         * <p>This is a convenience method that uses default timeout (60 seconds) and poll
         * interval (1 second). For custom settings, use
         * {@link #sendTransactionAndWait(TransactionRequest, long, long)}.
         *
         * <p>This method combines {@link #sendTransaction} with polling for the receipt.
         * It will:
         * <ol>
         *   <li>Submit the transaction (same as {@link #sendTransaction})</li>
         *   <li>Poll {@code eth_getTransactionReceipt} every second</li>
         *   <li>Return the receipt once the transaction is included in a block</li>
         *   <li>Throw an exception if 60 seconds is exceeded or transaction reverts</li>
         * </ol>
         *
         * @param request the transaction request
         * @return the transaction receipt once confirmed
         * @since 0.1.0
         */
        default TransactionReceipt sendTransactionAndWait(TransactionRequest request) {
            return sendTransactionAndWait(request, DEFAULT_TIMEOUT_MILLIS, DEFAULT_POLL_INTERVAL_MILLIS);
        }

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
     * Testing interface for interacting with test nodes (Anvil, Hardhat, Ganache).
     * <p>
     * This interface provides methods for test-specific operations like snapshots,
     * time manipulation, and state management that are only available on test networks.
     * <p>
     * <strong>Example:</strong>
     * <pre>{@code
     * // Obtain tester capabilities from a client
     * Brane.Tester tester = client.tester(TestNodeMode.ANVIL);
     *
     * // Create a snapshot
     * SnapshotId snapshot = tester.snapshot();
     *
     * // ... perform operations ...
     *
     * // Revert to snapshot
     * snapshot.revertUsing(tester);
     * // or equivalently:
     * tester.revert(snapshot);
     * }</pre>
     *
     * @since 0.1.0-alpha
     */
    interface Tester {

        /**
         * Creates a snapshot of the current blockchain state.
         * <p>
         * The returned {@link SnapshotId} can be used to revert the chain state
         * back to this point using {@link #revert(SnapshotId)}.
         *
         * @return the snapshot ID
         */
        SnapshotId snapshot();

        /**
         * Reverts the blockchain state to a previously taken snapshot.
         *
         * @param snapshotId the snapshot to revert to
         * @return true if the revert succeeded, false otherwise
         */
        boolean revert(SnapshotId snapshotId);
    }

    /**
     * Builder for creating {@link Brane} client instances.
     *
     * <p>The builder provides fine-grained control over client configuration, including
     * transport selection, retry behavior, and chain-specific settings.
     *
     * <h2>Configuration Options</h2>
     *
     * <table border="1">
     *   <caption>Builder Configuration Options</caption>
     *   <tr><th>Method</th><th>Description</th><th>Required</th></tr>
     *   <tr><td>{@link #rpcUrl(String)}</td><td>HTTP/HTTPS RPC endpoint URL</td><td>Yes (unless provider set)</td></tr>
     *   <tr><td>{@link #wsUrl(String)}</td><td>WebSocket endpoint for subscriptions</td><td>No</td></tr>
     *   <tr><td>{@link #provider(BraneProvider)}</td><td>Custom provider instance</td><td>Alternative to rpcUrl</td></tr>
     *   <tr><td>{@link #signer(io.brane.core.crypto.Signer)}</td><td>Transaction signer</td><td>Yes for Signer client</td></tr>
     *   <tr><td>{@link #chain(ChainProfile)}</td><td>Chain-specific configuration</td><td>No</td></tr>
     *   <tr><td>{@link #retries(int)}</td><td>Max retry attempts</td><td>No (default: 3)</td></tr>
     *   <tr><td>{@link #retryConfig(RpcRetryConfig)}</td><td>Retry backoff settings</td><td>No</td></tr>
     * </table>
     *
     * <h2>Terminal Methods</h2>
     *
     * <ul>
     *   <li>{@link #build()} - Returns {@link Signer} if signer configured, otherwise {@link Reader}</li>
     *   <li>{@link #buildReader()} - Explicitly builds a read-only {@link Reader}</li>
     *   <li>{@link #buildSigner()} - Explicitly builds a signing {@link Signer} (requires signer)</li>
     * </ul>
     *
     * <h2>Usage Examples</h2>
     *
     * <p><strong>Simple HTTP client:</strong>
     * <pre>{@code
     * Brane client = Brane.builder()
     *     .rpcUrl("https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
     *     .build();
     * }</pre>
     *
     * <p><strong>With chain profile and retries:</strong>
     * <pre>{@code
     * Brane client = Brane.builder()
     *     .rpcUrl("https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
     *     .chain(ChainProfiles.MAINNET)
     *     .retries(5)
     *     .build();
     * }</pre>
     *
     * <p><strong>With transaction signing:</strong>
     * <pre>{@code
     * Signer key = PrivateKey.fromHex("0x...");
     * Brane.Signer client = Brane.builder()
     *     .rpcUrl("https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
     *     .signer(key)
     *     .chain(ChainProfiles.MAINNET)
     *     .buildSigner();
     * }</pre>
     *
     * <p><strong>With custom provider:</strong>
     * <pre>{@code
     * BraneProvider customProvider = new MyCustomProvider();
     * Brane client = Brane.builder()
     *     .provider(customProvider)
     *     .build();
     * }</pre>
     *
     * <p><strong>With custom retry configuration:</strong>
     * <pre>{@code
     * RpcRetryConfig retryConfig = RpcRetryConfig.builder()
     *     .baseDelayMs(100)
     *     .maxDelayMs(5000)
     *     .jitterFactor(0.1)
     *     .build();
     *
     * Brane client = Brane.builder()
     *     .rpcUrl("https://eth.example.com")
     *     .retries(10)
     *     .retryConfig(retryConfig)
     *     .build();
     * }</pre>
     *
     * @see Brane#builder()
     * @see Brane.Reader
     * @see Brane.Signer
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
         * Sets the WebSocket endpoint URL for the client.
         *
         * <p>When set, the client uses a WebSocket transport instead of HTTP. This enables
         * real-time subscription methods like {@link Brane#onNewHeads} and {@link Brane#onLogs}.
         *
         * <p>Provider resolution priority: explicit {@link #provider(BraneProvider)} &gt;
         * {@code wsUrl} &gt; {@link #rpcUrl(String)}.
         *
         * <p><strong>Example - WebSocket client:</strong>
         * <pre>{@code
         * // Create a WebSocket-based client with subscription support
         * Brane client = Brane.builder()
         *     .wsUrl("wss://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
         *     .build();
         *
         * // Subscribe to new blocks (only works with WebSocket)
         * client.onNewHeads(header -> {
         *     System.out.println("New block: " + header.number());
         * });
         * }</pre>
         *
         * <p><strong>Example - WebSocket client with signing:</strong>
         * <pre>{@code
         * Brane.Signer client = Brane.builder()
         *     .wsUrl("wss://eth-mainnet.g.alchemy.com/v2/YOUR_KEY")
         *     .signer(PrivateKey.fromHex("0x..."))
         *     .build();
         * }</pre>
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
         *   <li>A {@link Brane.Signer} if a signer was configured via {@link #signer(io.brane.core.crypto.Signer)}</li>
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
            BraneProvider resolvedProvider = resolveProvider();
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
            final io.brane.core.crypto.Signer resolvedSigner = signer;
            if (resolvedSigner == null) {
                throw new IllegalStateException(
                        "Cannot build Signer without a signer. Call signer() before buildSigner().");
            }
            BraneProvider resolvedProvider = resolveProvider();
            RpcRetryConfig resolvedRetryConfig = retryConfig != null ? retryConfig : RpcRetryConfig.defaults();
            return new DefaultSigner(resolvedProvider, resolvedSigner, chain, retries, resolvedRetryConfig);
        }

        /**
         * Resolves the provider with priority: explicit provider > wsUrl > rpcUrl.
         */
        private BraneProvider resolveProvider() {
            if (provider != null) {
                return provider;
            }
            if (wsUrl != null) {
                return WebSocketProvider.create(wsUrl);
            }
            return BraneProvider.http(rpcUrl);
        }

        private void validateProviderConfig() {
            if (provider == null && rpcUrl == null && wsUrl == null) {
                throw new IllegalStateException(
                        "Either rpcUrl, wsUrl, or provider must be configured. Call rpcUrl(), wsUrl(), or provider().");
            }
        }
    }
}
