package io.brane.core.model;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import java.util.List;

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
 *                          creation), or empty
 * @param logs              the list of event logs emitted during transaction
 *                          execution
 * @param status            {@code true} if execution succeeded, {@code false}
 *                          if reverted
 * @param cumulativeGasUsed the total gas used by all transactions up to and
 *                          including this one in the block
 */
public record TransactionReceipt(
                Hash transactionHash,
                Hash blockHash,
                long blockNumber,
                Address from,
                Address to,
                HexData contractAddress,
                List<LogEntry> logs,
                boolean status,
                Wei cumulativeGasUsed) {
}
