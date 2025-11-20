package io.brane.examples;

import io.brane.core.chain.ChainProfile;
import io.brane.core.chain.ChainProfiles;

public final class ChainProfilesDumpExample {

    private ChainProfilesDumpExample() {}

    public static void main(String[] args) {
        ChainProfile[] profiles = {
            ChainProfiles.ETH_MAINNET,
            ChainProfiles.ETH_SEPOLIA,
            ChainProfiles.BASE,
            ChainProfiles.BASE_SEPOLIA,
            ChainProfiles.ANVIL_LOCAL
        };

        System.out.println("=== Brane Chain Profiles Dump ===");
        for (ChainProfile p : profiles) {
            System.out.println(p);
        }
        System.out.println("=================================");
    }
}
