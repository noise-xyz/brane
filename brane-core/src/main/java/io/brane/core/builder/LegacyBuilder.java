package io.brane.core.builder;

import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

/**
 * Builder for legacy (pre-EIP-1559) transactions with a single gas price.
 *
 * <p>Legacy transactions use a single {@code gasPrice} field instead of
 * the EIP-1559 {@code maxFeePerGas} and {@code maxPriorityFeePerGas} fields.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * TransactionRequest tx = TxBuilder.legacy()
 *     .from(sender)
 *     .to(recipient)
 *     .value(Wei.ether("1"))
 *     .gasPrice(Wei.gwei(50))
 *     .gasLimit(21000)
 *     .build();
 * }</pre>
 *
 * <p><strong>Thread Safety:</strong> This builder is <em>not</em> thread-safe. Each thread
 * should use its own builder instance. The {@link #build()} method creates an immutable
 * {@link TransactionRequest} that is safe to share across threads.
 *
 * @see TxBuilder#legacy()
 * @since 0.1.0-alpha
 */
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

    /**
     * Sets the gas price for the transaction.
     *
     * <p>This is the price per gas unit that the sender is willing to pay.
     * Higher gas prices increase the likelihood of faster transaction inclusion.
     *
     * @param gasPrice the gas price in wei
     * @return this builder for chaining
     */
    public LegacyBuilder gasPrice(final Wei gasPrice) {
        this.gasPrice = gasPrice;
        return this;
    }

    @Override
    public TransactionRequest build() {
        BuilderValidation.validateTarget(to, data);

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
}
