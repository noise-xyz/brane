// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.crypto.eip3009;

import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Objects;

import sh.brane.core.crypto.Signature;
import sh.brane.core.crypto.Signer;
import sh.brane.core.crypto.eip712.Eip712Domain;
import sh.brane.core.crypto.eip712.TypedData;
import sh.brane.core.types.Address;
import sh.brane.core.types.Hash;

/**
 * EIP-3009 Transfer With Authorization utilities.
 *
 * <p>Provides helpers for signing and hashing EIP-3009 authorization messages.
 * These are the off-chain primitives needed for gasless token transfers
 * (e.g., USDC via the x402 protocol).
 *
 * <p>Example — sign a USDC transfer authorization:
 * <pre>{@code
 * var domain = Eip3009.usdcDomain(8453L, usdcAddress);  // Base mainnet
 *
 * var auth = Eip3009.transferAuthorization(
 *     signer.address(),
 *     recipient,
 *     BigInteger.valueOf(1_000_000),  // 1 USDC
 *     3600                            // valid for 1 hour
 * );
 *
 * Signature sig = Eip3009.sign(auth, domain, signer);
 *
 * // Extract v/r/s for on-chain submission
 * int v = sig.v();
 * byte[] r = sig.r();
 * byte[] s = sig.s();
 * }</pre>
 *
 * @see <a href="https://eips.ethereum.org/EIPS/eip-3009">EIP-3009</a>
 */
