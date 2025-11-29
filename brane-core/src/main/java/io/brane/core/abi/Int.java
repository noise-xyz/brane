package io.brane.core.abi;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Represents a Solidity signed integer (int8 to int256).
 * 
 * @param width the bit width (must be a multiple of 8, between 8 and 256)
 * @param value the BigInteger value (must fit in width)
 */
public record Int(int width, BigInteger value) implements AbiType {
    public Int {
        if (width % 8 != 0 || width < 8 || width > 256) {
            throw new IllegalArgumentException("Invalid int width: " + width);
        }
        Objects.requireNonNull(value, "value cannot be null");
        // Check bounds for signed integer
        // Max: 2^(width-1) - 1
        // Min: -2^(width-1)
        if (value.bitLength() >= width && value.signum() != -1) {
            // Positive number bitLength excludes sign bit, so if bitLength == width it's
            // too big?
            // BigInteger.bitLength() returns number of bits excluding sign bit.
            // For 8-bit: 127 is 01111111 (7 bits). 128 is 10000000 (8 bits, but signed it's
            // -128).
            // Actually BigInteger.bitLength() is minimal bits to represent magnitude.
            // For signed check:
            // min = -2^(width-1)
            // max = 2^(width-1) - 1
            BigInteger max = BigInteger.TWO.pow(width - 1).subtract(BigInteger.ONE);
            BigInteger min = BigInteger.TWO.pow(width - 1).negate();
            if (value.compareTo(max) > 0 || value.compareTo(min) < 0) {
                throw new IllegalArgumentException("value " + value + " out of range for int" + width);
            }
        }
    }

    @Override
    public int byteSize() {
        return 32;
    }

    @Override
    public boolean isDynamic() {
        return false;
    }

    @Override
    public String typeName() {
        return "int" + width;
    }
}
