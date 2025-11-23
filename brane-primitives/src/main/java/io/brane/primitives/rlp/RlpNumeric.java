package io.brane.primitives.rlp;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Numeric helpers for RLP encoding of EVM-style integers.
 *
 * Goals:
 *  - Avoid BigInteger for common non-negative {@code long} values.
 *  - For {@link BigInteger}, encode minimal unsigned big-endian bytes (no leading sign byte).
 *  - Delegate the actual RLP framing to {@link Rlp#encodeString(byte[])}.
 *
 * This class is pure helpers; it does not change the core Rlp logic.
 */
public final class RlpNumeric {

    private static final byte[] EMPTY = new byte[0];

    private RlpNumeric() {
        // Utility class
    }

    /**
     * Encode a non-negative long value as RLP bytes.
     *
     * Semantics:
     *  - 0 is encoded as RLP empty string (0x80), matching Ethereum RLP conventions.
     *  - 1..127 are encoded as a single byte (no string header), as per RLP spec.
     *  - >127 are encoded as minimal big-endian bytes as a string via {@link Rlp#encodeString(byte[])}.
     */
    public static byte[] encodeLongUnsigned(final long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("RLP numeric encoding only supports non-negative long values");
        }

        if (value == 0L) {
            // Empty string in RLP → 0x80; this is what encodeString(new byte[0]) will produce.
            return Rlp.encodeString(EMPTY);
        }

        // If value fits in single byte <= 0x7F, we can use the Rlp.encodeString rules directly
        // by constructing the minimal big-endian representation.
        // We'll still go through the generic path for uniformity, but without BigInteger.
        final int size = minimalByteSize(value);
        final byte[] raw = new byte[size];

        long tmp = value;
        for (int i = size - 1; i >= 0; i--) {
            raw[i] = (byte) tmp;
            tmp >>>= 8;
        }

        return Rlp.encodeString(raw);
    }

    /**
     * Encode a non-negative long value as an RlpItem (RlpString).
     *
     * Useful when you want to build lists of items and then call {@link Rlp#encode(RlpItem)}
     * or {@link Rlp#encodeList(java.util.List)}.
     */
    public static RlpItem encodeLongUnsignedItem(final long value) {
        if (value < 0L) {
            throw new IllegalArgumentException("RLP numeric encoding only supports non-negative long values");
        }

        if (value == 0L) {
            return new RlpString(EMPTY);
        }

        final int size = minimalByteSize(value);
        final byte[] raw = new byte[size];

        long tmp = value;
        for (int i = size - 1; i >= 0; i--) {
            raw[i] = (byte) tmp;
            tmp >>>= 8;
        }

        return new RlpString(raw);
    }

    /**
     * Encode a non-negative BigInteger as RLP bytes.
     *
     * Semantics:
     *  - 0 → empty string (0x80).
     *  - Positive values encoded as minimal unsigned big-endian (no sign byte),
     *    then passed to {@link Rlp#encodeString(byte[])}.
     */
    public static byte[] encodeBigIntegerUnsigned(final BigInteger value) {
        Objects.requireNonNull(value, "value cannot be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("RLP numeric encoding only supports non-negative BigInteger values");
        }

        if (value.signum() == 0) {
            return Rlp.encodeString(EMPTY);
        }

        // BigInteger.toByteArray() returns a two's complement representation:
        // there may be a leading 0x00 byte to preserve sign for positive numbers.
        final byte[] twosComp = value.toByteArray();
        final int offset = (twosComp[0] == 0x00) ? 1 : 0;
        final int length = twosComp.length - offset;

        if (length == 0) {
            // Shouldn't really happen: non-zero BigInteger should have non-empty magnitude.
            return Rlp.encodeString(EMPTY);
        }

        if (offset == 0) {
            // No leading sign byte; we can reuse the array directly.
            return Rlp.encodeString(twosComp);
        }

        // Strip the leading sign byte.
        final byte[] raw = new byte[length];
        System.arraycopy(twosComp, offset, raw, 0, length);
        return Rlp.encodeString(raw);
    }

    /**
     * Encode a non-negative BigInteger as an RlpItem (RlpString).
     */
    public static RlpItem encodeBigIntegerUnsignedItem(final BigInteger value) {
        Objects.requireNonNull(value, "value cannot be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("RLP numeric encoding only supports non-negative BigInteger values");
        }

        if (value.signum() == 0) {
            return new RlpString(EMPTY);
        }

        final byte[] twosComp = value.toByteArray();
        final int offset = (twosComp[0] == 0x00) ? 1 : 0;
        final int length = twosComp.length - offset;

        if (length == 0) {
            return new RlpString(EMPTY);
        }

        if (offset == 0) {
            // Directly wrap; RlpString is a "by-value" wrapper over the byte[].
            // If in future you make RlpString zero-copy, you can use a slice constructor.
            return new RlpString(twosComp);
        }

        final byte[] raw = new byte[length];
        System.arraycopy(twosComp, offset, raw, 0, length);
        return new RlpString(raw);
    }

    /**
     * Compute the number of bytes required to represent a non-negative long in big-endian form
     * without leading zero bytes.
     */
    private static int minimalByteSize(final long value) {
        // value > 0 is guaranteed by callers.
        final int leadingZeros = Long.numberOfLeadingZeros(value);
        final int bits = 64 - leadingZeros;
        return (bits + 7) >>> 3; // round up to full bytes
    }
}