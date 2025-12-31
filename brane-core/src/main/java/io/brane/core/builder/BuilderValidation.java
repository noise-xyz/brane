package io.brane.core.builder;

import io.brane.core.types.Address;
import io.brane.core.types.HexData;

/**
 * Shared validation utilities for transaction builders.
 */
final class BuilderValidation {

    private BuilderValidation() {
        // Utility class
    }

    /**
     * Validates the target address and data combination.
     *
     * <p>A transaction must either have a recipient address or data for contract creation.
     * Contract creation (to == null) requires non-empty bytecode.
     *
     * @param to   the recipient address, or null for contract creation
     * @param data the transaction data
     * @throws BraneTxBuilderException if the combination is invalid
     */
    static void validateTarget(Address to, HexData data) {
        if (to == null && data == null) {
            throw new BraneTxBuilderException("Transaction must have a recipient or data");
        }
        if (to == null && data != null && data.value().isBlank()) {
            throw new BraneTxBuilderException("Contract creation requires non-empty data");
        }
    }
}
