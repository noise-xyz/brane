// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Address;

/**
 * Tests for AssetToken validation and construction.
 */
class AssetTokenTest {

    private static final Address VALID_ADDRESS = new Address("0x" + "a".repeat(40));
    private static final Address ETH_ADDRESS = new Address("0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee");

    @Test
    void validAssetTokenWithMetadata() {
        AssetToken token = new AssetToken(VALID_ADDRESS, 18, "WETH");

        assertNotNull(token);
        assertEquals(VALID_ADDRESS, token.address());
        assertEquals(18, token.decimals());
        assertEquals("WETH", token.symbol());
    }

    @Test
    void validAssetTokenWithoutMetadata() {
        AssetToken token = new AssetToken(VALID_ADDRESS, null, null);

        assertNotNull(token);
        assertEquals(VALID_ADDRESS, token.address());
        assertNull(token.decimals());
        assertNull(token.symbol());
    }

    @Test
    void validEthToken() {
        AssetToken token = new AssetToken(ETH_ADDRESS, 18, "ETH");

        assertNotNull(token);
        assertEquals(ETH_ADDRESS, token.address());
        assertEquals(18, token.decimals());
        assertEquals("ETH", token.symbol());
    }

    @Test
    void rejectsNullAddress() {
        assertThrows(NullPointerException.class, () -> new AssetToken(null, 18, "WETH"));
    }
}
