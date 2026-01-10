package io.brane.core.tx;

import java.util.ArrayList;
import java.util.List;

import io.brane.core.types.Blob;

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
     * Maximum data size that can be encoded (6 blobs * 126,976 - 8 byte prefix).
     */
    public static final int MAX_DATA_SIZE = (USABLE_BYTES_PER_BLOB * 6) - LENGTH_PREFIX_SIZE;

    private final List<Blob> blobs;

    private SidecarBuilder() {
        this.blobs = new ArrayList<>();
    }
}
