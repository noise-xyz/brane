package io.brane.core.builder;

import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

public final class LegacyBuilder implements TxBuilder<LegacyBuilder> {
    private Address from;
    private Address to;
    private Wei value;
    private Long nonce;
    private Long gasLimit;
    private Wei gasPrice;
    private HexData data;

    @Override
    public LegacyBuilder from(final Address address) {
        this.from = address;
        return this;
    }

    @Override
    public LegacyBuilder to(final Address address) {
        this.to = address;
        return this;
    }

    @Override
    public LegacyBuilder value(final Wei value) {
        this.value = value;
        return this;
    }

    @Override
    public LegacyBuilder data(final HexData data) {
        this.data = data;
        return this;
    }

    @Override
    public LegacyBuilder nonce(final long nonce) {
        this.nonce = nonce;
        return this;
    }

    @Override
    public LegacyBuilder gasLimit(final long gasLimit) {
        this.gasLimit = gasLimit;
        return this;
    }

    public LegacyBuilder gasPrice(final Wei gasPrice) {
        this.gasPrice = gasPrice;
        return this;
    }

    @Override
    public TransactionRequest build() {
        validateTarget();

        return new TransactionRequest(
                from,
                to,
                value,
                gasLimit,
                gasPrice,
                null,
                null,
                nonce,
                data,
                false,
                null);
    }

    private void validateTarget() {
        if (to == null && data == null) {
            throw new BraneTxBuilderException("Transaction must have a recipient or data");
        }

        if (to == null && data != null && data.value().isBlank()) {
            throw new BraneTxBuilderException("Contract creation requires non-empty data");
        }
    }
}
