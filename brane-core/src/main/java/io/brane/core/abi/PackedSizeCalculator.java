// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.abi;

import java.util.List;

/**
 * Calculates the exact byte size of encoded ABI types without performing the encoding.
 *
 * <p>This utility class is used by encoders like {@link FastAbiEncoder} to pre-calculate
 * buffer sizes before encoding. This two-pass approach (calculate size, then encode)
 * avoids buffer resizing and enables zero-copy encoding.
 *
 * <p>The calculation follows the Ethereum Contract ABI specification:
 * <ul>
 *   <li>Static types contribute their head size directly (typically 32 bytes)</li>
 *   <li>Dynamic types contribute 32 bytes for the offset pointer plus their content size</li>
 * </ul>
 *
 * <h2>Usage Example</h2>
 * <pre>{@code
 * List<AbiType> args = List.of(
 *     new UInt(256, BigInteger.valueOf(100)),
 *     new Utf8String("hello")
 * );
 * int size = PackedSizeCalculator.calculate(args);
 * // size = 32 (uint256) + 32 (string offset) + 32 (string length) + 32 (string data padded)
 * }</pre>
 *
 * <h2>Thread Safety</h2>
 * <p>This class is thread-safe. It contains no mutable state and all methods are static.
 *
 * @see FastAbiEncoder
 * @see AbiType
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
