// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.examples;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import io.brane.core.crypto.PrivateKey;
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
 * Integration test for EIP-712 typed data signing.
 *
 * <p>Tests the full signing flow including:
 * <ol>
 *   <li>ERC-2612 Permit sign/recover</li>
 *   <li>Safe transaction signing with EIP-712</li>
 *   <li>JSON parsing flow (WalletConnect style)</li>
 * </ol>
 *
 * <p>This test verifies that signatures can be created and verified
 * by recovering the signer's address from the signature.
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *     -PmainClass=io.brane.examples.Eip712IntegrationTest
 * </pre>
 */
public final class Eip712IntegrationTest {

    // Test private key (Anvil's default account #0)
    private static final String PRIVATE_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private Eip712IntegrationTest() {
        // Prevent instantiation
    }

    // =========================================================================
    // ERC-2612 Permit Record
    // =========================================================================

    /**
     * ERC-2612 Permit message structure for gasless token approvals.
     */
    public record Permit(
            Address owner,
            Address spender,
            BigInteger value,
            BigInteger nonce,
            BigInteger deadline) {

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
    // Safe Transaction Record (EIP-712)
    // =========================================================================

    /**
     * Safe (Gnosis Safe) transaction structure for EIP-712 signing.
     *
     * <p>This matches the SafeTx type used by Safe contracts for off-chain signing.
     */
    public record SafeTx(
            Address to,
            BigInteger value,
            byte[] data,
            int operation,
            BigInteger safeTxGas,
            BigInteger baseGas,
            BigInteger gasPrice,
            Address gasToken,
            Address refundReceiver,
            BigInteger nonce) {

        public static final TypeDefinition<SafeTx> DEFINITION = TypeDefinition.forRecord(
                SafeTx.class,
                "SafeTx",
                Map.of("SafeTx", List.of(
                        TypedDataField.of("to", "address"),
                        TypedDataField.of("value", "uint256"),
                        TypedDataField.of("data", "bytes"),
                        TypedDataField.of("operation", "uint8"),
                        TypedDataField.of("safeTxGas", "uint256"),
                        TypedDataField.of("baseGas", "uint256"),
                        TypedDataField.of("gasPrice", "uint256"),
                        TypedDataField.of("gasToken", "address"),
                        TypedDataField.of("refundReceiver", "address"),
                        TypedDataField.of("nonce", "uint256"))));
    }

    public static void main(String[] args) {
        System.out.println("=== EIP-712 Integration Test ===\n");

        var signer = new PrivateKeySigner(PRIVATE_KEY);
        Address signerAddress = signer.address();
        System.out.println("Signer address: " + signerAddress.value());

        int passed = 0;
        int failed = 0;

        // Test 1: Permit sign/recover
        try {
            testPermitSignAndRecover(signer);
            System.out.println("[PASS] Permit sign/recover");
            passed++;
        } catch (Exception e) {
            System.out.println("[FAIL] Permit sign/recover: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 2: Safe transaction signing
        try {
            testSafeTransactionSigning(signer);
            System.out.println("[PASS] Safe transaction signing");
            passed++;
        } catch (Exception e) {
            System.out.println("[FAIL] Safe transaction signing: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 3: JSON parsing flow
        try {
            testJsonParsingFlow(signer);
            System.out.println("[PASS] JSON parsing flow");
            passed++;
        } catch (Exception e) {
            System.out.println("[FAIL] JSON parsing flow: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        // Test 4: Hash determinism
        try {
            testHashDeterminism();
            System.out.println("[PASS] Hash determinism");
            passed++;
        } catch (Exception e) {
            System.out.println("[FAIL] Hash determinism: " + e.getMessage());
            e.printStackTrace();
            failed++;
        }

        System.out.println("\n=== Results: " + passed + " passed, " + failed + " failed ===");

        if (failed > 0) {
            System.exit(1);
        }

        System.out.println("\nEIP-712 Integration Tests Passed!");
    }

    /**
     * Test 1: ERC-2612 Permit - sign and recover signer address.
     */
    private static void testPermitSignAndRecover(PrivateKeySigner signer) {
        System.out.println("\n[Test 1] Permit Sign/Recover");

        // Create domain
        Address tokenAddress = new Address("0x6B175474E89094C44Da98b954EecdeAC495271dF");
        Eip712Domain domain = Eip712Domain.builder()
                .name("Dai Stablecoin")
                .version("1")
                .chainId(1L)
                .verifyingContract(tokenAddress)
                .build();

        // Create permit
        Address owner = signer.address();
        Address spender = new Address("0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D");
        var permit = new Permit(
                owner,
                spender,
                BigInteger.valueOf(1_000_000_000_000_000_000L), // 1 token (18 decimals)
                BigInteger.ZERO,
                BigInteger.valueOf(1893456000L) // Far future deadline
        );

        // Create typed data and sign
        TypedData<Permit> typedData = TypedData.create(domain, Permit.DEFINITION, permit);
        Hash hash = typedData.hash();
        Signature signature = typedData.sign(signer);

        System.out.println("  Hash: " + hash.value());
        System.out.println("  Signature v: " + signature.v());

        // Recover the signer address from the signature
        Address recovered = PrivateKey.recoverAddress(hash.toBytes(), signature);
        System.out.println("  Recovered: " + recovered.value());

        // Verify recovered address matches original signer
        if (!recovered.equals(owner)) {
            throw new AssertionError("Recovered address does not match signer: expected "
                    + owner.value() + ", got " + recovered.value());
        }
    }

    /**
     * Test 2: Safe transaction signing with EIP-712.
     */
    private static void testSafeTransactionSigning(PrivateKeySigner signer) {
        System.out.println("\n[Test 2] Safe Transaction Signing");

        // Create Safe domain (matches Safe contract's DOMAIN_SEPARATOR)
        Address safeAddress = new Address("0x5FbDB2315678afecb367f032d93F642f64180aa3");
        Eip712Domain domain = Eip712Domain.builder()
                .chainId(1L)
                .verifyingContract(safeAddress)
                .build();

        // Create Safe transaction
        Address recipient = new Address("0x90F79bf6EB2c4f870365E785982E1f101E93b906");
        var safeTx = new SafeTx(
                recipient,
                BigInteger.valueOf(1_000_000_000_000_000_000L), // 1 ETH
                new byte[0], // Empty data (simple ETH transfer)
                0, // Call operation
                BigInteger.ZERO, // safeTxGas
                BigInteger.ZERO, // baseGas
                BigInteger.ZERO, // gasPrice
                new Address("0x0000000000000000000000000000000000000000"), // gasToken (ETH)
                new Address("0x0000000000000000000000000000000000000000"), // refundReceiver
                BigInteger.ZERO // nonce
        );

        // Create typed data and sign
        TypedData<SafeTx> typedData = TypedData.create(domain, SafeTx.DEFINITION, safeTx);
        Hash hash = typedData.hash();
        Signature signature = typedData.sign(signer);

        System.out.println("  SafeTx Hash: " + hash.value());
        System.out.println("  Signature v: " + signature.v());

        // Verify signature recovery
        Address recovered = PrivateKey.recoverAddress(hash.toBytes(), signature);
        System.out.println("  Recovered: " + recovered.value());

        if (!recovered.equals(signer.address())) {
            throw new AssertionError("Recovered address does not match signer");
        }

        // Verify v is 27 or 28 (EIP-712 standard)
        if (signature.v() != 27 && signature.v() != 28) {
            throw new AssertionError("Signature v should be 27 or 28, got: " + signature.v());
        }
    }

    /**
     * Test 3: JSON parsing flow (WalletConnect style requests).
     */
    private static void testJsonParsingFlow(PrivateKeySigner signer) {
        System.out.println("\n[Test 3] JSON Parsing Flow");

        // Uniswap Permit2 request (realistic WalletConnect payload)
        String permit2Json = """
                {
                    "domain": {
                        "name": "Permit2",
                        "chainId": 1,
                        "verifyingContract": "0x000000000022D473030F116dDEE9F6B43aC78BA3"
                    },
                    "primaryType": "PermitSingle",
                    "types": {
                        "EIP712Domain": [
                            {"name": "name", "type": "string"},
                            {"name": "chainId", "type": "uint256"},
                            {"name": "verifyingContract", "type": "address"}
                        ],
                        "PermitSingle": [
                            {"name": "details", "type": "PermitDetails"},
                            {"name": "spender", "type": "address"},
                            {"name": "sigDeadline", "type": "uint256"}
                        ],
                        "PermitDetails": [
                            {"name": "token", "type": "address"},
                            {"name": "amount", "type": "uint160"},
                            {"name": "expiration", "type": "uint48"},
                            {"name": "nonce", "type": "uint48"}
                        ]
                    },
                    "message": {
                        "details": {
                            "token": "0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48",
                            "amount": "1000000000000000000",
                            "expiration": "1735689600",
                            "nonce": "0"
                        },
                        "spender": "0x3fC91A3afd70395Cd496C647d5a6CC9D4B2b7FAD",
                        "sigDeadline": "1893456000"
                    }
                }
                """;

        // Parse and validate JSON
        TypedData<?> typedData = TypedDataJson.parseAndValidate(permit2Json);

        // Verify domain was parsed correctly
        if (!"Permit2".equals(typedData.domain().name())) {
            throw new AssertionError("Domain name mismatch: expected Permit2, got " + typedData.domain().name());
        }
        if (typedData.domain().chainId() != 1L) {
            throw new AssertionError("Chain ID mismatch");
        }
        if (!"PermitSingle".equals(typedData.primaryType())) {
            throw new AssertionError("Primary type mismatch");
        }

        System.out.println("  Domain: " + typedData.domain().name());
        System.out.println("  Primary Type: " + typedData.primaryType());

        // Sign the parsed data
        Hash hash = typedData.hash();
        Signature signature = typedData.sign(signer);

        System.out.println("  Hash: " + hash.value());
        System.out.println("  Signature v: " + signature.v());

        // Verify signature recovery
        Address recovered = PrivateKey.recoverAddress(hash.toBytes(), signature);
        if (!recovered.equals(signer.address())) {
            throw new AssertionError("Recovered address does not match signer");
        }

        // Test with a Mail example (nested types)
        String mailJson = """
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

        TypedData<?> mailData = TypedDataJson.parseAndValidate(mailJson);
        Hash mailHash = mailData.hash();
        Signature mailSig = mailData.sign(signer);

        System.out.println("  Mail Hash: " + mailHash.value());

        Address mailRecovered = PrivateKey.recoverAddress(mailHash.toBytes(), mailSig);
        if (!mailRecovered.equals(signer.address())) {
            throw new AssertionError("Mail signature recovery failed");
        }
    }

    /**
     * Test 4: Verify hash determinism (same input produces same hash).
     */
    private static void testHashDeterminism() {
        System.out.println("\n[Test 4] Hash Determinism");

        // Create identical typed data twice
        Address tokenAddress = new Address("0x6B175474E89094C44Da98b954EecdeAC495271dF");
        Eip712Domain domain = Eip712Domain.builder()
                .name("TestToken")
                .version("1")
                .chainId(1L)
                .verifyingContract(tokenAddress)
                .build();

        Address owner = new Address("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266");
        Address spender = new Address("0x7a250d5630B4cF539739dF2C5dAcb4c659F2488D");
        var permit1 = new Permit(owner, spender, BigInteger.valueOf(1000), BigInteger.ZERO, BigInteger.valueOf(999999));
        var permit2 = new Permit(owner, spender, BigInteger.valueOf(1000), BigInteger.ZERO, BigInteger.valueOf(999999));

        TypedData<Permit> typedData1 = TypedData.create(domain, Permit.DEFINITION, permit1);
        TypedData<Permit> typedData2 = TypedData.create(domain, Permit.DEFINITION, permit2);

        Hash hash1 = typedData1.hash();
        Hash hash2 = typedData2.hash();

        System.out.println("  Hash 1: " + hash1.value());
        System.out.println("  Hash 2: " + hash2.value());

        if (!hash1.equals(hash2)) {
            throw new AssertionError("Hash determinism failed: identical inputs produced different hashes");
        }

        // Verify different inputs produce different hashes
        var permit3 = new Permit(owner, spender, BigInteger.valueOf(2000), BigInteger.ZERO, BigInteger.valueOf(999999));
        TypedData<Permit> typedData3 = TypedData.create(domain, Permit.DEFINITION, permit3);
        Hash hash3 = typedData3.hash();

        System.out.println("  Hash 3 (different value): " + hash3.value());

        if (hash1.equals(hash3)) {
            throw new AssertionError("Hash collision: different inputs produced same hash");
        }
    }
}
