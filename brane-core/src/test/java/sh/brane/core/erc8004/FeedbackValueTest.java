// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class FeedbackValueTest {

    @Test
    void of_createsFromLong() {
        var fv = FeedbackValue.of(9977, 2);
        assertEquals(BigInteger.valueOf(9977), fv.value());
        assertEquals(2, fv.decimals());
    }

    @Test
    void toBigDecimal_scalesCorrectly() {
        // 9977 with 2 decimals → 99.77
        var fv = FeedbackValue.of(9977, 2);
        assertEquals(new BigDecimal("99.77"), fv.toBigDecimal());
    }

    @Test
    void toBigDecimal_negativeValue() {
        // -32 with 1 decimal → -3.2
        var fv = FeedbackValue.of(-32, 1);
        assertEquals(new BigDecimal("-3.2"), fv.toBigDecimal());
    }

    @Test
    void toBigDecimal_zeroDecimals() {
        var fv = FeedbackValue.of(87, 0);
        assertEquals(new BigDecimal("87"), fv.toBigDecimal());
    }

    @Test
    void constructor_rejectsNullValue() {
        assertThrows(NullPointerException.class, () -> new FeedbackValue(null, 0));
    }

    @Test
    void constructor_rejectsNegativeDecimals() {
        assertThrows(IllegalArgumentException.class,
            () -> FeedbackValue.of(100, -1));
    }

    @Test
    void constructor_rejectsDecimalsOver18() {
        assertThrows(IllegalArgumentException.class,
            () -> FeedbackValue.of(100, 19));
    }

    @Test
    void constructor_accepts18Decimals() {
        var fv = FeedbackValue.of(1, 18);
        assertEquals(18, fv.decimals());
    }

    @Test
    void equality() {
        assertEquals(FeedbackValue.of(100, 2), FeedbackValue.of(100, 2));
        assertNotEquals(FeedbackValue.of(100, 2), FeedbackValue.of(100, 3));
        assertNotEquals(FeedbackValue.of(100, 2), FeedbackValue.of(99, 2));
    }
}
