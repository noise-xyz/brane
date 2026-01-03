package io.brane.core.model;

import java.util.Objects;
import java.util.Optional;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

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
 * <p>Optional fields (use {@code *Opt()} accessors):
 * <ul>
 *   <li>{@code to} - the recipient address (null for contract creation)</li>
 *   <li>{@code blockNumber} - the block number (null if pending)</li>
 * </ul>
 *
 * <h2>API Design Note</h2>
 * <p>Optional fields use nullable types with explicit {@code *Opt()} accessor methods
 * (e.g., {@link #toOpt()}, {@link #blockNumberOpt()}) rather than {@code Optional<T>}
 * record components. This design:
 * <ul>
 *   <li>Keeps record components simple and serialization-friendly</li>
 *   <li>Provides explicit opt-in for Optional handling when needed</li>
 *   <li>Allows null checks with standard {@code == null} patterns</li>
 * </ul>
 *
 * @param hash        the transaction hash (required)
 * @param from        the sender address (required)
 * @param to          the recipient address (null for contract creation)
 * @param input       the input data (required, may be empty)
 * @param value       the value transferred (required)
 * @param nonce       the transaction nonce (required)
 * @param blockNumber the block number containing this transaction (null if pending)
 * @since 0.1.0-alpha
 */
public record Transaction(
        Hash hash,
        Address from,
        Address to,
        HexData input,
        Wei value,
        long nonce,
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
        // nonce uses primitive long, so no null check needed
        // to can be null (contract creation)
        // blockNumber can be null (pending transaction)
    }

    /**
     * Returns the recipient address as an Optional.
     *
     * @return Optional containing the recipient, or empty for contract creation
     */
    public Optional<Address> toOpt() {
        return Optional.ofNullable(to);
    }

    /**
     * Returns the block number as an Optional.
     *
     * @return Optional containing the block number, or empty if pending
     */
    public Optional<Long> blockNumberOpt() {
        return Optional.ofNullable(blockNumber);
    }
}
