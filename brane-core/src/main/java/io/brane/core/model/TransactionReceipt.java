package io.brane.core.model;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import java.util.List;

public record TransactionReceipt(
        Hash transactionHash,
        Hash blockHash,
        long blockNumber,
        Address from,
        Address to,
        HexData contractAddress,
        List<LogEntry> logs,
        boolean status,
        Wei cumulativeGasUsed) {}
