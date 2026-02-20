// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.contract.erc8004;

import sh.brane.core.erc8004.FeedbackValue;

/**
 * Aggregated feedback summary for an ERC-8004 agent.
 *
 * <p>Returned by the Reputation Registry's {@code getSummary()} view function.
 *
 * @param count          the number of (non-revoked) feedback entries
 * @param aggregateScore the aggregate score across all matching feedback
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
public record FeedbackSummary(long count, FeedbackValue aggregateScore) {}
