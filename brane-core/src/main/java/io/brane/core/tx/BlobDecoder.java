// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.tx;

import java.util.List;
import java.util.Objects;

import io.brane.core.types.Blob;

/**
 * Decoder for extracting original data from EIP-4844 blobs.
 * <p>
 * This class provides the inverse operation of {@link SidecarBuilder#from(byte[])},
 * decoding blobs back to the original data that was encoded.
 *
 * <h2>Decoding Process</h2>
 * <p>
 * The decoding process:
 * <ol>
 *   <li>Extracts 31 usable bytes from each 32-byte field element (skipping the 0x00 high byte)</li>
 *   <li>Reads the 8-byte big-endian length prefix from the first bytes</li>
 *   <li>Returns the original data bytes based on the length prefix</li>
 * </ol>
 *
 * @since 0.2.0
 * @see SidecarBuilder
 */
public final class BlobDecoder {

    private BlobDecoder() {
        // Utility class
    }

    /**
     * Decodes the original data from a list of blobs.
     * <p>
     * This method reverses the encoding performed by {@link SidecarBuilder#from(byte[])}.
     *
     * <h4>Decoding Scheme</h4>
     * <p>
     * Each blob consists of 4096 field elements (32 bytes each). Due to the BLS modulus
     * constraint, the first byte of each field element is a {@code 0x00} prefix byte,
     * leaving only 31 usable bytes per field element. The decoding process:
     * <ol>
     *   <li>Extracts 31 bytes from each 32-byte field element (skipping the {@code 0x00} prefix byte)</li>
     *   <li>Reads the 8-byte big-endian length prefix from the first bytes of the extracted data</li>
     *   <li>Returns exactly that many bytes of original data following the length prefix</li>
     * </ol>
     *
     * @param blobs the list of blobs to decode, must not be null or empty
     * @return the original data bytes that were encoded into the blobs
     * @throws NullPointerException if blobs is null or contains null elements
     * @throws IllegalArgumentException if blobs is empty or if the encoded length is invalid
     * @see SidecarBuilder#from(byte[]) for the encoding scheme
     */
    public static byte[] decode(final List<Blob> blobs) {
        Objects.requireNonNull(blobs, "blobs");
        if (blobs.isEmpty()) {
            throw new IllegalArgumentException("blobs must not be empty");
        }
        for (int i = 0; i < blobs.size(); i++) {
            if (blobs.get(i) == null) {
                throw new NullPointerException("blobs[" + i + "] is null");
            }
        }

        // Calculate total usable bytes across all blobs
        int totalUsableBytes = blobs.size() * SidecarBuilder.USABLE_BYTES_PER_BLOB;

        // Extract all usable bytes from field elements
        byte[] extractedData = new byte[totalUsableBytes];
        int extractedOffset = 0;

        for (Blob blob : blobs) {
            byte[] blobBytes = blob.toBytes();

            for (int fe = 0; fe < SidecarBuilder.FIELD_ELEMENTS_PER_BLOB; fe++) {
                int blobOffset = fe * Blob.BYTES_PER_FIELD_ELEMENT;

                // Skip the first byte (0x00 high byte constraint) and copy 31 bytes
                System.arraycopy(
                        blobBytes,
                        blobOffset + 1,
                        extractedData,
                        extractedOffset,
                        SidecarBuilder.USABLE_BYTES_PER_FIELD_ELEMENT);
                extractedOffset += SidecarBuilder.USABLE_BYTES_PER_FIELD_ELEMENT;
            }
        }

        // Read the 8-byte big-endian length prefix
        long dataLength = 0;
        for (int i = 0; i < SidecarBuilder.LENGTH_PREFIX_SIZE; i++) {
            dataLength = (dataLength << 8) | (extractedData[i] & 0xFFL);
        }

        // Validate the length
        if (dataLength < 0) {
            throw new IllegalArgumentException("Invalid data length: negative value");
        }
        long maxAllowedLength = totalUsableBytes - SidecarBuilder.LENGTH_PREFIX_SIZE;
        if (dataLength > maxAllowedLength) {
            throw new IllegalArgumentException(
                    "Invalid data length: " + dataLength + " exceeds maximum " + maxAllowedLength + " bytes");
        }

        // Extract the original data (after the length prefix)
        int length = (int) dataLength;
        byte[] result = new byte[length];
        System.arraycopy(extractedData, SidecarBuilder.LENGTH_PREFIX_SIZE, result, 0, length);

        return result;
    }
}
