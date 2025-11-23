// package io.brane.primitives.rlp;

// import io.brane.primitives.Hex;
// import java.math.BigInteger;
// import java.util.Arrays;
// import java.util.Objects;

// /**
//  * RLP representation of a byte string.
//  */
// public record RlpString(byte[] bytes) implements RlpItem {
//     /**
//      * Constructs an {@link RlpString} copying the provided bytes.
//      *
//      * @param bytes the value to wrap
//      */
//     public RlpString {
//         Objects.requireNonNull(bytes, "bytes cannot be null");
//         bytes = Arrays.copyOf(bytes, bytes.length);
//     }

//     /**
//      * Creates an {@link RlpString} from the provided bytes.
//      *
//      * @param bytes the bytes to wrap
//      * @return {@link RlpString} wrapping the provided bytes
//      */
//     public static RlpString of(final byte[] bytes) {
//         return new RlpString(bytes);
//     }

//     /**
//      * Creates an {@link RlpString} from a hex string with or without {@code 0x} prefix.
//      *
//      * @param hex the hex string to decode
//      * @return {@link RlpString} wrapping the decoded bytes
//      */
//     public static RlpString of(final String hex) {
//         return new RlpString(Hex.decode(hex));
//     }

//     /**
//      * Creates an {@link RlpString} from a non-negative long value using minimal big-endian representation.
//      *
//      * @param value the value to encode
//      * @return {@link RlpString} wrapping the encoded value
//      * @throws IllegalArgumentException if {@code value} is negative
//      */
//     public static RlpString of(final long value) {
//         if (value < 0) {
//             throw new IllegalArgumentException("RLP numeric values must be non-negative");
//         }
//         return new RlpString(longToMinimalBytes(value));
//     }

//     /**
//      * Creates an {@link RlpString} from a non-negative {@link BigInteger} using minimal big-endian representation.
//      *
//      * @param value the value to encode
//      * @return {@link RlpString} wrapping the encoded value
//      * @throws IllegalArgumentException if {@code value} is null or negative
//      */
//     public static RlpString of(final BigInteger value) {
//         Objects.requireNonNull(value, "value cannot be null");
//         if (value.signum() < 0) {
//             throw new IllegalArgumentException("RLP numeric values must be non-negative");
//         }
//         return new RlpString(toMinimalBytes(value));
//     }

//     @Override
//     public byte[] encode() {
//         return Rlp.encodeString(bytes);
//     }

//     @Override
//     public boolean equals(final Object o) {
//         if (this == o) {
//             return true;
//         }
//         if (!(o instanceof RlpString other)) {
//             return false;
//         }
//         return Arrays.equals(bytes, other.bytes);
//     }

//     @Override
//     public int hashCode() {
//         return Arrays.hashCode(bytes);
//     }

//     /**
//      * Convert a long to its minimal RLP byte representation.
//      */
//     private static byte[] longToMinimalBytes(final long value) {
//         if (value == 0) {
//             return new byte[0];
//         }
//         int bits = 64 - Long.numberOfLeadingZeros(value);
//         int size = (bits + 7) >>> 3;
//         byte[] result = new byte[size];
//         long tmp = value;
//         for (int i = size - 1; i >= 0; i--) {
//             result[i] = (byte) tmp;
//             tmp >>>= 8;
//         }
//         return result;
//     }

//     /**
//      * Convert a {@link BigInteger} to its minimal byte array per RLP spec.
//      */
//     private static byte[] toMinimalBytes(final BigInteger value) {
//         if (value.signum() == 0) {
//             return new byte[0];
//         }
//         final byte[] raw = value.toByteArray();
//         if (raw[0] == 0) {
//             return Arrays.copyOfRange(raw, 1, raw.length);
//         }
//         return raw;
//     }
// }


package io.brane.primitives.rlp;

import io.brane.primitives.Hex;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.Objects;

/**
 * RLP representation of a byte string.
 *
 * Design notes:
 * - Internally stores a byte[] "as is" (no mandatory copy) so that internal callers
 *   (decoder, numeric helpers) can avoid double copies.
 * - Public factory methods (of(byte[]), of(String), of(long), of(BigInteger)) still
 *   perform defensive copies or build fresh arrays, preserving external immutability.
 * - Encoded RLP bytes are cached lazily in {@link #encode()} to avoid recomputing for
 *   hot paths that re-encode the same value many times (transactions, benchmarks, etc.).
 */
public final class RlpString implements RlpItem {

