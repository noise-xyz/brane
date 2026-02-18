// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.primitives;

import java.util.Arrays;
import java.util.Objects;

/**
 * Utility methods for hex encoding/decoding with optional {@code 0x} prefixes.
 *
 * @since 1.0
 */
public final class Hex {
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    private static final int[] NIBBLE_LOOKUP = new int[128];

    static {
        Arrays.fill(NIBBLE_LOOKUP, -1);

        for (int i = 0; i <= 9; i++) {
            NIBBLE_LOOKUP['0' + i] = i;
        }

        for (int i = 0; i < 6; i++) {
            NIBBLE_LOOKUP['a' + i] = 10 + i;
            NIBBLE_LOOKUP['A' + i] = 10 + i;
        }
    }

    private Hex() {
        // Utility class
    }

    /**
     * Convert a {@code 0x}-prefixed hex string into a byte array.
     *
     * <p><b>Allocation:</b> 1 allocation (result byte[]). For zero-allocation decoding,
     * use {@link #decodeTo(CharSequence, int, int, byte[], int)}.
     *
     * @param hexString the string to decode
     * @return the decoded bytes
     * @throws IllegalArgumentException if the input is null, has an odd number of
     *                                  characters, or contains invalid hex
     */
    public static byte[] decode(final String hexString) {
        if (hexString == null) {
            throw new IllegalArgumentException("hex string cannot be null");
        }

        final int start = hasPrefix(hexString) ? 2 : 0;
        final int hexLength = hexString.length() - start;

        if (hexLength == 0) {
            return new byte[0];
        }

        if ((hexLength & 1) == 1) {
            throw new IllegalArgumentException("hex string must have even length: " + hexString);
        }

        final int len = hexLength / 2;
        final byte[] result = new byte[len];

        for (int i = 0; i < len; i++) {
            final int high = toNibble(hexString.charAt(start + i * 2), hexString);
            final int low = toNibble(hexString.charAt(start + i * 2 + 1), hexString);
            result[i] = (byte) ((high << 4) | low);
        }

        return result;
    }

