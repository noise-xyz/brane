package io.brane.core.abi;

import io.brane.core.crypto.Keccak256;
import io.brane.primitives.Hex;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * High-performance ABI encoder that eliminates intermediate object allocations.
 */
public final class FastAbiEncoder {

    private FastAbiEncoder() {
    }

    public static byte[] encode(List<AbiType> args) {
        int totalSize = PackedSizeCalculator.calculate(args);
        byte[] result = new byte[totalSize];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        encodeTuple(args, buffer);
        return result;
    }

    public static byte[] encodeFunction(String signature, List<AbiType> args) {
        byte[] selector = Arrays.copyOf(Keccak256.hash(signature.getBytes(StandardCharsets.UTF_8)), 4);
        int argsSize = PackedSizeCalculator.calculate(args);
        byte[] result = new byte[4 + argsSize];
        ByteBuffer buffer = ByteBuffer.wrap(result);
        buffer.put(selector);
        encodeTuple(args, buffer);
        return result;
    }

    private static void encodeTuple(List<AbiType> components, ByteBuffer buffer) {
        int headSize = 0;
        for (AbiType component : components) {
            headSize += component.byteSize();
        }

        int currentTailOffset = headSize;
        int startPosition = buffer.position();

        // Pass 1: Heads
        for (AbiType component : components) {
            if (component.isDynamic()) {
                // Write offset
                buffer.put(encodeUInt256(BigInteger.valueOf(currentTailOffset)));
                currentTailOffset += PackedSizeCalculator.calculateType(component);
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
        // But wait, if a Tuple is static, it means all its components are static.
        // So encodeTuple will just write heads (which are data) and no tails.
        encodeContent(type, buffer);
    }

    private static byte[] encodeUInt256(BigInteger value) {
        // Optimized version could write directly to buffer, but for now reuse logic
        // or copy implementation to avoid dependency on AbiEncoder if we want
        // isolation.
        // Let's reimplement efficiently.
        if (value.signum() < 0)
            throw new IllegalArgumentException("Unsigned value cannot be negative");
        byte[] bytes = value.toByteArray();
        if (bytes.length > 1 && bytes[0] == 0)
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        if (bytes.length > 32)
            throw new IllegalArgumentException("Value too large for uint256");

        byte[] result = new byte[32];
        System.arraycopy(bytes, 0, result, 32 - bytes.length, bytes.length);
        return result;
    }

    private static byte[] encodeInt256(BigInteger value) {
        byte[] bytes = value.toByteArray();
        byte[] result = new byte[32];
        byte padding = value.signum() < 0 ? (byte) 0xFF : 0;
        Arrays.fill(result, padding);
        int length = Math.min(bytes.length, 32);
        System.arraycopy(bytes, Math.max(0, bytes.length - length), result, 32 - length, length);
        return result;
    }

    private static byte[] encodeAddress(io.brane.core.types.Address address) {
        byte[] addrBytes = Hex.decode(address.value());
        byte[] result = new byte[32];
        System.arraycopy(addrBytes, 0, result, 12, 20);
        return result;
    }

    private static byte[] encodeBool(boolean value) {
        byte[] result = new byte[32];
        if (value)
            result[31] = 1;
        return result;
    }

    private static void encodeBytes(Bytes bytes, ByteBuffer buffer) {
        byte[] data = Hex.decode(bytes.value().value());
        if (bytes.isDynamic()) {
            buffer.put(encodeUInt256(BigInteger.valueOf(data.length)));
        }
        buffer.put(padRight(data));
    }

    private static void encodeString(Utf8String string, ByteBuffer buffer) {
        byte[] data = string.value().getBytes(StandardCharsets.UTF_8);
        buffer.put(encodeUInt256(BigInteger.valueOf(data.length)));
        buffer.put(padRight(data));
    }

    private static void encodeArray(Array<?> array, ByteBuffer buffer) {
        @SuppressWarnings("unchecked")
        List<AbiType> values = (List<AbiType>) array.values();

        if (array.isDynamicLength()) {
            buffer.put(encodeUInt256(BigInteger.valueOf(values.size())));
        }
        encodeTuple(values, buffer);
    }

    private static byte[] padRight(byte[] data) {
        int padding = (32 - (data.length % 32)) % 32;
        if (padding == 0)
            return data;
        byte[] result = new byte[data.length + padding];
        System.arraycopy(data, 0, result, 0, data.length);
        return result;
    }
}
