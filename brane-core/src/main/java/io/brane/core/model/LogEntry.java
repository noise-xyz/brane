package io.brane.core.model;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import java.util.List;

public record LogEntry(
        Address address,
        HexData data,
        List<Hash> topics,
        Hash blockHash,
        Hash transactionHash,
        long logIndex,
        boolean removed) {}
