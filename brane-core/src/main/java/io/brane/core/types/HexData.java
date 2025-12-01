package io.brane.core.types;

import io.brane.primitives.Hex;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Represents arbitrary-length hexadecimal-encoded byte data with "0x" prefix.
 * 
 * <p>
 * This type is commonly used for:
 * <ul>
 * <li>Smart contract bytecode</li>
 * <li>Transaction input data (calldata)</li>
 * <li>Function selectors and encoded arguments</li>
 * <li>Contract addresses (when deployed)</li>
 * </ul>
 * 
 * <p>
 * <strong>Validation:</strong> The value must:
 * <ul>
 * <li>Start with "0x" prefix</li>
 * <li>Contain only hex characters (0-9, a-f, A-F)</li>
 * <li>Have an even number of hex digits (each byte = 2 hex chars)</li>
 * </ul>
 * 
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
 * 
 * @param value the hex-encoded string with "0x" prefix
 *              <p>
 *              Throws {@link IllegalArgumentException} if value is not valid
 *              hex data.
 *              <p>
 *              Throws {@link NullPointerException} if value is null.
 */
public final class HexData {
    private static final Pattern HEX = Pattern.compile("^0x([0-9a-fA-F]{2})*$");
    public static final HexData EMPTY = new HexData("0x", true);

    private String value;
    private final byte[] raw;

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
    public String value() {
        if (value == null) {
            value = Hex.encode(raw);
        }
        return value;
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
        return new HexData(bytes);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        HexData hexData = (HexData) o;
        return Objects.equals(value(), hexData.value());
    }

    @Override
    public int hashCode() {
        return Objects.hash(value());
    }

    @Override
    public String toString() {
        return "HexData[" + "value=" + value() + ']';
    }
}
