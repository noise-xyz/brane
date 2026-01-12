// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.primitives.rlp;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Utility methods for encoding and decoding Recursive Length Prefix (RLP) data.
 * Optimized for Java 21 with aggressive performance enhancements.
 *
 * Hotspots &amp; design goals:
 * - Medium strings (16–55 bytes): avoid unnecessary bounds checks and copies on
 * decode.
 * - Complex structures (lists): reduce temporary allocations and growable
 * collections.
 * - Large integers (length >= 65535): efficient length-prefix
 * encoding/decoding.
 *
 * @see <a href=
 *      "https://ethereum.org/en/developers/docs/data-structures-and-encoding/rlp/">Ethereum
 *      RLP Specification</a>
 * @since 1.0
 */
public final class Rlp {

    private Rlp() {
        // Utility class
    }

    /**
     * Encodes the provided item to RLP bytes.
     *
     * @param item the item to encode
     * @return encoded bytes
     */
    public static byte[] encode(final RlpItem item) {
        Objects.requireNonNull(item, "item cannot be null");
        return item.encode();
    }

    /**
     * Encodes the provided byte string into RLP format.
     * Optimized for small/medium strings (0–55 bytes) via single allocation.
     *
     * @param bytes the raw bytes to encode
     * @return encoded bytes
     */
    public static byte[] encodeString(final byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");

        final int length = bytes.length;

        // Single byte [0x00, 0x7f] - fast path (no header)
        if (length == 1) {
            final int b = bytes[0] & 0xFF;
            if (b <= 0x7F) {
                return new byte[] { bytes[0] }; // no extra copy
            }
        }

        // Short string (0–55 bytes) - header + payload
        if (length <= 55) {
            final byte[] result = new byte[1 + length];
            result[0] = (byte) (0x80 + length);
            System.arraycopy(bytes, 0, result, 1, length);
            return result;
        }

        // Long string (56+ bytes) - header prefix + length + payload
        final int lengthSize = lengthSize(length);
        final byte[] result = new byte[1 + lengthSize + length];
        result[0] = (byte) (0xB7 + lengthSize);
        writeLength(result, 1, length, lengthSize);
        System.arraycopy(bytes, 0, result, 1 + lengthSize, length);
        return result;
    }

    /**
     * Encodes the provided list of RLP items.
     * Optimized: avoids streams, pre-calculates total payload size, and minimizes
     * copies.
     *
     * @param items the items to encode
     * @return encoded bytes
     */
    public static byte[] encodeList(final List<RlpItem> items) {
        Objects.requireNonNull(items, "items cannot be null");

        final int itemCount = items.size();

        // Empty list - fast path
        if (itemCount == 0) {
            return new byte[] { (byte) 0xC0 };
        }

        // First pass: encode all items and compute payload size.
        final byte[][] encodedItems = new byte[itemCount][];
        int payloadSize = 0;
        for (int i = 0; i < itemCount; i++) {
            final RlpItem item = items.get(i);
            Objects.requireNonNull(item, "items cannot contain null values");

            final byte[] encoded = item.encode();
            encodedItems[i] = encoded;
            payloadSize += encoded.length;
        }

        // Short list (payload 0–55 bytes) – single header + payload allocation
        if (payloadSize <= 55) {
            final byte[] result = new byte[1 + payloadSize];
            result[0] = (byte) (0xC0 + payloadSize);
            int offset = 1;
            for (int i = 0; i < itemCount; i++) {
                final byte[] encoded = encodedItems[i];
                final int len = encoded.length;
                System.arraycopy(encoded, 0, result, offset, len);
                offset += len;
            }
            return result;
        }

        // Long list (payload 56+ bytes) – header + length prefix + payload
        final int lengthSize = lengthSize(payloadSize);
        final byte[] result = new byte[1 + lengthSize + payloadSize];
        result[0] = (byte) (0xF7 + lengthSize);
        writeLength(result, 1, payloadSize, lengthSize);

        int offset = 1 + lengthSize;
        for (int i = 0; i < itemCount; i++) {
            final byte[] encoded = encodedItems[i];
            final int len = encoded.length;
            System.arraycopy(encoded, 0, result, offset, len);
            offset += len;
        }
        return result;
    }

