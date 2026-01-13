// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.chain;

import sh.brane.core.types.Wei;

/**
 * Pre-configured chain profiles for common Ethereum networks.
 *
 * <p>
 * These profiles provide sensible defaults for popular networks. The default RPC
 * endpoints use public nodes which may have rate limits. For production use, you
 * should create custom {@link ChainProfile} instances with your own RPC endpoints
 * from providers like Alchemy, Infura, or QuickNode.
 *
 * <p>
 * <strong>Example: Custom profile with your own RPC endpoint</strong>
 *
 * <pre>{@code
 * ChainProfile myProfile = ChainProfile.of(
 *     1L,                                   // chainId
 *     "https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY",  // your RPC URL
 *     true,                                 // supportsEip1559
 *     Wei.of(1_000_000_000L)               // priority fee (1 gwei)
 * );
 * }</pre>
 *
 * @see ChainProfile
 */
public final class ChainProfiles {
    private ChainProfiles() {}

    /**
     * Ethereum Mainnet (chainId: 1).
     * Uses a public RPC endpoint with potential rate limits.
     */
    public static final ChainProfile ETH_MAINNET =
            ChainProfile.of(1L, "https://ethereum.publicnode.com", true, Wei.of(1_000_000_000L));

    /**
     * Ethereum Sepolia testnet (chainId: 11155111).
     * Uses a public RPC endpoint with potential rate limits.
     */
    public static final ChainProfile ETH_SEPOLIA =
            ChainProfile.of(11155111L, "https://ethereum-sepolia.publicnode.com", true, Wei.of(1_000_000_000L));

    /**
     * Base Mainnet (chainId: 8453).
     * Uses a public RPC endpoint with potential rate limits.
     */
    public static final ChainProfile BASE =
            ChainProfile.of(8453L, "https://mainnet.base.org", true, Wei.of(1_000_000_000L));

    /**
     * Base Sepolia testnet (chainId: 84532).
     * Uses a public RPC endpoint with potential rate limits.
     */
    public static final ChainProfile BASE_SEPOLIA =
            ChainProfile.of(84532L, "https://sepolia.base.org", true, Wei.of(1_000_000_000L));

    /**
     * Local Anvil/Hardhat development node (chainId: 31337).
     * Connects to localhost:8545 for local development.
     */
    public static final ChainProfile ANVIL_LOCAL =
            ChainProfile.of(31337L, "http://127.0.0.1:8545", true, Wei.of(1_000_000_000L));
}
