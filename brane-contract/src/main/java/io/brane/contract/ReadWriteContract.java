package io.brane.contract;

import io.brane.core.builder.TxBuilder;
import io.brane.core.abi.Abi;
import java.util.Objects;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;

/**
 * Read-write façade for smart contract interactions.
 *
 * <p>Extends {@link ReadOnlyContract} with the ability to send state-changing
 * transactions. Requires a {@link WalletClient} for signing and submitting
 * transactions.
 *
 * <p><strong>Usage Example:</strong>
 * <pre>{@code
 * // Create contract instance
 * Abi abi = Abi.fromJson(abiJson);
 * ReadWriteContract contract = ReadWriteContract.from(
 *     new Address("0x..."),
 *     abi,
 *     publicClient,
 *     walletClient
 * );
 *
 * // Call view functions (inherited from ReadOnlyContract)
 * BigInteger balance = contract.call("balanceOf", BigInteger.class, holder);
 *
 * // Send transactions (fire-and-forget)
 * Hash txHash = contract.send("transfer", recipient, amount);
 *
 * // Send transactions and wait for receipt
 * TransactionReceipt receipt = contract.sendAndWait(
 *     "transfer",
 *     30_000L,   // timeout ms
 *     500L,      // poll interval ms
 *     recipient, amount
 * );
 * }</pre>
 *
 * <p>For type-safe contract interaction with compile-time checking,
 * consider using {@link BraneContract#bind} with a Java interface instead.
 *
 * @see ReadOnlyContract
 * @see BraneContract#bind
 * @see WalletClient
 */
public final class ReadWriteContract extends ReadOnlyContract {

    private final WalletClient walletClient;
    private final ContractOptions options;

    private ReadWriteContract(
            final Address address,
            final Abi abi,
            final PublicClient publicClient,
            final WalletClient walletClient,
            final ContractOptions options) {
        super(address, abi, publicClient);
        this.walletClient = Objects.requireNonNull(walletClient, "walletClient must not be null");
        this.options = Objects.requireNonNull(options, "options must not be null");
    }

    /**
     * Creates a new ReadWriteContract for the specified contract with default options.
     *
     * <p>Uses {@link ContractOptions#defaults()} for transaction configuration.
     *
     * @param address      the deployed contract address
     * @param abi          the contract ABI
     * @param publicClient the public client for read operations
     * @param walletClient the wallet client for write operations
     * @return a new ReadWriteContract instance
     * @throws NullPointerException if any parameter is null
     */
    public static ReadWriteContract from(
            final Address address,
            final Abi abi,
            final PublicClient publicClient,
            final WalletClient walletClient) {
        return from(address, abi, publicClient, walletClient, ContractOptions.defaults());
    }

    /**
     * Creates a new ReadWriteContract for the specified contract with custom options.
     *
     * <p>The options control transaction configuration including:
     * <ul>
     *   <li>{@link ContractOptions#gasLimit()} - gas limit for transactions</li>
     *   <li>{@link ContractOptions#transactionType()} - EIP-1559 or legacy transactions</li>
     *   <li>{@link ContractOptions#maxPriorityFee()} - priority fee for EIP-1559 transactions</li>
     *   <li>{@link ContractOptions#timeout()} - timeout for {@link #sendAndWait} methods</li>
     *   <li>{@link ContractOptions#pollInterval()} - poll interval for {@link #sendAndWait} methods</li>
     * </ul>
     *
     * @param address      the deployed contract address
     * @param abi          the contract ABI
     * @param publicClient the public client for read operations
     * @param walletClient the wallet client for write operations
     * @param options      the contract options for transaction configuration
     * @return a new ReadWriteContract instance
     * @throws NullPointerException if any parameter is null
     */
    public static ReadWriteContract from(
            final Address address,
            final Abi abi,
            final PublicClient publicClient,
            final WalletClient walletClient,
            final ContractOptions options) {
        return new ReadWriteContract(address, abi, publicClient, walletClient, options);
    }

