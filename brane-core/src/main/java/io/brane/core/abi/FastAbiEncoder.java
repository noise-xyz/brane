package io.brane.core.abi;

import io.brane.core.crypto.Keccak256;
import io.brane.core.types.HexData;
import io.brane.primitives.Hex;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * High-performance ABI encoder that eliminates intermediate object allocations.
 * <p>
 * This encoder is designed for maximum throughput and low garbage collection
 * pressure.
 * It achieves this by:
 * <ul>
 * <li><b>Two-Pass Encoding</b>: Calculating the exact buffer size required
 * before writing, avoiding array resizing.</li>
 * <li><b>Zero-Copy</b>: Writing primitive values and byte arrays directly to
 * the output {@link ByteBuffer}.</li>
 * <li><b>Direct Padding</b>: Applying zero-padding directly to the buffer
 * without allocating temporary padding arrays.</li>
 * </ul>
 * <p>
 * Supported types:
 * <ul>
 * <li>{@link UInt} (uint8 - uint256)</li>
 * <li>{@link Int} (int8 - int256)</li>
 * <li>{@link AddressType}</li>
 * <li>{@link Bool}</li>
 * <li>{@link Bytes} (static and dynamic)</li>
 * <li>{@link Utf8String}</li>
 * <li>{@link Array} (static and dynamic)</li>
 * <li>{@link Tuple}</li>
 * </ul>
 * <p>
 * This class is thread-safe as it contains no mutable state.
 */
public final class FastAbiEncoder {

    private FastAbiEncoder() {
    }

    /**
     * Encodes a list of ABI types into a byte array.
     *
     * @param args the list of ABI types to encode
     * @return the encoded byte array
     */
    public static byte[] encode(List<AbiType> args) {
        int totalSize = PackedSizeCalculator.calculate(args);
        byte[] result = new byte[totalSize];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        encodeTuple(args, buffer);
        return result;
    }

    /**
     * Encodes a function call with its arguments.
     *
     * @param signature the function signature (e.g., "transfer(address,uint256)")
     * @param args      the list of ABI typed arguments
     * @return the encoded function call data including the 4-byte selector
     */
    public static byte[] encodeFunction(String signature, List<AbiType> args) {
        byte[] selector = Arrays.copyOf(Keccak256.hash(signature.getBytes(StandardCharsets.UTF_8)), 4);
        int argsSize = PackedSizeCalculator.calculate(args);
        byte[] result = new byte[4 + argsSize];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.put(selector);
        encodeTuple(args, buffer);
        return result;
    }

    /**
     * Encodes a tuple of ABI types directly into the provided ByteBuffer.
     * <p>
     * This method implements the standard ABI encoding for tuples:
     * 1. Calculate the head size (sum of all static sizes, where dynamic types have
     * a 32-byte head).
     * 2. Pass 1 (Heads): Write static data for static types, or the current tail
     * offset for dynamic types.
     * 3. Pass 2 (Tails): Write the actual content of dynamic types.
     * </p>
     *
     * @param components the list of ABI types in the tuple
     * @param buffer     the destination ByteBuffer
     */
    public static void encodeTuple(List<AbiType> components, ByteBuffer buffer) {
        int headSize = 0;
        for (AbiType component : components) {
            headSize += component.byteSize();
        }

        int currentTailOffset = headSize;

        // Pass 1: Heads
        for (AbiType component : components) {
            if (component.isDynamic()) {
                // Write offset
                encodeUInt256(BigInteger.valueOf(currentTailOffset), buffer);
                currentTailOffset += component.contentByteSize();
            } else {
                // Write static data
                encodeStaticContent(component, buffer);
            }
        }

        // Pass 2: Tails
        for (AbiType component : components) {
            if (component.isDynamic()) {
                encodeContent(component, buffer);
            }
        }
    }

    private static void encodeContent(AbiType type, ByteBuffer buffer) {
        switch (type) {
            case UInt u -> buffer.put(encodeUInt256(u.value()));
            case Int i -> buffer.put(encodeInt256(i.value()));
            case AddressType a -> buffer.put(encodeAddress(a.value()));
            case Bool b -> buffer.put(encodeBool(b.value()));
            case Bytes b -> encodeBytes(b, buffer);
            case Utf8String s -> encodeString(s, buffer);
            case Array<?> a -> encodeArray(a, buffer);
            case Tuple t -> encodeTuple(t.components(), buffer);
        }
    }

    private static void encodeStaticContent(AbiType type, ByteBuffer buffer) {
        // For static types, content encoding is the same as regular encoding
        // EXCEPT for static arrays and tuples, which just recurse.
        // Dynamic types (Bytes, String, Dynamic Array, Dynamic Tuple) are handled in
        // Pass 2.
        encodeContent(type, buffer);
    }

    /**
     * Encodes a uint256 value directly into the buffer.
     * Optimized to use primitive long for values &lt;= 63 bits to avoid BigInteger
     * overhead.
     *
     * @param value  the value to encode
     * @param buffer the destination buffer
     */
    public static void encodeUInt256(BigInteger value, ByteBuffer buffer) {
        if (value.signum() < 0)
            throw new IllegalArgumentException("Unsigned value cannot be negative");

        if (value.bitLength() <= 63) {
            long val = value.longValue();
            buffer.putLong(0L);
            buffer.putLong(0L);
            buffer.putLong(0L);
            buffer.putLong(val);
            return;
        }

        byte[] bytes = value.toByteArray();
        int start = 0;
        int len = bytes.length;
        if (len > 1 && bytes[0] == 0) {
            start = 1;
            len--;
        }
        if (len > 32)
            throw new IllegalArgumentException("Value too large for uint256");

        for (int i = 0; i < 32 - len; i++) {
            buffer.put((byte) 0);
        }
        buffer.put(bytes, start, len);
    }

