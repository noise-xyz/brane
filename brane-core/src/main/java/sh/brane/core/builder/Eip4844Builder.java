// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.builder;

import java.util.List;

import sh.brane.core.model.AccessListEntry;
import sh.brane.core.model.BlobTransactionRequest;
import sh.brane.core.types.Address;
import sh.brane.core.types.BlobSidecar;
import sh.brane.core.types.HexData;
import sh.brane.core.types.Wei;

/**
 * Builder for EIP-4844 blob transactions.
 *
 * <p>EIP-4844 transactions carry blob data in a sidecar for data availability.
 * They use EIP-1559 style gas pricing plus an additional {@code maxFeePerBlobGas}
 * field for blob-specific gas pricing.
 *
 * <p>Blob data can be provided in two mutually exclusive ways:
 * <ul>
 *   <li>{@link #blobData(byte[])} - raw data to be encoded into blobs</li>
 *   <li>{@link #sidecar(BlobSidecar)} - pre-constructed sidecar with blobs, commitments, and proofs</li>
 * </ul>
 *
 * <p><strong>Example with raw data:</strong>
 * <pre>{@code
 * BlobTransactionRequest tx = Eip4844Builder.create()
 *     .from(sender)
 *     .to(recipient)
 *     .blobData(rawBytes)
 *     .maxFeePerGas(Wei.gwei(100))
 *     .maxPriorityFeePerGas(Wei.gwei(2))
 *     .maxFeePerBlobGas(Wei.gwei(10))
 *     .build(kzg);
 * }</pre>
 *
 * <p><strong>Example with pre-built sidecar:</strong>
 * <pre>{@code
 * BlobTransactionRequest tx = Eip4844Builder.create()
 *     .from(sender)
 *     .to(recipient)
 *     .sidecar(preBuiltSidecar)
 *     .maxFeePerGas(Wei.gwei(100))
 *     .maxPriorityFeePerGas(Wei.gwei(2))
 *     .maxFeePerBlobGas(Wei.gwei(10))
 *     .build();
 * }</pre>
 *
 * <p><strong>Thread Safety:</strong> This builder is <em>not</em> thread-safe. Each thread
 * should use its own builder instance. The {@link #build()} and {@link #build(sh.brane.core.crypto.Kzg)}
 * methods create an immutable {@link BlobTransactionRequest} that is safe to share across threads.
 *
 * @since 0.2.0
 */
public final class Eip4844Builder {

    private Address from;
    private Address to;
    private Wei value;
    private Long nonce;
    private Long gasLimit;
    private Wei maxFeePerGas;
    private Wei maxPriorityFeePerGas;
    private Wei maxFeePerBlobGas;
    private HexData data;
    private List<AccessListEntry> accessList;
    private byte[] blobData;
    private BlobSidecar sidecar;

    private Eip4844Builder() {
        // Private constructor - use create()
    }

    /**
     * Creates a new EIP-4844 blob transaction builder.
     *
     * @return a new builder instance
     */
    public static Eip4844Builder create() {
        return new Eip4844Builder();
    }

    /**
     * Sets the sender address.
     *
     * @param address the sender address
     * @return this builder for chaining
     */
    public Eip4844Builder from(final Address address) {
        this.from = address;
        return this;
    }

    /**
     * Sets the recipient address.
     *
     * <p>Note: EIP-4844 transactions cannot be used for contract creation,
     * so a recipient address is required.
     *
     * @param address the recipient address (required, cannot be null)
     * @return this builder for chaining
     */
    public Eip4844Builder to(final Address address) {
        this.to = address;
        return this;
    }

    /**
     * Sets the value to transfer.
     *
     * @param value the amount of native currency to transfer
     * @return this builder for chaining
     */
    public Eip4844Builder value(final Wei value) {
        this.value = value;
        return this;
    }

    /**
     * Sets the transaction input data (calldata).
     *
     * @param data the encoded function call data
     * @return this builder for chaining
     */
    public Eip4844Builder data(final HexData data) {
        this.data = data;
        return this;
    }

    /**
     * Sets the transaction nonce.
     *
     * @param nonce the sender's transaction count
     * @return this builder for chaining
     */
    public Eip4844Builder nonce(final long nonce) {
        this.nonce = nonce;
        return this;
    }

    /**
     * Sets the gas limit.
     *
     * @param gasLimit the maximum gas to use
     * @return this builder for chaining
     */
    public Eip4844Builder gasLimit(final long gasLimit) {
        this.gasLimit = gasLimit;
        return this;
    }

    /**
     * Sets the maximum total fee per gas unit.
     *
     * <p>This is the absolute maximum the sender is willing to pay per gas unit,
     * including both the base fee and priority fee.
     *
     * @param maxFeePerGas the maximum fee per gas
     * @return this builder for chaining
     */
    public Eip4844Builder maxFeePerGas(final Wei maxFeePerGas) {
        this.maxFeePerGas = maxFeePerGas;
        return this;
    }

