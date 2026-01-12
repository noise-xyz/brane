// SPDX-License-Identifier: MIT OR Apache-2.0
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
import io.brane.core.types.Address;
import io.brane.core.types.Hash;

/**
 * Demonstrates ERC-2612 Permit signing using Brane's type-safe EIP-712 API.
 *
 * <p>ERC-2612 extends ERC-20 with gasless approvals via signed permits. Instead of
 * calling approve() directly (which costs gas), users sign an off-chain message that
 * anyone can submit to the permit() function. This enables:
 * <ul>
 *   <li>Gasless token approvals (relayers can pay gas)</li>
 *   <li>Single-transaction approve + transfer patterns</li>
 *   <li>Better UX by avoiding separate approval transactions</li>
 * </ul>
 *
 * <p>This example demonstrates:
 * <ol>
 *   <li>Defining a Permit record with {@link TypeDefinition#forRecord(Class, String, Map)}</li>
 *   <li>Creating the EIP-712 domain separator for a token contract</li>
 *   <li>Signing a permit message and extracting r, s, v components</li>
 *   <li>Verifying the signature hash matches expected values</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *     -PmainClass=io.brane.examples.Eip712PermitExample
 * </pre>
 */
public final class Eip712PermitExample {

    // Test private key (Anvil's default account #0)
    private static final String PRIVATE_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private Eip712PermitExample() {
        // Prevent instantiation
    }

    // =========================================================================
    // ERC-2612 Permit Record with TypeDefinition
    // =========================================================================

    /**
     * ERC-2612 Permit message structure.
     *
     * <p>This record represents the data signed for gasless token approvals.
     * The fields match the Solidity struct expected by the permit() function:
     *
     * <pre>{@code
     * struct Permit {
     *     address owner;    // Token owner granting approval
     *     address spender;  // Address being approved to spend
     *     uint256 value;    // Amount of tokens approved
     *     uint256 nonce;    // Owner's current permit nonce
     *     uint256 deadline; // Signature expiration timestamp
     * }
     * }</pre>
     *
     * <p>Using {@link TypeDefinition#forRecord(Class, String, Map)} provides:
     * <ul>
     *   <li>Compile-time type safety for permit fields</li>
     *   <li>Automatic extraction of record component values</li>
     *   <li>Clear mapping between Java types and Solidity types</li>
     * </ul>
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
         * <p>The {@code forRecord} factory method:
         * <ol>
         *   <li>Validates the class is a record type</li>
         *   <li>Creates a reflection-based extractor for field values</li>
         *   <li>Associates the type definition with the record class</li>
         * </ol>
         *
         * <p>Field order in the type definition must match the Solidity struct
         * exactly, as this affects the struct hash calculation.
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

    public static void main(String[] args) {
        System.out.println("=== ERC-2612 Permit Signing Example ===\n");

        // Create signer from private key
        var signer = new PrivateKeySigner(PRIVATE_KEY);
        Address owner = signer.address();
        System.out.println("Owner address: " + owner.value());

        // =====================================================================
        // Step 1: Define the EIP-712 Domain
        // =====================================================================
        System.out.println("\n[1] Creating EIP-712 Domain");

        // The domain separator uniquely identifies the token contract.
        // These values must match what the contract uses in its DOMAIN_SEPARATOR.
        Address tokenAddress = new Address("0x6B175474E89094C44Da98b954EecdeAC495271dF");
        Eip712Domain domain = Eip712Domain.builder()
                .name("Dai Stablecoin")       // Token name (from contract)
                .version("1")                  // Domain version
                .chainId(1L)                   // Ethereum mainnet
                .verifyingContract(tokenAddress) // Token contract address
                .build();

        System.out.println("  Token: " + domain.name());
        System.out.println("  Chain: " + domain.chainId());
        System.out.println("  Contract: " + domain.verifyingContract().value());

        // =====================================================================
        // Step 2: Create the Permit Message
        // =====================================================================
        System.out.println("\n[2] Creating Permit Message");

        // In production, nonce comes from token.nonces(owner)
        // Deadline should be a reasonable future timestamp
        Address spender = new Address("0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D"); // Uniswap V2 Router
        BigInteger value = new BigInteger("1000000000000000000000"); // 1000 tokens (18 decimals)
        BigInteger nonce = BigInteger.ZERO;
        BigInteger deadline = BigInteger.valueOf(System.currentTimeMillis() / 1000 + 3600); // 1 hour from now

        var permit = new Permit(owner, spender, value, nonce, deadline);

        System.out.println("  Owner: " + permit.owner().value());
        System.out.println("  Spender: " + permit.spender().value());
        System.out.println("  Value: " + permit.value() + " wei");
        System.out.println("  Nonce: " + permit.nonce());
        System.out.println("  Deadline: " + permit.deadline());

        // =====================================================================
        // Step 3: Create TypedData and Sign
        // =====================================================================
        System.out.println("\n[3] Signing Permit");

        // Combine domain + type definition + message into TypedData
        TypedData<Permit> typedData = TypedData.create(domain, Permit.DEFINITION, permit);

        // Compute the EIP-712 hash (this is what gets signed)
        Hash structHash = typedData.hash();
        System.out.println("  Struct hash: " + structHash.value());

        // Sign the typed data
        Signature signature = typedData.sign(signer);
        System.out.println(AnsiColors.success("  Signature: " + signature));

        // =====================================================================
        // Step 4: Extract r, s, v Components for Contract Call
        // =====================================================================
        System.out.println("\n[4] Extracting Signature Components");

        // The permit() function expects (owner, spender, value, deadline, v, r, s)
        // Extract components from the signature record
        byte[] r = signature.r();
        byte[] s = signature.s();
        int v = signature.v();

        System.out.println("  r: 0x" + bytesToHex(r));
        System.out.println("  s: 0x" + bytesToHex(s));
        System.out.println("  v: " + v);

        // =====================================================================
        // Step 5: Show How to Use with Contract
        // =====================================================================
        System.out.println("\n[5] Contract Call Parameters");
        System.out.println("  To call permit(owner, spender, value, deadline, v, r, s):");
        System.out.println("    owner:    " + owner.value());
        System.out.println("    spender:  " + spender.value());
        System.out.println("    value:    " + value);
        System.out.println("    deadline: " + deadline);
        System.out.println("    v:        " + v);
        System.out.println("    r:        0x" + bytesToHex(r));
        System.out.println("    s:        0x" + bytesToHex(s));

        System.out.println("\n" + AnsiColors.success("ERC-2612 Permit signing completed successfully!"));
    }

    private static String bytesToHex(byte[] bytes) {
        var sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }
}
