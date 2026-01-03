package io.brane.core.crypto;

import java.util.Arrays;
import java.util.Objects;

import io.brane.primitives.Hex;

/**
 * ECDSA signature for Ethereum transactions.
 *
 * <p>
 * A signature consists of three components:
 * <ul>
 * <li><b>r</b>: First 32 bytes of the signature</li>
 * <li><b>s</b>: Second 32 bytes of the signature</li>
 * <li><b>v</b>: Recovery ID and chain ID encoded together</li>
 * </ul>
 *
 * <h2>V Encoding</h2>
 * <p>
 * For EIP-155 (legacy transactions):
 *
 * <pre>
 * v = chainId * 2 + 35 + yParity
 * </pre>
 *
 * where yParity is 0 or 1.
 *
 * <p>
 * For EIP-1559 (typed transactions):
 *
 * <pre>
 * v = yParity
 * </pre>
 *
 * (just 0 or 1, no chain ID encoding)
 *
 * @param r first 32 bytes of signature
 * @param s second 32 bytes of signature (must be low-s normalized)
 * @param v recovery ID, possibly with EIP-155 chain ID encoding
 * @since 0.2.0
 */
public record Signature(byte[] r, byte[] s, int v) {

    /**
     * Maximum bytes to display in full hex in toString().
     * Beyond this, just show the byte count to keep logs readable.
     */
    private static final int MAX_BYTES_TO_DISPLAY = 8;

    public Signature {
        Objects.requireNonNull(r, "r cannot be null");
        Objects.requireNonNull(s, "s cannot be null");

        if (r.length != 32) {
            throw new IllegalArgumentException("r must be 32 bytes, got " + r.length);
        }
        if (s.length != 32) {
            throw new IllegalArgumentException("s must be 32 bytes, got " + s.length);
        }

        // Make defensive copies to ensure immutability
        r = Arrays.copyOf(r, 32);
        s = Arrays.copyOf(s, 32);
    }

    /**
     * Returns the r component of the signature.
     * <p>
     * A defensive copy is returned to preserve immutability.
     *
     * @return a copy of the r bytes (32 bytes)
     */
    @Override
    public byte[] r() {
        return Arrays.copyOf(r, r.length);
    }

    /**
     * Returns the s component of the signature.
     * <p>
     * A defensive copy is returned to preserve immutability.
     *
     * @return a copy of the s bytes (32 bytes)
     */
    @Override
    public byte[] s() {
        return Arrays.copyOf(s, s.length);
    }

    /**
     * Extracts the recovery ID (yParity) from the v value.
     *
     * <p>
     * For EIP-155 signatures: {@code yParity = (v - chainId * 2 - 35)}
     * <br>
     * For simple signatures: {@code yParity = v} (if v is 0 or 1)
     *
     * @param chainId the chain ID (use 0 if not EIP-155)
     * @return 0 or 1
     * @throws IllegalArgumentException if the computed recovery ID is not 0 or 1
     */
    public int getRecoveryId(final long chainId) {
        if (v == 0 || v == 1) {
            // Simple v (EIP-1559 or pre-EIP-155)
            return v;
        }
        // EIP-155 format: compute in long space to avoid overflow
        long recoveryId = (long) v - chainId * 2L - 35L;
        if (recoveryId != 0 && recoveryId != 1) {
            throw new IllegalArgumentException(
                    "Invalid recovery ID: " + recoveryId + " (v=" + v + ", chainId=" + chainId + ")");
        }
        return (int) recoveryId;
    }

    /**
     * Returns true if this is an EIP-155 signature (v encodes chain ID).
     *
     * @return true if v &gt; 35
     */
    public boolean isEip155() {
        return v > 35;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof Signature other))
            return false;
        return Arrays.equals(r, other.r) && Arrays.equals(s, other.s) && v == other.v;
    }

    @Override
    public int hashCode() {
        return Objects.hash(Arrays.hashCode(r), Arrays.hashCode(s), v);
    }

    @Override
    public String toString() {
        return "Signature[r=" + bytesToHex(r) + ", s=" + bytesToHex(s) + ", v=" + v + "]";
    }

    private static String bytesToHex(byte[] bytes) {
        if (bytes.length > MAX_BYTES_TO_DISPLAY) {
            return bytes.length + " bytes";
        }
        return Hex.encodeNoPrefix(bytes);
    }
}
