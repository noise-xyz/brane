package io.brane.core.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

/**
 * Tests for AssetValue validation and construction.
 */
class AssetValueTest {

    @Test
    void validAssetValueCreation() {
        BigInteger pre = new BigInteger("1000");
        BigInteger post = new BigInteger("1500");
        BigInteger diff = new BigInteger("500");

        AssetValue value = new AssetValue(pre, post, diff);

        assertNotNull(value);
        assertEquals(pre, value.pre());
        assertEquals(post, value.post());
        assertEquals(diff, value.diff());
    }

    @Test
    void validNegativeChange() {
        BigInteger pre = new BigInteger("1000");
        BigInteger post = new BigInteger("200");
        BigInteger diff = new BigInteger("-800");

        AssetValue value = new AssetValue(pre, post, diff);

        assertNotNull(value);
        assertEquals(diff, value.diff());
        assertTrue(value.diff().signum() < 0);
    }

    @Test
    void rejectsNullPre() {
        assertThrows(NullPointerException.class, () -> new AssetValue(null, BigInteger.ONE, BigInteger.ONE));
    }

    @Test
    void rejectsNullPost() {
        assertThrows(NullPointerException.class, () -> new AssetValue(BigInteger.ONE, null, BigInteger.ONE));
    }

    @Test
    void rejectsNullDiff() {
        assertThrows(NullPointerException.class, () -> new AssetValue(BigInteger.ONE, BigInteger.ONE, null));
    }

    @Test
    void rejectsInconsistentDiff() {
        BigInteger pre = new BigInteger("1000");
        BigInteger post = new BigInteger("1500");
        BigInteger wrongDiff = new BigInteger("100"); // Should be 500

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> new AssetValue(pre, post, wrongDiff));
        assertTrue(ex.getMessage().contains("Inconsistent AssetValue"));
    }
}
