// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004.registration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.Objects;

/**
 * ERC-8004 Agent Registration File (the "Agent Card").
 *
 * <p>Parsed from the JSON document at the agent's {@code tokenURI}. Enables
 * programmatic agent discovery — find an agent's service endpoints, supported
 * trust models, and cross-chain registrations.
 *
 * <p>Example:
 * <pre>{@code
 * AgentRegistration card = AgentRegistration.fromJson(jsonString);
 * card.services().stream()
 *     .filter(s -> "a2a".equals(s.name()))
 *     .findFirst()
 *     .ifPresent(s -> System.out.println("A2A endpoint: " + s.endpoint()));
 * }</pre>
 *
 * @param type           schema version URI (MUST be present)
 * @param name           human-readable agent name
 * @param description    natural language description of capabilities
 * @param image          avatar/logo URL
 * @param services       network endpoints the agent exposes
 * @param x402Support    whether the agent accepts x402 HTTP payments
 * @param active         whether the agent is currently operational
 * @param registrations  links to on-chain identity registrations
 * @param supportedTrust trust model categories
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentRegistration(
    String type,
    String name,
    String description,
    String image,
    List<AgentService> services,
    Boolean x402Support,
    Boolean active,
    List<ChainRegistration> registrations,
    List<String> supportedTrust
) {

    private static final ObjectMapper MAPPER = new ObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /**
     * Parses an Agent Registration File from JSON.
     *
     * @param json the JSON string
     * @return the parsed registration
     * @throws IllegalArgumentException if the JSON is invalid or null
     */
    public static AgentRegistration fromJson(String json) {
        Objects.requireNonNull(json, "json");
        try {
            return MAPPER.readValue(json, AgentRegistration.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                "Invalid agent registration JSON: " + e.getMessage(), e);
        }
    }
}