    /**
     * Decodes the provided RLP byte array into an {@link RlpItem}.
     *
     * @param encoded the encoded bytes
     * @return decoded item
     */
    public static RlpItem decode(final byte[] encoded) {
        Objects.requireNonNull(encoded, "encoded cannot be null");
        final DecodeResult result = decode(encoded, 0);
        if (result.consumed != encoded.length) {
            throw new IllegalArgumentException("RLP encoded data has trailing bytes");
        }
        return result.item;
    }

    /**
     * Decodes the provided RLP byte array, expecting a list at the root.
     *
     * @param encoded the encoded bytes representing a list
     * @return decoded list items
     */
    public static List<RlpItem> decodeList(final byte[] encoded) {
        final RlpItem item = decode(encoded);
        if (item instanceof RlpList list) {
            return list.items();
        }
        throw new IllegalArgumentException("RLP encoded data is not a list");
    }

    /**
     * Core recursive decode entry.
     */
    private static DecodeResult decode(final byte[] data, final int offset) {
        if (offset >= data.length) {
            throw new IllegalArgumentException("invalid RLP data: offset beyond end");
        }

        final int prefix = data[offset] & 0xFF;

        if (prefix <= 0x7F) {
            // Single byte string
            return new DecodeResult(new RlpString(new byte[] { (byte) prefix }), 1);
        }

        if (prefix <= 0xB7) {
            // Short string: 0x80 + length
            final int length = prefix - 0x80;
            return decodeString(data, offset, length, 1);
        }

        if (prefix <= 0xBF) {
            // Long string: 0xB7 + lengthOfLength
            final int lengthOfLength = prefix - 0xB7;
            return decodeLongString(data, offset, lengthOfLength);
        }

        if (prefix <= 0xF7) {
            // Short list: 0xC0 + length
            final int length = prefix - 0xC0;
            return decodeList(data, offset, length, 1);
        }

        // Long list: 0xF7 + lengthOfLength
        final int lengthOfLength = prefix - 0xF7;
        return decodeLongList(data, offset, lengthOfLength);
    }

    /**
     * Decode a short/medium RLP string (length <= 55).
     */
    private static DecodeResult decodeString(
            final byte[] data, final int offset, final int length, final int headerSize) {

        final int start = offset + headerSize;
        checkBounds(data, start, length, String.format("string (prefix=0x%02X, length=%d)",
                data[offset] & 0xFF, length));

        final byte[] value = new byte[length];
        System.arraycopy(data, start, value, 0, length);

        return new DecodeResult(new RlpString(value), headerSize + length);
    }

    /**
     * Decode a long RLP string (length >= 56).
     */
    private static DecodeResult decodeLongString(
            final byte[] data, final int offset, final int lengthOfLength) {

        final int lengthStart = offset + 1;
        checkBounds(data, lengthStart, lengthOfLength,
                String.format("long string length field (prefix=0x%02X)", data[offset] & 0xFF));

        final int length = readLength(data, lengthStart, lengthOfLength);

        if (length < 56) {
            throw new IllegalArgumentException(
                    String.format("non-minimal length encoding for string: length=%d < 56, prefix=0x%02X",
                            length, data[offset] & 0xFF));
        }

        final int valueStart = lengthStart + lengthOfLength;
        checkBounds(data, valueStart, length,
                String.format("long string payload (prefix=0x%02X, length=%d)", data[offset] & 0xFF, length));

        final byte[] value = new byte[length];
        System.arraycopy(data, valueStart, value, 0, length);

        return new DecodeResult(new RlpString(value), 1 + lengthOfLength + length);
    }

    /**
     * Decode a short list (payload length <= 55) starting at {@code offset}.
     */
    private static DecodeResult decodeList(
            final byte[] data, final int offset, final int length, final int headerSize) {

        final int start = offset + headerSize;
        checkBounds(data, start, length,
                String.format("list (prefix=0x%02X, length=%d)", data[offset] & 0xFF, length));

        // Improved capacity estimation: assume average item is ~3 bytes (typical case: 1-byte header + 2-byte payload).
        // Note: very small values (0-127) in RLP are encoded as a single byte with no header, so the actual average may be lower.
        // Cap at 16 to avoid over-allocation for large lists
        final int estimatedItems = Math.max(1, Math.min(length / 3, 16));
        final List<RlpItem> items = new ArrayList<>(estimatedItems);

        int currentOffset = start;
        final int end = start + length;
        while (currentOffset < end) {
            final DecodeResult child = decode(data, currentOffset);
            items.add(child.item);
            currentOffset += child.consumed;
        }

        if (currentOffset != end) {
            throw new IllegalArgumentException(
                    String.format("RLP list length mismatch: expected end=%d, actual=%d, prefix=0x%02X",
                            end, currentOffset, data[offset] & 0xFF));
        }

        return new DecodeResult(new RlpList(items), headerSize + length);
    }

