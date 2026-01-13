// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.examples;

import sh.brane.core.chain.ChainProfile;
import sh.brane.core.chain.ChainProfiles;
import sh.brane.core.crypto.PrivateKeySigner;
import sh.brane.core.crypto.Signer;
import sh.brane.core.types.Wei;
import sh.brane.rpc.Brane;
import sh.brane.rpc.RpcRetryConfig;

/**
 * Demonstrates the {@link Brane#builder()} API with chain profile configuration.
 *
 * <p>This example shows:
 * <ul>
 *   <li>Using predefined chain profiles from {@link ChainProfiles}</li>
 *   <li>Creating custom chain profiles with {@link ChainProfile#of}</li>
 *   <li>Configuring retry behavior with {@link RpcRetryConfig}</li>
 *   <li>Building read-only clients with {@link Brane.Builder#buildReader()}</li>
 *   <li>Building signing clients with {@link Brane.Builder#buildSigner()}</li>
 *   <li>Accessing chain metadata via {@link Brane#chain()}</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * # Requires Anvil running locally: anvil
 * ./gradlew :brane-examples:run --no-daemon \
 *   -PmainClass=sh.brane.examples.BraneBuilderExample
 * </pre>
 */
public final class BraneBuilderExample {

    // Standard Anvil test private key (account 0)
    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private BraneBuilderExample() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== Brane.builder() Examples ===\n");

        // Example 1: Using a predefined chain profile
        demonstratePredefinedProfile();

        // Example 2: Creating a custom chain profile
        demonstrateCustomProfile();

        // Example 3: Configuring retry behavior
        demonstrateRetryConfiguration();

        // Example 4: Building a signing client
        demonstrateSigningClient();

        System.out.println("\n=== All examples completed successfully ===");
    }

    /**
     * Example 1: Using predefined chain profiles from {@link ChainProfiles}.
     */
    private static void demonstratePredefinedProfile() throws Exception {
        System.out.println("--- Example 1: Predefined ChainProfile ---");

        // Use the ANVIL_LOCAL profile for local development
        Brane.Reader client = Brane.builder()
                .rpcUrl("http://127.0.0.1:8545")
                .chain(ChainProfiles.ANVIL_LOCAL)
                .buildReader();

        try {
            // Access the chain profile metadata
            ChainProfile profile = client.chain().orElseThrow();
            System.out.println("Chain ID from profile: " + profile.chainId());
            System.out.println("Chain ID from RPC: " + client.chainId());
            System.out.println("Supports EIP-1559: " + profile.supportsEip1559());
            System.out.println("Default priority fee: " + profile.defaultPriorityFeePerGas());
            System.out.println("Latest block: " + client.getLatestBlock().number());
        } finally {
            client.close();
        }
        System.out.println();
    }

    /**
     * Example 2: Creating a custom chain profile for a specific network.
     */
    private static void demonstrateCustomProfile() throws Exception {
        System.out.println("--- Example 2: Custom ChainProfile ---");

        // Create a custom profile for a private network or L2
        // Example: A private network with chain ID 12345
        ChainProfile customProfile = ChainProfile.of(
                31337L,                           // chainId (using Anvil for demo)
                "http://127.0.0.1:8545",         // default RPC URL
                true,                             // supportsEip1559
                Wei.of(2_000_000_000L)           // custom priority fee: 2 gwei
        );

        Brane.Reader client = Brane.builder()
                .rpcUrl("http://127.0.0.1:8545")
                .chain(customProfile)
                .buildReader();

        try {
            ChainProfile profile = client.chain().orElseThrow();
            System.out.println("Custom profile chainId: " + profile.chainId());
            System.out.println("Custom priority fee: " + profile.defaultPriorityFeePerGas());
            System.out.println("Latest block: " + client.getLatestBlock().number());
        } finally {
            client.close();
        }
        System.out.println();
    }

    /**
     * Example 3: Configuring retry behavior for resilient RPC calls.
     */
    private static void demonstrateRetryConfiguration() throws Exception {
        System.out.println("--- Example 3: Retry Configuration ---");

        // Custom retry config with aggressive backoff for high-traffic scenarios
        RpcRetryConfig customRetryConfig = RpcRetryConfig.builder()
                .backoffBaseMs(500)      // start with 500ms
                .backoffMaxMs(30_000)    // cap at 30 seconds
                .jitterMin(0.10)         // 10% minimum jitter
                .jitterMax(0.30)         // 30% maximum jitter
                .build();

        Brane.Reader client = Brane.builder()
                .rpcUrl("http://127.0.0.1:8545")
                .chain(ChainProfiles.ANVIL_LOCAL)
                .retries(5)                     // retry up to 5 times on transient failures
                .retryConfig(customRetryConfig)
                .buildReader();

        try {
            System.out.println("Client configured with 5 retries and custom backoff");
            System.out.println("Latest block: " + client.getLatestBlock().number());
        } finally {
            client.close();
        }
        System.out.println();
    }

    /**
     * Example 4: Building a signing client for transactions.
     */
    private static void demonstrateSigningClient() throws Exception {
        System.out.println("--- Example 4: Signing Client with ChainProfile ---");

        // Create a signer from a private key
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);

        // Build a full-featured signing client
        Brane.Signer client = Brane.builder()
                .rpcUrl("http://127.0.0.1:8545")
                .chain(ChainProfiles.ANVIL_LOCAL)
                .signer(signer)
                .retries(3)
                .buildSigner();

        try {
            System.out.println("Signer address: " + signer.address());
            System.out.println("Can sign: " + client.canSign());
            System.out.println("Can subscribe: " + client.canSubscribe());
            System.out.println("Chain profile: " + client.chain().orElse(null));
            System.out.println("Balance: " + client.getBalance(signer.address()) + " wei");
            // Note: We don't send a transaction in this example to keep it simple
            // For transaction examples, see CanonicalTxExample.java
        } finally {
            client.close();
        }
    }
}
