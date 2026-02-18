// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.tx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import sh.brane.core.crypto.Signature;
import sh.brane.core.model.AccessListEntry;
import sh.brane.core.types.Address;
import sh.brane.core.types.Blob;
import sh.brane.core.types.BlobSidecar;
import sh.brane.core.types.Hash;
import sh.brane.core.types.HexData;
import sh.brane.core.types.KzgCommitment;
import sh.brane.core.types.KzgProof;
import sh.brane.core.types.Wei;
import sh.brane.primitives.rlp.Rlp;
import sh.brane.primitives.rlp.RlpItem;
import sh.brane.primitives.rlp.RlpList;
import sh.brane.primitives.rlp.RlpNumeric;
import sh.brane.primitives.rlp.RlpString;

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

    /**
     * Validates the transaction parameters.
     *
     * @throws IllegalArgumentException if chainId, nonce, or gasLimit is invalid
     * @throws NullPointerException     if any required field is null
     */
    public Eip4844Transaction {
        if (chainId <= 0) {
            throw new IllegalArgumentException("Chain ID must be positive");
        }
        if (nonce < 0) {
            throw new IllegalArgumentException("Nonce cannot be negative");
        }
        Objects.requireNonNull(maxPriorityFeePerGas, "maxPriorityFeePerGas");
        Objects.requireNonNull(maxFeePerGas, "maxFeePerGas");
        if (gasLimit <= 0) {
            throw new IllegalArgumentException("gasLimit must be positive");
        }
        // EIP-4844 transactions cannot create contracts, to address is required
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(data, "data");
        Objects.requireNonNull(maxFeePerBlobGas, "maxFeePerBlobGas");
        Objects.requireNonNull(blobVersionedHashes, "blobVersionedHashes");

        if (blobVersionedHashes.size() < MIN_BLOB_HASHES || blobVersionedHashes.size() > MAX_BLOB_HASHES) {
            throw new IllegalArgumentException(
                    "blobVersionedHashes must contain " + MIN_BLOB_HASHES + "-" + MAX_BLOB_HASHES
                            + " hashes, got " + blobVersionedHashes.size());
        }

        // Validate no null hashes
        for (int i = 0; i < blobVersionedHashes.size(); i++) {
            if (blobVersionedHashes.get(i) == null) {
                throw new NullPointerException("blobVersionedHashes[" + i + "] is null");
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
     * Encodes the transaction with blob sidecar for network transmission.
     * <p>
     * Network wrapper format:
     * <pre>
     * 0x03 || RLP([[signed tx fields], blobs, commitments, proofs])
     * </pre>
     * <p>
     * The signed transaction fields are wrapped in an inner list, followed by
     * the blob sidecar arrays at the outer level.
     *
     * @param signature the transaction signature
     * @param sidecar   the blob sidecar containing blobs, commitments, and proofs
     * @return the network wrapper encoded bytes
     * @throws NullPointerException     if signature or sidecar is null
     * @throws IllegalArgumentException if sidecar versioned hashes don't match
     *                                  this transaction's blobVersionedHashes
     */
    public byte[] encodeAsNetworkWrapper(final Signature signature, final BlobSidecar sidecar) {
        Objects.requireNonNull(signature, "signature cannot be null");
        Objects.requireNonNull(sidecar, "sidecar cannot be null");

        // Validate that sidecar hashes match transaction's blobVersionedHashes
        sidecar.validateHashes(blobVersionedHashes);

        final byte[] rlpPayload = encodeNetworkWrapperPayload(signature, sidecar);

        // Prepend type byte
        final byte[] result = new byte[rlpPayload.length + 1];
        result[0] = EIP4844_TYPE;
        System.arraycopy(rlpPayload, 0, result, 1, rlpPayload.length);

        return result;
    }

    /**
     * Encodes the network wrapper RLP payload (without type byte prefix).
     * <p>
     * Format: RLP([[signed tx fields], blobs, commitments, proofs])
     *
     * @param signature the transaction signature
     * @param sidecar   the blob sidecar
     * @return RLP-encoded network wrapper payload
     */
    private byte[] encodeNetworkWrapperPayload(final Signature signature, final BlobSidecar sidecar) {
        // Build the signed transaction fields list (inner list)
        final List<RlpItem> signedTxItems = new ArrayList<>(14);

        signedTxItems.add(RlpNumeric.encodeLongUnsignedItem(chainId));
        signedTxItems.add(RlpNumeric.encodeLongUnsignedItem(nonce));
        signedTxItems.add(RlpNumeric.encodeBigIntegerUnsignedItem(maxPriorityFeePerGas.value()));
        signedTxItems.add(RlpNumeric.encodeBigIntegerUnsignedItem(maxFeePerGas.value()));
        signedTxItems.add(RlpNumeric.encodeLongUnsignedItem(gasLimit));
        signedTxItems.add(new RlpString(to.toBytes()));
        signedTxItems.add(RlpNumeric.encodeBigIntegerUnsignedItem(value.value()));
        signedTxItems.add(new RlpString(data.toBytes()));
        signedTxItems.add(encodeAccessList());
        signedTxItems.add(RlpNumeric.encodeBigIntegerUnsignedItem(maxFeePerBlobGas.value()));
        signedTxItems.add(encodeBlobVersionedHashes());

        // Add signature (yParity must be 0 or 1)
        final int yParity = signature.v();
        if (yParity != 0 && yParity != 1) {
            throw new IllegalArgumentException(
                    "EIP-4844 signature v must be yParity (0 or 1), got: " + yParity);
        }
        signedTxItems.add(RlpNumeric.encodeLongUnsignedItem(yParity));
        signedTxItems.add(RlpNumeric.encodeBigIntegerUnsignedItem(signature.rAsBigInteger()));
        signedTxItems.add(RlpNumeric.encodeBigIntegerUnsignedItem(signature.sAsBigInteger()));

        // Build outer list: [signedTxList, blobs, commitments, proofs]
        final List<RlpItem> outerItems = new ArrayList<>(4);
        outerItems.add(new RlpList(signedTxItems));
        outerItems.add(encodeBlobList(sidecar.blobs()));
        outerItems.add(encodeCommitmentList(sidecar.commitments()));
        outerItems.add(encodeProofList(sidecar.proofs()));

        return Rlp.encodeList(outerItems);
    }

    /**
     * Encodes the blob list as RLP.
     *
     * @param blobs the blobs to encode
     * @return RLP list of blob bytes
     */
    private static RlpItem encodeBlobList(final List<Blob> blobs) {
        return encodeBytesList(blobs, Blob::toBytes);
    }

    /**
     * Encodes the commitment list as RLP.
     *
     * @param commitments the commitments to encode
     * @return RLP list of commitment bytes
     */
    private static RlpItem encodeCommitmentList(final List<KzgCommitment> commitments) {
        return encodeBytesList(commitments, KzgCommitment::toBytes);
    }

    /**
     * Encodes the proof list as RLP.
     *
     * @param proofs the proofs to encode
     * @return RLP list of proof bytes
     */
    private static RlpItem encodeProofList(final List<KzgProof> proofs) {
        return encodeBytesList(proofs, KzgProof::toBytes);
    }

    /**
     * Generic helper to encode a list of items as RLP bytes list.
     *
     * @param items     the items to encode
     * @param extractor function to extract bytes from each item
     * @param <T>       the item type
     * @return RLP list of bytes
     */
    private static <T> RlpItem encodeBytesList(List<T> items, Function<T, byte[]> extractor) {
        List<RlpItem> rlpItems = new ArrayList<>(items.size());
        for (T item : items) {
            rlpItems.add(new RlpString(extractor.apply(item)));
        }
        return new RlpList(rlpItems);
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
            items.add(RlpNumeric.encodeBigIntegerUnsignedItem(signature.rAsBigInteger()));
            items.add(RlpNumeric.encodeBigIntegerUnsignedItem(signature.sAsBigInteger()));
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
                storageKeys.add(new RlpString(key.toBytes()));
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
        List<RlpItem> items = new ArrayList<>(blobVersionedHashes.size());
        for (var hash : blobVersionedHashes) {
            items.add(new RlpString(hash.toBytes()));
        }
        return new RlpList(items);
    }
}
