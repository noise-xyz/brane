package io.brane.rpc;

import java.math.BigInteger;
import java.util.List;

import org.jspecify.annotations.Nullable;

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
    }
}
