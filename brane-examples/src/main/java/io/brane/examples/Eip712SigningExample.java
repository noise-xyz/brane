package io.brane.examples;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import io.brane.core.AnsiColors;
import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.crypto.Signature;
import io.brane.core.crypto.eip712.Eip712Domain;
import io.brane.core.crypto.eip712.TypeDefinition;
import io.brane.core.crypto.eip712.TypedData;
import io.brane.core.crypto.eip712.TypedDataField;
import io.brane.core.crypto.eip712.TypedDataJson;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;

/**
 * Demonstrates EIP-712 typed data signing using Brane's type-safe API.
 *
 * <p>EIP-712 provides a way to sign structured data with clear domain separation,
 * enabling users to see exactly what they're signing in wallet UIs. This is essential
 * for protocols like ERC-2612 (permit), ERC-20 approvals, and off-chain order signing.
 *
 * <p>This example shows two approaches:
 * <ol>
 *   <li><b>Type-Safe (Compile-time)</b>: Using Java records with TypeDefinition for
 *       strong typing and compile-time safety</li>
 *   <li><b>Dynamic (Runtime)</b>: Parsing JSON from dapps using TypedDataJson for
 *       wallet-style signing of arbitrary typed data</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *     -PmainClass=io.brane.examples.Eip712SigningExample
 * </pre>
 */
public final class Eip712SigningExample {

    // Test private key (Anvil's default account #0)
    private static final String PRIVATE_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private Eip712SigningExample() {
        // Prevent instantiation
    }

    // =========================================================================
    // Type-Safe Approach: Define a Permit record with its TypeDefinition
    // =========================================================================

    /**
     * ERC-2612 Permit message structure.
     *
     * <p>This record represents the data signed for gasless token approvals.
     * The DEFINITION constant maps Java record fields to Solidity types.
     */
    public record Permit(
            Address owner,
            Address spender,
            BigInteger value,
            BigInteger nonce,
            BigInteger deadline) {

        /**
         * TypeDefinition maps the Java record to EIP-712 struct format.
         *
         * <p>Key points:
         * <ul>
         *   <li>primaryType: "Permit" - the main struct being signed</li>
         *   <li>types: Map of struct name to field definitions</li>
         *   <li>Field order matters - must match the Solidity struct</li>
         * </ul>
         */
        public static final TypeDefinition<Permit> DEFINITION = TypeDefinition.forRecord(
                Permit.class,
                "Permit",
                Map.of("Permit", List.of(
                        TypedDataField.of("owner", "address"),
                        TypedDataField.of("spender", "address"),
                        TypedDataField.of("value", "uint256"),
                        TypedDataField.of("nonce", "uint256"),
                        TypedDataField.of("deadline", "uint256"))));
    }

    // =========================================================================
    // Another Example: Mail struct with nested Person type
    // =========================================================================

    /**
     * Example nested struct for demonstrating complex EIP-712 types.
     */
    public record Person(String name, Address wallet) {
    }

    /**
     * Mail message with nested Person types.
     *
     * <p>Demonstrates how to define types with nested structs.
     */
    public record Mail(Person from, Person to, String contents) {

        /**
         * TypeDefinition for Mail with nested Person type.
         *
         * <p>When using nested structs, all referenced types must be included
         * in the types map. The primary type fields reference nested types
         * by their struct name (e.g., "Person" instead of a Solidity primitive).
         */
        public static final TypeDefinition<Mail> DEFINITION = TypeDefinition.forRecord(
                Mail.class,
                "Mail",
                Map.of(
                        "Mail", List.of(
                                TypedDataField.of("from", "Person"),
                                TypedDataField.of("to", "Person"),
                                TypedDataField.of("contents", "string")),
                        "Person", List.of(
                                TypedDataField.of("name", "string"),
                                TypedDataField.of("wallet", "address"))));
    }

