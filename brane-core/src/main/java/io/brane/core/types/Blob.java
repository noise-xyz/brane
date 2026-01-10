package io.brane.core.types;

import java.util.Arrays;
import java.util.Objects;

import io.brane.primitives.Hex;

/**
 * Represents an EIP-4844 blob - a fixed-size data blob used in blob transactions.
 * <p>
 * A blob consists of {@value #FIELD_ELEMENTS} field elements, each {@value #BYTES_PER_FIELD_ELEMENT}
 * bytes, totaling {@value #SIZE} bytes (128 KiB).
 * <p>
 * This class is immutable and thread-safe. The internal byte array is defensively copied
 * on construction and when accessed via {@link #toBytes()}.
 *
 * @since 0.2.0
 */
public final class Blob {

    /**
     * Size of a blob in bytes (128 KiB).
     */
    public static final int SIZE = 131072;

    /**
     * Number of field elements in a blob.
     */
    public static final int FIELD_ELEMENTS = 4096;

    /**
     * Size of each field element in bytes.
     */
    public static final int BYTES_PER_FIELD_ELEMENT = 32;

    private final byte[] data;

    /**
     * Creates a blob from raw bytes.
     *
     * @param data the blob data, must be exactly {@value #SIZE} bytes
     * @throws NullPointerException if data is null
     * @throws IllegalArgumentException if data is not exactly {@value #SIZE} bytes
     */
    public Blob(final byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length != SIZE) {
            throw new IllegalArgumentException(
                    "Blob must be exactly " + SIZE + " bytes, got " + data.length);
        }
        this.data = data.clone();
    }

    /**
     * Returns a copy of the blob data.
     *
     * @return a defensive copy of the blob bytes
     */
    public byte[] toBytes() {
        return data.clone();
    }

    /**
     * Returns the raw byte array without copying.
     * <p>
     * Package-private for performance-critical internal use only.
     * Callers must not modify the returned array.
     *
     * @return the internal byte array (do not modify)
     */
    byte[] toBytesUnsafe() {
        return data;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Blob blob = (Blob) o;
        return Arrays.equals(data, blob.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "Blob[size=" + SIZE + ", hash=0x" + Hex.encodeNoPrefix(
                Arrays.copyOf(data, 8)) + "...]";
    }
}
