// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.crypto.eip712;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import sh.brane.core.error.Eip712Exception;

/**
 * JSON parsing support for EIP-712 typed data.
 * Compatible with eth_signTypedData_v4 JSON format used by MetaMask, WalletConnect, etc.
 *
 * <p>Example usage:
 * <pre>{@code
 * // Parse JSON from dapp request
 * String jsonFromDapp = walletConnectRequest.getTypedData();
 * TypedDataPayload payload = TypedDataJson.parse(jsonFromDapp);
 *
 * // Or parse and create signable TypedData in one step
 * TypedData<?> typedData = TypedDataJson.parseAndValidate(jsonFromDapp);
 * Signature sig = typedData.sign(signer);
 * }</pre>
 *
 * @see TypedDataPayload
 * @see TypedData
 * @since 0.1.0-alpha
 */
public final class TypedDataJson {
    private static final ObjectMapper MAPPER = createMapper();

    private TypedDataJson() {}

    /**
     * Parses EIP-712 typed data from JSON string.
     *
     * @param json the JSON string in eth_signTypedData_v4 format
     * @return parsed typed data payload
     * @throws Eip712Exception if JSON is invalid or missing required fields
     */
    public static TypedDataPayload parse(String json) {
        try {
            return MAPPER.readValue(json, TypedDataPayload.class);
        } catch (JsonProcessingException e) {
            throw new Eip712Exception("Invalid EIP-712 JSON: " + e.getMessage(), e);
        }
    }

    /**
     * Parses and validates typed data, then creates a signable TypedData instance.
     *
     * @param json the JSON string in eth_signTypedData_v4 format
     * @return validated TypedData ready for signing or hashing
     * @throws Eip712Exception if JSON is invalid, missing required fields, or validation fails
     */
    public static TypedData<?> parseAndValidate(String json) {
        TypedDataPayload payload = parse(json);
        return TypedData.fromPayload(payload);
    }

    private static ObjectMapper createMapper() {
        return new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }
}
