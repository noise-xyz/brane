package io.brane.core.model;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

import java.util.Objects;
import java.util.Optional;

/**
 * Represents a blockchain transaction.
 *
 * <p>Required fields (never null):
 * <ul>
 *   <li>{@code hash} - the transaction hash</li>
 *   <li>{@code from} - the sender address</li>
 *   <li>{@code input} - the input data (may be empty)</li>
 *   <li>{@code value} - the value transferred</li>
 *   <li>{@code nonce} - the transaction nonce</li>
 * </ul>
 *
 * <p>Optional fields:
 * <ul>
 *   <li>{@code to} - the recipient address (empty for contract creation)</li>
 *   <li>{@code blockNumber} - the block number (null if pending)</li>
 * </ul>
 *
 * @param hash        the transaction hash (required)
 * @param from        the sender address (required)
 * @param to          the recipient address (empty for contract creation)
 * @param input       the input data (required, may be empty)
 * @param value       the value transferred (required)
 * @param nonce       the transaction nonce (required)
 * @param blockNumber the block number containing this transaction (null if pending)
 */
public record Transaction(
        Hash hash,
        Address from,
        Optional<Address> to,
        HexData input,
        Wei value,
        Long nonce,
        Long blockNumber) {

    /**
     * Creates a new Transaction with validation.
     *
     * @throws NullPointerException if any required field is null
     */
    public Transaction {
        Objects.requireNonNull(hash, "hash is required");
        Objects.requireNonNull(from, "from is required");
        Objects.requireNonNull(input, "input is required");
        Objects.requireNonNull(value, "value is required");
        Objects.requireNonNull(nonce, "nonce is required");
        // to can be empty (contract creation)
        to = to != null ? to : Optional.empty();
        // blockNumber can be null (pending transaction)
    }
}
