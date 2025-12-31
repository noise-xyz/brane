package io.brane.core.builder;

import io.brane.core.model.AccessListEntry;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import java.util.List;

/**
 * Builder for EIP-1559 transactions with dynamic fee market.
 *
 * <p>EIP-1559 transactions use separate {@code maxFeePerGas} and {@code maxPriorityFeePerGas}
 * fields instead of a single {@code gasPrice}. This enables more predictable gas pricing
 * and allows users to set a tip for miners/validators.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * TransactionRequest tx = TxBuilder.eip1559()
 *     .from(sender)
 *     .to(recipient)
 *     .value(Wei.ether("1"))
 *     .maxFeePerGas(Wei.gwei(100))
 *     .maxPriorityFeePerGas(Wei.gwei(2))
 *     .gasLimit(21000)
 *     .build();
 * }</pre>
 *
 * @see TxBuilder#eip1559()
 */
public final class Eip1559Builder implements TxBuilder<Eip1559Builder> {
    private Address from;
    private Address to;
    private Wei value;
    private Long nonce;
    private Long gasLimit;
    private Wei maxFeePerGas;
    private Wei maxPriorityFeePerGas;
    private HexData data;
    private List<AccessListEntry> accessList;

    @Override
    public Eip1559Builder from(final Address address) {
        this.from = address;
        return this;
    }

    @Override
    public Eip1559Builder to(final Address address) {
        this.to = address;
        return this;
    }

    @Override
    public Eip1559Builder value(final Wei value) {
        this.value = value;
        return this;
    }

    @Override
    public Eip1559Builder data(final HexData data) {
        this.data = data;
        return this;
    }

    @Override
    public Eip1559Builder nonce(final long nonce) {
        this.nonce = nonce;
        return this;
    }

    @Override
    public Eip1559Builder gasLimit(final long gasLimit) {
        this.gasLimit = gasLimit;
        return this;
    }

    /**
     * Sets the maximum total fee per gas unit.
     *
     * <p>This is the absolute maximum the sender is willing to pay per gas unit,
     * including both the base fee and priority fee.
     *
     * @param maxFeePerGas the maximum fee per gas
     * @return this builder for chaining
     */
    public Eip1559Builder maxFeePerGas(final Wei maxFeePerGas) {
        this.maxFeePerGas = maxFeePerGas;
        return this;
    }

    /**
     * Sets the maximum priority fee (tip) per gas unit.
     *
     * <p>This is the tip paid directly to miners/validators to incentivize
     * inclusion of the transaction.
     *
     * @param maxPriorityFeePerGas the maximum priority fee per gas
     * @return this builder for chaining
     */
    public Eip1559Builder maxPriorityFeePerGas(final Wei maxPriorityFeePerGas) {
        this.maxPriorityFeePerGas = maxPriorityFeePerGas;
        return this;
    }

    /**
     * Sets the EIP-2930 access list for gas optimization.
     *
     * <p>Access lists pre-declare which accounts and storage slots will be accessed,
     * reducing gas costs for those accesses.
     *
     * @param accessList the list of access list entries, or null for no access list
     * @return this builder for chaining
     */
    public Eip1559Builder accessList(final List<AccessListEntry> accessList) {
        this.accessList = accessList == null ? null : List.copyOf(accessList);
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
                null,
                maxPriorityFeePerGas,
                maxFeePerGas,
                nonce,
                data,
                true,
                accessList);
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
