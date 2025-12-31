package io.brane.core.model;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import java.util.List;
import java.util.Objects;

/**
 * Represents an event log entry emitted by a smart contract.
 *
 * <p>
 * Log entries are produced when smart contracts emit events. Each entry contains
 * the emitting contract's address, event data, and indexed topics for filtering.
 *
 * <p>
 * <strong>Nullability:</strong>
 * <ul>
 * <li>{@code blockHash} - can be {@code null} for logs from pending transactions</li>
 * <li>All other fields are required and cannot be null</li>
 * </ul>
 *
 * @param address         the address of the contract that emitted the log (required)
 * @param data            the non-indexed log data (required, may be empty)
 * @param topics          the indexed log topics (required, may be empty; topic[0]
 *                        is usually the event signature)
 * @param blockHash       the hash of the block containing this log ({@code null}
 *                        for pending logs)
 * @param transactionHash the hash of the transaction that generated this log (required)
 * @param logIndex        the index of this log within the block
 * @param removed         true if this log was removed due to a chain reorganization
 */
public record LogEntry(
                Address address,
                HexData data,
                List<Hash> topics,
                Hash blockHash,
                Hash transactionHash,
                long logIndex,
                boolean removed) {

    /**
     * Validates required fields and makes defensive copy of topics.
     *
     * @throws NullPointerException if address, data, topics, or transactionHash is null
     */
    public LogEntry {
        Objects.requireNonNull(address, "address cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(topics, "topics cannot be null");
        Objects.requireNonNull(transactionHash, "transactionHash cannot be null");
        topics = List.copyOf(topics);  // Defensive copy for immutability
        // blockHash can be null for pending logs
    }
}