    /**
     * Underlying byte content for this RLP string (pre-RLP, raw bytes).
     */
    private final byte[] bytes;

    /**
     * Lazily cached RLP-encoded representation of {@link #bytes}.
     * Not thread-safe in the strictest sense, but safe/benign under benign races:
     * at worst, the RLP encoding may be recomputed concurrently.
     */
    private volatile byte[] encoded;

    /**
     * Constructs an {@link RlpString} wrapping the provided bytes without copying.
     * <p>
     * This constructor is intended for internal use where the caller guarantees
     * exclusive ownership of the array (e.g., decoder, numeric helpers).
     *
     * @param bytes the value to wrap (must not be null)
     */
    public RlpString(final byte[] bytes) {
        this.bytes = Objects.requireNonNull(bytes, "bytes cannot be null");
    }

    /**
     * Creates an {@link RlpString} from the provided bytes, defensively copying them.
     *
     * @param bytes the bytes to wrap
     * @return {@link RlpString} wrapping a copy of the provided bytes
     */
    public static RlpString of(final byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");
        return new RlpString(Arrays.copyOf(bytes, bytes.length));
    }

    /**
     * Creates an {@link RlpString} from a hex string with or without {@code 0x} prefix.
     *
     * @param hex the hex string to decode
     * @return {@link RlpString} wrapping the decoded bytes
     */
    public static RlpString of(final String hex) {
        return new RlpString(Hex.decode(hex));
    }

    /**
     * Creates an {@link RlpString} from a non-negative long value using minimal big-endian representation.
     *
     * @param value the value to encode
     * @return {@link RlpString} wrapping the encoded value
     * @throws IllegalArgumentException if {@code value} is negative
     */
    public static RlpString of(final long value) {
        if (value < 0) {
            throw new IllegalArgumentException("RLP numeric values must be non-negative");
        }
        return new RlpString(longToMinimalBytes(value));
    }

    /**
     * Creates an {@link RlpString} from a non-negative {@link BigInteger} using minimal big-endian representation.
     *
     * @param value the value to encode
     * @return {@link RlpString} wrapping the encoded value
     * @throws IllegalArgumentException if {@code value} is null or negative
     */
    public static RlpString of(final BigInteger value) {
        Objects.requireNonNull(value, "value cannot be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("RLP numeric values must be non-negative");
        }
        return new RlpString(toMinimalBytes(value));
    }

    /**
     * Returns a defensive copy of the underlying bytes.
     *
     * This mirrors the former record component accessor and preserves immutability
     * for API consumers.
     */
    public byte[] bytes() {
        return Arrays.copyOf(bytes, bytes.length);
    }

    /**
     * Lazily encodes this RLP string.
     *
     * The encoded bytes are cached after the first call.
     * This dramatically accelerates repeated encodings of the same RlpString,
     * which is common in transaction building and benchmark loops.
     *
     * WARNING:
     *  - The returned byte[] MUST NOT be mutated by callers.
     *  - This cache is not strictly thread-safe, but benignly racy:
     *    at worst, multiple threads compute the same value concurrently.
     */
    @Override
    public byte[] encode() {
        final byte[] cached = encoded;
        if (cached != null) {
            return cached;
        }
        final byte[] result = Rlp.encodeString(bytes);
        encoded = result;
        return result;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof RlpString other)) {
            return false;
        }
        return Arrays.equals(this.bytes, other.bytes);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(bytes);
    }

    @Override
    public String toString() {
        return "RlpString[" + Hex.encode(bytes) + "]";
    }

    /**
     * Convert a long to its minimal RLP byte representation.
     */
    private static byte[] longToMinimalBytes(final long value) {
        if (value == 0) {
            return new byte[0];
        }
        final int bits = 64 - Long.numberOfLeadingZeros(value);
        final int size = (bits + 7) >>> 3;
        final byte[] result = new byte[size];
        long tmp = value;
        for (int i = size - 1; i >= 0; i--) {
            result[i] = (byte) tmp;
            tmp >>>= 8;
        }
        return result;
    }

    /**
     * Convert a {@link BigInteger} to its minimal byte array per RLP spec.
     */
    private static byte[] toMinimalBytes(final BigInteger value) {
        if (value.signum() == 0) {
            return new byte[0];
        }
        final byte[] raw = value.toByteArray();
        if (raw[0] == 0) {
            return Arrays.copyOfRange(raw, 1, raw.length);
        }
        return raw;
    }
}