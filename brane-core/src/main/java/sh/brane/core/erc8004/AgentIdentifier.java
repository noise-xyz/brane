// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import sh.brane.core.types.Address;

import java.util.Objects;

/**
 * Globally unique identifier for an ERC-8004 agent across all chains.
 *
 * <p>Combines the registry location ({@link RegistryId}) with the agent's
 * ERC-721 token ID ({@link AgentId}).
 *
 * @param registry identifies the chain and registry contract
 * @param agentId  the ERC-721 token ID within that registry
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
public record AgentIdentifier(RegistryId registry, AgentId agentId) {

    public AgentIdentifier {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(agentId, "agentId");
    }

    /** Shortcut: the chain ID from the registry. */
    public long chainId() {
        return registry.chainId();
    }

    /** Shortcut: the registry contract address. */
    public Address registryAddress() {
        return registry.address();
    }

    /**
     * Creates an identifier from individual components.
     *
     * @param chainId         the EIP-155 chain ID
     * @param registryAddress the registry contract address
     * @param agentId         the agent token ID
     * @return the agent identifier
     */
    public static AgentIdentifier of(long chainId, Address registryAddress, AgentId agentId) {
        return new AgentIdentifier(new RegistryId(chainId, registryAddress), agentId);
    }
}