    public static byte[] encodeUInt256(BigInteger value) {
        byte[] result = new byte[32];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        encodeUInt256(value, buffer);
        return result;
    }

    /**
     * Encodes an int256 value directly into the buffer.
     *
     * <p>Valid range for int256: {@code -2^255} to {@code 2^255 - 1} (inclusive).
     *
     * @param value  the value to encode (must be in int256 range)
     * @param buffer the destination buffer
     * @throws IllegalArgumentException if value is outside int256 range
     */
    public static void encodeInt256(BigInteger value, ByteBuffer buffer) {
        if (value.bitLength() > 255) {
            throw new IllegalArgumentException("Value outside int256 range: must be between -2^255 and 2^255-1");
        }
        byte[] bytes = value.toByteArray();
        int len = bytes.length;
        byte padding = value.signum() < 0 ? (byte) 0xFF : 0;

        if (len > 32) {
            buffer.put(bytes, len - 32, 32);
        } else {
            for (int i = 0; i < 32 - len; i++) {
                buffer.put(padding);
            }
            buffer.put(bytes);
        }
    }

    public static byte[] encodeInt256(BigInteger value) {
        byte[] result = new byte[32];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        encodeInt256(value, buffer);
        return result;
    }

    /**
     * Encodes an address directly into the buffer.
     *
     * @param address the address to encode
     * @param buffer  the destination buffer
     */
    public static void encodeAddress(io.brane.core.types.Address address, ByteBuffer buffer) {
        byte[] addrBytes = Hex.decode(address.value());
        buffer.putLong(0L);
        buffer.putInt(0);
        buffer.put(addrBytes);
    }

    public static byte[] encodeAddress(io.brane.core.types.Address address) {
        byte[] result = new byte[32];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        encodeAddress(address, buffer);
        return result;
    }

    /**
     * Encodes a boolean value directly into the buffer.
     *
     * @param value  the boolean value
     * @param buffer the destination buffer
     */
    public static void encodeBool(boolean value, ByteBuffer buffer) {
        buffer.putLong(0L);
        buffer.putLong(0L);
        buffer.putLong(0L);
        buffer.putInt(0);
        buffer.putShort((short) 0);
        buffer.put((byte) 0);
        buffer.put(value ? (byte) 1 : (byte) 0);
    }

    public static byte[] encodeBool(boolean value) {
        byte[] result = new byte[32];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        encodeBool(value, buffer);
        return result;
    }

    /**
     * Encodes dynamic bytes (HexData) directly into the buffer.
     * Writes length (uint256) followed by data, right-padded to 32 bytes.
     *
     * @param data   the HexData object
     * @param buffer the destination buffer
     */
    public static void encodeBytes(HexData data, ByteBuffer buffer) {
        int length = data.byteLength();
        encodeUInt256(BigInteger.valueOf(length), buffer);
        data.putTo(buffer);
        int padding = (32 - (length % 32)) % 32;
        for (int i = 0; i < padding; i++) {
            buffer.put((byte) 0);
        }
    }

    /**
     * Encodes dynamic bytes directly into the buffer.
     * Writes length (uint256) followed by data, right-padded to 32 bytes.
     *
     * @param bytes  the bytes object
     * @param buffer the destination buffer
     */
    public static void encodeBytes(Bytes bytes, ByteBuffer buffer) {
        HexData hex = bytes.value();
        int length = hex.byteLength();

        if (bytes.isDynamic()) {
            encodeUInt256(BigInteger.valueOf(length), buffer);
        }

        hex.putTo(buffer);

        int padding = (32 - (length % 32)) % 32;
        for (int i = 0; i < padding; i++) {
            buffer.put((byte) 0);
        }
    }

    /**
     * Encodes a UTF-8 string directly into the buffer.
     * Writes length (uint256) followed by UTF-8 bytes, right-padded to 32 bytes.
     *
     * @param string the string object
     * @param buffer the destination buffer
     */
    public static void encodeString(Utf8String string, ByteBuffer buffer) {
        byte[] data = string.value().getBytes(StandardCharsets.UTF_8);
        encodeUInt256(BigInteger.valueOf(data.length), buffer);
        buffer.put(data);
        int padding = (32 - (data.length % 32)) % 32;
        for (int i = 0; i < padding; i++) {
            buffer.put((byte) 0);
        }
    }

    /**
     * Pads a byte array to the right with zeros to a multiple of 32 bytes.
     *
     * @param data the data to pad
     * @return the padded byte array
     */
    public static byte[] padRight(byte[] data) {
        int padding = (32 - (data.length % 32)) % 32;
        if (padding == 0)
            return data;
        byte[] result = new byte[data.length + padding];
        System.arraycopy(data, 0, result, 0, data.length);
        return result;
    }

    /**
     * Encodes an array of ABI types directly into the buffer.
     *
     * @param array  the array to encode
     * @param buffer the destination buffer
     */
    public static void encodeArray(Array<?> array, ByteBuffer buffer) {
        @SuppressWarnings("unchecked")
        List<AbiType> values = (List<AbiType>) array.values();

        if (array.isDynamicLength()) {
            encodeUInt256(BigInteger.valueOf(values.size()), buffer);
        }
        encodeTuple(values, buffer);
    }

}
