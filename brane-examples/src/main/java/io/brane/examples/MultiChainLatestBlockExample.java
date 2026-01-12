// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.examples;

import io.brane.core.chain.ChainProfile;
import io.brane.core.chain.ChainProfiles;
import io.brane.rpc.Brane;

/**
 * Demonstrates the new Brane.connect() API for read-only blockchain operations.
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *   -PmainClass=io.brane.examples.MultiChainLatestBlockExample
 * </pre>
 *
 * <p>Optional: Pass a Base Sepolia RPC URL:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *   -PmainClass=io.brane.examples.MultiChainLatestBlockExample \
 *   -Dbrane.examples.rpc.base-sepolia=https://sepolia.base.org
 * </pre>
 */
public final class MultiChainLatestBlockExample {

    private MultiChainLatestBlockExample() {}

    public static void main(String[] args) throws Exception {
        // 1) Always: sanity check Anvil/local using Brane.connect()
        System.out.println("=== ANVIL_LOCAL sanity check ===");
        Brane anvilClient = Brane.connect("http://127.0.0.1:8545");
        try {
            System.out.println("Connected to Anvil");
            System.out.println("Anvil latest block: " + anvilClient.getLatestBlock().number());
            System.out.println();
        } finally {
            anvilClient.close();
        }

        // 2) Optional: real Base Sepolia RPC, passed via -Dbrane.examples.rpc.base-sepolia
        // Example URL: https://sepolia.base.org
        String baseSepoliaUrl = System.getProperty("brane.examples.rpc.base-sepolia");
        if (baseSepoliaUrl != null && !baseSepoliaUrl.isBlank()) {
            System.out.println("=== BASE_SEPOLIA smoke test ===");
            System.out.println("Using RPC URL: " + baseSepoliaUrl);

            // Use builder to include chain profile for network-specific configuration
            ChainProfile profile = ChainProfiles.BASE_SEPOLIA;
            Brane baseClient = Brane.builder()
                    .rpcUrl(baseSepoliaUrl)
                    .chain(profile)
                    .buildReader();
            try {
                System.out.println("Base Sepolia ChainProfile: " + baseClient.chain().orElse(null));
                System.out.println("Base Sepolia latest block: " + baseClient.getLatestBlock().number());
                System.out.println();
            } finally {
                baseClient.close();
            }
        } else {
            System.out.println("Skipping BASE_SEPOLIA smoke test: "
                    + "no -Dbrane.examples.rpc.base-sepolia provided.");
        }
    }
}
