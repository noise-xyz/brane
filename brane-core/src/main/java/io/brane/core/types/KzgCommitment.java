// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.types;

import io.brane.core.crypto.Sha256;

/**
 * Represents an EIP-4844 KZG commitment - a 48-byte G1 point on the BLS12-381 curve.
 * <p>
 * A KZG commitment is produced by polynomial commitment schemes used in blob transactions.
 * The commitment can be converted to a versioned hash using {@link #toVersionedHash()},
 * which produces the hash used in blob transaction encoding.
 * <p>
 * This class is immutable and thread-safe. The internal byte array is defensively copied
 * on construction and when accessed via {@link #toBytes()}.
 *
 * @since 0.2.0
 */
public final class KzgCommitment extends FixedSizeG1Point {

    /**
     * Size of a KZG commitment in bytes (48 bytes for a compressed G1 point).
     * @deprecated Use {@link FixedSizeG1Point#SIZE} instead. This constant is retained for
     *             backward compatibility and will be removed in a future release.
     */
    @Deprecated(forRemoval = true)
    public static final int SIZE = FixedSizeG1Point.SIZE;

    /**
     * Version byte for KZG versioned hashes (EIP-4844).
     */
    private static final byte VERSIONED_HASH_VERSION_KZG = 0x01;

    /**
     * Cached versioned hash. Computed lazily and cached for performance.
     * Uses volatile for safe publication across threads.
     */
    private volatile Hash versionedHash;

    /**
     * Creates a KZG commitment from raw bytes.
     *
     * @param data the commitment data, must be exactly 48 bytes
     * @throws NullPointerException if data is null
     * @throws IllegalArgumentException if data is not exactly 48 bytes
     */
    public KzgCommitment(final byte[] data) {
        super(data, "KZG commitment");
    }

    /**
     * Returns the versioned hash of this commitment.
     * <p>
     * The versioned hash is computed as: {@code 0x01 || sha256(commitment)[1:32]}.
     * This replaces the first byte of the SHA-256 hash with the KZG version byte.
     * <p>
     * The result is cached for subsequent calls.
     *
     * @return the versioned hash
     */
    public Hash toVersionedHash() {
        Hash result = versionedHash;
        if (result == null) {
            byte[] sha256Hash = Sha256.hash(data);
            sha256Hash[0] = VERSIONED_HASH_VERSION_KZG;
            result = Hash.fromBytes(sha256Hash);
            versionedHash = result;
        }
        return result;
    }

    @Override
    protected String typeName() {
        return "KzgCommitment";
    }
}