    /**
     * Sends a state-changing transaction to the contract.
     *
     * <p>This method submits the transaction and returns immediately with the
     * transaction hash. Use {@link #sendAndWait} if you need to wait for
     * confirmation.
     *
     * @param functionName the contract function name
     * @param args         the function arguments
     * @return the transaction hash
     */
    public Hash send(final String functionName, final Object... args) {
        return send(functionName, Wei.of(0), args);
    }

    /**
     * Sends a state-changing transaction with ETH value to the contract.
     *
     * <p>This method submits the transaction and returns immediately with the
     * transaction hash. Use {@link #sendAndWait} if you need to wait for
     * confirmation.
     *
     * <p>Use this method for payable functions that require sending ETH.
     *
     * <p>Transaction configuration (gas limit, transaction type, priority fee)
     * is controlled by the {@link ContractOptions} provided at construction time.
     *
     * @param functionName the contract function name
     * @param value        the ETH value to send with the transaction
     * @param args         the function arguments
     * @return the transaction hash
     * @throws NullPointerException if value is null
     */
    public Hash send(final String functionName, final Wei value, final Object... args) {
        Objects.requireNonNull(value, "value must not be null");
        final Abi.FunctionCall fnCall = abi().encodeFunction(functionName, args);
        final TransactionRequest request = buildTransactionRequest(fnCall, value);
        return walletClient.sendTransaction(request);
    }

    /**
     * Sends a state-changing transaction and waits for the receipt.
     *
     * <p>This method submits the transaction and polls for the receipt until
     * confirmed or the timeout is reached.
     *
     * @param functionName       the contract function name
     * @param timeoutMillis      maximum time to wait for confirmation in milliseconds
     * @param pollIntervalMillis interval between receipt checks in milliseconds
     * @param args               the function arguments
     * @return the transaction receipt
     * @throws io.brane.core.error.RpcException if the transaction fails or times out
     */
    public TransactionReceipt sendAndWait(
            final String functionName,
            final long timeoutMillis,
            final long pollIntervalMillis,
            final Object... args) {
        return sendAndWait(functionName, Wei.of(0), timeoutMillis, pollIntervalMillis, args);
    }

    /**
     * Sends a state-changing transaction with ETH value and waits for the receipt.
     *
     * <p>This method submits the transaction and polls for the receipt until
     * confirmed or the timeout is reached.
     *
     * <p>Use this method for payable functions that require sending ETH.
     *
     * @param functionName       the contract function name
     * @param value              the ETH value to send with the transaction
     * @param timeoutMillis      maximum time to wait for confirmation in milliseconds
     * @param pollIntervalMillis interval between receipt checks in milliseconds
     * @param args               the function arguments
     * @return the transaction receipt
     * @throws NullPointerException if value is null
     * @throws io.brane.core.error.RpcException if the transaction fails or times out
     */
    public TransactionReceipt sendAndWait(
            final String functionName,
            final Wei value,
            final long timeoutMillis,
            final long pollIntervalMillis,
            final Object... args) {
        Objects.requireNonNull(value, "value must not be null");
        final Abi.FunctionCall fnCall = abi().encodeFunction(functionName, args);
        final TransactionRequest request = buildTransactionRequest(fnCall, value);
        return walletClient.sendTransactionAndWait(request, timeoutMillis, pollIntervalMillis);
    }

    private TransactionRequest buildTransactionRequest(
            final Abi.FunctionCall call, final Wei value) {
        if (options.transactionType() == ContractOptions.TransactionType.LEGACY) {
            return TxBuilder.legacy()
                    .to(address())
                    .data(new HexData(call.data()))
                    .value(value)
                    .gasLimit(options.gasLimit())
                    .build();
        }
        return TxBuilder.eip1559()
                .to(address())
                .data(new HexData(call.data()))
                .value(value)
                .gasLimit(options.gasLimit())
                .maxPriorityFeePerGas(options.maxPriorityFee())
                .build();
    }
}
