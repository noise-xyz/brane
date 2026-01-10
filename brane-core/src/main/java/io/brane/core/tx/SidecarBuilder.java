package io.brane.core.tx;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.brane.core.crypto.Kzg;
import io.brane.core.types.Blob;
import io.brane.core.types.BlobSidecar;
import io.brane.core.types.KzgCommitment;
import io.brane.core.types.KzgProof;

/**
 * Builder for creating EIP-4844 blob sidecars from raw data.
 * <p>
 * This builder handles the encoding of arbitrary data into blobs following
 * the EIP-4844 specification. Data is split across multiple blobs as needed,
 * with a length prefix to support correct decoding.
 *
 * <h2>Blob Data Encoding</h2>
 * <p>
 * Each blob field element is 32 bytes, but only the lower 31 bytes can be used
 * for data (the high byte must be 0 to ensure the value is less than the BLS
 * modulus). With 4096 field elements per blob, this gives 126,976 usable bytes
 * per blob.
 * <p>
 * The first 8 bytes of the encoded data contain the total data length as a
 * big-endian uint64, allowing the decoder to strip padding from the final blob.
 *
 * @since 0.2.0
 */
public final class SidecarBuilder {

    /**
     * Size of the length prefix in bytes (uint64 big-endian).
     */
    public static final int LENGTH_PREFIX_SIZE = 8;

    /**
     * Usable bytes per field element (32 - 1 for the high byte constraint).
     */
    public static final int USABLE_BYTES_PER_FIELD_ELEMENT = 31;

    /**
     * Number of field elements per blob.
     */
    public static final int FIELD_ELEMENTS_PER_BLOB = 4096;

    /**
     * Usable bytes per blob (31 * 4096 = 126,976).
     */
    public static final int USABLE_BYTES_PER_BLOB = USABLE_BYTES_PER_FIELD_ELEMENT * FIELD_ELEMENTS_PER_BLOB;

    /**
     * Maximum number of blobs per transaction (EIP-4844).
     */
    public static final int MAX_BLOBS = 6;

    /**
     * Maximum data size that can be encoded (6 blobs * 126,976 - 8 byte prefix).
     */
    public static final int MAX_DATA_SIZE = (USABLE_BYTES_PER_BLOB * MAX_BLOBS) - LENGTH_PREFIX_SIZE;

    private final List<Blob> blobs;

    private SidecarBuilder(List<Blob> blobs) {
        this.blobs = blobs;
    }

    /**
     * Creates a SidecarBuilder from raw data by encoding it into blobs.
     *
     * <h4>Encoding Scheme</h4>
     * <p>
     * The data is encoded with an 8-byte big-endian length prefix followed by the raw data.
     * Each blob consists of 4096 field elements (32 bytes each). Due to the BLS modulus
     * constraint, the first byte of each field element must be {@code 0x00}, leaving only
     * 31 usable bytes per field element. This gives 126,976 usable bytes per blob.
     * <p>
     * The encoding process:
     * <ol>
     *   <li>Prepends an 8-byte big-endian length prefix to support correct decoding</li>
     *   <li>Encodes data into field elements ({@code 0x00} prefix + 31 data bytes per element)</li>
     *   <li>Creates blob(s) from field elements, zero-padding the last blob if necessary</li>
     * </ol>
     *
     * @param data the raw data to encode, must not exceed {@value #MAX_DATA_SIZE} bytes
     * @return a new SidecarBuilder containing the encoded blobs
     * @throws NullPointerException if data is null
     * @throws IllegalArgumentException if data exceeds {@value #MAX_DATA_SIZE} bytes
     * @see BlobDecoder#decode(java.util.List) for the inverse decoding operation
     */
    public static SidecarBuilder from(final byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length > MAX_DATA_SIZE) {
            throw new IllegalArgumentException(
                    "Data size " + data.length + " exceeds maximum " + MAX_DATA_SIZE + " bytes");
        }

        // Create prefixed data: 8-byte big-endian length + original data
        int totalLength = LENGTH_PREFIX_SIZE + data.length;
        byte[] prefixedData = new byte[totalLength];

        // Write 8-byte big-endian length prefix
        long length = data.length;
        for (int i = 0; i < LENGTH_PREFIX_SIZE; i++) {
            prefixedData[LENGTH_PREFIX_SIZE - 1 - i] = (byte) (length & 0xFF);
            length >>>= 8;
        }

        // Copy original data after prefix
        System.arraycopy(data, 0, prefixedData, LENGTH_PREFIX_SIZE, data.length);

