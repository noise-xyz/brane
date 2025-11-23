package io.brane.primitives.rlp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Utility methods for encoding and decoding Recursive Length Prefix (RLP) data.
 * Optimized for Java 21 with aggressive performance enhancements.
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
     * OPTIMIZED: Pre-calculates final size and writes directly to avoid intermediate allocations.
     *
     * @param bytes the raw bytes to encode
     * @return encoded bytes
     */
    public static byte[] encodeString(final byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");

        final int length = bytes.length;
        
        // Single byte [0x00, 0x7f] - fast path
        if (length == 1 && (bytes[0] & 0xFF) <= 0x7F) {
            return new byte[] {bytes[0]};  // Avoid array copy
        }

        // Short string (0-55 bytes) - optimized single allocation
        if (length <= 55) {
            final byte[] result = new byte[1 + length];
            result[0] = (byte) (0x80 + length);
            System.arraycopy(bytes, 0, result, 1, length);
            return result;
        }

        // Long string (56+ bytes) - pre-calculate size
        final int lengthSize = lengthSize(length);
        final byte[] result = new byte[1 + lengthSize + length];
        result[0] = (byte) (0xB7 + lengthSize);
        writeLength(result, 1, length, lengthSize);
        System.arraycopy(bytes, 0, result, 1 + lengthSize, length);
        return result;
    }

    /**
     * Encodes the provided list of RLP items.
     * OPTIMIZED: Eliminates stream overhead, pre-calculates total size.
     *
     * @param items the items to encode
     * @return encoded bytes
     */
    public static byte[] encodeList(final List<RlpItem> items) {
        Objects.requireNonNull(items, "items cannot be null");
        
        final int itemCount = items.size();
        
        // Empty list - fast path
        if (itemCount == 0) {
            return new byte[] {(byte) 0xC0};
        }
        
        // Encode all items and calculate total payload size
        final byte[][] encodedItems = new byte[itemCount][];
        int payloadSize = 0;
        for (int i = 0; i < itemCount; i++) {
            final RlpItem item = items.get(i);
            Objects.requireNonNull(item, "items cannot contain null values");
            final byte[] encoded = item.encode();
            encodedItems[i] = encoded;
            payloadSize += encoded.length;
        }

        // Short list (0-55 bytes) - single allocation
        if (payloadSize <= 55) {
            final byte[] result = new byte[1 + payloadSize];
            result[0] = (byte) (0xC0 + payloadSize);
            int offset = 1;
            for (final byte[] encoded : encodedItems) {
                System.arraycopy(encoded, 0, result, offset, encoded.length);
                offset += encoded.length;
            }
            return result;
        }

        // Long list (56+ bytes) - pre-calculate total size
        final int lengthSize = lengthSize(payloadSize);
        final byte[] result = new byte[1 + lengthSize + payloadSize];
        result[0] = (byte) (0xF7 + lengthSize);
        writeLength(result, 1, payloadSize, lengthSize);
        int offset = 1 + lengthSize;
        for (final byte[] encoded : encodedItems) {
            System.arraycopy(encoded, 0, result, offset, encoded.length);
            offset += encoded.length;
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
            throw new IllegalArgumentException("RLP data has trailing bytes");
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
        throw new IllegalArgumentException("RLP data is not a list");
    }

    private static DecodeResult decode(final byte[] data, final int offset) {
        if (offset >= data.length) {
            throw new IllegalArgumentException("Invalid RLP data: offset beyond end");
        }

        final int prefix = data[offset] & 0xFF;

        if (prefix <= 0x7F) {
            return new DecodeResult(new RlpString(new byte[] {(byte) prefix}), 1);
        }
        if (prefix <= 0xB7) {
            final int length = prefix - 0x80;
            return decodeString(data, offset, length, 1);
        }
        if (prefix <= 0xBF) {
            final int lengthOfLength = prefix - 0xB7;
            return decodeLongString(data, offset, lengthOfLength);
        }
        if (prefix <= 0xF7) {
            final int length = prefix - 0xC0;
            return decodeList(data, offset, length, 1);
        }

        final int lengthOfLength = prefix - 0xF7;
        return decodeLongList(data, offset, lengthOfLength);
    }

    private static DecodeResult decodeString(
            final byte[] data, final int offset, final int length, final int headerSize) {
        final int start = offset + headerSize;
        final int end = start + length;
        if (end > data.length) {
            throw new IllegalArgumentException("Invalid RLP string length");
        }
        final byte[] value = Arrays.copyOfRange(data, start, end);
        return new DecodeResult(new RlpString(value), headerSize + length);
    }

    private static DecodeResult decodeLongString(
            final byte[] data, final int offset, final int lengthOfLength) {
        final int lengthStart = offset + 1;
        final int lengthEnd = lengthStart + lengthOfLength;
        if (lengthEnd > data.length) {
            throw new IllegalArgumentException("Invalid RLP long string length");
        }

        final int length = readLength(data, lengthStart, lengthOfLength);
        if (length < 56) {
            throw new IllegalArgumentException("Non-minimal length encoding for string");
        }

        final int valueStart = lengthEnd;
        final int valueEnd = valueStart + length;
        if (valueEnd > data.length) {
            throw new IllegalArgumentException("Invalid RLP string length");
        }

        final byte[] value = Arrays.copyOfRange(data, valueStart, valueEnd);
        return new DecodeResult(new RlpString(value), 1 + lengthOfLength + length);
    }

    private static DecodeResult decodeList(
            final byte[] data, final int offset, final int length, final int headerSize) {
        final int start = offset + headerSize;
        final int end = start + length;
        if (end > data.length) {
            throw new IllegalArgumentException("Invalid RLP list length");
        }

        final List<RlpItem> items = new ArrayList<>();
        int currentOffset = start;
        while (currentOffset < end) {
            final DecodeResult child = decode(data, currentOffset);
            items.add(child.item);
            currentOffset += child.consumed;
        }

        if (currentOffset != end) {
            throw new IllegalArgumentException("RLP list length mismatch");
        }

        return new DecodeResult(new RlpList(items), headerSize + length);
    }

    private static DecodeResult decodeLongList(
            final byte[] data, final int offset, final int lengthOfLength) {
        final int lengthStart = offset + 1;
        final int lengthEnd = lengthStart + lengthOfLength;
        if (lengthEnd > data.length) {
            throw new IllegalArgumentException("Invalid RLP long list length");
        }

        final int length = readLength(data, lengthStart, lengthOfLength);
        if (length < 56) {
            throw new IllegalArgumentException("Non-minimal length encoding for list");
        }

        return decodeList(data, offset, length, 1 + lengthOfLength);
    }

    private static int readLength(final byte[] data, final int start, final int lengthOfLength) {
        if (lengthOfLength < 1 || lengthOfLength > 8) {
            throw new IllegalArgumentException("Invalid length of length");
        }
        if (data[start] == 0) {
            throw new IllegalArgumentException("Length has leading zeros");
        }

        int length = 0;
        for (int i = 0; i < lengthOfLength; i++) {
            length = (length << 8) + (data[start + i] & 0xFF);
        }
        return length;
    }

    /**
     * Calculate how many bytes needed to represent a length value.
     * OPTIMIZED: Uses bit shifts for fast calculation.
     */
    private static int lengthSize(final int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Length cannot be negative");
        }
        if (value < 0x100) return 1;           // 256
        if (value < 0x10000) return 2;         // 65536
        if (value < 0x1000000) return 3;       // 16777216
        return 4;                               // Max int is ~2.1B
    }

    /**
     * Write a length value into a buffer at the specified offset.
     * OPTIMIZED: Direct buffer writes, no intermediate allocation.
     */
    private static void writeLength(final byte[] buffer, final int offset, final int value, final int size) {
        for (int i = size - 1; i >= 0; i--) {
            buffer[offset + i] = (byte) (value >>> (8 * (size - 1 - i)));
        }
    }

    private record DecodeResult(RlpItem item, int consumed) {}
}
