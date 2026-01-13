// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.types;

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
public final class KzgProof extends FixedSizeG1Point {

    /**
     * Size of a KZG proof in bytes (48 bytes for a compressed G1 point).
     * @deprecated Use {@link FixedSizeG1Point#SIZE} instead. This constant is retained for
     *             backward compatibility and will be removed in a future release.
     */
    @Deprecated(forRemoval = true)
    public static final int SIZE = FixedSizeG1Point.SIZE;

    /**
     * Creates a KZG proof from raw bytes.
     *
     * @param data the proof data, must be exactly 48 bytes
     * @throws NullPointerException if data is null
     * @throws IllegalArgumentException if data is not exactly 48 bytes
     */
    public KzgProof(final byte[] data) {
        super(data, "KZG proof");
    }

    @Override
    protected String typeName() {
        return "KzgProof";
    }
}
