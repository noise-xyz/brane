package io.brane.core.abi;

import java.util.List;

/**
 * Calculates the exact byte size of encoded ABI types without performing the
 * encoding.
 */
public final class PackedSizeCalculator {

    private PackedSizeCalculator() {
    }

    /**
     * Calculates the size of a list of ABI types (tuple).
     */
    public static int calculate(List<AbiType> components) {
        int headSize = 0;
        int dynamicTailSize = 0;

        for (AbiType component : components) {
            headSize += component.byteSize(); // 32 for dynamic, actual size for static
            if (component.isDynamic()) {
                dynamicTailSize += calculateType(component);
            }
        }
        return headSize + dynamicTailSize;
    }

    /**
     * Calculates the size of a single ABI type.
     */
    public static int calculateType(AbiType type) {
        return switch (type) {
            case UInt u -> 32;
            case Int i -> 32;
            case AddressType a -> 32;
            case Bool b -> 32;
            case Bytes b -> calculateBytes(b);
            case Utf8String s -> calculateString(s);
            case Array<?> a -> calculateArray(a);
            case Tuple t -> calculate(t.components());
        };
    }

    private static int calculateBytes(Bytes bytes) {
        int dataLen = io.brane.primitives.Hex.decode(bytes.value().value()).length;
        int paddedLen = pad(dataLen);
        if (bytes.isDynamic()) {
            return 32 + paddedLen; // Length + Data
        } else {
            return paddedLen; // Data only
        }
    }

    private static int calculateString(Utf8String string) {
        int dataLen = string.value().getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
        return 32 + pad(dataLen); // Length + Data
    }

    private static int calculateArray(Array<?> array) {
        @SuppressWarnings("unchecked")
        List<AbiType> values = (List<AbiType>) array.values();

        if (array.isDynamicLength()) {
            // Dynamic array: Length (32) + Elements
            return 32 + calculate(values);
        } else {
            // Static array: Elements only
            // Note: calculate(values) handles the logic for static/dynamic elements
            // correctly
            // because it iterates and checks isDynamic() for each component.
            return calculate(values);
        }
    }

    private static int pad(int length) {
        int remainder = length % 32;
        return remainder == 0 ? length : length + (32 - remainder);
    }
}
