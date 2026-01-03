package io.brane.rpc;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;

/**
 * Filter criteria for querying event logs via {@code eth_getLogs}.
 *
 * <p>Use this to filter blockchain event logs by:
 * <ul>
 *   <li>Block range ({@code fromBlock} to {@code toBlock})</li>
 *   <li>Contract addresses (one or more contracts, or all contracts)</li>
 *   <li>Event topics (event signatures and indexed parameters)</li>
 * </ul>
 *
 * <p><strong>Block Range:</strong>
 * <ul>
 *   <li>Empty {@code fromBlock}/{@code toBlock} = query entire chain (may timeout on mainnet!)</li>
 *   <li>Recommended: Always specify a limited range</li>
 * </ul>
 *
 * <p><strong>Addresses:</strong>
 * The {@code eth_getLogs} RPC method supports filtering by one or more contract addresses.
 * When multiple addresses are specified, logs from any of the addresses will be returned.
 *
 * <p><strong>Topics:</strong>
 * Event signatures and indexed parameters. The first topic is usually the event signature
 * hash (keccak256 of "EventName(type1,type2,...)").
 *
 * <p><strong>Usage Examples:</strong>
 * <pre>{@code
 * // Filter by single contract address
 * LogFilter filter = LogFilter.byContract(usdcAddress, List.of());
 *
 * // Filter by multiple contract addresses
 * LogFilter filter = LogFilter.byContracts(
 *         List.of(usdcAddress, usdtAddress, daiAddress),
 *         List.of(transferEventTopic));
 *
 * // Filter by contract + specific event signature
 * Hash transferSig = new Hash("0xddf252ad..."); // keccak256("Transfer(address,address,uint256)")
 * LogFilter filter = LogFilter.byContract(usdcAddress, List.of(transferSig));
 *
 * // Filter with block range and multiple addresses
 * LogFilter filter = new LogFilter(
 *         Optional.of(1000000L),
 *         Optional.of(1001000L),
 *         Optional.of(List.of(addr1, addr2)),
 *         Optional.of(topics));
 * }</pre>
 *
 * @param fromBlock the starting block number (inclusive), or empty for genesis
 * @param toBlock   the ending block number (inclusive), or empty for latest
 * @param addresses the contract addresses to filter by, or empty for all contracts
 * @param topics    the event topic filters, or empty for all events
 * @see PublicClient#getLogs(LogFilter)
 */
public record LogFilter(
        Optional<Long> fromBlock,
        Optional<Long> toBlock,
        Optional<List<Address>> addresses,
        Optional<List<Hash>> topics) {

    public LogFilter {
        Objects.requireNonNull(fromBlock, "fromBlock must not be null; use Optional.empty()");
        Objects.requireNonNull(toBlock, "toBlock must not be null; use Optional.empty()");
        Objects.requireNonNull(addresses, "addresses must not be null; use Optional.empty()");
        Objects.requireNonNull(topics, "topics must not be null; use Optional.empty()");
    }

    /**
     * Creates a filter for a single contract address.
     *
     * <p>For filtering by multiple addresses, use {@link #byContracts(List, List)}.
     *
     * @param address the contract address to filter by
     * @param topics  the event topics to filter by (empty list for all events)
     * @return a new LogFilter
     */
    public static LogFilter byContract(final Address address, final List<Hash> topics) {
        Objects.requireNonNull(address, "address");
        return new LogFilter(Optional.empty(), Optional.empty(), Optional.of(List.of(address)), Optional.of(topics));
    }

    /**
     * Creates a filter for multiple contract addresses.
     *
     * <p>Logs from any of the specified addresses will be returned.
     *
     * @param addresses the contract addresses to filter by (must not be empty)
     * @param topics    the event topics to filter by (empty list for all events)
     * @return a new LogFilter
     * @throws IllegalArgumentException if addresses is empty
     */
    public static LogFilter byContracts(final List<Address> addresses, final List<Hash> topics) {
        Objects.requireNonNull(addresses, "addresses");
        if (addresses.isEmpty()) {
            throw new IllegalArgumentException("addresses must not be empty; use empty Optional for all contracts");
        }
        return new LogFilter(Optional.empty(), Optional.empty(), Optional.of(List.copyOf(addresses)), Optional.of(topics));
    }

    /**
     * Returns the single address if exactly one is specified.
     *
     * <p>This is a convenience method for backward compatibility.
     *
     * @return the single address, or empty if zero or multiple addresses
     * @deprecated Access {@link #addresses()} directly instead
     */
    @Deprecated(since = "0.5.0", forRemoval = true)
    public Optional<Address> address() {
        return addresses.filter(list -> list.size() == 1).map(list -> list.get(0));
    }
}