    /**
     * Convert a single byte value (0x00-0xFF) into a lowercase hex string with {@code 0x} prefix.
     * This is useful for debugging RLP data where individual bytes need to be displayed.
     *
     * @param value the byte value (0-255)
     * @return hex string with {@code 0x} prefix, always 4 characters (e.g., "0x0f")
     * @throws IllegalArgumentException if {@code value} is outside the range 0-255
     */
    public static String encodeByte(final int value) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException("byte value must be in range 0-255: " + value);
        }
        final char[] chars = new char[4];
        chars[0] = '0';
        chars[1] = 'x';
        chars[2] = HEX_CHARS[(value >>> 4) & 0x0F];
        chars[3] = HEX_CHARS[value & 0x0F];
        return new String(chars);
    }

    /**
     * Convert a byte array into a lowercase hex string with a {@code 0x} prefix.
     *
     * <p><b>Allocation:</b> 2 allocations (char[] + String). For zero-allocation encoding,
     * use {@link #encodeTo(byte[], char[], int, boolean)}.
     *
     * @param bytes the bytes to encode
     * @return hex string with {@code 0x} prefix
     * @throws IllegalArgumentException if {@code bytes} is {@code null}
     */
    public static String encode(final byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes cannot be null");
        }

        final char[] chars = new char[2 + bytes.length * 2];
        chars[0] = '0';
        chars[1] = 'x';
        for (int i = 0; i < bytes.length; i++) {
            final int v = bytes[i] & 0xFF;
            chars[2 + i * 2] = HEX_CHARS[v >>> 4];
            chars[2 + i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(chars);
    }

    /**
     * Convert a sub-range of a byte array into a lowercase hex string with a {@code 0x} prefix.
     *
     * <p><b>Allocation:</b> 2 allocations (char[] + String). Avoids copying the sub-range
     * into a temporary byte array.
     *
     * @param bytes  the source byte array
     * @param offset the starting offset in the byte array
     * @param length the number of bytes to encode
     * @return hex string with {@code 0x} prefix
     * @throws IllegalArgumentException if {@code bytes} is {@code null},
     *                                  or if offset/length are out of bounds
     */
    public static String encode(final byte[] bytes, final int offset, final int length) {
        validateSubarray(bytes, offset, length);

        final char[] chars = new char[2 + length * 2];
        chars[0] = '0';
        chars[1] = 'x';
        for (int i = 0; i < length; i++) {
            final int v = bytes[offset + i] & 0xFF;
            chars[2 + i * 2] = HEX_CHARS[v >>> 4];
            chars[2 + i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(chars);
    }

    /**
     * Convert a sub-range of a byte array into a lowercase hex string without a {@code 0x} prefix.
     *
     * <p><b>Allocation:</b> 1 allocation (char[] converted to String). Avoids copying the
     * sub-range into a temporary byte array.
     *
     * @param bytes  the source byte array
     * @param offset the starting offset in the byte array
     * @param length the number of bytes to encode
     * @return hex string without {@code 0x} prefix
     * @throws IllegalArgumentException if {@code bytes} is {@code null},
     *                                  or if offset/length are out of bounds
     */
    public static String encodeNoPrefix(final byte[] bytes, final int offset, final int length) {
        validateSubarray(bytes, offset, length);

        final char[] chars = new char[length * 2];
        for (int i = 0; i < length; i++) {
            final int v = bytes[offset + i] & 0xFF;
            chars[i * 2] = HEX_CHARS[v >>> 4];
            chars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(chars);
    }

    /**
     * Convert a byte array into a lowercase hex string without a {@code 0x} prefix.
     *
     * @param bytes the bytes to encode
     * @return hex string without {@code 0x} prefix
     * @throws IllegalArgumentException if {@code bytes} is {@code null}
     */
    public static String encodeNoPrefix(final byte[] bytes) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes cannot be null");
        }

        final char[] chars = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            final int v = bytes[i] & 0xFF;
            chars[i * 2] = HEX_CHARS[v >>> 4];
            chars[i * 2 + 1] = HEX_CHARS[v & 0x0F];
        }
        return new String(chars);
    }

    /**
     * Decode hex characters directly into a pre-allocated byte array.
     *
     * <p>This method decodes hex characters from the specified region of a {@link CharSequence}
     * directly into the destination byte array, avoiding intermediate allocations.
     *
     * <p><b>Allocation:</b> 0 allocations. Writes directly to the provided buffer.
     *
     * @param hex        the hex string to decode (with or without {@code 0x} prefix)
     * @param hexOffset  the starting offset in the hex string
     * @param hexLength  the number of characters to decode from the hex string
     * @param dest       the destination byte array
     * @param destOffset the offset in the destination array to start writing
     * @return the number of bytes written to the destination array
     * @throws IllegalArgumentException if {@code hex} or {@code dest} is {@code null},
     *                                  if {@code hexLength} is odd,
     *                                  if the destination buffer is too small,
     *                                  if offsets or lengths are negative,
     *                                  if indices are out of bounds,
     *                                  or if the input contains invalid hex characters
     */
    public static int decodeTo(
            final CharSequence hex,
            final int hexOffset,
            final int hexLength,
            final byte[] dest,
            final int destOffset) {
        if (hex == null) {
            throw new IllegalArgumentException("hex cannot be null");
        }
        if (dest == null) {
            throw new IllegalArgumentException("dest cannot be null");
        }
        if (hexOffset < 0) {
            throw new IllegalArgumentException("hexOffset cannot be negative: " + hexOffset);
        }
        if (hexLength < 0) {
            throw new IllegalArgumentException("hexLength cannot be negative: " + hexLength);
        }
        if (destOffset < 0) {
            throw new IllegalArgumentException("destOffset cannot be negative: " + destOffset);
        }

        // Check bounds on hex input
        if (hexOffset + hexLength > hex.length()) {
            throw new IllegalArgumentException(
                    "hex region out of bounds: offset=" + hexOffset + ", length=" + hexLength + ", hex.length=" + hex.length());
        }

        // Skip 0x prefix if present at the start of the region
        int start = hexOffset;
        int len = hexLength;
        if (len >= 2 && hex.charAt(start) == '0' && (hex.charAt(start + 1) == 'x' || hex.charAt(start + 1) == 'X')) {
            start += 2;
            len -= 2;
        }

        // Empty input
        if (len == 0) {
            return 0;
        }

        // Must have even length
        if ((len & 1) == 1) {
            throw new IllegalArgumentException("hex string must have even length: " + len);
        }

        final int bytesToWrite = len / 2;

        // Check destination has enough space
        if (destOffset + bytesToWrite > dest.length) {
            throw new IllegalArgumentException(
                    "destination buffer too small: need " + bytesToWrite + " bytes but only " + (dest.length - destOffset) + " available");
        }

        // Decode hex pairs into bytes
        for (int i = 0; i < bytesToWrite; i++) {
            final char highChar = hex.charAt(start + i * 2);
            final char lowChar = hex.charAt(start + i * 2 + 1);

            final int high = toNibbleFromChar(highChar);
            final int low = toNibbleFromChar(lowChar);

            if (high == -1 || low == -1) {
                throw new IllegalArgumentException("invalid hex character in input");
            }

            dest[destOffset + i] = (byte) ((high << 4) | low);
        }

        return bytesToWrite;
    }

    private static int toNibbleFromChar(final char c) {
        if (c >= NIBBLE_LOOKUP.length) {
            return -1;
        }
        return NIBBLE_LOOKUP[c];
    }

    /**
     * Write hex characters to a pre-allocated buffer.
     *
     * <p><b>Allocation:</b> 0 allocations. Writes directly to the provided buffer.
     *
     * @param bytes      the bytes to encode
     * @param dest       the destination character array
     * @param destOffset the offset in the destination array to start writing
     * @param withPrefix if {@code true}, write {@code 0x} prefix before hex characters
     * @return the number of characters written
     * @throws IllegalArgumentException if {@code bytes} or {@code dest} is {@code null},
     *                                  or if the destination buffer is too small
     */
    public static int encodeTo(final byte[] bytes, final char[] dest, final int destOffset, final boolean withPrefix) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes cannot be null");
        }
        if (dest == null) {
            throw new IllegalArgumentException("dest cannot be null");
        }

        final int prefixLen = withPrefix ? 2 : 0;
        final int charsNeeded = prefixLen + bytes.length * 2;
        final int available = dest.length - destOffset;

        if (available < charsNeeded) {
            throw new IllegalArgumentException(
                    "destination buffer too small: need " + charsNeeded + " chars but only " + available + " available");
        }

        int pos = destOffset;
        if (withPrefix) {
            dest[pos++] = '0';
            dest[pos++] = 'x';
        }

        for (final byte b : bytes) {
            final int v = b & 0xFF;
            dest[pos++] = HEX_CHARS[v >>> 4];
            dest[pos++] = HEX_CHARS[v & 0x0F];
        }

        return charsNeeded;
    }

    /**
     * Remove a {@code 0x} prefix from the given string if present.
     *
     * @param hexString the string to clean
     * @return the string without a {@code 0x} prefix
     * @throws IllegalArgumentException if {@code hexString} is {@code null}
     */
    public static String cleanPrefix(final String hexString) {
        if (hexString == null) {
            throw new IllegalArgumentException("hex string cannot be null");
        }
        return hasPrefix(hexString) ? hexString.substring(2) : hexString;
    }

    /**
     * Returns {@code true} if the provided string starts with {@code 0x}
     * (case-insensitive).
     *
     * @param hexString the string to check
     * @return {@code true} when the prefix is present
     */
    public static boolean hasPrefix(final String hexString) {
        return hexString != null
                && hexString.length() >= 2
                && hexString.charAt(0) == '0'
                && (hexString.charAt(1) == 'x' || hexString.charAt(1) == 'X');
    }

    /**
     * Convert input to a byte array. Accepts either a hex {@link String} (with or without
     * {@code 0x} prefix) or a {@code byte[]}.
     * <p>
     * This mirrors the ethers.js {@code getBytes()} function for flexible input handling.
     * <p>
     * <strong>Note:</strong> When the input is already a {@code byte[]}, the same array
     * reference is returned without copying. Callers must not modify the returned array.
     *
     * @param input the input to convert (String or byte[])
     * @return the resulting byte array (same reference if input is byte[])
     * @throws IllegalArgumentException if input is null or not a supported type
     */
    public static byte[] toBytes(final Object input) {
        if (input == null) {
            throw new IllegalArgumentException("input cannot be null");
        }
        if (input instanceof byte[] bytes) {
            return bytes;
        }
        if (input instanceof String hex) {
            return decode(hex);
        }
        throw new IllegalArgumentException(
                "unsupported input type: " + input.getClass().getName() + ", expected String or byte[]");
    }

    private static int toNibble(final char c, final String originalInput) {
        if (c >= NIBBLE_LOOKUP.length || NIBBLE_LOOKUP[c] == -1) {
            throw new IllegalArgumentException("invalid hex character in: " + originalInput);
        }
        return NIBBLE_LOOKUP[c];
    }

    private static void validateSubarray(final byte[] bytes, final int offset, final int length) {
        if (bytes == null) {
            throw new IllegalArgumentException("bytes cannot be null");
        }
        // Overflow-safe bounds check (JVM intrinsic, Java 16+)
        Objects.checkFromIndexSize(offset, length, bytes.length);
    }
}
