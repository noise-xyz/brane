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
 *   <li>{@link Tester} - Test node operations (snapshots, impersonation, time manipulation)</li>
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
 *     case Brane.Tester t -> {
 *         // Test node operations
 *         SnapshotId snapshot = t.snapshot();
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
 * @see Brane.Tester
 * @see Brane.Builder
 * @see BraneProvider
 * @since 0.1.0
 */
public sealed interface Brane extends AutoCloseable permits Brane.Reader, Brane.Signer, Brane.Tester {

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
     * {@link Signer} or {@link Tester} return {@code true}; clients implementing
     * only {@link Reader} return {@code false}.
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
        return this instanceof Signer || this instanceof Tester;
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

    /** Default local Anvil RPC URL. */
    String DEFAULT_ANVIL_URL = "http://127.0.0.1:8545";

    /**
     * Creates a test client connected to a local Anvil node with the default test key.
     *
     * <p>This is the simplest way to create a test client for local development.
     * It connects to Anvil at {@code http://127.0.0.1:8545} using the default
     * funded test account (index 0).
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * Brane.Tester tester = Brane.connectTest();
     *
     * // Take a snapshot before test operations
     * SnapshotId snapshot = tester.snapshot();
     * try {
     *     // Perform test operations
     *     tester.setBalance(address, Wei.fromEther("1000"));
     *     // ... more operations ...
     * } finally {
     *     tester.revert(snapshot);
     * }
     * }</pre>
     *
     * <p><strong>Prerequisites:</strong> Anvil must be running on localhost:8545.
     * Start it with: {@code anvil}
     *
     * @return a new test client connected to local Anvil
     * @since 0.3.0
     * @see AnvilSigners#defaultKey()
     */
    static Tester connectTest() {
        return connectTest(DEFAULT_ANVIL_URL);
    }

    /**
     * Creates a test client connected to a local Anvil node with the specified test key.
     *
     * <p>Connects to Anvil at {@code http://127.0.0.1:8545} using the provided signer.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * // Use a specific Anvil test account
     * Signer key = AnvilSigners.keyAt(3);
     * Brane.Tester tester = Brane.connectTest(key);
     * }</pre>
     *
     * @param signer the signer for transaction signing
     * @return a new test client connected to local Anvil
     * @since 0.3.0
     */
    static Tester connectTest(io.brane.core.crypto.Signer signer) {
        return connectTest(DEFAULT_ANVIL_URL, signer);
    }

    /**
     * Creates a test client connected to the specified RPC endpoint with the default test key.
     *
     * <p>Uses Anvil mode by default and the default funded test account (index 0).
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * // Connect to a remote Anvil instance
     * Brane.Tester tester = Brane.connectTest("http://192.168.1.100:8545");
     * }</pre>
     *
     * @param rpcUrl the HTTP/HTTPS RPC endpoint URL
     * @return a new test client with ANVIL mode
     * @since 0.3.0
     */
    static Tester connectTest(String rpcUrl) {
        return builder().rpcUrl(rpcUrl).signer(AnvilSigners.defaultKey()).buildTester();
    }

    /**
     * Creates a test client connected to the specified RPC endpoint with the provided signer.
     *
     * <p>Uses Anvil mode by default.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * Signer key = PrivateKey.fromHex("0x...");
     * Brane.Tester tester = Brane.connectTest("http://localhost:8545", key);
     *
     * // Impersonate a whale for testing
     * Address whale = Address.from("0x...");
     * try (ImpersonationSession session = tester.impersonate(whale)) {
     *     session.sendTransactionAndWait(request);
     * }
     * }</pre>
     *
     * @param rpcUrl the HTTP/HTTPS RPC endpoint URL
     * @param signer the signer for transaction signing
     * @return a new test client with ANVIL mode
     * @since 0.3.0
     */
    static Tester connectTest(String rpcUrl, io.brane.core.crypto.Signer signer) {
        return builder().rpcUrl(rpcUrl).signer(signer).buildTester();
    }

    /**
     * Creates a test client connected to the specified RPC endpoint with the specified test node mode.
     *
     * <p>Use this method when connecting to non-Anvil test nodes like Hardhat or Ganache.
     *
     * <p><strong>Example:</strong>
     * <pre>{@code
     * Signer key = PrivateKey.fromHex("0x...");
     * Brane.Tester tester = Brane.connectTest("http://localhost:8545", key, TestNodeMode.HARDHAT);
     * }</pre>
     *
     * @param rpcUrl the HTTP/HTTPS RPC endpoint URL
     * @param signer the signer for transaction signing
     * @param mode   the test node mode (Anvil, Hardhat, or Ganache)
     * @return a new test client with the specified mode
     * @since 0.3.0
     */
    static Tester connectTest(String rpcUrl, io.brane.core.crypto.Signer signer, TestNodeMode mode) {
        return builder().rpcUrl(rpcUrl).signer(signer).testMode(mode).buildTester();
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
     *
     * <p>This interface provides methods for test-specific operations like snapshots,
     * time manipulation, account impersonation, and state management that are only
     * available on test networks.
     *
     * <h2>Capabilities Overview</h2>
     * <ul>
     *   <li><strong>Signing:</strong> {@link #asSigner()} - Convert to a Signer for transaction sending</li>
     *   <li><strong>Snapshots:</strong> {@link #snapshot()}, {@link #revert(SnapshotId)} - Save/restore chain state</li>
     *   <li><strong>Impersonation:</strong> {@link #impersonate(Address)}, {@link #stopImpersonating(Address)} - Act as any address</li>
     *   <li><strong>Account Manipulation:</strong> {@link #setBalance(Address, Wei)}, {@link #setCode(Address, HexData)}, {@link #setNonce(Address, long)}, {@link #setStorageAt(Address, Hash, Hash)}</li>
     *   <li><strong>Mining:</strong> {@link #mine()}, {@link #mine(long)}, {@link #setAutomine(boolean)}, {@link #setIntervalMining(long)}</li>
     *   <li><strong>Time:</strong> {@link #setNextBlockTimestamp(long)}, {@link #increaseTime(long)}</li>
     *   <li><strong>Block Config:</strong> {@link #setNextBlockBaseFee(Wei)}, {@link #setCoinbase(Address)}</li>
     *   <li><strong>Reset:</strong> {@link #reset()}, {@link #reset(String, long)} - Reset chain state</li>
     * </ul>
     *
     * <h2>Example Usage</h2>
     * <pre>{@code
     * // Create a tester client
     * Brane.Tester tester = ... // obtain from factory
     *
     * // Snapshot and restore pattern
     * SnapshotId snapshot = tester.snapshot();
     * try {
     *     // ... perform test operations ...
     * } finally {
     *     tester.revert(snapshot);
     * }
     *
     * // Impersonation pattern (auto-cleanup)
     * Address whale = Address.from("0x...");
     * try (ImpersonationSession session = tester.impersonate(whale)) {
     *     TransactionReceipt receipt = session.sendTransactionAndWait(request);
     * }
     *
     * // Time manipulation
     * tester.setNextBlockTimestamp(System.currentTimeMillis() / 1000 + 86400); // +1 day
     * tester.mine();
     * }</pre>
     *
     * @since 0.1.0-alpha
     * @see SnapshotId
     * @see ImpersonationSession
     */
    non-sealed interface Tester extends Brane {

        // ==================== Signer Conversion ====================

        /**
         * Returns this tester as a {@link Signer} for transaction sending.
         *
         * <p>The returned signer uses the underlying signer configured for this tester,
         * allowing standard transaction operations while retaining access to test-specific
         * methods through the original tester reference.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * Brane.Signer signer = tester.asSigner();
         * Hash txHash = signer.sendTransaction(request);
         * }</pre>
         *
         * @return a signer view of this tester
         */
        Signer asSigner();

        // ==================== Transaction Methods (Signer delegation) ====================

        /**
         * Submits a transaction to the blockchain and returns immediately.
         *
         * <p>This method delegates to the underlying signer. See {@link Signer#sendTransaction}
         * for full details.
         *
         * @param request the transaction request
         * @return the transaction hash
         * @see Signer#sendTransaction
         */
        Hash sendTransaction(TransactionRequest request);

        /**
         * Submits a transaction and waits for confirmation using default settings.
         *
         * <p>Uses default timeout (60 seconds) and poll interval (1 second).
         * Delegates to {@link Signer#sendTransactionAndWait}.
         *
         * @param request the transaction request
         * @return the transaction receipt once confirmed
         * @see Signer#sendTransactionAndWait(TransactionRequest)
         */
        default TransactionReceipt sendTransactionAndWait(TransactionRequest request) {
            return sendTransactionAndWait(request, Signer.DEFAULT_TIMEOUT_MILLIS, Signer.DEFAULT_POLL_INTERVAL_MILLIS);
        }

        /**
         * Submits a transaction and waits for confirmation with custom settings.
         *
         * <p>Delegates to {@link Signer#sendTransactionAndWait(TransactionRequest, long, long)}.
         *
         * @param request            the transaction request
         * @param timeoutMillis      maximum wait time in milliseconds
         * @param pollIntervalMillis poll interval in milliseconds
         * @return the transaction receipt once confirmed
         * @see Signer#sendTransactionAndWait(TransactionRequest, long, long)
         */
        TransactionReceipt sendTransactionAndWait(
                TransactionRequest request, long timeoutMillis, long pollIntervalMillis);

        /**
         * Returns the signer instance used by this tester.
         *
         * @return the signer
         * @see Signer#signer()
         */
        io.brane.core.crypto.Signer signer();

        // ==================== Snapshot Methods ====================

        /**
         * Creates a snapshot of the current blockchain state.
         *
         * <p>The returned {@link SnapshotId} can be used to revert the chain state
         * back to this point using {@link #revert(SnapshotId)}.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * SnapshotId snapshot = tester.snapshot();
         * // ... perform operations ...
         * tester.revert(snapshot);
         * }</pre>
         *
         * @return the snapshot ID
         */
        SnapshotId snapshot();

        /**
         * Reverts the blockchain state to a previously taken snapshot.
         *
         * <p><strong>Note:</strong> After reverting, the snapshot is consumed and cannot
         * be reused. Take a new snapshot if you need to revert to the same state again.
         *
         * @param snapshotId the snapshot to revert to
         * @return true if the revert succeeded, false otherwise
         */
        boolean revert(SnapshotId snapshotId);

        // ==================== Impersonation Methods ====================

        /**
         * Starts impersonating the specified address.
         *
         * <p>Returns an {@link ImpersonationSession} that allows sending transactions
         * from the impersonated address without possessing its private key. The session
         * implements {@link AutoCloseable} for automatic cleanup.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * Address whale = Address.from("0x...");
         * try (ImpersonationSession session = tester.impersonate(whale)) {
         *     TransactionReceipt receipt = session.sendTransactionAndWait(request);
         * }
         * // Impersonation automatically stopped
         * }</pre>
         *
         * @param address the address to impersonate
         * @return an impersonation session for sending transactions
         * @see #stopImpersonating(Address)
         */
        ImpersonationSession impersonate(Address address);

        /**
         * Stops impersonating the specified address.
         *
         * <p>This method is automatically called when an {@link ImpersonationSession}
         * is closed. Direct calls are useful when managing impersonation manually.
         *
         * @param address the address to stop impersonating
         */
        void stopImpersonating(Address address);

        /**
         * Enables automatic impersonation for all addresses.
         *
         * <p>When enabled, any address can send transactions without explicit
         * impersonation. This is useful for complex test scenarios requiring
         * multiple addresses.
         *
         * <p><strong>Note:</strong> Only supported by Anvil ({@code anvil_autoImpersonateAccount}).
         */
        void enableAutoImpersonate();

        /**
         * Disables automatic impersonation.
         *
         * <p><strong>Note:</strong> Only supported by Anvil ({@code anvil_autoImpersonateAccount}).
         */
        void disableAutoImpersonate();

        // ==================== Account Manipulation Methods ====================

        /**
         * Sets the ETH balance of an account.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * tester.setBalance(address, Wei.fromEther("1000"));
         * }</pre>
         *
         * @param address the account address
         * @param balance the new balance in Wei
         */
        void setBalance(Address address, io.brane.core.types.Wei balance);

        /**
         * Sets the bytecode at an address.
         *
         * <p>This can be used to deploy arbitrary bytecode or modify existing contracts.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * tester.setCode(contractAddress, HexData.from("0x608060405234..."));
         * }</pre>
         *
         * @param address the address to set code at
         * @param code    the bytecode to set
         */
        void setCode(Address address, HexData code);

        /**
         * Sets the nonce of an account.
         *
         * <p>The nonce affects the order and validity of transactions from this account.
         *
         * @param address the account address
         * @param nonce   the new nonce value
         */
        void setNonce(Address address, long nonce);

        /**
         * Sets a storage slot value at an address.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * Hash slot = Hash.from("0x0000000000000000000000000000000000000000000000000000000000000000");
         * Hash value = Hash.from("0x000000000000000000000000000000000000000000000000000000000000002a");
         * tester.setStorageAt(contractAddress, slot, value);
         * }</pre>
         *
         * @param address the contract address
         * @param slot    the storage slot (32 bytes)
         * @param value   the value to set (32 bytes)
         */
        void setStorageAt(Address address, Hash slot, Hash value);

        // ==================== Mining Methods ====================

        /**
         * Mines a single block.
         *
         * <p>This is equivalent to {@code mine(1)}.
         */
        void mine();

        /**
         * Mines the specified number of blocks.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * tester.mine(100); // Mine 100 blocks
         * }</pre>
         *
         * @param blocks the number of blocks to mine
         */
        void mine(long blocks);

        /**
         * Mines the specified number of blocks with a time interval between each block.
         *
         * <p>This allows simulating realistic block production with consistent block times.
         * The interval is applied between consecutive blocks, so the total time will be
         * approximately {@code (blocks - 1) * intervalSeconds}.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * tester.mine(10, 12); // Mine 10 blocks with 12 seconds between each
         * }</pre>
         *
         * @param blocks          the number of blocks to mine
         * @param intervalSeconds the time interval in seconds between each block
         */
        void mine(long blocks, long intervalSeconds);

        /**
         * Mines a single block with the specified timestamp.
         *
         * <p>This combines mining with time manipulation in a single operation.
         *
         * @param timestamp the Unix timestamp for the mined block
         */
        void mineAt(long timestamp);

        /**
         * Returns whether automine is currently enabled.
         *
         * <p>Automine controls whether transactions are mined immediately upon submission.
         *
         * @return true if automine is enabled, false otherwise
         */
        boolean getAutomine();

        /**
         * Enables or disables automatic mining.
         *
         * <p>When automine is enabled (default), transactions are mined immediately
         * upon submission. When disabled, transactions remain in the mempool until
         * {@link #mine()} is called explicitly.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * tester.setAutomine(false);
         * // Transactions now stay in mempool
         * Hash tx1 = signer.sendTransaction(request1);
         * Hash tx2 = signer.sendTransaction(request2);
         * tester.mine(); // Both transactions mined in same block
         * }</pre>
         *
         * @param enabled true to enable automine, false to disable
         */
        void setAutomine(boolean enabled);

        /**
         * Sets interval mining with the specified block time.
         *
         * <p>When interval mining is enabled, blocks are mined automatically at
         * the specified interval, simulating real network behavior.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * tester.setIntervalMining(12_000); // Mine every 12 seconds (like mainnet)
         * }</pre>
         *
         * @param intervalMs the mining interval in milliseconds (0 to disable)
         */
        void setIntervalMining(long intervalMs);

        // ==================== Time Manipulation Methods ====================

        /**
         * Sets the timestamp for the next block.
         *
         * <p>The timestamp must be greater than the current block's timestamp.
         * This affects only the next block; subsequent blocks will increment
         * normally from this timestamp.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * long futureTime = System.currentTimeMillis() / 1000 + 86400; // +1 day
         * tester.setNextBlockTimestamp(futureTime);
         * tester.mine();
         * }</pre>
         *
         * @param timestamp the Unix timestamp (seconds since epoch)
         */
        void setNextBlockTimestamp(long timestamp);

        /**
         * Increases the blockchain time by the specified number of seconds.
         *
         * <p>Unlike {@link #setNextBlockTimestamp(long)}, this increments from
         * the current time rather than setting an absolute value.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * tester.increaseTime(3600); // Advance 1 hour
         * tester.mine();
         * }</pre>
         *
         * @param seconds the number of seconds to advance
         */
        void increaseTime(long seconds);

        // ==================== Block Configuration Methods ====================

        /**
         * Sets the base fee for the next block.
         *
         * <p>This is useful for testing gas price edge cases and EIP-1559 behavior.
         *
         * @param baseFee the base fee in Wei
         */
        void setNextBlockBaseFee(io.brane.core.types.Wei baseFee);

        /**
         * Sets the coinbase (block reward recipient) address.
         *
         * <p>This affects the {@code block.coinbase} value in subsequent blocks.
         *
         * @param coinbase the coinbase address
         */
        void setCoinbase(Address coinbase);

        // ==================== Reset Methods ====================

        /**
         * Resets the chain to its initial state.
         *
         * <p>This clears all transactions, blocks (except genesis), and restores
         * the initial account states.
         */
        void reset();

        /**
         * Resets the chain and forks from the specified RPC endpoint at the given block.
         *
         * <p>This is useful for resetting to a specific state from a live network.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * tester.reset("https://eth-mainnet.g.alchemy.com/v2/...", 18_000_000L);
         * }</pre>
         *
         * @param forkUrl     the RPC URL to fork from
         * @param blockNumber the block number to fork at
         */
        void reset(String forkUrl, long blockNumber);

        // ==================== Receipt Waiting Methods ====================

        /** Default timeout for waiting for receipt: 60 seconds. */
        long DEFAULT_WAIT_TIMEOUT_MILLIS = 60_000;

        /** Default initial poll interval for waiting for receipt: 100 milliseconds. */
        long DEFAULT_WAIT_POLL_INTERVAL_MILLIS = 100;

        /**
         * Waits for a transaction receipt to be available.
         *
         * <p>This method polls {@code eth_getTransactionReceipt} with exponential backoff
         * until the receipt is available or the timeout is reached. The poll interval
         * starts at the specified value and doubles after each poll, capped at 10 seconds.
         *
         * <p>Uses default timeout (60 seconds) and initial poll interval (100 milliseconds).
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * // Wait for a transaction sent elsewhere
         * Hash txHash = session.sendTransaction(request);
         * TransactionReceipt receipt = tester.waitForReceipt(txHash);
         * }</pre>
         *
         * @param txHash the transaction hash to wait for
         * @return the transaction receipt once available
         * @throws io.brane.core.error.RpcException if timeout is reached or interrupted
         */
        default TransactionReceipt waitForReceipt(Hash txHash) {
            return waitForReceipt(txHash, DEFAULT_WAIT_TIMEOUT_MILLIS, DEFAULT_WAIT_POLL_INTERVAL_MILLIS);
        }

        /**
         * Waits for a transaction receipt to be available with custom timeout settings.
         *
         * <p>This method polls {@code eth_getTransactionReceipt} with exponential backoff
         * until the receipt is available or the timeout is reached. The poll interval
         * starts at the specified value and doubles after each poll, capped at 10 seconds.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * // Wait with custom settings
         * TransactionReceipt receipt = tester.waitForReceipt(txHash, 120_000, 50);
         * }</pre>
         *
         * @param txHash             the transaction hash to wait for
         * @param timeoutMillis      maximum time to wait, in milliseconds
         * @param pollIntervalMillis initial poll interval in milliseconds (doubles each poll, max 10s)
         * @return the transaction receipt once available
         * @throws io.brane.core.error.RpcException if timeout is reached or interrupted
         */
        TransactionReceipt waitForReceipt(Hash txHash, long timeoutMillis, long pollIntervalMillis);
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
        private TestNodeMode testMode = TestNodeMode.ANVIL;

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
         * Sets the test node mode for tester clients.
         *
         * <p>This determines which RPC method prefixes to use for test-specific
         * operations like snapshots, impersonation, and time manipulation.
         *
         * <p>Default is {@link TestNodeMode#ANVIL}.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * Brane.Tester tester = Brane.builder()
         *     .rpcUrl("http://localhost:8545")
         *     .signer(key)
         *     .testMode(TestNodeMode.HARDHAT)
         *     .buildTester();
         * }</pre>
         *
         * @param testMode the test node mode
         * @return this builder for chaining
         * @since 0.3.0
         * @see TestNodeMode
         */
        public Builder testMode(TestNodeMode testMode) {
            this.testMode = testMode;
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
         * Builds a test-capable {@link Brane.Tester} client.
         *
         * <p>Use this method when you need test node operations such as snapshots,
         * impersonation, and time manipulation. Requires a signer to be configured.
         *
         * <p><strong>Example:</strong>
         * <pre>{@code
         * Brane.Tester tester = Brane.builder()
         *     .rpcUrl("http://localhost:8545")
         *     .signer(AnvilSigners.defaultKey())
         *     .testMode(TestNodeMode.ANVIL)
         *     .buildTester();
         *
         * // Take snapshot before test
         * SnapshotId snapshot = tester.snapshot();
         * try {
         *     // Test operations...
         * } finally {
         *     tester.revert(snapshot);
         * }
         * }</pre>
         *
         * @return a new test-capable {@link Brane.Tester} instance
         * @throws IllegalStateException if neither rpcUrl nor provider is configured
         * @throws IllegalStateException if no signer is configured
         * @since 0.3.0
         * @see TestNodeMode
         */
        public Tester buildTester() {
            validateProviderConfig();
            final io.brane.core.crypto.Signer resolvedSigner = signer;
            if (resolvedSigner == null) {
                throw new IllegalStateException(
                        "Cannot build Tester without a signer. Call signer() before buildTester().");
            }
            BraneProvider resolvedProvider = resolveProvider();
            RpcRetryConfig resolvedRetryConfig = retryConfig != null ? retryConfig : RpcRetryConfig.defaults();
            return new DefaultTester(resolvedProvider, resolvedSigner, chain, retries, resolvedRetryConfig, testMode);
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
