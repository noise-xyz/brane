// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004.registration;

import java.math.BigInteger;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import sh.brane.core.erc8004.AgentId;
import sh.brane.core.erc8004.AgentIdentifier;
import sh.brane.core.erc8004.RegistryId;

/**
 * Cross-chain registration entry linking an on-chain identity to an Agent Card.
 *
 * @param agentId       the ERC-721 token ID
 * @param agentRegistry the registry identifier in {@code eip155:{chainId}:{address}} format
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ChainRegistration(
    BigInteger agentId,
    String agentRegistry
) {

    /**
     * Parses the {@code agentRegistry} string into a typed {@link RegistryId}.
     *
     * @return the parsed registry identifier
     * @throws IllegalArgumentException if the format is invalid
     */
    public RegistryId toRegistryId() {
        return RegistryId.parse(agentRegistry);
    }

    /**
     * Constructs a full {@link AgentIdentifier} from this registration entry.
     *
     * @return the agent identifier
     * @throws IllegalArgumentException if the registry format is invalid
     */
    public AgentIdentifier toAgentIdentifier() {
        return new AgentIdentifier(toRegistryId(), new AgentId(agentId));
    }
}