    /**
     * Sets the maximum priority fee (tip) per gas unit.
     *
     * <p>This is the tip paid directly to validators to incentivize
     * inclusion of the transaction.
     *
     * @param maxPriorityFeePerGas the maximum priority fee per gas
     * @return this builder for chaining
     */
    public Eip4844Builder maxPriorityFeePerGas(final Wei maxPriorityFeePerGas) {
        this.maxPriorityFeePerGas = maxPriorityFeePerGas;
        return this;
    }

    /**
     * Sets the maximum fee per blob gas unit.
     *
     * <p>This is the maximum the sender is willing to pay per unit of blob gas.
     * Blob gas is priced independently from execution gas using its own
     * EIP-1559 style fee market.
     *
     * @param maxFeePerBlobGas the maximum fee per blob gas
     * @return this builder for chaining
     */
    public Eip4844Builder maxFeePerBlobGas(final Wei maxFeePerBlobGas) {
        this.maxFeePerBlobGas = maxFeePerBlobGas;
        return this;
    }

    /**
     * Sets the EIP-2930 access list for gas optimization.
     *
     * <p>Access lists pre-declare which accounts and storage slots will be accessed,
     * reducing gas costs for those accesses.
     *
     * @param accessList the list of access list entries, or null for no access list
     * @return this builder for chaining
     */
    public Eip4844Builder accessList(final List<AccessListEntry> accessList) {
        this.accessList = accessList == null ? null : List.copyOf(accessList);
        return this;
    }

    /**
     * Sets raw data to be encoded into blobs.
     *
     * <p>The data will be encoded into blobs when {@link #build(sh.brane.core.crypto.Kzg)}
     * is called. This method is mutually exclusive with {@link #sidecar(BlobSidecar)}.
     *
     * @param blobData the raw data to encode into blobs
     * @return this builder for chaining
     * @throws BraneTxBuilderException if a sidecar has already been set
     */
    public Eip4844Builder blobData(final byte[] blobData) {
        if (this.sidecar != null) {
            throw new BraneTxBuilderException("Cannot set blobData when sidecar is already set");
        }
        this.blobData = blobData == null ? null : blobData.clone();
        return this;
    }

    /**
     * Sets a pre-constructed blob sidecar.
     *
     * <p>Use this method when you have already constructed a {@link BlobSidecar}
     * with blobs, commitments, and proofs. This method is mutually exclusive
     * with {@link #blobData(byte[])}.
     *
     * @param sidecar the pre-constructed blob sidecar
     * @return this builder for chaining
     * @throws BraneTxBuilderException if blobData has already been set
     */
    public Eip4844Builder sidecar(final BlobSidecar sidecar) {
        if (this.blobData != null) {
            throw new BraneTxBuilderException("Cannot set sidecar when blobData is already set");
        }
        this.sidecar = sidecar;
        return this;
    }

    /**
     * Builds the blob transaction request using raw blob data.
     *
     * <p>This method requires that {@link #blobData(byte[])} was called to provide
     * raw data. The data will be encoded into blobs and a sidecar will be
     * constructed using the provided KZG implementation.
     *
     * @param kzg the KZG implementation for computing commitments and proofs
     * @return the constructed blob transaction request
     * @throws BraneTxBuilderException if required fields are missing or invalid
     * @throws NullPointerException if kzg is null
     */
    public BlobTransactionRequest build(final sh.brane.core.crypto.Kzg kzg) {
        if (kzg == null) {
            throw new NullPointerException("kzg is required when building with blobData");
        }
        if (blobData == null) {
            throw new BraneTxBuilderException("blobData is required when building with Kzg");
        }
        if (sidecar != null) {
            throw new BraneTxBuilderException("Cannot build with Kzg when sidecar is already set");
        }

        BlobSidecar builtSidecar = sh.brane.core.tx.SidecarBuilder.from(blobData).build(kzg);
        return buildWithSidecar(builtSidecar);
    }

    /**
     * Builds the blob transaction request using a pre-constructed sidecar.
     *
     * <p>This method requires that {@link #sidecar(BlobSidecar)} was called to
     * provide a pre-constructed sidecar.
     *
     * @return the constructed blob transaction request
     * @throws BraneTxBuilderException if required fields are missing or invalid
     */
    public BlobTransactionRequest build() {
        if (sidecar == null) {
            throw new BraneTxBuilderException("sidecar is required; use build(Kzg) for raw blobData");
        }
        if (blobData != null) {
            throw new BraneTxBuilderException("Cannot build without Kzg when blobData is set");
        }

        return buildWithSidecar(sidecar);
    }

    private BlobTransactionRequest buildWithSidecar(final BlobSidecar resolvedSidecar) {
        if (to == null) {
            throw new BraneTxBuilderException("to address is required for EIP-4844 transactions");
        }

        return new BlobTransactionRequest(
                from,
                to,
                value,
                gasLimit,
                maxPriorityFeePerGas,
                maxFeePerGas,
                maxFeePerBlobGas,
                nonce,
                data,
                accessList,
                resolvedSidecar);
    }
}
