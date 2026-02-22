// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import sh.brane.core.types.Address;

/**
 * Known ERC-8004 registry deployment addresses.
 *
 * <p>ERC-8004 contracts use deterministic CREATE2 deployment with {@code 0x8004}
 * vanity prefix. <b>The same addresses are shared across all mainnet EVM chains</b>
 * (Ethereum, Base, Arbitrum, Polygon, Optimism, Avalanche, Celo, Gnosis, Linea,
 * Mantle, Scroll, Taiko, etc.).
 *
 * @see <a href="https://github.com/erc-8004/erc-8004-contracts">erc-8004-contracts</a>
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
public final class Erc8004Addresses {

    // ─── Mainnet (all EVM chains — deterministic CREATE2 deployment) ───

    /** Identity Registry address shared across all mainnet EVM chains. */
    public static final Address MAINNET_IDENTITY =
        new Address("0x8004a169fb4a3325136eb29fa0ceb6d2e539a432");

    /** Reputation Registry address shared across all mainnet EVM chains. */
    public static final Address MAINNET_REPUTATION =
        new Address("0x8004baa17c55a88189ae136b182e5fda19de9b63");

    // ─── Testnets (Sepolia, Base Sepolia, Arbitrum Sepolia — shared addresses) ───

    /** Identity Registry address shared across Sepolia testnets. */
    public static final Address SEPOLIA_IDENTITY =
        new Address("0x8004a818bfb912233c491871b3d84c89a494bd9e");

    /** Reputation Registry address shared across Sepolia testnets. */
    public static final Address SEPOLIA_REPUTATION =
        new Address("0x8004b663056a597dffe9eccc1965a193b7388713");

    // ─── Reference Implementation (ChaosChain — Sepolia only) ───

    /** ChaosChain reference implementation Identity Registry on Sepolia. */
    public static final Address RI_SEPOLIA_IDENTITY =
        new Address("0xf66e7cbdae1cb710fee7732e4e1f173624e137a7");

    /** ChaosChain reference implementation Reputation Registry on Sepolia. */
    public static final Address RI_SEPOLIA_REPUTATION =
        new Address("0x6e2a285294b5c74cb76d76ab77c1ef15c2a9e407");

    /** ChaosChain reference implementation Validation Registry on Sepolia. */
    public static final Address RI_SEPOLIA_VALIDATION =
        new Address("0xc26171a3c4e1d958cea196a5e84b7418c58dca2c");

    private Erc8004Addresses() {}
}
