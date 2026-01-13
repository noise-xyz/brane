// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.types;

import java.util.Arrays;
import java.util.Objects;

/**
 * Abstract base class for fixed-size 48-byte G1 points on the BLS12-381 curve.
 * <p>
 * Both KZG commitments and KZG proofs are compressed G1 points of exactly 48 bytes.
 * This sealed class provides the common functionality for size validation, defensive
 * copying, and content-based equality semantics.
 * <p>
 * This class is immutable and thread-safe. The internal byte array is defensively copied
 * on construction and when accessed via {@link #toBytes()}.
 *
 * @since 0.2.0
 */
// TODO: Add 'sealed permits KzgCommitment, KzgProof' after subclasses are migrated [REFACTOR2-3.x]
public abstract class FixedSizeG1Point {

    /**
     * Size of a G1 point in bytes (48 bytes for a compressed G1 point on BLS12-381).
     */
    public static final int SIZE = 48;

    /**
     * The raw bytes of the G1 point.
     */
    protected final byte[] data;

    /**
     * Creates a G1 point from raw bytes.
     *
     * @param data the point data, must be exactly {@value #SIZE} bytes
     * @param typeName the name of the concrete type (for error messages)
     * @throws NullPointerException if data is null
     * @throws IllegalArgumentException if data is not exactly {@value #SIZE} bytes
     */
    protected FixedSizeG1Point(final byte[] data, final String typeName) {
        Objects.requireNonNull(data, "data");
        if (data.length != SIZE) {
            throw new IllegalArgumentException(
                    typeName + " must be exactly " + SIZE + " bytes, got " + data.length);
        }
        this.data = data.clone();
    }

    /**
     * Returns a copy of the point data.
     *
     * @return a defensive copy of the point bytes
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
        FixedSizeG1Point that = (FixedSizeG1Point) o;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }
}
