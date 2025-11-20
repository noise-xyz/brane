package io.brane.core.types;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class WeiTest {

    @Test
    void rejectsNegative() {
        assertThrows(IllegalArgumentException.class, () -> new Wei(BigInteger.valueOf(-1)));
    }

    @Test
    void ofHelpers() {
        assertEquals(BigInteger.TEN, Wei.of(10).value());
        assertEquals(BigInteger.TEN, Wei.of(BigInteger.TEN).value());
    }

    @Test
    void etherRoundTrip() {
        Wei wei = Wei.fromEther(new BigDecimal("1.500000000000000000"));
        assertEquals(new BigInteger("1500000000000000000"), wei.value());
        assertEquals(new BigDecimal("1.500000000000000000"), wei.toEther());
    }
}
