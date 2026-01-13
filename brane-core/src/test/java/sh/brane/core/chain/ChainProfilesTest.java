// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.chain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChainProfilesTest {

    @Test
    void chainIdsAreCorrect() {
        assertEquals(1L, ChainProfiles.ETH_MAINNET.chainId());
        assertEquals(11155111L, ChainProfiles.ETH_SEPOLIA.chainId());
        assertEquals(8453L, ChainProfiles.BASE.chainId());
        assertEquals(84532L, ChainProfiles.BASE_SEPOLIA.chainId());
        assertEquals(31337L, ChainProfiles.ANVIL_LOCAL.chainId());
    }

    @Test
    void eip1559FlagsAreTrueForKnownChains() {
        assertTrue(ChainProfiles.ETH_MAINNET.supportsEip1559());
        assertTrue(ChainProfiles.ETH_SEPOLIA.supportsEip1559());
        assertTrue(ChainProfiles.BASE.supportsEip1559());
        assertTrue(ChainProfiles.BASE_SEPOLIA.supportsEip1559());
        assertTrue(ChainProfiles.ANVIL_LOCAL.supportsEip1559());
    }

    @Test
    void defaultRpcUrlsAreDefinedForDefaultProfiles() {
        assertNotNull(ChainProfiles.ETH_MAINNET.defaultRpcUrl());
        assertNotNull(ChainProfiles.ETH_SEPOLIA.defaultRpcUrl());
        assertNotNull(ChainProfiles.BASE.defaultRpcUrl());
        assertNotNull(ChainProfiles.BASE_SEPOLIA.defaultRpcUrl());
        assertNotNull(ChainProfiles.ANVIL_LOCAL.defaultRpcUrl());
    }

    @Test
    void defaultPriorityFeeIsPresent() {
        assertNotNull(ChainProfiles.ETH_MAINNET.defaultPriorityFeePerGas());
        assertNotNull(ChainProfiles.ETH_SEPOLIA.defaultPriorityFeePerGas());
        assertNotNull(ChainProfiles.BASE.defaultPriorityFeePerGas());
        assertNotNull(ChainProfiles.BASE_SEPOLIA.defaultPriorityFeePerGas());
        assertNotNull(ChainProfiles.ANVIL_LOCAL.defaultPriorityFeePerGas());
    }
}
