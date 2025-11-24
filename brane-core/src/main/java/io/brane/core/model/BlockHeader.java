package io.brane.core.model;

import io.brane.core.types.Hash;
import io.brane.core.types.Wei;

public record BlockHeader(Hash hash, Long number, Hash parentHash, Long timestamp, Wei baseFeePerGas) {}
