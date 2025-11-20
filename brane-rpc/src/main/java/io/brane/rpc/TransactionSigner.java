package io.brane.rpc;

import io.brane.internal.web3j.crypto.RawTransaction;

@FunctionalInterface
public interface TransactionSigner {
    String sign(RawTransaction tx);
}
