package io.brane.core.chain;

import io.brane.core.types.Wei;

public final class ChainProfiles {
    private ChainProfiles() {}

    public static final ChainProfile ETH_MAINNET =
            ChainProfile.of(1L, "https://ethereum.publicnode.com", true, Wei.of(1_000_000_000L));

    public static final ChainProfile ETH_SEPOLIA =
            ChainProfile.of(11155111L, "https://sepolia.infura.io/v3/YOUR_KEY", true, Wei.of(1_000_000_000L));

    public static final ChainProfile BASE =
            ChainProfile.of(8453L, "https://mainnet.base.org", true, Wei.of(1_000_000_000L));

    public static final ChainProfile BASE_SEPOLIA =
            ChainProfile.of(84532L, "https://sepolia.base.org", true, Wei.of(1_000_000_000L));

    public static final ChainProfile ANVIL_LOCAL =
            ChainProfile.of(31337L, "http://127.0.0.1:8545", true, Wei.of(1_000_000_000L));
}