    public static void main(String[] args) {
        System.out.println("=== EIP-712 Typed Data Signing Example ===\n");

        // Create signer from private key
        var signer = new PrivateKeySigner(PRIVATE_KEY);
        System.out.println("Signer address: " + signer.address().value());

        // =====================================================================
        // [1] Type-Safe Approach: ERC-2612 Permit
        // =====================================================================
        System.out.println("\n[1] Type-Safe Permit Signing (ERC-2612 Style)");

        // Define the domain separator - this uniquely identifies the protocol/contract
        // that will verify the signature. All fields are optional but should match
        // what the verifying contract expects.
        Address tokenAddress = new Address("0x6B175474E89094C44Da98b954EecdBeAC495271d");
        Eip712Domain permitDomain = Eip712Domain.builder()
                .name("MyToken")           // Protocol/token name
                .version("1")              // Domain version
                .chainId(1L)               // EIP-155 chain ID (1 = mainnet)
                .verifyingContract(tokenAddress) // Contract that verifies the sig
                .build();

        // Create the permit message
        // In a real scenario, nonce comes from the contract, deadline is set appropriately
        Address owner = signer.address();
        Address spender = new Address("0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D"); // Uniswap router
        var permit = new Permit(
                owner,
                spender,
                BigInteger.valueOf(1_000_000),  // value: 1M tokens (in smallest unit)
                BigInteger.ZERO,                 // nonce: first permit
                BigInteger.valueOf(Long.MAX_VALUE) // deadline: far future
        );

        // Create TypedData combining domain + definition + message
        TypedData<Permit> typedData = TypedData.create(permitDomain, Permit.DEFINITION, permit);

        // Compute hash (useful for verification)
        Hash permitHash = typedData.hash();
        System.out.println(AnsiColors.success("Permit hash: " + permitHash.value()));

        // Sign the typed data
        Signature permitSig = typedData.sign(signer);
        System.out.println(AnsiColors.success("Signature: " + permitSig));

        // =====================================================================
        // [2] Dynamic Approach: Parsing JSON from dApp
        // =====================================================================
        System.out.println("\n[2] Dynamic JSON Parsing (Wallet-Style)");

        // This JSON format is what wallets receive from dapps via eth_signTypedData_v4.
        // It's compatible with MetaMask, WalletConnect, and other wallet providers.
        String jsonFromDapp = """
                {
                    "domain": {
                        "name": "Ether Mail",
                        "version": "1",
                        "chainId": 1,
                        "verifyingContract": "0xCcCCccccCCCCcCCCCCCcCcCccCcCCCcCcccccccC"
                    },
                    "primaryType": "Mail",
                    "types": {
                        "EIP712Domain": [
                            {"name": "name", "type": "string"},
                            {"name": "version", "type": "string"},
                            {"name": "chainId", "type": "uint256"},
                            {"name": "verifyingContract", "type": "address"}
                        ],
                        "Person": [
                            {"name": "name", "type": "string"},
                            {"name": "wallet", "type": "address"}
                        ],
                        "Mail": [
                            {"name": "from", "type": "Person"},
                            {"name": "to", "type": "Person"},
                            {"name": "contents", "type": "string"}
                        ]
                    },
                    "message": {
                        "from": {
                            "name": "Alice",
                            "wallet": "0xCD2a3d9F938E13CD947Ec05AbC7FE734Df8DD826"
                        },
                        "to": {
                            "name": "Bob",
                            "wallet": "0xbBbBBBBbbBBBbbbBbbBbbbbBBbBbbbbBbBbbBBbB"
                        },
                        "contents": "Hello, Bob!"
                    }
                }
                """;

        // Parse JSON and create TypedData in one step
        // The wildcard type (?) indicates dynamic structure determined at runtime
        TypedData<?> dynamicTypedData = TypedDataJson.parseAndValidate(jsonFromDapp);

        // Compute hash
        Hash mailHash = dynamicTypedData.hash();
        System.out.println(AnsiColors.success("Mail hash: " + mailHash.value()));

        // Sign the dynamic typed data
        Signature mailSig = dynamicTypedData.sign(signer);
        System.out.println(AnsiColors.success("Signature: " + mailSig));

        // =====================================================================
        // [3] Accessing TypedData Properties
        // =====================================================================
        System.out.println("\n[3] Inspecting TypedData Properties");

        // TypedData provides accessors for all components
        System.out.println("Domain name: " + dynamicTypedData.domain().name());
        System.out.println("Domain version: " + dynamicTypedData.domain().version());
        System.out.println("Primary type: " + dynamicTypedData.primaryType());
        System.out.println("Message: " + dynamicTypedData.message());

        System.out.println("\n" + AnsiColors.success("EIP-712 signing examples completed successfully!"));
    }
}
