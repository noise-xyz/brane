package io.brane.rpc;

import io.brane.core.model.BlockHeader;
import io.brane.core.model.Transaction;
import io.brane.core.types.Hash;
import java.util.Map;
import java.util.List;

public interface PublicClient {

    static PublicClient from(final BraneProvider provider) {
        return new DefaultPublicClient(provider);
    }

    BlockHeader getLatestBlock();

    BlockHeader getBlockByNumber(long blockNumber);

    Transaction getTransactionByHash(Hash hash);

    String call(Map<String, Object> callObject, String blockTag);

    List<io.brane.core.model.LogEntry> getLogs(LogFilter filter);
}
