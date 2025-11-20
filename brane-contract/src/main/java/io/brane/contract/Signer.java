package io.brane.contract;

import io.brane.core.types.Address;
import io.brane.internal.web3j.crypto.RawTransaction;

public interface Signer {

    Address address();

    String signTransaction(RawTransaction tx);
}
