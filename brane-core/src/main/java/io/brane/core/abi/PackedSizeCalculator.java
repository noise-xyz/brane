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
            headSize += component.byteSize();
            if (component.isDynamic()) {
                dynamicTailSize += component.contentByteSize();
            }
        }
        return headSize + dynamicTailSize;
    }

}
