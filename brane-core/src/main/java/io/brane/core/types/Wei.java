package io.brane.core.types;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;
import java.util.Objects;

/**
 * Represents a quantity in wei.
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

    public BigDecimal toEther() {
        return new BigDecimal(value).divide(WEI_PER_ETHER, 18, RoundingMode.DOWN);
    }
}
