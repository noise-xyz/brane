package io.brane.core.model;

import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import java.util.Optional;

public record TransactionRequest(
        Address from,
        Optional<Address> to,
        Optional<Wei> value,
        Optional<Long> gasLimit,
        Optional<Wei> gasPrice,
        Optional<Long> nonce,
        HexData data) {

    public TransactionRequest {
        to = to == null ? Optional.empty() : to;
        value = value == null ? Optional.empty() : value;
        gasLimit = gasLimit == null ? Optional.empty() : gasLimit;
        gasPrice = gasPrice == null ? Optional.empty() : gasPrice;
        nonce = nonce == null ? Optional.empty() : nonce;
    }
}
