package io.brane.core.crypto.eip712;

import java.util.Map;
import java.util.Objects;

import io.brane.core.crypto.Keccak256;
import io.brane.core.crypto.Signature;
import io.brane.core.crypto.Signer;
import io.brane.core.types.Hash;

/**
 * Type-safe EIP-712 typed data container.
 * Provides compile-time safety for message structure.
 *
 * <p>This is the PRIMARY API for EIP-712 typed data signing in Brane SDK.
 * It encapsulates a domain, type definition, and message for signing or hashing.
 *
 * <p>Example usage for ERC-2612 Permit:
 * <pre>{@code
 * record Permit(Address owner, Address spender, BigInteger value, BigInteger nonce, BigInteger deadline) {
 *     public static final TypeDefinition<Permit> DEFINITION = TypeDefinition.forRecord(
 *         Permit.class,
 *         "Permit",
 *         Map.of("Permit", List.of(
 *             TypedDataField.of("owner", "address"),
 *             TypedDataField.of("spender", "address"),
 *             TypedDataField.of("value", "uint256"),
 *             TypedDataField.of("nonce", "uint256"),
 *             TypedDataField.of("deadline", "uint256")
 *         ))
 *     );
 * }
 *
 * var domain = Eip712Domain.builder()
 *     .name("MyToken")
 *     .version("1")
 *     .chainId(1L)
 *     .verifyingContract(tokenAddress)
 *     .build();
 *
 * var permit = new Permit(owner, spender, value, nonce, deadline);
 * var typedData = TypedData.create(domain, Permit.DEFINITION, permit);
 *
 * Signature sig = typedData.sign(signer);
 * Hash hash = typedData.hash();
 * }</pre>
 *
 * @param <T> the message type (typically a record)
 * @see <a href="https://eips.ethereum.org/EIPS/eip-712">EIP-712</a>
 */
public final class TypedData<T> {

    /** EIP-712 prefix: 0x19 0x01 */
    private static final byte[] EIP712_PREFIX = new byte[] { 0x19, 0x01 };

    private final Eip712Domain domain;
    private final TypeDefinition<T> definition;
    private final T message;

    private TypedData(Eip712Domain domain, TypeDefinition<T> definition, T message) {
        this.domain = Objects.requireNonNull(domain, "domain");
        this.definition = Objects.requireNonNull(definition, "definition");
        this.message = Objects.requireNonNull(message, "message");
    }

    /**
     * Creates typed data from a domain, type definition, and message.
     *
     * @param <T> the message type
     * @param domain     the EIP-712 domain
     * @param definition the type definition with field mappings
     * @param message    the message instance
     * @return typed data ready for signing or hashing
     */
    public static <T> TypedData<T> create(
            Eip712Domain domain,
            TypeDefinition<T> definition,
            T message) {
        return new TypedData<>(domain, definition, message);
    }

    /**
     * Computes the EIP-712 hash without signing.
     *
     * <p>The hash is computed as:
     * {@code keccak256("\x19\x01" || domainSeparator || hashStruct(message))}
     *
     * @return the 32-byte EIP-712 hash
     */
    public Hash hash() {
        // Compute domain separator
        Hash domainSeparator = TypedDataEncoder.hashDomain(domain);

        // Extract message data using the definition's extractor
        Map<String, Object> messageData = definition.extractor().apply(message);

        // Compute message struct hash
        byte[] messageHash = TypedDataEncoder.hashStruct(
            definition.primaryType(),
            definition.types(),
            messageData
        );

        // Compute final hash: keccak256(0x19 0x01 || domainSeparator || messageHash)
        byte[] toHash = new byte[2 + 32 + 32];
        System.arraycopy(EIP712_PREFIX, 0, toHash, 0, 2);
        System.arraycopy(domainSeparator.toBytes(), 0, toHash, 2, 32);
        System.arraycopy(messageHash, 0, toHash, 34, 32);

        return Hash.fromBytes(Keccak256.hash(toHash));
    }

    /**
     * Signs this typed data.
     *
     * <p>The signature is computed by signing the EIP-712 hash.
     * The returned signature has v=27 or v=28 (EIP-191 style).
     *
     * @param signer the signer to use
     * @return signature with v=27 or v=28
     */
    public Signature sign(Signer signer) {
        Objects.requireNonNull(signer, "signer");

        // Compute the hash to sign
        Hash hashToSign = hash();

        // Sign using personal message signing (which returns v=27 or 28)
        // But we need to sign the raw hash, not apply personal_sign prefix
        // So we sign the raw hash bytes directly
        return signHash(signer, hashToSign.toBytes());
    }

    /**
     * Signs a raw hash without any message prefix.
     * This is needed for EIP-712 since the 0x19 0x01 prefix is already included.
     */
    private static Signature signHash(Signer signer, byte[] hash) {
        // For EIP-712, we need to sign the raw hash without the personal_sign prefix.
        // The Signer interface provides signMessage which adds EIP-191 prefix.
        // We need direct hash signing capability.
        //
        // If the signer is a PrivateKeySigner, we can access the underlying PrivateKey.
        // For now, we use a workaround: cast to PrivateKeySigner or require PrivateKey.
        //
        // The cleanest approach is to require the signer to support raw hash signing.
        // Since PrivateKeySigner wraps PrivateKey which has sign(byte[] hash), we can:
        // 1. Check if signer is PrivateKeySigner and use its key
        // 2. Add a signHash method to Signer interface (breaking change)
        // 3. Use reflection (not ideal)
        //
        // For now, we require that the caller provide a PrivateKey-backed signer.
        // Future: add signTypedData or signHash to Signer interface.

        if (signer instanceof io.brane.core.crypto.PrivateKeySigner pks) {
            Signature rawSig = pks.signRawHash(hash);
            // Convert v from 0/1 to 27/28
            int adjustedV = rawSig.v() + 27;
            return new Signature(rawSig.r(), rawSig.s(), adjustedV);
        }

        throw new UnsupportedOperationException(
            "EIP-712 signing requires a PrivateKeySigner. " +
            "Provided signer type: " + signer.getClass().getName()
        );
    }

    /**
     * Returns the EIP-712 domain.
     *
     * @return the domain
     */
    public Eip712Domain domain() {
        return domain;
    }

    /**
     * Returns the primary type name.
     *
     * @return the primary type name (e.g., "Permit", "Mail")
     */
    public String primaryType() {
        return definition.primaryType();
    }

    /**
     * Returns the message instance.
     *
     * @return the message
     */
    public T message() {
        return message;
    }

    /**
     * Returns the type definition.
     *
     * @return the type definition
     */
    public TypeDefinition<T> definition() {
        return definition;
    }
}
