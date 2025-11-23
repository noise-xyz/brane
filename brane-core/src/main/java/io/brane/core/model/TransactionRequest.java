package io.brane.core.model;

import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import java.util.Optional;

public record TransactionRequest(
        Address from,
        Address to,
        Wei value,
        Long gasLimit,
        Wei gasPrice,
        Wei maxPriorityFeePerGas,
        Wei maxFeePerGas,
        Long nonce,
        HexData data) {

    public Optional<Address> toOpt() {
        return Optional.ofNullable(to);
    }

    public Optional<Wei> valueOpt() {
        return Optional.ofNullable(value);
    }

    public Optional<Long> gasLimitOpt() {
        return Optional.ofNullable(gasLimit);
    }

    public Optional<Wei> gasPriceOpt() {
        return Optional.ofNullable(gasPrice);
    }

    public Optional<Wei> maxPriorityFeePerGasOpt() {
        return Optional.ofNullable(maxPriorityFeePerGas);
    }

    public Optional<Wei> maxFeePerGasOpt() {
        return Optional.ofNullable(maxFeePerGas);
    }

    public Optional<Long> nonceOpt() {
        return Optional.ofNullable(nonce);
    }
}
