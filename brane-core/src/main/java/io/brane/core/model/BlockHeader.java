// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.model;

import java.util.Objects;

import io.brane.core.types.Hash;
import io.brane.core.types.Wei;

/**
 * Represents a block header in the blockchain.
 *
 * <p>
 * Block headers contain essential metadata about a block including its position
 * in the chain (number, parent hash) and timing information (timestamp).
 *
 * <p>
 * <strong>Note on baseFeePerGas:</strong> This field is {@code null} for blocks
 * before the London hard fork (EIP-1559), which occurred at block 12,965,000 on
 * Ethereum mainnet. For post-London blocks, this field contains the base fee that
 * determines the minimum gas price for transactions to be included.
 *
 * @param hash          the block hash (required)
 * @param number        the block number (required)
 * @param parentHash    the hash of the parent block (required)
 * @param timestamp     the block timestamp in seconds since Unix epoch (required)
 * @param baseFeePerGas the base fee per gas (EIP-1559), {@code null} for pre-London blocks
 * @since 0.1.0-alpha
 */
public record BlockHeader(Hash hash, long number, Hash parentHash, long timestamp, Wei baseFeePerGas) {

    /**
     * Validates required fields.
     *
     * @throws NullPointerException if hash or parentHash is null
     */
    public BlockHeader {
        Objects.requireNonNull(hash, "hash cannot be null");
        Objects.requireNonNull(parentHash, "parentHash cannot be null");
        // baseFeePerGas can be null for pre-London blocks
    }
}
