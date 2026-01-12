// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.crypto.eip712;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.brane.core.error.Eip712Exception;

/**
 * Raw typed data payload parsed from JSON.
 * Mirrors the eth_signTypedData_v4 request format.
 * <p>
 * The compact constructor validates that all parameters are non-null and that
 * the {@code primaryType} exists in the {@code types} map. A {@link NullPointerException}
 * is thrown if any parameter is null, and an {@link Eip712Exception} is thrown if
 * {@code primaryType} is not found in {@code types}.
 *
 * @param domain the EIP-712 domain separator fields (must not be null)
 * @param primaryType the primary type name to encode (must not be null)
 * @param types map of type names to their field definitions (must not be null, must contain primaryType)
 * @param message the message data to sign (must not be null)
 * @see <a href="https://eips.ethereum.org/EIPS/eip-712">EIP-712</a>
 */
public record TypedDataPayload(
        @JsonProperty("domain") Eip712Domain domain,
        @JsonProperty("primaryType") String primaryType,
        @JsonProperty("types") Map<String, List<TypedDataField>> types,
        @JsonProperty("message") Map<String, Object> message
) {
    public TypedDataPayload {
        Objects.requireNonNull(domain, "domain");
        Objects.requireNonNull(primaryType, "primaryType");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(message, "message");
        if (!types.containsKey(primaryType)) {
            throw Eip712Exception.primaryTypeNotFound(primaryType);
        }
    }
}
