// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.types;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Objects;
import java.util.regex.Pattern;

import io.brane.primitives.Hex;

/**
 * Represents arbitrary-length hexadecimal-encoded byte data with "0x" prefix.
 * <p>
 * This type is commonly used for:
 * <ul>
 * <li>Smart contract bytecode</li>
 * <li>Transaction input data (calldata)</li>
 * <li>Function selectors and encoded arguments</li>
 * <li>Contract addresses (when deployed)</li>
 * </ul>
 * <p>
 * <strong>Validation:</strong> The value must:
 * <ul>
 * <li>Start with "0x" prefix</li>
 * <li>Contain only hex characters (0-9, a-f, A-F)</li>
 * <li>Have an even number of hex digits (each byte = 2 hex chars)</li>
 * </ul>
 * <p>
 * <strong>Example usage:</strong>
 *
 * <pre>{@code
 * // Empty data
 * HexData empty = HexData.EMPTY;
 *
 * // From hex string
 * HexData data = new HexData("0x1234abcd");
 *
 * // From bytes
 * byte[] bytes = { 0x12, 0x34 };
 * HexData fromBytes = HexData.fromBytes(bytes);
 *
 * // Convert back to bytes
 * byte[] decoded = data.toBytes();
 * }</pre>
 * <p>
 * <strong>Performance:</strong>
 * <p>
 * This class is designed to be immutable and efficient. It supports lazy
 * initialization
 * of the hex string representation when created from raw bytes, avoiding
 * unnecessary
 * string allocations in hot paths (like RLP encoding).
 * <p>
 * Key features:
 * <ul>
 * <li><b>Immutable</b>: Thread-safe and safe to share across components.</li>
 * <li><b>Lazy String Generation</b>: Defers hex encoding until {@link #value()}
 * is called.</li>
 * <li><b>Zero-Copy Encoding</b>: Can write directly to a
 * {@link java.nio.ByteBuffer} via {@link #putTo(java.nio.ByteBuffer)}.</li>
 * </ul>
 *
 * @since 0.1.0-alpha
 */
public final class HexData {
    private static final Pattern HEX = Pattern.compile("^0x([0-9a-fA-F]{2})*$");
    public static final HexData EMPTY = new HexData("0x", true);

    /** Volatile for thread-safe lazy initialization via double-checked locking. */
    private volatile String value;
    private final byte[] raw;

    /**
     * Creates a HexData from a hex string.
     *
     * @param value the hex-encoded string with "0x" prefix
     * @throws NullPointerException     if value is null
     * @throws IllegalArgumentException if value is not valid hex data
     */
    public HexData(String value) {
        Objects.requireNonNull(value, "hex");
        if (!HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid hex data: " + value);
        }
        this.value = value;
        this.raw = null;
    }

    /**
     * Trusted constructor for internal use with string.
     */
    private HexData(String value, boolean trusted) {
        this.value = value;
        this.raw = null;
    }

    /**
     * Constructor from raw bytes.
     * <p>
     * This constructor is optimized for performance by storing the raw bytes
     * directly
     * and deferring the expensive hex string generation until {@link #value()} is
     * called.
     * </p>
     *
     * @param raw the raw byte array
     */
    private HexData(byte[] raw) {
        this.raw = raw;
        this.value = null;
    }

    /**
     * Returns the hex string representation with "0x" prefix.
     * <p>
     * The string generation is lazy and cached. If this instance was created from
     * bytes,
     * the hex string is generated on the first call to this method.
     * </p>
     *
     * @return the hex string
     */
    @com.fasterxml.jackson.annotation.JsonValue
    public String value() {
        String v = value;
        if (v == null) {
            synchronized (this) {
                v = value;
                if (v == null) {
                    value = v = Hex.encode(raw);
                }
            }
        }
        return v;
    }

    /**
     * Returns the length of the raw bytes.
     *
     * @return the byte length
     */
    public int byteLength() {
        if (raw != null) {
            return raw.length;
        }
        // "0x" + 2 chars per byte
        return (value.length() - 2) / 2;
    }

    /**
     * Writes the raw bytes directly to the provided ByteBuffer.
     *
     * @param buffer the destination buffer
     */
    public void putTo(ByteBuffer buffer) {
        if (raw != null) {
            buffer.put(raw);
        } else {
            buffer.put(Hex.decode(value));
        }
    }

    /**
     * Decodes this hex data into raw bytes.
     *
     * @return the decoded byte array
     */
    public byte[] toBytes() {
        if (raw != null) {
            return raw.clone();
        }
        return Hex.decode(value);
    }

    /**
     * Creates HexData from raw bytes.
     * <p>
     * This factory method is the preferred way to create HexData from bytes as it
     * avoids immediate string conversion, significantly improving performance in
     * encoding hot paths.
     * </p>
     *
     * @param bytes the byte array to encode, or null/empty for {@link #EMPTY}
     * @return HexData with "0x" prefix, or {@link #EMPTY} if bytes is null or empty
     */
    public static HexData fromBytes(final byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return EMPTY;
        }
        return new HexData(bytes.clone()); // Defensive copy to preserve immutability
    }

    /**
     * Compares this HexData with another for equality.
     * <p>
     * This implementation is optimized for performance by comparing raw bytes
     * directly when both instances have them, avoiding expensive hex string
     * generation.
     *
     * @param o the object to compare with
     * @return true if the objects represent the same byte data
     */
    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        HexData other = (HexData) o;
        // Fast path: both have raw bytes - compare directly without string generation
        if (this.raw != null && other.raw != null) {
            return Arrays.equals(this.raw, other.raw);
        }
        // Slow path: fall back to string comparison (triggers lazy init if needed)
        return Objects.equals(value(), other.value());
    }

    /**
     * Returns a hash code consistent with {@link #equals(Object)}.
     * <p>
     * Note: This triggers lazy string generation for bytes-based instances to ensure
     * consistent hashing between string-based and bytes-based HexData representing
     * the same content.
     *
     * @return a hash code value for this object
     */
    @Override
    public int hashCode() {
        // Must use string-based hash to ensure consistency between string-based
        // and bytes-based HexData with the same content
        return value().hashCode();
    }

    @Override
    public String toString() {
        return "HexData[" + "value=" + value() + ']';
    }
}
