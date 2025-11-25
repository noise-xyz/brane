package io.brane.rpc;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Filter criteria for querying event logs via {@code eth_getLogs}.
 * 
 * <p>
 * Use this to filter blockchain event logs by:
 * <ul>
 * <li>Block range ({@code fromBlock} to {@code toBlock})</li>
 * <li>Contract address (specific contract or all contracts)</li>
 * <li>Event topics (event signatures and indexed parameters)</li>
 * </ul>
 * 
 * <p>
 * <strong>Block Range:</strong>
 * <ul>
 * <li>Empty {@code fromBlock}/{@code toBlock} = query entire chain (may timeout
 * on mainnet!)</li>
 * <li>Recommended: Always specify a limited range</li>
 * </ul>
 * 
 * <p>
 * <strong>Topics:</strong>
 * Event signatures and indexed parameters. The first topic is usually
 * the event signature hash (keccak256 of "EventName(type1,type2,...)").
 * 
 * <p>
 * <strong>Usage Examples:</strong>
 * 
 * <pre>{@code
 * // Filter by contract address only
 * LogFilter filter = LogFilter.byContract(
 *         new Address("0x..."),
 *         List.of() // all events
 * );
 * 
 * // Filter by contract + specific event signature
 * Hash transferSig = new Hash("0xddf252ad..."); // keccak256("Transfer(address,address,uint256)")
 * LogFilter filter = LogFilter.byContract(
 *         usdcAddress,
 *         List.of(transferSig));
 * 
 * // Filter with block range
 * LogFilter filter = new LogFilter(
 *         Optional.of(1000000L), // from block
 *         Optional.of(1001000L), // to block
 *         Optional.of(contractAddress),
 *         Optional.of(topics));
 * }</pre>
 * 
 * @param fromBlock the starting block number (inclusive), or empty for genesis
 * @param toBlock   the ending block number (inclusive), or empty for latest
 * @param address   the contract address to filter by, or empty for all
 *                  contracts
 * @param topics    the event topic filters, or empty for all events
 * @see PublicClient#getLogs(LogFilter)
 */
public record LogFilter(
        Optional<Long> fromBlock,
        Optional<Long> toBlock,
        Optional<Address> address,
        Optional<List<Hash>> topics) {

    public LogFilter {
        fromBlock = fromBlock == null ? Optional.empty() : fromBlock;
        toBlock = toBlock == null ? Optional.empty() : toBlock;
        address = address == null ? Optional.empty() : address;
        topics = topics == null ? Optional.empty() : topics;
    }

    public static LogFilter byContract(final Address address, final List<Hash> topics) {
        Objects.requireNonNull(address, "address");
        return new LogFilter(Optional.empty(), Optional.empty(), Optional.of(address), Optional.of(topics));
    }
}
