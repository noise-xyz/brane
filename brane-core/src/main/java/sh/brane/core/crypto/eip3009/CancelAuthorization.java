// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.crypto.eip3009;

import sh.brane.core.crypto.eip712.TypeDefinition;
import sh.brane.core.crypto.eip712.TypedDataField;
import sh.brane.core.types.Address;
import sh.brane.core.types.Hash;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import sh.brane.primitives.Hex;

/**
 * EIP-3009 CancelAuthorization message.
 *
 * <p>Allows an authorizer to cancel an outstanding authorization before it is used.
 * Once canceled, the nonce is permanently consumed and cannot be reused.
 *
 * @param authorizer the address that originally signed the authorization
 * @param nonce      the 32-byte nonce of the authorization to cancel
 * @see <a href="https://eips.ethereum.org/EIPS/eip-3009">EIP-3009</a>
 */
public record CancelAuthorization(
    Address authorizer,
    byte[] nonce
) {
    /** Length in bytes of the EIP-3009 nonce (bytes32). */
    private static final int NONCE_LENGTH = 32;

    /**
     * EIP-712 typehash for CancelAuthorization.
     *
     * <p>{@code keccak256("CancelAuthorization(address authorizer,bytes32 nonce)")}
     */
    public static final Hash TYPEHASH = new Hash(
        "0x158b0a9edf7a828aad02f63cd515c68ef2f50ba807396f6d12842833a1597429");

    /** EIP-712 type definition for CancelAuthorization. */
    public static final TypeDefinition<CancelAuthorization> DEFINITION =
        TypeDefinition.forRecord(
            CancelAuthorization.class,
            "CancelAuthorization",
            Map.of("CancelAuthorization", List.of(
                TypedDataField.of("authorizer", "address"),
                TypedDataField.of("nonce", "bytes32")
            ))
        );

    /** Compact constructor — validates all fields and makes defensive copy of nonce. */
    public CancelAuthorization {
        Objects.requireNonNull(authorizer, "authorizer");
        Objects.requireNonNull(nonce, "nonce");
        if (nonce.length != NONCE_LENGTH) {
            throw new IllegalArgumentException(
                "nonce must be " + NONCE_LENGTH + " bytes, got " + nonce.length);
        }
        nonce = Arrays.copyOf(nonce, NONCE_LENGTH);
    }

    /**
     * Returns the nonce bytes.
     * <p>A defensive copy is returned to preserve immutability.
     *
     * @return a copy of the nonce bytes (32 bytes)
     */
    @Override
    public byte[] nonce() {
        return Arrays.copyOf(nonce, nonce.length);
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof CancelAuthorization other)) return false;
        return authorizer.equals(other.authorizer)
            && Arrays.equals(nonce, other.nonce);
    }

    @Override
    public int hashCode() {
        return Objects.hash(authorizer, Arrays.hashCode(nonce));
    }

    @Override
    public String toString() {
        return "CancelAuthorization[authorizer=" + authorizer.value()
            + ", nonce=0x" + Hex.encodeNoPrefix(nonce) + "]";
    }
}
