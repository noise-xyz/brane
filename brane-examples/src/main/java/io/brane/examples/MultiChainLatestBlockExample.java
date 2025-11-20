package io.brane.examples;

import io.brane.core.chain.ChainProfile;
import io.brane.core.chain.ChainProfiles;
import io.brane.rpc.BranePublicClient;

public final class MultiChainLatestBlockExample {

    private MultiChainLatestBlockExample() {}

    public static void main(String[] args) {
        // 1) Always: sanity check Anvil/local (using ANVIL_LOCAL profile)
        System.out.println("=== ANVIL_LOCAL sanity check ===");
        BranePublicClient anvilClient = BranePublicClient
                .forChain(ChainProfiles.ANVIL_LOCAL)
                .build();
        System.out.println("Anvil ChainProfile: " + anvilClient.profile());
        System.out.println("Anvil latest block: " + anvilClient.getLatestBlock().number());
        System.out.println();

        // 2) Optional: real Base Sepolia RPC, passed via -Dbrane.examples.rpc.base-sepolia
        // Example URL: https://sepolia.base.org
        String baseSepoliaUrl = System.getProperty("brane.examples.rpc.base-sepolia");
        if (baseSepoliaUrl != null && !baseSepoliaUrl.isBlank()) {
            System.out.println("=== BASE_SEPOLIA smoke test ===");
            System.out.println("Using RPC URL: " + baseSepoliaUrl);

            ChainProfile profile = ChainProfiles.BASE_SEPOLIA;
            BranePublicClient baseSepoliaClient = BranePublicClient
                    .forChain(profile)
                    .withRpcUrl(baseSepoliaUrl)
                    .build();

            System.out.println("Base Sepolia ChainProfile: " + baseSepoliaClient.profile());
            System.out.println(
                    "Base Sepolia latest block: " + baseSepoliaClient.getLatestBlock().number());
            System.out.println();
        } else {
            System.out.println("Skipping BASE_SEPOLIA smoke test: "
                    + "no -Dbrane.examples.rpc.base-sepolia provided.");
        }
    }
}
