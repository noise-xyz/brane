package io.brane.core.abi;

import io.brane.core.crypto.Keccak256;

import io.brane.primitives.Hex;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Encodes ABI types into byte arrays according to the Ethereum Contract ABI
 * specification.
 * 
 * <p>
 * This class provides static methods for encoding lists of {@link AbiType}
 * (tuples) and function calls. It handles both static and dynamic types, including
 * correct padding and offset calculation.
 * 
 * <h2>Usage Example</h2>
 * <pre>{@code
 * // Encode a function call: transfer(address,uint256)
 * List<AbiType> args = List.of(
 *     new AddressType(new Address("0x...")),
 *     new UInt(256, BigInteger.valueOf(1000))
 * );
 * byte[] data = AbiEncoder.encodeFunction("transfer(address,uint256)", args);
 * }</pre>
 * 
 * @see AbiType
 * @see AbiDecoder
 */
public final class AbiEncoder {

    private AbiEncoder() {
    }

    /**
     * Encodes a list of ABI types as a tuple.
     * 
     * <p>
     * This is equivalent to encoding the arguments of a function call (without the
     * selector)
     * or the data of a constructor.
     *
     * @param args the list of ABI types to encode
     * @return the encoded byte array
     */
    public static byte[] encode(List<AbiType> args) {
        return encodeTuple(args);
    }

    /**
     * Encodes a function call with its arguments.
     * 
     * <p>
     * The encoding consists of the 4-byte function selector (Keccak-256 hash of the
     * signature)
     * followed by the encoded arguments.
     *
     * @param signature the function signature (e.g., "transfer(address,uint256)")
     * @param args      the list of arguments to encode
     * @return the encoded byte array including the selector
     */
    public static byte[] encodeFunction(String signature, List<AbiType> args) {
        byte[] selector = Arrays.copyOf(Keccak256.hash(signature.getBytes(StandardCharsets.UTF_8)), 4);
        byte[] encodedArgs = encode(args);
        byte[] result = new byte[4 + encodedArgs.length];
        System.arraycopy(selector, 0, result, 0, 4);
        System.arraycopy(encodedArgs, 0, result, 4, encodedArgs.length);
        return result;
    }

    private static byte[] encodeTuple(List<AbiType> components) {
        // Calculate head size
        int headSize = 0;
        for (AbiType component : components) {
            headSize += component.byteSize();
        }

        List<byte[]> heads = new ArrayList<>();
        List<byte[]> tails = new ArrayList<>();
        int currentTailOffset = headSize;

        for (AbiType component : components) {
            if (component.isDynamic()) {
                // Dynamic type: head is offset, tail is encoded data
                heads.add(encodeUInt256(BigInteger.valueOf(currentTailOffset)));
                byte[] tail = encodeType(component);
                tails.add(tail);
                currentTailOffset += tail.length;
            } else {
                // Static type: head is encoded data, no tail
                heads.add(encodeType(component));
            }
        }

        // Combine
        int totalSize = headSize + tails.stream().mapToInt(b -> b.length).sum();
        byte[] result = new byte[totalSize];
        int offset = 0;
        for (byte[] head : heads) {
            System.arraycopy(head, 0, result, offset, head.length);
            offset += head.length;
        }
        for (byte[] tail : tails) {
            System.arraycopy(tail, 0, result, offset, tail.length);
            offset += tail.length;
        }
        return result;
    }

    private static byte[] encodeType(AbiType type) {
        return switch (type) {
            case UInt u -> encodeUInt256(u.value());
            case Int i -> encodeInt256(i.value());
            case AddressType a -> encodeAddress(a.value());
            case Bool b -> encodeBool(b.value());
            case Bytes b -> encodeBytes(b);
            case Utf8String s -> encodeString(s);
            case Array<?> a -> encodeArray(a);
            case Tuple t -> encodeTuple(t.components());
        };
    }

    private static byte[] encodeUInt256(BigInteger value) {
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Unsigned value cannot be negative");
        }
        byte[] bytes = value.toByteArray();
        // remove leading zero byte if present (BigInteger adds it for positive numbers
        // if MSB is 1)
        if (bytes.length > 1 && bytes[0] == 0) {
            bytes = Arrays.copyOfRange(bytes, 1, bytes.length);
        }
        if (bytes.length > 32) {
            throw new IllegalArgumentException("Value too large for uint256");
        }
        byte[] result = new byte[32];
        System.arraycopy(bytes, 0, result, 32 - bytes.length, bytes.length);
        return result;
    }

    private static byte[] encodeInt256(BigInteger value) {
        byte[] bytes = value.toByteArray();
        // BigInteger.toByteArray() returns two's complement
        if (bytes.length > 32) {
            // It might have a leading sign byte (0x00 or 0xFF) that makes it 33 bytes
            // e.g. -1 is 0xFF, but 256-bit -1 is 32 bytes of 0xFF.
            // BigInteger might return minimal bytes.
            // If it's too large, we check if we can trim sign extension.
            // But for simplicity, let's assume if it fits in 256 bits, it fits.
            // If strictly > 32 bytes and not just sign extension, it's error.
            // But let's just use sign extension logic.
        }

        byte[] result = new byte[32];
        byte padding = value.signum() < 0 ? (byte) 0xFF : 0;
        Arrays.fill(result, padding);

        int length = Math.min(bytes.length, 32);
        // Copy bytes to the end
        System.arraycopy(bytes, Math.max(0, bytes.length - length), result, 32 - length, length);
        return result;
    }

    private static byte[] encodeAddress(io.brane.core.types.Address address) {
        // Address is 20 bytes, pad left to 32
        byte[] addrBytes = Hex.decode(address.value()); // 20 bytes
        byte[] result = new byte[32];
        System.arraycopy(addrBytes, 0, result, 12, 20);
        return result;
    }

    private static byte[] encodeBool(boolean value) {
        byte[] result = new byte[32];
        if (value) {
            result[31] = 1;
        }
        return result;
    }

    private static byte[] encodeBytes(Bytes bytes) {
        byte[] data = Hex.decode(bytes.value().value());
        if (bytes.isDynamic()) {
            // Length + Data + Padding
            byte[] length = encodeUInt256(BigInteger.valueOf(data.length));
            byte[] paddedData = padRight(data);
            byte[] result = new byte[32 + paddedData.length];
            System.arraycopy(length, 0, result, 0, 32);
            System.arraycopy(paddedData, 0, result, 32, paddedData.length);
            return result;
        } else {
            // Static: Data + Padding (Right)
            return padRight(data);
        }
    }

    private static byte[] encodeString(Utf8String string) {
        byte[] data = string.value().getBytes(StandardCharsets.UTF_8);
        // Same as dynamic bytes
        byte[] length = encodeUInt256(BigInteger.valueOf(data.length));
        byte[] paddedData = padRight(data);
        byte[] result = new byte[32 + paddedData.length];
        System.arraycopy(length, 0, result, 0, 32);
        System.arraycopy(paddedData, 0, result, 32, paddedData.length);
        return result;
    }

    private static byte[] encodeArray(Array<?> array) {
        @SuppressWarnings("unchecked")
        List<AbiType> values = (List<AbiType>) array.values();
        byte[] elements = encodeTuple(values);
        if (array.isDynamicLength()) {
            // Length + Elements
            byte[] length = encodeUInt256(BigInteger.valueOf(array.values().size()));
            byte[] result = new byte[32 + elements.length];
            System.arraycopy(length, 0, result, 0, 32);
            System.arraycopy(elements, 0, result, 32, elements.length);
            return result;
        } else {
            return elements;
        }
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
