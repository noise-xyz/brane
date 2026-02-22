// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.contract.erc8004;

/**
 * Aggregated validation summary for an ERC-8004 agent.
 *
 * <p>Returned by the Validation Registry's {@code getSummary()} view function.
 *
 * @param count           the number of validation responses
 * @param averageResponse the average response score (0-255)
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
public record ValidationSummary(long count, int averageResponse) {}
