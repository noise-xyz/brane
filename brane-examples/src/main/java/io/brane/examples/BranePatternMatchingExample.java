package io.brane.examples;

import java.math.BigInteger;

import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.crypto.Signer;
import io.brane.core.types.Address;
import io.brane.rpc.Brane;

/**
 * Demonstrates Java 21 pattern matching with the {@link Brane} sealed interface hierarchy.
 *
 * <p>Because {@link Brane} is a sealed interface with exactly two permitted implementations
 * ({@link Brane.Reader} and {@link Brane.Signer}), Java 21 switch expressions provide
 * exhaustive type-safe pattern matching without requiring a default case.
 *
 * <p>This example shows:
 * <ul>
 *   <li>Using switch expressions with sealed interface pattern matching</li>
 *   <li>Exhaustive matching guarantees at compile time</li>
 *   <li>Type-safe access to subtype-specific methods</li>
 *   <li>Practical use cases for differentiating client capabilities</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * # Requires Anvil running locally: anvil
 * ./gradlew :brane-examples:run --no-daemon \
 *   -PmainClass=io.brane.examples.BranePatternMatchingExample
 * </pre>
 */
public final class BranePatternMatchingExample {

    // Standard Anvil test private key (account 0)
    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private static final String RPC_URL = "http://127.0.0.1:8545";

    private BranePatternMatchingExample() {}

    public static void main(String[] args) throws Exception {
        System.out.println("=== Brane Pattern Matching Examples ===\n");

        // Example 1: Basic pattern matching with switch expression
        demonstrateBasicPatternMatching();

        // Example 2: Pattern matching to extract capabilities
        demonstrateCapabilityExtraction();

        // Example 3: Pattern matching in a utility method
        demonstrateUtilityMethod();

        System.out.println("\n=== All examples completed successfully ===");
    }

    /**
     * Example 1: Basic switch expression pattern matching.
     *
     * <p>Java 21's sealed interface pattern matching allows exhaustive switching
     * without a default case. The compiler guarantees all cases are handled.
     */
    private static void demonstrateBasicPatternMatching() throws Exception {
        System.out.println("--- Example 1: Basic Pattern Matching ---");

        // Create a read-only client
        Brane readOnlyClient = Brane.connect(RPC_URL);
        try {
            String clientType = switch (readOnlyClient) {
                case Brane.Reader r -> "Read-only client (Reader)";
                case Brane.Signer s -> "Signing client (Signer)";
            };
            System.out.println("Client type: " + clientType);
        } finally {
            readOnlyClient.close();
        }

        // Create a signing client
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane signingClient = Brane.connect(RPC_URL, signer);
        try {
            String clientType = switch (signingClient) {
                case Brane.Reader r -> "Read-only client (Reader)";
                case Brane.Signer s -> "Signing client (Signer)";
            };
            System.out.println("Client type: " + clientType);
        } finally {
            signingClient.close();
        }
        System.out.println();
    }

    /**
     * Example 2: Using pattern matching to extract type-specific capabilities.
     *
     * <p>Pattern matching allows safe access to subtype-specific methods
     * without explicit casting.
     */
    private static void demonstrateCapabilityExtraction() throws Exception {
        System.out.println("--- Example 2: Capability Extraction ---");

        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane client = Brane.connect(RPC_URL, signer);

        try {
            // Pattern matching extracts the Signer, giving access to signer()
            Address signerAddress = switch (client) {
                case Brane.Reader r -> {
                    System.out.println("Reader cannot access signer address");
                    yield null;
                }
                case Brane.Signer s -> {
                    System.out.println("Extracted signer with address: " + s.signer().address());
                    yield s.signer().address();
                }
            };

            // All Brane clients support read operations
            BigInteger balance = client.getBalance(signerAddress);
            System.out.println("Balance: " + balance + " wei");
        } finally {
            client.close();
        }
        System.out.println();
    }

    /**
     * Example 3: Pattern matching in a utility method for conditional operations.
     *
     * <p>This pattern is useful when writing library code that accepts any Brane
     * client but may perform additional operations if the client can sign.
     */
    private static void demonstrateUtilityMethod() throws Exception {
        System.out.println("--- Example 3: Utility Method Pattern ---");

        // Test with read-only client
        Brane readOnly = Brane.connect(RPC_URL);
        try {
            describeClient(readOnly);
        } finally {
            readOnly.close();
        }

        // Test with signing client
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane signing = Brane.connect(RPC_URL, signer);
        try {
            describeClient(signing);
        } finally {
            signing.close();
        }
        System.out.println();
    }

    /**
     * Utility method demonstrating pattern matching for polymorphic behavior.
     *
     * @param client any Brane client (Reader or Signer)
     */
    private static void describeClient(Brane client) {
        // Common operations available on all clients
        BigInteger chainId = client.chainId();

        // Pattern-specific description
        String description = switch (client) {
            case Brane.Reader r -> String.format(
                    "Read-only client on chain %d - can query but cannot send transactions",
                    chainId);
            case Brane.Signer s -> String.format(
                    "Signing client on chain %d - signer address: %s, can send transactions",
                    chainId, s.signer().address());
        };

        System.out.println(description);
    }
}
