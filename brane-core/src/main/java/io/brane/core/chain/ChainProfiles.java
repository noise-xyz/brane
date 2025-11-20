package io.brane.core.chain;

public final class ChainProfiles {
    private ChainProfiles() {}

    public static final ChainProfile ETH_MAINNET =
            ChainProfile.of(1L, "https://ethereum.publicnode.com", true);

    public static final ChainProfile ETH_SEPOLIA =
            ChainProfile.of(11155111L, "https://sepolia.infura.io/v3/YOUR_KEY", true);

    public static final ChainProfile BASE =
            ChainProfile.of(8453L, "https://mainnet.base.org", true);

    public static final ChainProfile BASE_SEPOLIA =
            ChainProfile.of(84532L, "https://sepolia.base.org", true);

    public static final ChainProfile ANVIL_LOCAL =
            ChainProfile.of(31337L, "http://127.0.0.1:8545", true);
}
