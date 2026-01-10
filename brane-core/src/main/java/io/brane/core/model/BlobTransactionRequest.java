package io.brane.core.model;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import io.brane.core.tx.Eip4844Transaction;
import io.brane.core.types.Address;
import io.brane.core.types.BlobSidecar;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

/**
 * Request to submit an EIP-4844 blob transaction to the blockchain.
 *
 * <p>
 * Blob transactions are used for data availability in rollups and other
 * Layer 2 solutions. They carry blob data in a sidecar that is not included
 * in the execution payload but is available for data availability verification.
 *
 * <p>
 * <strong>Required Fields:</strong>
 * <ul>
 * <li>{@code to} - the recipient address (EIP-4844 does not support contract creation)</li>
 * <li>{@code sidecar} - the blob sidecar containing blobs, commitments, and proofs</li>
 * </ul>
 *
 * <p>
 * <strong>Optional Fields:</strong>
 * <ul>
 * <li>{@code from} - may be null during construction but required for signing</li>
 * <li>{@code value} - amount of ether to send (defaults to zero)</li>
 * <li>{@code gasLimit} - estimated via {@code eth_estimateGas} if null</li>
 * <li>{@code maxPriorityFeePerGas} - fetched from network if null</li>
 * <li>{@code maxFeePerGas} - fetched from network if null</li>
 * <li>{@code maxFeePerBlobGas} - fetched from network if null</li>
 * <li>{@code nonce} - fetched from {@code eth_getTransactionCount} if null</li>
 * <li>{@code data} - transaction calldata (defaults to empty)</li>
 * <li>{@code accessList} - EIP-2930 access list (defaults to empty)</li>
 * </ul>
 *
 * @param from                 the address sending the transaction (may be null during
 *                             construction, but required for signing)
 * @param to                   the recipient address (required, cannot be null)
 * @param value                the amount of native currency to transfer, or null for zero
 * @param gasLimit             the maximum gas to use, or null to auto-estimate
 * @param maxPriorityFeePerGas the miner tip for EIP-1559 fee market, or null
 * @param maxFeePerGas         the maximum total fee per gas, or null
 * @param maxFeePerBlobGas     the maximum fee per blob gas unit, or null
 * @param nonce                the transaction nonce, or null to auto-fetch
 * @param data                 the transaction input data (calldata), or null
 * @param accessList           the EIP-2930 access list, or null/empty
 * @param sidecar              the blob sidecar (required, cannot be null)
 * @see Eip4844Transaction
 * @since 0.2.0
 */
public record BlobTransactionRequest(
        Address from,
        Address to,
        Wei value,
        Long gasLimit,
        Wei maxPriorityFeePerGas,
        Wei maxFeePerGas,
        Wei maxFeePerBlobGas,
        Long nonce,
        HexData data,
        List<AccessListEntry> accessList,
        BlobSidecar sidecar) {

    /**
     * Validates blob transaction request parameters.
     *
     * @throws NullPointerException     if to or sidecar is null
     * @throws IllegalArgumentException if gasLimit or nonce is negative
     */
    public BlobTransactionRequest {
        Objects.requireNonNull(to, "to address is required for EIP-4844 transactions");
        Objects.requireNonNull(sidecar, "sidecar is required for EIP-4844 transactions");

        if (gasLimit != null && gasLimit < 0) {
            throw new IllegalArgumentException("gasLimit cannot be negative: " + gasLimit);
        }
        if (nonce != null && nonce < 0) {
            throw new IllegalArgumentException("nonce cannot be negative: " + nonce);
        }
    }

    /**
     * Returns the blob versioned hashes from the sidecar.
     *
     * @return an unmodifiable list of versioned hashes
     */
    public List<Hash> blobVersionedHashes() {
        return sidecar.versionedHashes();
    }

    public Optional<Wei> valueOpt() {
        return Optional.ofNullable(value);
    }

    public Optional<Long> gasLimitOpt() {
        return Optional.ofNullable(gasLimit);
    }

    public Optional<Wei> maxPriorityFeePerGasOpt() {
        return Optional.ofNullable(maxPriorityFeePerGas);
    }

    public Optional<Wei> maxFeePerGasOpt() {
        return Optional.ofNullable(maxFeePerGas);
    }

    public Optional<Wei> maxFeePerBlobGasOpt() {
        return Optional.ofNullable(maxFeePerBlobGas);
    }

    public Optional<Long> nonceOpt() {
        return Optional.ofNullable(nonce);
    }

    public List<AccessListEntry> accessListOrEmpty() {
        return accessList == null ? List.of() : accessList;
    }

    /**
     * Converts this request into an unsigned EIP-4844 transaction ready for signing.
     *
     * <p>
     * All required fields must be populated (no nulls except where noted).
     * Use {@code Brane.Signer} to auto-fill missing fields before calling this method.
     *
     * @param chainId the chain ID for the transaction
     * @return unsigned EIP-4844 transaction
     * @throws IllegalStateException if required fields are null (from, nonce, gasLimit,
     *                               maxPriorityFeePerGas, maxFeePerGas, maxFeePerBlobGas)
     */
    public Eip4844Transaction toUnsignedTransaction(final long chainId) {
        if (from == null) {
            throw new IllegalStateException("from address is required for unsigned transaction");
        }
        if (nonce == null) {
            throw new IllegalStateException("nonce must be set");
        }
        if (gasLimit == null) {
            throw new IllegalStateException("gasLimit must be set");
        }
        if (maxPriorityFeePerGas == null) {
            throw new IllegalStateException("maxPriorityFeePerGas must be set for EIP-4844 transactions");
        }
        if (maxFeePerGas == null) {
            throw new IllegalStateException("maxFeePerGas must be set for EIP-4844 transactions");
        }
        if (maxFeePerBlobGas == null) {
            throw new IllegalStateException("maxFeePerBlobGas must be set for EIP-4844 transactions");
        }

        final Wei valueOrZero = value != null ? value : Wei.of(0);
        final HexData dataOrEmpty = data != null ? data : HexData.EMPTY;

        return new Eip4844Transaction(
                chainId,
                nonce,
                maxPriorityFeePerGas,
                maxFeePerGas,
                gasLimit,
                to,
                valueOrZero,
                dataOrEmpty,
                accessListOrEmpty(),
                maxFeePerBlobGas,
                blobVersionedHashes());
    }
}
