package io.brane.rpc;

import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;

import io.brane.core.model.AccessListWithGas;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.Transaction;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;

/**
 * A client for interacting with an Ethereum node via JSON-RPC.
 * <p>
 * This client provides read-only access to the blockchain, including:
 * <ul>
 * <li>Fetching block headers and transaction data</li>
 * <li>Reading smart contract state via {@link #call}</li>
 * <li>Querying event logs via {@link #getLogs}</li>
 * <li>Monitoring blockchain state</li>
 * </ul>
 * <p>
 * <strong>Thread Safety:</strong> Implementations are expected to be
 * thread-safe and can be shared across multiple threads.
 * <p>
 * <strong>Usage Example:</strong>
 *
 * <pre>{@code
 * PublicClient client = BranePublicClient.forChain(ChainProfiles.MAINNET)
 *         .build();
 *
 * // Get latest block
 * BlockHeader latest = client.getLatestBlock();
 * System.out.println("Block #" + latest.number());
 *
 * // Get specific transaction
 * Hash txHash = new Hash("0x...");
 * Transaction tx = client.getTransactionByHash(txHash);
 *
 * // Read contract storage (balanceOf call)
 * Map<String, Object> call = Map.of(
 *         "to", "0x...", // contract address
 *         "data", "0x..." // encoded function call
 * );
 * String result = client.call(call, "latest");
 * }</pre>
 *
 * @see WalletClient
 */
public interface PublicClient {

    /**
     * Creates a new {@link PublicClient} using the given provider.
     *
     * @param provider the JSON-RPC provider
     * @return a new PublicClient instance
     * @since 0.1.0
     */
    static PublicClient from(final BraneProvider provider) {
        return new DefaultPublicClient(provider);
    }

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
     * Executes a read-only call on the blockchain.
     * <p>
     * <strong>Example:</strong>
     * <pre>{@code
     * CallRequest request = CallRequest.builder()
     *     .to(contractAddress)
     *     .data(encodedFunctionCall)
     *     .build();
     * HexData result = client.call(request, BlockTag.LATEST);
     * }</pre>
     *
     * @param request  the type-safe call request
     * @param blockTag the block tag (e.g., BlockTag.LATEST)
     * @return the raw hex return value as HexData
     * @since 0.1.0-alpha
     */
    HexData call(CallRequest request, BlockTag blockTag);

    /**
     * Executes a read-only call on the blockchain.
     *
     * @param callObject the call parameters (e.g., "to", "data")
     * @param blockTag   the block tag (e.g., "latest")
     * @return the raw hex return value, or {@code null} if the call returns empty data
     * @deprecated Use {@link #call(CallRequest, BlockTag)} for type-safe calls
     */
    @Deprecated(since = "0.1.0-alpha", forRemoval = true)
    @Nullable String call(Map<String, Object> callObject, String blockTag);

    /**
     * Retrieves logs matching the given filter.
     *
     * @param filter the log filter criteria
     * @return a list of matching logs
     * @since 0.1.0
     */
    List<io.brane.core.model.LogEntry> getLogs(LogFilter filter);

    /**
     * Retrieves the chain ID.
     *
     * @return the chain ID
     * @since 0.1.0
     */
    java.math.BigInteger getChainId();

    /**
     * Retrieves the balance of an account.
     *
     * @param address the account address
     * @return the balance in Wei
     * @since 0.1.0
     */
    java.math.BigInteger getBalance(io.brane.core.types.Address address);

    /**
     * Subscribes to new block headers.
     *
     * @param callback the callback to invoke when a new block header is received
     * @return the subscription handle
     * @since 0.1.0
     */
    Subscription subscribeToNewHeads(java.util.function.Consumer<BlockHeader> callback);

    /**
     * Subscribes to logs matching the given filter.
     *
     * @param filter   the log filter criteria
     * @param callback the callback to invoke when a log is received
     * @return the subscription handle
     * @since 0.1.0
     */
    Subscription subscribeToLogs(LogFilter filter, java.util.function.Consumer<io.brane.core.model.LogEntry> callback);

    /**
     * Creates an access list for the given transaction request.
     *
     * <p>
     * This method simulates the transaction and returns the storage slots it
     * would access along with the gas that would be consumed. The access list can
     * be used to optimize gas costs by pre-declaring storage access (EIP-2930).
     *
     * <p>
     * <strong>Example:</strong>
     *
     * <pre>{@code
     * TransactionRequest request = new TransactionRequest(
     *         fromAddress,
     *         contractAddress,
     *         null, // value
     *         null, // gasLimit
     *         null, // gasPrice
     *         null, // maxPriorityFeePerGas
     *         null, // maxFeePerGas
     *         null, // nonce
     *         encodedData,
     *         true, // isEip1559
     *         null  // accessList
     * );
     *
     * AccessListWithGas result = client.createAccessList(request);
     * // Use result.accessList() in your transaction
     * }</pre>
     *
     * @param request the transaction request to analyze
     * @return the access list and gas used
     * @since 0.1.0
     */
    AccessListWithGas createAccessList(TransactionRequest request);

    /**
     * Creates a new multicall batch for bundling multiple read operations.
     *
     * @return a new MulticallBatch instance
     * @since 0.1.0
     */
    MulticallBatch createBatch();

    /**
     * Simulates one or more calls on the blockchain using eth_simulateV1.
     * <p>
     * This method allows you to:
     * <ul>
     *   <li>Execute multiple calls in sequence without changing the blockchain state</li>
     *   <li>Perform state overrides (balance, nonce, storage, code) during simulation</li>
     *   <li>Get detailed asset changes (token balance shifts)</li>
     * </ul>
     * <p>
     * If the RPC node does not support {@code eth_simulateV1} (returns -32601),
     * a {@link io.brane.rpc.exception.SimulateNotSupportedException} is thrown.
     *
     * @param request the simulation request parameters
     * @return the simulation results for each call
     * @throws io.brane.rpc.exception.SimulateNotSupportedException if eth_simulateV1 is not supported
     * @see <a href="https://ethereum.github.io/execution-apis/api-documentation/">eth_simulateV1 Specification</a>
     * @since 0.1.0
     */
    SimulateResult simulateCalls(SimulateRequest request);
}
