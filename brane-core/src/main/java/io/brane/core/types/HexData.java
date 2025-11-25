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
public record HexData(String value) {
    private static final Pattern HEX = Pattern.compile("^0x([0-9a-fA-F]{2})*$");
    public static final HexData EMPTY = new HexData("0x");

    public HexData {
        Objects.requireNonNull(value, "hex");
        if (!HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid hex data: " + value);
        }
    }

    /**
     * Decodes this hex data into raw bytes.
     * 
     * @return the decoded byte array
     */
    public byte[] toBytes() {
        return Hex.decode(value);
    }

    /**
     * Creates HexData from raw bytes.
     * 
     * @param bytes the byte array to encode, or null/empty for {@link #EMPTY}
     * @return HexData with "0x" prefix, or {@link #EMPTY} if bytes is null or empty
     */
    public static HexData fromBytes(final byte[] bytes) {
        if (bytes == null || bytes.length == 0) {
            return EMPTY;
        }
        return new HexData("0x" + Hex.encodeNoPrefix(bytes));
    }
}
