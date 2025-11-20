package io.brane.rpc;

import io.brane.core.model.TransactionRequest;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.types.Hash;

public interface WalletClient {
    Hash sendTransaction(TransactionRequest request);

    TransactionReceipt sendTransactionAndWait(
            TransactionRequest request, long timeoutMillis, long pollIntervalMillis);
}
