// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.crypto.eip3009;

import sh.brane.core.crypto.eip712.TypeDefinition;
import sh.brane.core.crypto.eip712.TypedDataField;
import sh.brane.core.types.Address;
import sh.brane.core.types.Hash;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import sh.brane.primitives.Hex;

/**
 * EIP-3009 ReceiveWithAuthorization message.
 *
 * <p>Similar to {@link TransferAuthorization} but requires {@code msg.sender == to},
 * preventing front-running by ensuring only the intended recipient can submit
 * the authorization on-chain.
 *
 * @param from        the payer address
 * @param to          the payee address (must be {@code msg.sender} on-chain)
 * @param value       the transfer amount in token smallest units
 * @param validAfter  earliest timestamp (unix seconds) when authorization is valid
 * @param validBefore latest timestamp (unix seconds) when authorization expires
 * @param nonce       random 32-byte nonce (NOT sequential)
 * @see <a href="https://eips.ethereum.org/EIPS/eip-3009">EIP-3009</a>
 */
public record ReceiveAuthorization(
    Address from,
    Address to,
    BigInteger value,
    BigInteger validAfter,
    BigInteger validBefore,
    byte[] nonce
) {
    /** Length in bytes of the EIP-3009 nonce (bytes32). */
    private static final int NONCE_LENGTH = 32;

    /**
     * EIP-712 typehash for ReceiveWithAuthorization.
     *
     * <p>{@code keccak256("ReceiveWithAuthorization(address from,address to,
     * uint256 value,uint256 validAfter,uint256 validBefore,bytes32 nonce)")}
     */
    public static final Hash TYPEHASH = new Hash(
        "0xd099cc98ef71107a616c4f0f941f04c322d8e254fe26b3c6668db87aae413de8");

    /** EIP-712 type definition for ReceiveWithAuthorization. */
    public static final TypeDefinition<ReceiveAuthorization> DEFINITION =
        TypeDefinition.forRecord(
            ReceiveAuthorization.class,
            "ReceiveWithAuthorization",
            Map.of("ReceiveWithAuthorization", List.of(
                TypedDataField.of("from", "address"),
                TypedDataField.of("to", "address"),
                TypedDataField.of("value", "uint256"),
                TypedDataField.of("validAfter", "uint256"),
                TypedDataField.of("validBefore", "uint256"),
                TypedDataField.of("nonce", "bytes32")
            ))
        );

    /** Compact constructor — validates all fields and makes defensive copy of nonce. */
    public ReceiveAuthorization {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        Objects.requireNonNull(value, "value");
        Objects.requireNonNull(validAfter, "validAfter");
        Objects.requireNonNull(validBefore, "validBefore");
        Objects.requireNonNull(nonce, "nonce");
        if (validBefore.compareTo(validAfter) <= 0) {
            throw new IllegalArgumentException(
                "validBefore (" + validBefore + ") must be greater than validAfter (" + validAfter + ")");
        }
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
        if (!(obj instanceof ReceiveAuthorization other)) return false;
        return from.equals(other.from) && to.equals(other.to)
            && value.equals(other.value)
            && validAfter.equals(other.validAfter) && validBefore.equals(other.validBefore)
            && Arrays.equals(nonce, other.nonce);
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, value, validAfter, validBefore, Arrays.hashCode(nonce));
    }

    @Override
    public String toString() {
        return "ReceiveAuthorization[from=" + from.value() + ", to=" + to.value()
            + ", value=" + value + ", validAfter=" + validAfter
            + ", validBefore=" + validBefore
            + ", nonce=0x" + Hex.encodeNoPrefix(nonce) + "]";
    }
}
