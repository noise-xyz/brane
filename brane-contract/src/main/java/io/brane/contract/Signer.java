package io.brane.contract;

import io.brane.core.tx.UnsignedTransaction;
import io.brane.core.types.Address;

public interface Signer {

    Address address();

    String signTransaction(UnsignedTransaction tx, long chainId);
}
