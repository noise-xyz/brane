// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.contract.erc8004;

import sh.brane.core.erc8004.AgentId;
import sh.brane.core.types.Address;
import sh.brane.core.types.Hash;

/**
 * Validation status for a specific validation request.
 *
 * <p>Returned by the Validation Registry's {@code getValidationStatus()} view function.
 *
 * @param validator    the validator address
 * @param agentId      the agent token ID
 * @param response     the validation response score (0-255)
 * @param responseHash hash of the response content
 * @param tag          the classification tag
 * @param lastUpdate   timestamp of the last update (unix seconds)
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
public record ValidationStatus(
    Address validator,
    AgentId agentId,
    int response,
    Hash responseHash,
    String tag,
    long lastUpdate
) {}
