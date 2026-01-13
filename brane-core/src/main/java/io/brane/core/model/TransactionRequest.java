// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

/**
 * Request to submit a transaction to the blockchain.
 *
 * <p>
 * This type supports both legacy and EIP-1559 transaction types:
 * <ul>
 * <li><strong>Legacy transactions</strong> ({@code isEip1559 = false}): Use
 * {@code gasPrice}</li>
 * <li><strong>EIP-1559 transactions</strong> ({@code isEip1559 = true}): Use
 * {@code maxFeePerGas} and {@code maxPriorityFeePerGas}</li>
 * </ul>
 *
 * <p>
 * <strong>Optional vs Required Fields:</strong>
 * <ul>
 * <li><strong>Required for signing:</strong> {@code from} (sender address) - may be null during
 * construction but must be non-null when calling {@link #toUnsignedTransaction(long)}</li>
 * <li><strong>Optional:</strong> All other fields can be null and will be
 * auto-filled by {@code Brane.Signer}:
 * <ul>
 * <li>{@code gasLimit} - estimated via {@code eth_estimateGas} if null</li>
 * <li>{@code nonce} - fetched from {@code eth_getTransactionCount} if null</li>
 * <li>{@code gasPrice}/{@code maxFeePerGas} - fetched from network if null</li>
 * </ul>
 * </li>
 * </ul>
 *
 * <p>
 * <strong>Contract Interaction:</strong>
 * <ul>
 * <li><strong>Contract call:</strong> Set {@code to} to contract address,
 * {@code data} to encoded function call</li>
 * <li><strong>Contract deployment:</strong> Set {@code to} to null,
 * {@code data} to bytecode + constructor args</li>
 * <li><strong>Simple transfer:</strong> Set {@code to} to recipient,
 * {@code value} to amount, {@code data} can be null/empty</li>
 * </ul>
 *
 * @param from                 the address sending the transaction (may be null during
 *                             construction, but required for signing)
 * @param to                   the recipient address, or null for contract
 *                             deployment
 * @param value                the amount of native currency to transfer, or
 *                             null for zero
 * @param gasLimit             the maximum gas to use, or null to auto-estimate
 * @param gasPrice             the gas price for legacy transactions, or null
 * @param maxPriorityFeePerGas the miner tip for EIP-1559 transactions, or null
 * @param maxFeePerGas         the maximum total fee for EIP-1559 transactions,
 *                             or null
 * @param nonce                the transaction nonce, or null to auto-fetch
 * @param data                 the transaction input data (calldata), or null
 *                             for simple transfers
 * @param isEip1559            {@code true} for EIP-1559 transactions,
 *                             {@code false} for legacy
 * @param accessList           the EIP-2930 access list, or null/empty
 * @since 0.1.0-alpha
 */
public record TransactionRequest(
        Address from,
        Address to,
        Wei value,
        Long gasLimit,
        Wei gasPrice,
        Wei maxPriorityFeePerGas,
        Wei maxFeePerGas,
        Long nonce,
        HexData data,
        boolean isEip1559,
        List<AccessListEntry> accessList) {

    /**
     * Validates transaction request parameters.
     *
     * @throws IllegalArgumentException if gasLimit is negative, or if both legacy
     *         (gasPrice) and EIP-1559 (maxFeePerGas/maxPriorityFeePerGas) fee fields are set
     */
    public TransactionRequest {
        if (gasLimit != null && gasLimit < 0) {
            throw new IllegalArgumentException("gasLimit cannot be negative: " + gasLimit);
        }
        if (nonce != null && nonce < 0) {
            throw new IllegalArgumentException("nonce cannot be negative: " + nonce);
        }
        // Validate mutually exclusive fee fields
        boolean hasLegacyGas = gasPrice != null;
        boolean hasEip1559Gas = maxFeePerGas != null || maxPriorityFeePerGas != null;
        if (hasLegacyGas && hasEip1559Gas) {
            throw new IllegalArgumentException(
                    "Cannot specify both gasPrice and EIP-1559 fee fields (maxFeePerGas/maxPriorityFeePerGas)");
        }
    }

    public Optional<Address> toOpt() {
        return Optional.ofNullable(to);
    }

    public Optional<Wei> valueOpt() {
        return Optional.ofNullable(value);
    }

    public Optional<Long> gasLimitOpt() {
        return Optional.ofNullable(gasLimit);
    }

    public Optional<Wei> gasPriceOpt() {
        return Optional.ofNullable(gasPrice);
    }

    public Optional<Wei> maxPriorityFeePerGasOpt() {
        return Optional.ofNullable(maxPriorityFeePerGas);
    }

    public Optional<Wei> maxFeePerGasOpt() {
        return Optional.ofNullable(maxFeePerGas);
    }

    public Optional<Long> nonceOpt() {
        return Optional.ofNullable(nonce);
    }

    public List<AccessListEntry> accessListOrEmpty() {
        return Objects.requireNonNullElse(accessList, List.of());
    }

    /**
     * Converts this request into an unsigned transaction ready for signing.
     *
     * <p>
     * All fields must be populated (no nulls except {@code to} for contract
     * creation).
     * Use {@code Brane.Signer} to auto-fill missing fields before calling this
     * method.
     *
     * @param chainId the chain ID for the transaction
     * @return unsigned transaction (Legacy or EIP-1559 depending on
     *         {@code isEip1559})
     * @throws IllegalStateException if required fields are null (from, nonce, gasLimit,
     *         and gas fee fields depending on transaction type)
     */
    public io.brane.core.tx.UnsignedTransaction toUnsignedTransaction(final long chainId) {
        if (from == null) {
            throw new IllegalStateException("from address is required for unsigned transaction");
        }
        if (nonce == null) {
            throw new IllegalStateException("nonce must be set");
        }
        if (gasLimit == null) {
            throw new IllegalStateException("gasLimit must be set");
        }

        final Wei valueOrZero = Objects.requireNonNullElse(value, Wei.ZERO);
        final HexData dataOrEmpty = Objects.requireNonNullElse(data, HexData.EMPTY);

        if (isEip1559) {
            if (maxPriorityFeePerGas == null || maxFeePerGas == null) {
                throw new IllegalStateException(
                        "maxPriorityFeePerGas and maxFeePerGas must be set for EIP-1559 transactions");
            }
            return new io.brane.core.tx.Eip1559Transaction(
                    chainId,
                    nonce,
                    maxPriorityFeePerGas,
                    maxFeePerGas,
                    gasLimit,
                    to,
                    valueOrZero,
                    dataOrEmpty,
                    accessListOrEmpty());
        } else {
            if (gasPrice == null) {
                throw new IllegalStateException("gasPrice must be set for legacy transactions");
            }
            return new io.brane.core.tx.LegacyTransaction(
                    nonce,
                    gasPrice,
                    gasLimit,
                    to,
                    valueOrZero,
                    dataOrEmpty);
        }
    }
}
