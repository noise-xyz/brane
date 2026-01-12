// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.types;

import java.util.Arrays;
import java.util.Objects;

import io.brane.core.crypto.Sha256;
import io.brane.primitives.Hex;

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
 * <h2>Design Rationale</h2>
 * This is intentionally a class rather than a record for two reasons:
 * <ol>
 *   <li><b>Content-based equality for byte arrays</b>: Records use reference equality for
 *       array components, but we need {@link java.util.Arrays#equals(byte[], byte[])} semantics
 *       so that two commitments with identical bytes are considered equal.</li>
 *   <li><b>Lazy caching</b>: The versioned hash computation (SHA-256) is expensive. We cache
 *       the result in a volatile field, which records cannot express since all their fields
 *       are derived from constructor parameters.</li>
 * </ol>
 *
 * @since 0.2.0
 */
public final class KzgCommitment {

    /**
     * Size of a KZG commitment in bytes (48 bytes for a compressed G1 point).
     */
    public static final int SIZE = 48;

    /**
     * Version byte for KZG versioned hashes (EIP-4844).
     */
    private static final byte VERSIONED_HASH_VERSION_KZG = 0x01;

    private final byte[] data;

    /**
     * Cached versioned hash. Computed lazily and cached for performance.
     * Uses volatile for safe publication across threads.
     */
    private volatile Hash versionedHash;

    /**
     * Creates a KZG commitment from raw bytes.
     *
     * @param data the commitment data, must be exactly {@value #SIZE} bytes
     * @throws NullPointerException if data is null
     * @throws IllegalArgumentException if data is not exactly {@value #SIZE} bytes
     */
    public KzgCommitment(final byte[] data) {
        Objects.requireNonNull(data, "data");
        if (data.length != SIZE) {
            throw new IllegalArgumentException(
                    "KZG commitment must be exactly " + SIZE + " bytes, got " + data.length);
        }
        this.data = data.clone();
    }

    /**
     * Returns a copy of the commitment data.
     *
     * @return a defensive copy of the commitment bytes
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
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        KzgCommitment that = (KzgCommitment) o;
        return Arrays.equals(data, that.data);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(data);
    }

    @Override
    public String toString() {
        return "KzgCommitment[0x" + Hex.encodeNoPrefix(data) + "]";
    }
}
