package io.brane.core.builder;

import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

public sealed interface TxBuilder<T extends TxBuilder<T>> permits LegacyBuilder, Eip1559Builder {
    T from(Address address);

    T to(Address address);

    T value(Wei value);

    T data(HexData data);

    T nonce(long nonce);

    T gasLimit(long gasLimit);

    TransactionRequest build();

    static Eip1559Builder eip1559() {
        return new Eip1559Builder();
    }

    static LegacyBuilder legacy() {
        return new LegacyBuilder();
    }
}
