package io.brane.core.model;

import io.brane.core.types.Hash;

public record BlockHeader(Hash hash, Long number, Hash parentHash, Long timestamp) {}