        // Calculate number of field elements needed
        int fieldElementsNeeded = (totalLength + USABLE_BYTES_PER_FIELD_ELEMENT - 1) / USABLE_BYTES_PER_FIELD_ELEMENT;

        // Calculate number of blobs needed
        int blobsNeeded = (fieldElementsNeeded + FIELD_ELEMENTS_PER_BLOB - 1) / FIELD_ELEMENTS_PER_BLOB;

        List<Blob> blobs = new ArrayList<>(blobsNeeded);
        int dataOffset = 0;

        for (int blobIndex = 0; blobIndex < blobsNeeded; blobIndex++) {
            byte[] blobData = new byte[Blob.SIZE];

            for (int fe = 0; fe < FIELD_ELEMENTS_PER_BLOB; fe++) {
                int blobOffset = fe * Blob.BYTES_PER_FIELD_ELEMENT;

                // First byte of field element is always 0x00 (high byte constraint)
                blobData[blobOffset] = 0x00;

                // Copy up to 31 bytes of data into the remaining 31 bytes
                int bytesToCopy = Math.min(USABLE_BYTES_PER_FIELD_ELEMENT, totalLength - dataOffset);
                if (bytesToCopy > 0) {
                    System.arraycopy(prefixedData, dataOffset, blobData, blobOffset + 1, bytesToCopy);
                    dataOffset += bytesToCopy;
                }
                // Remaining bytes in the field element stay as 0x00 (zero-padded)
            }

            blobs.add(new Blob(blobData));
        }

        return new SidecarBuilder(blobs);
    }

    /**
     * Creates a SidecarBuilder from pre-constructed blobs.
     * <p>
     * Use this method when you have already-constructed {@link Blob} instances,
     * for example when receiving blobs from an external source or for testing.
     *
     * @param blobs the blobs to include, must not be empty and must not exceed {@value #MAX_BLOBS}
     * @return a new SidecarBuilder containing the provided blobs
     * @throws NullPointerException if blobs is null or contains null elements
     * @throws IllegalArgumentException if blobs is empty or exceeds {@value #MAX_BLOBS} elements
     */
    public static SidecarBuilder fromBlobs(final Blob... blobs) {
        Objects.requireNonNull(blobs, "blobs");
        if (blobs.length == 0) {
            throw new IllegalArgumentException("blobs must not be empty");
        }
        if (blobs.length > MAX_BLOBS) {
            throw new IllegalArgumentException(
                    "blobs size " + blobs.length + " exceeds maximum " + MAX_BLOBS);
        }
        for (int i = 0; i < blobs.length; i++) {
            if (blobs[i] == null) {
                throw new NullPointerException("blobs[" + i + "] is null");
            }
        }
        return new SidecarBuilder(List.of(blobs));
    }

    /**
     * Builds a {@link BlobSidecar} by computing KZG commitments and proofs for all blobs.
     * <p>
     * For each blob in this builder, this method:
     * <ol>
     *   <li>Computes a KZG commitment using {@link Kzg#blobToCommitment(Blob)}</li>
     *   <li>Computes a KZG proof using {@link Kzg#computeProof(Blob, KzgCommitment)}</li>
     * </ol>
     *
     * @param kzg the KZG implementation to use for computing commitments and proofs
     * @return a new BlobSidecar containing the blobs with their commitments and proofs
     * @throws NullPointerException if kzg is null
     * @throws IllegalStateException if this builder has no blobs
     * @throws io.brane.core.error.KzgException if commitment or proof computation fails
     */
    public BlobSidecar build(final Kzg kzg) {
        Objects.requireNonNull(kzg, "kzg");
        if (blobs.isEmpty()) {
            throw new IllegalStateException("blobs must not be empty");
        }

        var commitments = new ArrayList<KzgCommitment>(blobs.size());
        var proofs = new ArrayList<KzgProof>(blobs.size());

        for (Blob blob : blobs) {
            KzgCommitment commitment = kzg.blobToCommitment(blob);
            KzgProof proof = kzg.computeProof(blob, commitment);
            commitments.add(commitment);
            proofs.add(proof);
        }

        return new BlobSidecar(blobs, commitments, proofs);
    }

    /**
     * Returns the list of blobs in this builder.
     * <p>
     * Package-private for internal use and testing.
     *
     * @return unmodifiable list of blobs
     */
    List<Blob> blobs() {
        return List.copyOf(blobs);
    }
}
