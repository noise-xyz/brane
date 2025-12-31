package io.brane.core.model;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.Wei;
import java.util.List;
import java.util.Objects;

/**
 * Receipt for an executed transaction containing execution results and emitted
 * events.
 * 
 * <p>
 * A receipt is created after a transaction is included in a block and executed.
 * It provides information about the transaction's execution status, gas usage,
 * and
 * any events (logs) emitted during execution.
 * 
 * <p>
 * <strong>Status interpretation:</strong>
 * <ul>
 * <li>{@code status = true}: Transaction executed successfully</li>
 * <li>{@code status = false}: Transaction reverted (execution failed)</li>
 * </ul>
 * 
 * <p>
 * <strong>Contract deployment:</strong> If {@code to} is null in the original
 * transaction,
 * this is a contract creation, and {@code contractAddress} will contain the
 * address
 * of the newly deployed contract.
 * 
 * @param transactionHash   the hash of the executed transaction
 * @param blockHash         the hash of the block containing this transaction
 * @param blockNumber       the number of the block containing this transaction
 * @param from              the address that sent the transaction
 * @param to                the recipient address, or null for contract creation
 * @param contractAddress   the address of the deployed contract (for contract
 *                          creation), or null for non-deployment transactions
 * @param logs              the list of event logs emitted during transaction
 *                          execution
 * @param status            {@code true} if execution succeeded, {@code false}
 *                          if reverted
 * @param cumulativeGasUsed the total gas used by all transactions up to and
 *                          including this one in the block
 * @since 0.1.0-alpha
 */
public record TransactionReceipt(
                Hash transactionHash,
                Hash blockHash,
                long blockNumber,
                Address from,
                Address to,
                Address contractAddress,
                List<LogEntry> logs,
                boolean status,
                Wei cumulativeGasUsed) {

    /**
     * Validates required fields and makes defensive copy of logs.
     *
     * <p>
     * Required fields that cannot be null:
     * <ul>
     * <li>{@code transactionHash} - every receipt has a transaction</li>
     * <li>{@code blockHash} - receipt only exists for mined transactions</li>
     * <li>{@code from} - every transaction has a sender</li>
     * <li>{@code cumulativeGasUsed} - always present</li>
     * <li>{@code logs} - may be empty but never null</li>
     * </ul>
     *
     * <p>
     * Fields that can be null:
     * <ul>
     * <li>{@code to} - null for contract creation transactions</li>
     * <li>{@code contractAddress} - null for non-contract-creation transactions</li>
     * </ul>
     */
    public TransactionReceipt {
        Objects.requireNonNull(transactionHash, "transactionHash cannot be null");
        Objects.requireNonNull(blockHash, "blockHash cannot be null");
        Objects.requireNonNull(from, "from cannot be null");
        Objects.requireNonNull(cumulativeGasUsed, "cumulativeGasUsed cannot be null");
        Objects.requireNonNull(logs, "logs cannot be null");
        logs = List.copyOf(logs);  // Defensive copy for immutability
    }
}
