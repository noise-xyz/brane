package io.brane.core.model;

import io.brane.core.types.Hash;
import io.brane.core.types.Wei;

/**
 * Represents a block header in the blockchain.
 *
 * @param hash the block hash
 * @param number the block number
 * @param parentHash the hash of the parent block
 * @param timestamp the block timestamp
 * @param baseFeePerGas the base fee per gas (EIP-1559)
 */
public record BlockHeader(Hash hash, Long number, Hash parentHash, Long timestamp, Wei baseFeePerGas) {}
