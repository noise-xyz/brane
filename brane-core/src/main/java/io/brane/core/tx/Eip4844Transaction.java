package io.brane.core.tx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.brane.core.crypto.Signature;
import io.brane.core.model.AccessListEntry;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.primitives.Hex;
import io.brane.primitives.rlp.Rlp;
import io.brane.primitives.rlp.RlpItem;
import io.brane.primitives.rlp.RlpList;
import io.brane.primitives.rlp.RlpNumeric;
import io.brane.primitives.rlp.RlpString;

/**
 * EIP-4844 blob transaction for data availability.
 *
 * <p>
 * This transaction type introduces blob-carrying transactions for rollups and
 * other data availability use cases. It extends the EIP-1559 fee market with
 * additional fields for blob gas pricing and blob versioned hashes.
 *
 * <p>
 * EIP-4844 transactions are encoded using EIP-2718 typed transaction envelopes
 * with type byte {@code 0x03}.
 *
 * <h2>Key Differences from EIP-1559</h2>
 * <ul>
 * <li>Must have a non-null {@code to} address (no contract creation)</li>
 * <li>Includes {@code maxFeePerBlobGas} for blob data pricing</li>
 * <li>Includes {@code blobVersionedHashes} (1-6 hashes)</li>
 * </ul>
 *
 * <h2>EIP-2718/4844 Encoding</h2>
 * <p>
 * Signing preimage:
 *
 * <pre>
 * 0x03 || RLP([chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, to, value, data, accessList, maxFeePerBlobGas, blobVersionedHashes])
 * </pre>
 *
 * <p>
 * Signed envelope:
 *
 * <pre>
 * 0x03 || RLP([chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, to, value, data, accessList, maxFeePerBlobGas, blobVersionedHashes, yParity, r, s])
 * </pre>
 *
 * @param chainId              the chain ID
 * @param nonce                the transaction nonce
 * @param maxPriorityFeePerGas the maximum priority fee (miner tip)
 * @param maxFeePerGas         the maximum total fee per gas
 * @param gasLimit             the maximum gas to use
 * @param to                   the recipient address (required, cannot be null)
 * @param value                the amount of ether to send
 * @param data                 the transaction data (calldata)
 * @param accessList           the EIP-2930 access list
 * @param maxFeePerBlobGas     the maximum fee per blob gas unit
 * @param blobVersionedHashes  the versioned hashes of the blobs (1-6 hashes)
 * @since 0.2.0
 */
