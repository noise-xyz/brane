package io.brane.core.types;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Represents a quantity in Wei (10^-18 Ether).
 * <p>
 * This is the smallest denomination of Ether.
 * <p>
 * <strong>Common Conversions:</strong>
 * <ul>
 * <li>1 Ether = 10^18 Wei</li>
 * <li>1 Gwei = 10^9 Wei</li>
 * </ul>
 * <p>
 * Use {@link #fromEther(BigDecimal)} and {@link #toEther()} for convenient
 * conversions.
 */
public record Wei(BigInteger value) {
    private static final BigDecimal WEI_PER_ETHER = BigDecimal.TEN.pow(18);

    public Wei {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Wei must be non-negative");
        }
    }

    public static Wei of(final long wei) {
        return new Wei(BigInteger.valueOf(wei));
    }

    public static Wei of(final BigInteger wei) {
        return new Wei(wei);
    }

    public static Wei fromEther(final BigDecimal ether) {
        Objects.requireNonNull(ether, "ether");
        return new Wei(ether.multiply(WEI_PER_ETHER).toBigIntegerExact());
    }

    private static final BigInteger GWEI_MULTIPLIER = BigInteger.valueOf(1_000_000_000L);

    public static Wei gwei(final long gwei) {
        return new Wei(BigInteger.valueOf(gwei).multiply(GWEI_MULTIPLIER));
    }

    public BigDecimal toEther() {
        return new BigDecimal(value).divide(WEI_PER_ETHER, 18, RoundingMode.DOWN);
    }

    @com.fasterxml.jackson.annotation.JsonValue
    public String toHexString() {
        return "0x" + value.toString(16);
    }
}
