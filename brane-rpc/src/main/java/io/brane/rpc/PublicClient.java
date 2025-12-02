package io.brane.rpc;

import io.brane.core.model.BlockHeader;
import io.brane.core.model.Transaction;
import io.brane.core.types.Hash;
import java.util.Map;
import java.util.List;

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
 * 
 * <p>
 * <strong>Thread Safety:</strong> Implementations are expected to be
 * thread-safe
 * and can be shared across multiple threads.
 * 
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
 * @see BranePublicClient
 * @see WalletClient
 */
public interface PublicClient {

    /**
     * Creates a new {@link PublicClient} using the given provider.
     *
     * @param provider the JSON-RPC provider
     * @return a new PublicClient instance
     */
    static PublicClient from(final BraneProvider provider) {
        return new DefaultPublicClient(provider);
    }

    /**
     * Retrieves the latest block header.
     *
     * @return the latest block header, or null if not found
     */
    BlockHeader getLatestBlock();

    /**
     * Retrieves a block header by its number.
     *
     * @param blockNumber the block number
     * @return the block header, or null if not found
     */
    BlockHeader getBlockByNumber(long blockNumber);

    /**
     * Retrieves a transaction by its hash.
     *
     * @param hash the transaction hash
     * @return the transaction, or null if not found
     */
    Transaction getTransactionByHash(Hash hash);

    /**
     * Executes a read-only call on the blockchain.
     *
     * @param callObject the call parameters (e.g., "to", "data")
     * @param blockTag   the block tag (e.g., "latest")
     * @return the raw hex return value
     */
    String call(Map<String, Object> callObject, String blockTag);

    /**
     * Retrieves logs matching the given filter.
     *
     * @param filter the log filter criteria
     * @return a list of matching logs
     */
    List<io.brane.core.model.LogEntry> getLogs(LogFilter filter);

    /**
     * Retrieves the chain ID.
     *
     * @return the chain ID
     */
    java.math.BigInteger getChainId();

    /**
     * Retrieves the balance of an account.
     *
     * @param address the account address
     * @return the balance in Wei
     */
    java.math.BigInteger getBalance(io.brane.core.types.Address address);
}
