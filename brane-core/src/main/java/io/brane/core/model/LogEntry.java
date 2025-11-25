package io.brane.core.model;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import java.util.List;

/**
 * Represents an event log entry emitted by a smart contract.
 *
 * @param address         the address of the contract that emitted the log
 * @param data            the non-indexed log data
 * @param topics          the indexed log topics (topic[0] is usually the event
 *                        signature)
 * @param blockHash       the hash of the block containing this log
 * @param transactionHash the hash of the transaction that generated this log
 * @param logIndex        the index of this log within the block
 * @param removed         true if this log was removed due to a chain
 *                        reorganization
 */
public record LogEntry(
                Address address,
                HexData data,
                List<Hash> topics,
                Hash blockHash,
                Hash transactionHash,
                long logIndex,
                boolean removed) {
}