public final class Eip3009 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Eip3009() {}

    // ═══════════════════════════════════════════════════════════════════
    // Domain helpers
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates the EIP-712 domain for USDC on the given chain.
     *
     * <p>USDC uses domain name {@code "USD Coin"} and version {@code "2"}
     * across all chains where Circle deploys native USDC.
     *
     * @param chainId         the EIP-155 chain ID
     * @param contractAddress the USDC contract address on this chain
     * @return the EIP-712 domain
     * @throws NullPointerException if contractAddress is null
     */
    public static Eip712Domain usdcDomain(long chainId, Address contractAddress) {
        Objects.requireNonNull(contractAddress, "contractAddress");
        return Eip712Domain.builder()
            .name("USD Coin")
            .version("2")
            .chainId(chainId)
            .verifyingContract(contractAddress)
            .build();
    }

    /**
     * Creates the EIP-712 domain for EURC on the given chain.
     *
     * <p>EURC uses domain name {@code "EURC"} and version {@code "2"}.
     *
     * @param chainId         the EIP-155 chain ID
     * @param contractAddress the EURC contract address on this chain
     * @return the EIP-712 domain
     * @throws NullPointerException if contractAddress is null
     */
    public static Eip712Domain eurcDomain(long chainId, Address contractAddress) {
        Objects.requireNonNull(contractAddress, "contractAddress");
        return Eip712Domain.builder()
            .name("EURC")
            .version("2")
            .chainId(chainId)
            .verifyingContract(contractAddress)
            .build();
    }

    /**
     * Creates the EIP-712 domain for any EIP-3009 token.
     *
     * @param tokenName       the token's EIP-712 domain name (e.g., "USD Coin")
     * @param version         the token's EIP-712 domain version (e.g., "2")
     * @param chainId         the chain ID
     * @param contractAddress the token contract address
     * @return the EIP-712 domain
     * @throws NullPointerException if any argument is null
     */
    public static Eip712Domain tokenDomain(
            String tokenName, String version, long chainId, Address contractAddress) {
        Objects.requireNonNull(tokenName, "tokenName");
        Objects.requireNonNull(version, "version");
        Objects.requireNonNull(contractAddress, "contractAddress");
        return Eip712Domain.builder()
            .name(tokenName)
            .version(version)
            .chainId(chainId)
            .verifyingContract(contractAddress)
            .build();
    }

    // ═══════════════════════════════════════════════════════════════════
    // Nonce generation
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Generates a random 32-byte nonce for EIP-3009 authorization.
     *
     * <p>EIP-3009 uses random {@code bytes32} nonces (not sequential),
     * allowing multiple concurrent authorizations without ordering constraints.
     *
     * @return 32 cryptographically random bytes
     */
    public static byte[] randomNonce() {
        var nonce = new byte[32];
        RANDOM.nextBytes(nonce);
        return nonce;
    }

    // ═══════════════════════════════════════════════════════════════════
    // Factory methods
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Creates a TransferAuthorization with explicit timestamps and nonce.
     *
     * <p>This is the fully explicit factory — use for deterministic testing
     * or when you need precise control over the authorization parameters.
     *
     * @param from        payer address
     * @param to          payee address
     * @param value       token amount in smallest units
     * @param validAfter  earliest valid timestamp (unix seconds)
     * @param validBefore latest valid timestamp (unix seconds)
     * @param nonce       random 32-byte nonce
     * @return the authorization message
     */
    public static TransferAuthorization transferAuthorization(
            Address from, Address to, BigInteger value,
            BigInteger validAfter, BigInteger validBefore, byte[] nonce) {
        return new TransferAuthorization(from, to, value, validAfter, validBefore, nonce);
    }

    /**
     * Creates a TransferAuthorization with a random nonce and a validity window.
     *
     * <p>Sets {@code validAfter = now - 5s} (clock skew buffer) and
     * {@code validBefore = now + validForSeconds}. Generates a random nonce.
     *
     * @param from            payer address
     * @param to              payee address
     * @param value           token amount in smallest units
     * @param validForSeconds how many seconds from now the authorization is valid
     * @return the authorization message
     * @throws NullPointerException if from, to, or value is null
     */
    public static TransferAuthorization transferAuthorization(
            Address from, Address to, BigInteger value, long validForSeconds) {
        long now = System.currentTimeMillis() / 1000;
        return new TransferAuthorization(
            from, to, value,
            BigInteger.valueOf(now - 5),
            BigInteger.valueOf(now + validForSeconds),
            randomNonce()
        );
    }

    /**
     * Creates a ReceiveAuthorization with explicit timestamps and nonce.
     *
     * @param from        payer address
     * @param to          payee address (must be {@code msg.sender} on-chain)
     * @param value       token amount in smallest units
     * @param validAfter  earliest valid timestamp (unix seconds)
     * @param validBefore latest valid timestamp (unix seconds)
     * @param nonce       random 32-byte nonce
     * @return the authorization message
     */
    public static ReceiveAuthorization receiveAuthorization(
            Address from, Address to, BigInteger value,
            BigInteger validAfter, BigInteger validBefore, byte[] nonce) {
        return new ReceiveAuthorization(from, to, value, validAfter, validBefore, nonce);
    }

    /**
     * Creates a ReceiveAuthorization with a random nonce and a validity window.
     *
     * <p>Sets {@code validAfter = now - 5s} (clock skew buffer) and
     * {@code validBefore = now + validForSeconds}. Generates a random nonce.
     *
     * @param from            payer address
     * @param to              payee address (must be {@code msg.sender} on-chain)
     * @param value           token amount in smallest units
     * @param validForSeconds how many seconds from now the authorization is valid
     * @return the authorization message
     * @throws NullPointerException if from, to, or value is null
     */
    public static ReceiveAuthorization receiveAuthorization(
            Address from, Address to, BigInteger value, long validForSeconds) {
        long now = System.currentTimeMillis() / 1000;
        return new ReceiveAuthorization(
            from, to, value,
            BigInteger.valueOf(now - 5),
            BigInteger.valueOf(now + validForSeconds),
            randomNonce()
        );
    }

    /**
     * Creates a CancelAuthorization with an explicit nonce.
     *
     * @param authorizer the address that originally signed the authorization
     * @param nonce      the 32-byte nonce of the authorization to cancel
     * @return the cancel authorization message
     */
    public static CancelAuthorization cancelAuthorization(Address authorizer, byte[] nonce) {
        return new CancelAuthorization(authorizer, nonce);
    }


    // ═══════════════════════════════════════════════════════════════════
    // Signing
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Signs a TransferWithAuthorization message.
     *
     * @param auth   the authorization message
     * @param domain the token's EIP-712 domain
     * @param signer the signer (must be the {@code from} address)
     * @return signature with v=27 or v=28
     * @throws NullPointerException if any argument is null
     */
    public static Signature sign(TransferAuthorization auth, Eip712Domain domain, Signer signer) {
        return TypedData.create(domain, TransferAuthorization.DEFINITION, auth).sign(signer);
    }

    /**
     * Signs a ReceiveWithAuthorization message.
     *
     * @param auth   the authorization message
     * @param domain the token's EIP-712 domain
     * @param signer the signer (must be the {@code from} address)
     * @return signature with v=27 or v=28
     * @throws NullPointerException if any argument is null
     */
    public static Signature sign(ReceiveAuthorization auth, Eip712Domain domain, Signer signer) {
        return TypedData.create(domain, ReceiveAuthorization.DEFINITION, auth).sign(signer);
    }

    /**
     * Signs a CancelAuthorization message.
     *
     * @param auth   the cancel authorization message
     * @param domain the token's EIP-712 domain
     * @param signer the signer (must be the {@code authorizer} address)
     * @return signature with v=27 or v=28
     * @throws NullPointerException if any argument is null
     */
    public static Signature sign(CancelAuthorization auth, Eip712Domain domain, Signer signer) {
        return TypedData.create(domain, CancelAuthorization.DEFINITION, auth).sign(signer);
    }

    // ═══════════════════════════════════════════════════════════════════
    // Hashing
    // ═══════════════════════════════════════════════════════════════════

    /**
     * Computes the EIP-712 hash for a TransferWithAuthorization without signing.
     *
     * @param auth   the authorization message
     * @param domain the token's EIP-712 domain
     * @return the 32-byte EIP-712 signing hash
     */
    public static Hash hash(TransferAuthorization auth, Eip712Domain domain) {
        return TypedData.create(domain, TransferAuthorization.DEFINITION, auth).hash();
    }

    /**
     * Computes the EIP-712 hash for a ReceiveWithAuthorization without signing.
     *
     * @param auth   the authorization message
     * @param domain the token's EIP-712 domain
     * @return the 32-byte EIP-712 signing hash
     */
    public static Hash hash(ReceiveAuthorization auth, Eip712Domain domain) {
        return TypedData.create(domain, ReceiveAuthorization.DEFINITION, auth).hash();
    }

    /**
     * Computes the EIP-712 hash for a CancelAuthorization without signing.
     *
     * @param auth   the cancel authorization message
     * @param domain the token's EIP-712 domain
     * @return the 32-byte EIP-712 signing hash
     */
    public static Hash hash(CancelAuthorization auth, Eip712Domain domain) {
        return TypedData.create(domain, CancelAuthorization.DEFINITION, auth).hash();
    }
}
