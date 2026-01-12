// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.types;

import java.util.Arrays;
import java.util.Objects;

import io.brane.primitives.Hex;

/**
 * Represents an EIP-4844 KZG proof - a 48-byte G1 point on the BLS12-381 curve.
 * <p>
 * A KZG proof is used to verify that a polynomial evaluation is correct with respect
 * to a KZG commitment. In EIP-4844 blob transactions, proofs are used alongside
 * commitments to enable data availability verification.
 * <p>
 * This class is immutable and thread-safe. The internal byte array is defensively copied
 * on construction and when accessed via {@link #toBytes()}.
 *
 * @since 0.2.0
 */
public final class KzgProof {

    /**
     * Size of a KZG proof in bytes (48 bytes for a compressed G1 point).
     */
    public static final int SIZE = 48;

    private final byte[] data;

    /**
     * Creates a KZG proof from raw bytes.
     *
     * @param data the proof data, must be exactly {@value #SIZE} bytes
     * @throws NullPointerException if data is null
     * @throws IllegalArgumentException if data is not exactly {@value #SIZE} bytes
     */
    public KzgProof(final byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length != SIZE) {
            throw new IllegalArgumentException(
                    "KZG proof must be exactly " + SIZE + " bytes, got " + data.length);
        }
        this.data = data.clone();
    }

    /**
     * Returns a copy of the proof data.
     *
     * @return a defensive copy of the proof bytes
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
        KzgProof that = (KzgProof) o;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "KzgProof[0x" + Hex.encodeNoPrefix(data) + "]";
    }
}
