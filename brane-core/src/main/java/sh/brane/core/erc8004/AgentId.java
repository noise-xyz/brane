// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import java.math.BigInteger;
import java.util.Objects;

/**
 * ERC-8004 agent token identifier.
 *
 * <p>Wraps the {@code uint256} ERC-721 token ID assigned to an agent when it is
 * registered in an Identity Registry.
 *
 * @param value the ERC-721 token ID (must be non-negative)
 * @throws NullPointerException     if value is null
 * @throws IllegalArgumentException if value is negative
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
public record AgentId(BigInteger value) {

    public AgentId {
        Objects.requireNonNull(value, "agentId");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("agentId must be non-negative");
        }
    }

    /**
     * Creates an AgentId from a long value.
     *
     * @param id the agent token ID
     * @return the agent identifier
     * @throws IllegalArgumentException if id is negative
     */
    public static AgentId of(long id) {
        return new AgentId(BigInteger.valueOf(id));
    }

    @Override
    public String toString() {
        return "AgentId(" + value + ")";
    }
}
