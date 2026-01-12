// SPDX-License-Identifier: MIT OR Apache-2.0
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
     * Creates typed data from a parsed JSON payload.
     *
     * <p>This factory method is used for dynamic EIP-712 signing when the message
     * type is not known at compile time (e.g., from WalletConnect or dapp requests).
     *
     * @param payload the parsed typed data payload from JSON
     * @return typed data ready for signing or hashing
     * @see TypedDataJson#parseAndValidate(String)
     */
    @SuppressWarnings("unchecked")
    public static TypedData<Map<String, Object>> fromPayload(TypedDataPayload payload) {
        Objects.requireNonNull(payload, "payload");

        // Create a type definition for dynamic (Map-based) message
        TypeDefinition<Map<String, Object>> definition = new TypeDefinition<>(
            payload.primaryType(),
            payload.types(),
            msg -> (Map<String, Object>) msg
        );

        return new TypedData<>(payload.domain(), definition, payload.message());
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
        Hash hashToSign = hash();
        return TypedDataSigner.signHash(signer, hashToSign.toBytes());
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
