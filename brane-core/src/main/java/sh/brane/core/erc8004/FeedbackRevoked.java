// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import java.math.BigInteger;

import sh.brane.core.types.Address;

/**
 * Decoded ERC-8004 {@code FeedbackRevoked} event from the Reputation Registry.
 *
 * <p>Emitted when feedback is revoked:
 * {@code event FeedbackRevoked(uint256 indexed agentId, address indexed clientAddress,
 * uint64 indexed feedbackIndex)}
 *
 * <p>Constructor uses raw ABI types for compatibility with {@code Abi.decodeEvents()}.
 *
 * @param agentId       the agent token ID
 * @param client        the original feedback submitter
 * @param feedbackIndex the index of the revoked feedback
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
public record FeedbackRevoked(BigInteger agentId, Address client, BigInteger feedbackIndex) {}
