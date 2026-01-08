package io.brane.core.crypto.eip712;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.brane.core.error.Eip712Exception;

/**
 * Raw typed data payload parsed from JSON.
 * Mirrors the eth_signTypedData_v4 request format.
 *
 * @param domain the EIP-712 domain separator fields
 * @param primaryType the primary type name to encode
 * @param types map of type names to their field definitions
 * @param message the message data to sign
 * @see <a href="https://eips.ethereum.org/EIPS/eip-712">EIP-712</a>
 */
public record TypedDataPayload(
        @JsonProperty("domain") Eip712Domain domain,
        @JsonProperty("primaryType") String primaryType,
        @JsonProperty("types") Map<String, List<TypedDataField>> types,
        @JsonProperty("message") Map<String, Object> message
) {
    public TypedDataPayload {
        Objects.requireNonNull(primaryType, "primaryType");
        Objects.requireNonNull(types, "types");
        Objects.requireNonNull(message, "message");
        if (!types.containsKey(primaryType)) {
            throw Eip712Exception.primaryTypeNotFound(primaryType);
        }
    }
}
