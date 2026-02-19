// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.examples;

import java.math.BigInteger;

import sh.brane.core.AnsiColors;
import sh.brane.core.crypto.PrivateKeySigner;
import sh.brane.core.crypto.Signature;
import sh.brane.core.crypto.eip3009.Eip3009;
import sh.brane.core.crypto.eip3009.TransferAuthorization;
import sh.brane.core.crypto.eip712.Eip712Domain;
import sh.brane.core.types.Address;
import sh.brane.core.types.Hash;
import sh.brane.primitives.Hex;

/**
 * Demonstrates EIP-3009 TransferWithAuthorization signing using Brane.
 *
 * <p>EIP-3009 enables meta-transactions — gasless token transfers via signed
 * EIP-712 authorizations. Instead of the token holder submitting an on-chain
 * transaction, they sign an authorization off-chain, and a relayer submits it.
 *
 * <p>This is the on-chain primitive powering <a href="https://www.x402.org/">x402</a> —
 * Coinbase's HTTP-native payment protocol for USDC.
 *
 * <p>This example demonstrates:
 * <ol>
 *   <li>Creating the EIP-712 domain for USDC</li>
 *   <li>Building a TransferWithAuthorization message</li>
 *   <li>Signing with EIP-712 and extracting v, r, s components</li>
 *   <li>Showing the parameters needed for on-chain submission</li>
 * </ol>
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *     -PmainClass=sh.brane.examples.Eip3009Example
 * </pre>
 *
 * @see <a href="https://eips.ethereum.org/EIPS/eip-3009">EIP-3009</a>
 */
public final class Eip3009Example {

    // Test private key (Anvil's default account #0)
    private static final String PRIVATE_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private Eip3009Example() {
        // Prevent instantiation
    }

    public static void main(String[] args) {
        System.out.println("=== EIP-3009 Transfer With Authorization Example ===\n");

        // Create signer from private key
        var signer = new PrivateKeySigner(PRIVATE_KEY);
        Address from = signer.address();
        System.out.println("Signer address: " + from.value());

        // =====================================================================
        // Step 1: Create EIP-712 Domain for USDC
        // =====================================================================
        System.out.println("\n[1] Creating EIP-712 Domain for USDC");

        // USDC on Ethereum mainnet
        Address usdcAddress = new Address("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48");
        Eip712Domain domain = Eip3009.usdcDomain(1L, usdcAddress);

        System.out.println("  Token: " + domain.name());
        System.out.println("  Version: " + domain.version());
        System.out.println("  Chain: " + domain.chainId());
        System.out.println("  Contract: " + domain.verifyingContract().value());
        System.out.println("  Domain separator: " + domain.separator().value());

        // =====================================================================
        // Step 2: Create TransferWithAuthorization Message
        // =====================================================================
        System.out.println("\n[2] Creating TransferWithAuthorization");

        Address recipient = new Address("0x70997970c51812dc3a010c7d01b50e0d17dc79c8");
        BigInteger value = BigInteger.valueOf(1_000_000); // 1 USDC (6 decimals)

        // Explicit factory for deterministic example output
        byte[] nonce = Eip3009.randomNonce();
        TransferAuthorization auth = Eip3009.transferAuthorization(
            from, recipient, value,
            BigInteger.ZERO,                      // validAfter: immediately
            BigInteger.valueOf(2_000_000_000L),   // validBefore: far future
            nonce
        );

        System.out.println("  From: " + auth.from().value());
        System.out.println("  To: " + auth.to().value());
        System.out.println("  Value: " + auth.value() + " (1 USDC)");
        System.out.println("  Valid after: " + auth.validAfter());
        System.out.println("  Valid before: " + auth.validBefore());
        System.out.println("  Nonce: 0x" + Hex.encodeNoPrefix(auth.nonce()));

        // =====================================================================
        // Step 3: Compute Hash and Sign
        // =====================================================================
        System.out.println("\n[3] Signing Authorization");

        Hash hash = Eip3009.hash(auth, domain);
        System.out.println("  EIP-712 hash: " + hash.value());

        Signature sig = Eip3009.sign(auth, domain, signer);
        System.out.println(AnsiColors.success("  Signature: " + sig));

        // =====================================================================
        // Step 4: Extract v, r, s for On-Chain Submission
        // =====================================================================
        System.out.println("\n[4] Extracting Signature Components");

        byte[] r = sig.r();
        byte[] s = sig.s();
        int v = sig.v();

        System.out.println("  v: " + v);
        System.out.println("  r: 0x" + Hex.encodeNoPrefix(r));
        System.out.println("  s: 0x" + Hex.encodeNoPrefix(s));

        // =====================================================================
        // Step 5: Show Contract Call Parameters
        // =====================================================================
        System.out.println("\n[5] Contract Call Parameters");
        System.out.println("  To call transferWithAuthorization(from, to, value, validAfter, validBefore, nonce, v, r, s):");
        System.out.println("    from:        " + auth.from().value());
        System.out.println("    to:          " + auth.to().value());
        System.out.println("    value:       " + auth.value());
        System.out.println("    validAfter:  " + auth.validAfter());
        System.out.println("    validBefore: " + auth.validBefore());
        System.out.println("    nonce:       0x" + Hex.encodeNoPrefix(auth.nonce()));
        System.out.println("    v:           " + v);
        System.out.println("    r:           0x" + Hex.encodeNoPrefix(r));
        System.out.println("    s:           0x" + Hex.encodeNoPrefix(s));

        // =====================================================================
        // Step 6: Demonstrate Convenience Factory
        // =====================================================================
        System.out.println("\n[6] Convenience Factory (auto nonce + time window)");

        TransferAuthorization autoAuth = Eip3009.transferAuthorization(
            from, recipient, value, 3600  // valid for 1 hour
        );

        System.out.println("  Valid after: " + autoAuth.validAfter() + " (now - 5s)");
        System.out.println("  Valid before: " + autoAuth.validBefore() + " (now + 1h)");
        System.out.println("  Nonce: 0x" + Hex.encodeNoPrefix(autoAuth.nonce()) + " (random)");

        System.out.println("\n" + AnsiColors.success("EIP-3009 Transfer With Authorization signing completed successfully!"));
    }
}
