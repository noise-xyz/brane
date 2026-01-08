package io.brane.core.crypto.eip712;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.brane.core.crypto.Keccak256;
import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.crypto.Signature;
import io.brane.core.crypto.Signer;
import io.brane.core.types.Hash;

/**
 * Static utility for EIP-712 typed data signing.
 *
 * <p>This is the SECONDARY API for dynamic use cases where type structure
 * is determined at runtime (JSON from dapps, scripting, runtime-generated types).
 *
 * <p>For compile-time type safety with records, use {@link TypedData} instead.
 *
 * <p>Example usage:
 * <pre>{@code
 * var domain = Eip712Domain.builder()
 *     .name("MyDapp")
 *     .version("1")
 *     .chainId(1L)
 *     .verifyingContract(contractAddress)
 *     .build();
 *
 * var types = Map.of(
 *     "Permit", List.of(
 *         TypedDataField.of("owner", "address"),
 *         TypedDataField.of("spender", "address"),
 *         TypedDataField.of("value", "uint256"),
 *         TypedDataField.of("nonce", "uint256"),
 *         TypedDataField.of("deadline", "uint256")
 *     )
 * );
 *
 * var message = Map.<String, Object>of(
 *     "owner", owner,
 *     "spender", spender,
 *     "value", BigInteger.valueOf(1000),
 *     "nonce", BigInteger.ZERO,
 *     "deadline", BigInteger.valueOf(deadline)
 * );
 *
 * Signature sig = TypedDataSigner.signTypedData(domain, "Permit", types, message, signer);
 * Hash hash = TypedDataSigner.hashTypedData(domain, "Permit", types, message);
 * }</pre>
 *
 * @see TypedData
 * @see <a href="https://eips.ethereum.org/EIPS/eip-712">EIP-712</a>
 */
public final class TypedDataSigner {

    /** EIP-712 prefix: 0x19 0x01 */
    private static final byte[] EIP712_PREFIX = new byte[] { 0x19, 0x01 };

    private TypedDataSigner() {}

    /**
     * Signs typed data according to EIP-712.
     * Use this API when type structure is determined at runtime.
     *
     * @param domain      the EIP-712 domain
     * @param primaryType the name of the primary type being signed
     * @param types       all type definitions (including primaryType)
     * @param message     the message data as field name -&gt; value map
     * @param signer      the signer to use
     * @return the signature with v=27 or v=28 for EIP-712 compatibility
     * @throws NullPointerException if any parameter is null
     * @throws UnsupportedOperationException if signer is not a PrivateKeySigner
     */
    public static Signature signTypedData(
            Eip712Domain domain,
            String primaryType,
            Map<String, List<TypedDataField>> types,
            Map<String, Object> message,
            Signer signer) {

        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(primaryType, "primaryType");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(message, "message");
        Objects.requireNonNull(signer, "signer");

        Hash hashToSign = hashTypedData(domain, primaryType, types, message);
        return signHash(signer, hashToSign.toBytes());
    }

    /**
     * Computes the EIP-712 hash without signing.
     * Useful for verification or when signing happens externally.
     *
     * <p>The hash is computed as:
     * {@code keccak256("\x19\x01" || domainSeparator || hashStruct(message))}
     *
     * @param domain      the EIP-712 domain
     * @param primaryType the name of the primary type being signed
     * @param types       all type definitions (including primaryType)
     * @param message     the message data as field name -&gt; value map
     * @return the 32-byte EIP-712 hash
     * @throws NullPointerException if any parameter is null
     */
    public static Hash hashTypedData(
            Eip712Domain domain,
            String primaryType,
            Map<String, List<TypedDataField>> types,
            Map<String, Object> message) {

        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(primaryType, "primaryType");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(message, "message");

        // Compute domain separator
        Hash domainSeparator = TypedDataEncoder.hashDomain(domain);

        // Compute message struct hash
        byte[] messageHash = TypedDataEncoder.hashStruct(primaryType, types, message);

        // Compute final hash: keccak256(0x19 0x01 || domainSeparator || messageHash)
        byte[] toHash = new byte[2 + 32 + 32];
        System.arraycopy(EIP712_PREFIX, 0, toHash, 0, 2);
        System.arraycopy(domainSeparator.toBytes(), 0, toHash, 2, 32);
        System.arraycopy(messageHash, 0, toHash, 34, 32);

        return Hash.fromBytes(Keccak256.hash(toHash));
    }

    /**
     * Signs a raw hash and adjusts v to 27/28 for EIP-712 compatibility.
     */
    private static Signature signHash(Signer signer, byte[] hash) {
        if (signer instanceof PrivateKeySigner pks) {
            Signature rawSig = pks.signRawHash(hash);
            int adjustedV = rawSig.v() + 27;
            return new Signature(rawSig.r(), rawSig.s(), adjustedV);
        }

        throw new UnsupportedOperationException(
            "EIP-712 signing requires a PrivateKeySigner. " +
            "Provided signer type: " + signer.getClass().getName()
        );
    }
}
