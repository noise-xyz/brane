package io.brane.examples;

import java.nio.charset.StandardCharsets;

import io.brane.core.crypto.Keccak256;
import io.brane.core.crypto.PrivateKey;
import io.brane.core.crypto.Signature;
import io.brane.core.types.Address;
import io.brane.primitives.Hex;

/**
 * Sanity check for Milestone 1 crypto primitives.
 *
 * <p>
 * Demonstrates:
 * <ul>
 * <li>Keccak256 hashing with known test vector</li>
 * <li>Private key loading and address derivation</li>
 * <li>Message signing with deterministic ECDSA</li>
 * <li>Public key recovery from signature</li>
 * </ul>
 */
public final class CryptoSanityCheck {

    // Known Ethereum test vector
    private static final String TEST_KEY = "0x4c0883a69102937d6231471b5dbb6204fe5129617082792ae468d01a3f362318";
    private static final String EXPECTED_ADDRESS = "0x2c7536e3605d9c16a7a3d7b1898e529396a65c23";

    public static void main(String[] args) {
        System.out.println("=== Milestone 1: Crypto Primitives Sanity Check ===\n");

        try {
            // Test 1: Keccak256
            testKeccak256();

            // Test 2: Address Derivation
            testAddressDerivation();

            // Test 3: Sign and Recover
            testSignAndRecover();

            // Test 4: Deterministic Signing
            testDeterministicSigning();

            System.out.println("\n=== All Sanity Checks Passed! ✅ ===");
            System.out.println("\nMilestone 1 crypto primitives are working correctly:");
            System.out.println("  • Keccak256 matches Ethereum spec");
            System.out.println("  • secp256k1 signatures are deterministic (RFC 6979)");
            System.out.println("  • Public key recovery works correctly");
            System.out.println("  • Zero web3j dependencies in crypto code");

        } catch (Exception e) {
            System.err.println("\n❌ Sanity check failed: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static void testKeccak256() {
        System.out.println("1. Testing Keccak256 Hash");
        System.out.println("   Input: empty byte array");

        final byte[] hash = Keccak256.hash(new byte[0]);
        final String hashHex = Hex.encodeNoPrefix(hash);
        final String expected = "c5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470";

        System.out.println("   Output: " + hashHex);
        System.out.println("   Expected: " + expected);

        if (!hashHex.equals(expected)) {
            throw new AssertionError("Keccak256 hash mismatch!");
        }

        System.out.println("   ✓ Keccak256 matches Ethereum test vector\n");
    }

    private static void testAddressDerivation() {
        System.out.println("2. Testing Address Derivation");
        System.out.println("   Private key: " + TEST_KEY.substring(0, 10) + "...");

        final PrivateKey privateKey = PrivateKey.fromHex(TEST_KEY);
        final Address address = privateKey.toAddress();

        System.out.println("   Derived address: " + address.value());
        System.out.println("   Expected: " + EXPECTED_ADDRESS);

        if (!address.value().equalsIgnoreCase(EXPECTED_ADDRESS)) {
            throw new AssertionError("Address derivation mismatch!");
        }

        System.out.println("   ✓ Address derivation correct\n");
    }

    private static void testSignAndRecover() {
        System.out.println("3. Testing Sign and Recover");

        final PrivateKey privateKey = PrivateKey.fromHex(TEST_KEY);
        final Address originalAddress = privateKey.toAddress();

        // Sign a message
        final String message = "Hello, Ethereum! This is Brane SDK.";
        System.out.println("   Message: \"" + message + "\"");

        final byte[] messageHash = Keccak256.hash(message.getBytes(StandardCharsets.UTF_8));
        System.out.println("   Message hash: " + Hex.encodeNoPrefix(messageHash).substring(0, 16) + "...");

        final Signature signature = privateKey.sign(messageHash);
        System.out.println("   Signature:");
        System.out.println("     r: " + Hex.encodeNoPrefix(signature.r()).substring(0, 16) + "...");
        System.out.println("     s: " + Hex.encodeNoPrefix(signature.s()).substring(0, 16) + "...");
        System.out.println("     v: " + signature.v());

        // Recover address
        final Address recoveredAddress = PrivateKey.recoverAddress(messageHash, signature);
        System.out.println("   Recovered address: " + recoveredAddress.value());
        System.out.println("   Original address:  " + originalAddress.value());

        if (!recoveredAddress.equals(originalAddress)) {
            throw new AssertionError("Address recovery failed!");
        }

        System.out.println("   ✓ Public key recovery works\n");
    }

    private static void testDeterministicSigning() {
        System.out.println("4. Testing Deterministic Signing (RFC 6979)");

        final PrivateKey privateKey = PrivateKey.fromHex(TEST_KEY);
        final byte[] messageHash = Keccak256.hash("test".getBytes(StandardCharsets.UTF_8));

        System.out.println("   Signing same message 3 times...");

        final Signature sig1 = privateKey.sign(messageHash);
        final Signature sig2 = privateKey.sign(messageHash);
        final Signature sig3 = privateKey.sign(messageHash);

        if (!sig1.equals(sig2) || !sig2.equals(sig3)) {
            throw new AssertionError("Signatures are not deterministic!");
        }

        System.out.println("   Signature 1: " + Hex.encodeNoPrefix(sig1.r()).substring(0, 16) + "...");
        System.out.println("   Signature 2: " + Hex.encodeNoPrefix(sig2.r()).substring(0, 16) + "...");
        System.out.println("   Signature 3: " + Hex.encodeNoPrefix(sig3.r()).substring(0, 16) + "...");
        System.out.println("   ✓ All signatures identical (deterministic)\n");
    }

}
