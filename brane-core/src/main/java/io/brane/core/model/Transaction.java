package io.brane.core.model;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

public record Transaction(
        Hash hash,
        Address from,
        Address to,
        HexData input,
        Wei value,
        Long nonce,
        Long blockNumber) {}
