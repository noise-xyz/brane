// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Objects;

/**
 * ERC-8004 signed fixed-point feedback score.
 *
 * <p>Represents a feedback value with explicit decimal precision, matching the
 * on-chain {@code (int128 value, uint8 valueDecimals)} pair from the Reputation
 * Registry. Examples:
 * <ul>
 *   <li>{@code value=9977, decimals=2} → 99.77%</li>
 *   <li>{@code value=-32, decimals=1} → -3.2</li>
 * </ul>
 *
 * <p>Uses {@link BigInteger} because Solidity {@code int128} exceeds Java {@code long} range.
 *
 * @param value    the raw score value
 * @param decimals the number of decimal places (0–18)
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
public record FeedbackValue(BigInteger value, int decimals) {

    public FeedbackValue {
        Objects.requireNonNull(value, "value");
        if (decimals < 0 || decimals > 18) {
            throw new IllegalArgumentException("decimals must be 0-18, got " + decimals);
        }
    }

    /**
     * Converts this feedback value to a {@link BigDecimal}.
     *
     * @return the decimal representation
     */
    public BigDecimal toBigDecimal() {
        return new BigDecimal(value, decimals);
    }

    /**
     * Creates a FeedbackValue from a long value and decimals.
     *
     * @param value    the raw score value
     * @param decimals the number of decimal places (0–18)
     * @return the feedback value
     * @throws IllegalArgumentException if decimals is out of range
     */
    public static FeedbackValue of(long value, int decimals) {
        return new FeedbackValue(BigInteger.valueOf(value), decimals);
    }
}
