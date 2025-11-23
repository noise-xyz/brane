package io.brane.primitives;

/**
 * Utility methods for hex encoding/decoding with optional {@code 0x} prefixes.
 */
public final class Hex {
    private static final char[] HEX_CHARS = "0123456789abcdef".toCharArray();
    private static final int[] NIBBLE_LOOKUP = new int[128];

    static {
        for (int i = 0; i < NIBBLE_LOOKUP.length; i++) {
            NIBBLE_LOOKUP[i] = -1;
        }

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
     * @param hexString the string to decode
     * @return the decoded bytes
     * @throws IllegalArgumentException if the input is null, has an odd number of characters, or contains invalid hex
     */
    public static byte[] decode(final String hexString) {
        if (hexString == null) {
            throw new IllegalArgumentException("Hex string cannot be null");
        }

        final String cleanHex = cleanPrefix(hexString);
        if (cleanHex.length() == 0) {
            return new byte[0];
        }

        if ((cleanHex.length() & 1) == 1) {
            throw new IllegalArgumentException("Hex string must have an even length: " + hexString);
        }

        final int len = cleanHex.length() / 2;
        final byte[] result = new byte[len];

        for (int i = 0; i < len; i++) {
            final int high = toNibble(cleanHex.charAt(i * 2), hexString);
            final int low = toNibble(cleanHex.charAt(i * 2 + 1), hexString);
            result[i] = (byte) ((high << 4) | low);
        }

        return result;
    }

    /**
     * Convert a byte array into a lowercase hex string with a {@code 0x} prefix.
     *
     * @param bytes the bytes to encode
     * @return hex string with {@code 0x} prefix
     * @throws IllegalArgumentException if {@code bytes} is {@code null}
     */
    public static String encode(final byte[] bytes) {
        return "0x" + encodeNoPrefix(bytes);
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
            throw new IllegalArgumentException("Bytes cannot be null");
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
     * Remove a {@code 0x} prefix from the given string if present.
     *
     * @param hexString the string to clean
     * @return the string without a {@code 0x} prefix
     * @throws IllegalArgumentException if {@code hexString} is {@code null}
     */
    public static String cleanPrefix(final String hexString) {
        if (hexString == null) {
            throw new IllegalArgumentException("Hex string cannot be null");
        }
        return hasPrefix(hexString) ? hexString.substring(2) : hexString;
    }

    /**
     * Returns {@code true} if the provided string starts with {@code 0x} (case-insensitive).
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

    private static int toNibble(final char c, final String originalInput) {
        if (c >= NIBBLE_LOOKUP.length || NIBBLE_LOOKUP[c] == -1) {
            throw new IllegalArgumentException("Invalid hex character in: " + originalInput);
        }
        return NIBBLE_LOOKUP[c];
    }
}