    /**
     * Decode a long list (payload length >= 56).
     */
    private static DecodeResult decodeLongList(
            final byte[] data, final int offset, final int lengthOfLength) {

        final int lengthStart = offset + 1;
        checkBounds(data, lengthStart, lengthOfLength,
                String.format("long list length field (prefix=0x%02X)", data[offset] & 0xFF));

        final int length = readLength(data, lengthStart, lengthOfLength);

        if (length < 56) {
            throw new IllegalArgumentException(
                    String.format("non-minimal length encoding for list: length=%d < 56, prefix=0x%02X",
                            length, data[offset] & 0xFF));
        }

        return decodeList(data, offset, length, 1 + lengthOfLength);
    }

    /**
     * Read a big-endian length of up to 4 bytes.
     */
    private static int readLength(final byte[] data, final int start, final int lengthOfLength) {
        if (lengthOfLength < 1 || lengthOfLength > 4) {
            throw new IllegalArgumentException("invalid length-of-length: " + lengthOfLength);
        }

        final int first = data[start] & 0xFF;
        if (first == 0) {
            throw new IllegalArgumentException("length has leading zeros");
        }

        return switch (lengthOfLength) {
            case 1 -> first;
            case 2 -> (first << 8)
                    | (data[start + 1] & 0xFF);
            case 3 -> (first << 16)
                    | ((data[start + 1] & 0xFF) << 8)
                    | (data[start + 2] & 0xFF);
            case 4 -> (first << 24)
                    | ((data[start + 1] & 0xFF) << 16)
                    | ((data[start + 2] & 0xFF) << 8)
                    | (data[start + 3] & 0xFF);
            default -> throw new IllegalArgumentException("invalid length-of-length: " + lengthOfLength);
        };
    }

    /**
     * Calculate how many bytes are needed to represent a length value.
     */
    private static int lengthSize(final int value) {
        if (value < 0) {
            throw new IllegalArgumentException("length cannot be negative");
        }
        if (value < 0x100) {
            return 1; // < 256
        }
        if (value < 0x10000) {
            return 2; // < 65,536
        }
        if (value < 0x1000000) {
            return 3; // < 16,777,216
        }
        return 4; // up to Integer.MAX_VALUE
    }

    /**
     * Write a big-endian length value into {@code buffer} at {@code offset}.
     */
    private static void writeLength(
            final byte[] buffer, final int offset, final int value, final int size) {

        if (value < 0) {
            throw new IllegalArgumentException("length cannot be negative");
        }

        switch (size) {
            case 1 -> buffer[offset] = (byte) value;
            case 2 -> {
                buffer[offset] = (byte) (value >>> 8);
                buffer[offset + 1] = (byte) value;
            }
            case 3 -> {
                buffer[offset] = (byte) (value >>> 16);
                buffer[offset + 1] = (byte) (value >>> 8);
                buffer[offset + 2] = (byte) value;
            }
            case 4 -> {
                buffer[offset] = (byte) (value >>> 24);
                buffer[offset + 1] = (byte) (value >>> 16);
                buffer[offset + 2] = (byte) (value >>> 8);
                buffer[offset + 3] = (byte) value;
            }
            default -> throw new IllegalArgumentException("unsupported length size: " + size);
        }
    }

    /**
     * Validates that the data buffer has sufficient bytes available from the given offset.
     * Throws a descriptive exception if bounds are exceeded.
     *
     * @param data the data buffer
     * @param offset the current offset
     * @param required the number of bytes required
     * @param context descriptive context for the error message
     */
    private static void checkBounds(
            final byte[] data, final int offset, final int required, final String context) {
        if (required < 0 || offset < 0 || offset > data.length - required) {
            throw new IllegalArgumentException(
                    String.format("invalid RLP %s: offset=%d, required=%d, available=%d",
                            context, offset, required, Math.max(0, data.length - offset)));
        }
    }

    private record DecodeResult(RlpItem item, int consumed) {
    }
}
