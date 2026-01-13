// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.abi;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Represents a Solidity unsigned integer (uint8 to uint256).
 *
 * @param width the bit width (must be a multiple of 8, between 8 and 256)
 * @param value the BigInteger value (must be non-negative and fit in width)
 */
public record UInt(int width, BigInteger value) implements StaticAbiType {
    public UInt {
        if (width % 8 != 0 || width < 8 || width > 256) {
            throw new IllegalArgumentException("Invalid uint width: " + width);
        }
        Objects.requireNonNull(value, "value cannot be null");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("uint cannot be negative");
        }
        if (value.bitLength() > width) {
            throw new IllegalArgumentException("value " + value + " too large for uint" + width);
        }
    }

    @Override
    public String typeName() {
        return "uint" + width;
    }
}
