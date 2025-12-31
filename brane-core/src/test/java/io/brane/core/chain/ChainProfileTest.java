package io.brane.core.chain;

import io.brane.core.types.Wei;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

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
    void rejectsNullDefaultRpcUrl() {
        assertThrows(NullPointerException.class, () -> new ChainProfile(
                1L,
                null,  // null defaultRpcUrl
                true,
                VALID_PRIORITY_FEE
        ));
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
    void rejectsNullDefaultPriorityFeePerGas() {
        assertThrows(NullPointerException.class, () -> new ChainProfile(
                1L,
                "http://localhost:8545",
                true,
                null  // null defaultPriorityFeePerGas
        ));
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