public record Eip4844Transaction(
        long chainId,
        long nonce,
        Wei maxPriorityFeePerGas,
        Wei maxFeePerGas,
        long gasLimit,
        Address to,
        Wei value,
        HexData data,
        List<AccessListEntry> accessList,
        Wei maxFeePerBlobGas,
        List<Hash> blobVersionedHashes) implements UnsignedTransaction {

    private static final byte EIP4844_TYPE = (byte) 0x03;

    /**
     * Minimum number of blob versioned hashes required.
     */
    private static final int MIN_BLOB_HASHES = 1;

    /**
     * Maximum number of blob versioned hashes allowed per transaction.
     */
    private static final int MAX_BLOB_HASHES = 6;

    public Eip4844Transaction {
        if (chainId <= 0) {
            throw new IllegalArgumentException("Chain ID must be positive");
        }
        if (nonce < 0) {
            throw new IllegalArgumentException("Nonce cannot be negative");
        }
        Objects.requireNonNull(maxPriorityFeePerGas, "maxPriorityFeePerGas cannot be null");
        Objects.requireNonNull(maxFeePerGas, "maxFeePerGas cannot be null");
        if (gasLimit <= 0) {
            throw new IllegalArgumentException("gasLimit must be positive");
        }
        // EIP-4844 transactions cannot create contracts, to address is required
        Objects.requireNonNull(to, "to address is required for EIP-4844 transactions");
        Objects.requireNonNull(value, "value cannot be null");
        Objects.requireNonNull(data, "data cannot be null");
        Objects.requireNonNull(maxFeePerBlobGas, "maxFeePerBlobGas cannot be null");
        Objects.requireNonNull(blobVersionedHashes, "blobVersionedHashes cannot be null");

        if (blobVersionedHashes.size() < MIN_BLOB_HASHES || blobVersionedHashes.size() > MAX_BLOB_HASHES) {
            throw new IllegalArgumentException(
                    "blobVersionedHashes must contain " + MIN_BLOB_HASHES + "-" + MAX_BLOB_HASHES
                            + " hashes, got " + blobVersionedHashes.size());
        }

        // Validate no null hashes
        for (int i = 0; i < blobVersionedHashes.size(); i++) {
            if (blobVersionedHashes.get(i) == null) {
                throw new IllegalArgumentException("blobVersionedHashes[" + i + "] cannot be null");
            }
        }

        // Make defensive copies
        accessList = accessList != null ? List.copyOf(accessList) : List.of();
        blobVersionedHashes = List.copyOf(blobVersionedHashes);
    }

    @Override
    public byte[] encodeForSigning(final long chainId) {
        if (chainId != this.chainId) {
            throw new IllegalArgumentException(
                    "chainId parameter (" + chainId + ") must match transaction chainId (" + this.chainId + ")");
        }
        final byte[] rlpPayload = encodePayload(false, null);

        // Prepend type byte
        final byte[] result = new byte[rlpPayload.length + 1];
        result[0] = EIP4844_TYPE;
        System.arraycopy(rlpPayload, 0, result, 1, rlpPayload.length);

        return result;
    }

    @Override
    public byte[] encodeAsEnvelope(final Signature signature) {
        final byte[] rlpPayload = encodePayload(true, signature);

        // Prepend type byte
        final byte[] result = new byte[rlpPayload.length + 1];
        result[0] = EIP4844_TYPE;
        System.arraycopy(rlpPayload, 0, result, 1, rlpPayload.length);

        return result;
    }

    /**
     * Encodes the RLP payload (without type byte prefix).
     *
     * @param includeSig whether to include signature fields
     * @param signature  the signature (required if includeSig is true)
     * @return RLP-encoded payload
     */
    private byte[] encodePayload(final boolean includeSig, final Signature signature) {
        final List<RlpItem> items = new ArrayList<>(14);

        items.add(RlpNumeric.encodeLongUnsignedItem(chainId));
        items.add(RlpNumeric.encodeLongUnsignedItem(nonce));
        items.add(RlpNumeric.encodeBigIntegerUnsignedItem(maxPriorityFeePerGas.value()));
        items.add(RlpNumeric.encodeBigIntegerUnsignedItem(maxFeePerGas.value()));
        items.add(RlpNumeric.encodeLongUnsignedItem(gasLimit));
        items.add(new RlpString(to.toBytes()));
        items.add(RlpNumeric.encodeBigIntegerUnsignedItem(value.value()));
        items.add(new RlpString(data.toBytes()));

        // Encode access list
        items.add(encodeAccessList());

        // EIP-4844 specific fields
        items.add(RlpNumeric.encodeBigIntegerUnsignedItem(maxFeePerBlobGas.value()));
        items.add(encodeBlobVersionedHashes());

        if (includeSig) {
            Objects.requireNonNull(signature, "signature is required");
            // For EIP-4844, v is just yParity (0 or 1), not EIP-155 encoded
            final int yParity = signature.v();
            if (yParity != 0 && yParity != 1) {
                throw new IllegalArgumentException(
                        "EIP-4844 signature v must be yParity (0 or 1), got: " + yParity);
            }
            items.add(RlpNumeric.encodeLongUnsignedItem(yParity));
            items.add(RlpNumeric.encodeBigIntegerUnsignedItem(new java.math.BigInteger(1, signature.r())));
            items.add(RlpNumeric.encodeBigIntegerUnsignedItem(new java.math.BigInteger(1, signature.s())));
        }

        return Rlp.encodeList(items);
    }

    /**
     * Encodes the access list as RLP.
     *
     * @return RLP list of access list entries
     */
    private RlpItem encodeAccessList() {
        if (accessList.isEmpty()) {
            return new RlpList(List.of());
        }

        final List<RlpItem> entries = new ArrayList<>(accessList.size());
        for (AccessListEntry entry : accessList) {
            final List<RlpItem> entryItems = new ArrayList<>(2);
            entryItems.add(new RlpString(entry.address().toBytes()));

            // Encode storage keys
            final List<RlpItem> storageKeys = new ArrayList<>(entry.storageKeys().size());
            for (Hash key : entry.storageKeys()) {
                // Storage keys are 32-byte hashes
                final byte[] keyBytes = Hex.decode(key.value());
                storageKeys.add(new RlpString(keyBytes));
            }
            entryItems.add(new RlpList(storageKeys));

            entries.add(new RlpList(entryItems));
        }

        return new RlpList(entries);
    }

    /**
     * Encodes the blob versioned hashes as RLP.
     *
     * @return RLP list of versioned hashes
     */
    private RlpItem encodeBlobVersionedHashes() {
        final List<RlpItem> hashes = new ArrayList<>(blobVersionedHashes.size());
        for (Hash hash : blobVersionedHashes) {
            hashes.add(new RlpString(hash.toBytes()));
        }
        return new RlpList(hashes);
    }
}
