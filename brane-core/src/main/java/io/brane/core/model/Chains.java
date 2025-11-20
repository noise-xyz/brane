package io.brane.core.model;

/**
 * Common chain profiles.
 */
public final class Chains {

    public static final ChainProfile MAINNET = new ChainProfile(1L, "Ethereum Mainnet", "ETH");
    public static final ChainProfile SEPOLIA = new ChainProfile(11155111L, "Sepolia", "ETH");
    public static final ChainProfile HOLESKY = new ChainProfile(17000L, "Holesky", "ETH");

    private Chains() {}
}
