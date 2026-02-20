// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import sh.brane.core.types.Address;

import java.math.BigInteger;

/**
 * Decoded ERC-8004 {@code Registered} event from the Identity Registry.
 *
 * <p>Emitted when a new agent is registered:
 * {@code event Registered(uint256 indexed agentId, string agentURI, address indexed owner)}
 *
 * <p>Constructor uses raw ABI types for compatibility with {@code Abi.decodeEvents()}.
 *
 * @param agentId  the ERC-721 token ID
 * @param agentURI the agent's registration URI
 * @param owner    the owner address
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
public record AgentRegistered(BigInteger agentId, String agentURI, Address owner) {

    /** Converts the raw {@code agentId} to a typed {@link AgentId}. */
    public AgentId toAgentId() {
        return new AgentId(agentId);
    }
}
