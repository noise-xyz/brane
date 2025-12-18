package io.brane.core.model;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

import java.util.Optional; 

/**
 * Represents a blockchain transaction.
 *
 * @param hash the transaction hash
 * @param from the sender address
 * @param to the recipient address (null for contract creation)
 * @param input the input data
 * @param value the value transferred
 * @param nonce the transaction nonce
 * @param blockNumber the block number containing this transaction
 */
public record Transaction(
        Hash hash,
        Address from,
        Optional<Address> to,
        HexData input,
        Wei value,
        Long nonce,
        Long blockNumber) {}
