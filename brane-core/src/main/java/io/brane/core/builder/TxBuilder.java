package io.brane.core.builder;

import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

/**
 * Fluent builder for constructing Ethereum transactions.
 *
 * <p>This sealed interface supports two transaction types:
 * <ul>
 *   <li>{@link LegacyBuilder} - Pre-EIP-1559 transactions with gasPrice</li>
 *   <li>{@link Eip1559Builder} - EIP-1559 transactions with dynamic fees</li>
 * </ul>
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * TransactionRequest tx = TxBuilder.eip1559()
 *     .from(sender)
 *     .to(recipient)
 *     .value(Wei.ether("1"))
 *     .maxFeePerGas(Wei.gwei(100))
 *     .maxPriorityFeePerGas(Wei.gwei(2))
 *     .build();
 * }</pre>
 *
 * @param <T> the concrete builder type for fluent chaining
 */
public sealed interface TxBuilder<T extends TxBuilder<T>> permits LegacyBuilder, Eip1559Builder {

    /**
     * Sets the sender address.
     *
     * @param address the sender address
     * @return this builder for chaining
     */
    T from(Address address);

    /**
     * Sets the recipient address. Null for contract deployment.
     *
     * @param address the recipient address, or null for contract creation
     * @return this builder for chaining
     */
    T to(Address address);

    /**
     * Sets the value to transfer.
     *
     * @param value the amount of native currency to transfer
     * @return this builder for chaining
     */
    T value(Wei value);

    /**
     * Sets the transaction input data (calldata).
     *
     * @param data the encoded function call or contract bytecode
     * @return this builder for chaining
     */
    T data(HexData data);

    /**
     * Sets the transaction nonce.
     *
     * @param nonce the sender's transaction count
     * @return this builder for chaining
     */
    T nonce(long nonce);

    /**
     * Sets the gas limit.
     *
     * @param gasLimit the maximum gas to use
     * @return this builder for chaining
     */
    T gasLimit(long gasLimit);

    /**
     * Builds the transaction request.
     *
     * @return the constructed transaction request
     */
    TransactionRequest build();

    /**
     * Creates a new EIP-1559 transaction builder.
     *
     * @return a new EIP-1559 builder instance
     */
    static Eip1559Builder eip1559() {
        return new Eip1559Builder();
    }

    /**
     * Creates a new legacy transaction builder.
     *
     * @return a new legacy builder instance
     */
    static LegacyBuilder legacy() {
        return new LegacyBuilder();
    }
}
