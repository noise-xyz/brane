// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004.registration;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

/**
 * A service endpoint exposed by an ERC-8004 agent.
 *
 * @param name     service type: "a2a", "mcp", "web", "ens", etc. (MUST)
 * @param endpoint service URL or identifier (MUST)
 * @param version  protocol version
 * @param skills   capability tags (OASF taxonomy)
 * @param domains  domain categories
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record AgentService(
    String name,
    String endpoint,
    String version,
    List<String> skills,
    List<String> domains
) {}
