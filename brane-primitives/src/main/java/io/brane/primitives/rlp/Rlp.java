package io.brane.primitives.rlp;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Utility methods for encoding and decoding Recursive Length Prefix (RLP) data.
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
     *
     * @param bytes the raw bytes to encode
     * @return encoded bytes
     */
    public static byte[] encodeString(final byte[] bytes) {
        Objects.requireNonNull(bytes, "bytes cannot be null");

        if (bytes.length == 1 && (bytes[0] & 0xFF) <= 0x7F) {
            // Single byte [0x00, 0x7f]
            return Arrays.copyOf(bytes, bytes.length);
        }

        if (bytes.length <= 55) {
            final byte[] result = new byte[1 + bytes.length];
            result[0] = (byte) (0x80 + bytes.length);
            System.arraycopy(bytes, 0, result, 1, bytes.length);
            return result;
        }

        final byte[] lengthBytes = toMinimalBytes(bytes.length);
        final byte[] result = new byte[1 + lengthBytes.length + bytes.length];
        result[0] = (byte) (0xB7 + lengthBytes.length);
        System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length);
        System.arraycopy(bytes, 0, result, 1 + lengthBytes.length, bytes.length);
        return result;
    }

    /**
     * Encodes the provided list of RLP items.
     *
     * @param items the items to encode
     * @return encoded bytes
     */
    public static byte[] encodeList(final List<RlpItem> items) {
        Objects.requireNonNull(items, "items cannot be null");
        for (final RlpItem item : items) {
            Objects.requireNonNull(item, "items cannot contain null values");
        }
        final byte[][] encodedItems = new byte[items.size()][];
        int totalLength = 0;
        for (int i = 0; i < items.size(); i++) {
            final byte[] encoded = items.get(i).encode();
            encodedItems[i] = encoded;
            totalLength += encoded.length;
        }

        if (totalLength <= 55) {
            final byte[] result = new byte[1 + totalLength];
            result[0] = (byte) (0xC0 + totalLength);
            int offset = 1;
            for (final byte[] encoded : encodedItems) {
                System.arraycopy(encoded, 0, result, offset, encoded.length);
                offset += encoded.length;
            }
            return result;
        }

        final byte[] lengthBytes = toMinimalBytes(totalLength);
        final byte[] result = new byte[1 + lengthBytes.length + totalLength];
        result[0] = (byte) (0xF7 + lengthBytes.length);
        System.arraycopy(lengthBytes, 0, result, 1, lengthBytes.length);
        int offset = 1 + lengthBytes.length;
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

    private static byte[] toMinimalBytes(final int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Length cannot be negative");
        }
        if (value == 0) {
            return new byte[] {0};
        }

        final int size = (32 - Integer.numberOfLeadingZeros(value) + 7) / 8;
        final byte[] result = new byte[size];
        for (int i = size - 1; i >= 0; i--) {
            result[i] = (byte) (value >>> (8 * (size - 1 - i)));
        }
        return result;
    }

    private static byte[] concat(final byte[]... arrays) {
        int total = 0;
        for (final byte[] array : arrays) {
            total += array.length;
        }

        final byte[] result = new byte[total];
        int offset = 0;
        for (final byte[] array : arrays) {
            System.arraycopy(array, 0, result, offset, array.length);
            offset += array.length;
        }
        return result;
    }

    private static byte[] concat(final byte first, final byte[]... arrays) {
        final byte[] firstArray = new byte[] {first};
        final byte[][] all = new byte[arrays.length + 1][];
        all[0] = firstArray;
        System.arraycopy(arrays, 0, all, 1, arrays.length);
        return concat(all);
    }

    private record DecodeResult(RlpItem item, int consumed) {}
}
