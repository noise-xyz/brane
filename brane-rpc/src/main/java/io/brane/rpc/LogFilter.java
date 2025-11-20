package io.brane.rpc;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Filter for eth_getLogs queries.
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
