// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.chain;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Wei;

/**
 * Tests for ChainProfile validation.
 */
class ChainProfileTest {

    private static final Wei VALID_PRIORITY_FEE = Wei.of(1_000_000_000L);

    @Test
    void validChainProfileCreation() {
        ChainProfile profile = new ChainProfile(
                1L,
                "http://localhost:8545",
                true,
                VALID_PRIORITY_FEE
        );

        assertNotNull(profile);
        assertEquals(1L, profile.chainId());
        assertEquals("http://localhost:8545", profile.defaultRpcUrl());
        assertTrue(profile.supportsEip1559());
        assertEquals(VALID_PRIORITY_FEE, profile.defaultPriorityFeePerGas());
    }

    @Test
    void staticFactoryMethodWorks() {
        ChainProfile profile = ChainProfile.of(
                1L,
                "http://localhost:8545",
                true,
                VALID_PRIORITY_FEE
        );

        assertEquals(1L, profile.chainId());
    }

    @Test
    void rejectsZeroChainId() {
        assertThrows(IllegalArgumentException.class, () -> new ChainProfile(
                0L,  // zero chainId
                "http://localhost:8545",
                true,
                VALID_PRIORITY_FEE
        ));
    }

    @Test
    void rejectsNegativeChainId() {
        assertThrows(IllegalArgumentException.class, () -> new ChainProfile(
                -1L,  // negative chainId
                "http://localhost:8545",
                true,
                VALID_PRIORITY_FEE
        ));
    }

    @Test
    void acceptsNullDefaultRpcUrl() {
        ChainProfile profile = new ChainProfile(
                1L,
                null,  // null defaultRpcUrl is valid (URL must be provided at build time)
                true,
                VALID_PRIORITY_FEE
        );

        assertNull(profile.defaultRpcUrl());
    }

    @Test
    void rejectsEmptyDefaultRpcUrl() {
        assertThrows(IllegalArgumentException.class, () -> new ChainProfile(
                1L,
                "",  // empty defaultRpcUrl
                true,
                VALID_PRIORITY_FEE
        ));
    }

    @Test
    void rejectsBlankDefaultRpcUrl() {
        assertThrows(IllegalArgumentException.class, () -> new ChainProfile(
                1L,
                "   ",  // blank defaultRpcUrl
                true,
                VALID_PRIORITY_FEE
        ));
    }

    @Test
    void acceptsNullDefaultPriorityFeePerGas() {
        ChainProfile profile = new ChainProfile(
                1L,
                "http://localhost:8545",
                false,  // non-EIP-1559 chain may not have default priority fee
                null    // null defaultPriorityFeePerGas is valid
        );

        assertNull(profile.defaultPriorityFeePerGas());
    }

    @Test
    void supportsNonEip1559Chains() {
        ChainProfile profile = new ChainProfile(
                1L,
                "http://localhost:8545",
                false,  // non-EIP-1559
                VALID_PRIORITY_FEE
        );

        assertFalse(profile.supportsEip1559());
    }
}
