// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class Erc8004AddressesTest {

    @Test
    void mainnet_identity_hasVanityPrefix() {
        assertTrue(Erc8004Addresses.MAINNET_IDENTITY.value().startsWith("0x8004"));
    }

    @Test
    void mainnet_reputation_hasVanityPrefix() {
        assertTrue(Erc8004Addresses.MAINNET_REPUTATION.value().startsWith("0x8004"));
    }

    @Test
    void sepolia_identity_hasVanityPrefix() {
        assertTrue(Erc8004Addresses.SEPOLIA_IDENTITY.value().startsWith("0x8004"));
    }

    @Test
    void sepolia_reputation_hasVanityPrefix() {
        assertTrue(Erc8004Addresses.SEPOLIA_REPUTATION.value().startsWith("0x8004"));
    }

    @Test
    void mainnet_addresses_areDifferentFromSepolia() {
        assertNotEquals(Erc8004Addresses.MAINNET_IDENTITY, Erc8004Addresses.SEPOLIA_IDENTITY);
        assertNotEquals(Erc8004Addresses.MAINNET_REPUTATION, Erc8004Addresses.SEPOLIA_REPUTATION);
    }

    @Test
    void ri_sepolia_addresses_areDifferentFromOfficial() {
        assertNotEquals(Erc8004Addresses.RI_SEPOLIA_IDENTITY, Erc8004Addresses.SEPOLIA_IDENTITY);
        assertNotEquals(Erc8004Addresses.RI_SEPOLIA_REPUTATION, Erc8004Addresses.SEPOLIA_REPUTATION);
    }

    @Test
    void all_addresses_areNonNull() {
        assertNotNull(Erc8004Addresses.MAINNET_IDENTITY);
        assertNotNull(Erc8004Addresses.MAINNET_REPUTATION);
        assertNotNull(Erc8004Addresses.SEPOLIA_IDENTITY);
        assertNotNull(Erc8004Addresses.SEPOLIA_REPUTATION);
        assertNotNull(Erc8004Addresses.RI_SEPOLIA_IDENTITY);
        assertNotNull(Erc8004Addresses.RI_SEPOLIA_REPUTATION);
        assertNotNull(Erc8004Addresses.RI_SEPOLIA_VALIDATION);
    }
}
