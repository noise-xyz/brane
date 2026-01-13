// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.model;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import sh.brane.core.types.Address;

/**
 * Tests for AssetChange validation and construction.
 */
class AssetChangeTest {

    private static final Address TOKEN_ADDRESS = new Address("0x" + "a".repeat(40));

    @Test
    void validAssetChangeCreation() {
        AssetToken token = new AssetToken(TOKEN_ADDRESS, 18, "DAI");
        AssetValue value = new AssetValue(BigInteger.ZERO, BigInteger.TEN, BigInteger.TEN);

        AssetChange change = new AssetChange(token, value);

        assertNotNull(change);
        assertEquals(token, change.token());
        assertEquals(value, change.value());
    }

    @Test
    void rejectsNullToken() {
        AssetValue value = new AssetValue(BigInteger.ZERO, BigInteger.TEN, BigInteger.TEN);
        assertThrows(NullPointerException.class, () -> new AssetChange(null, value));
    }

    @Test
    void rejectsNullValue() {
        AssetToken token = new AssetToken(TOKEN_ADDRESS, 18, "DAI");
        assertThrows(NullPointerException.class, () -> new AssetChange(token, null));
    }
}
