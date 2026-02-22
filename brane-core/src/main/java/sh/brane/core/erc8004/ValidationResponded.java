// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import java.math.BigInteger;

import sh.brane.core.types.Address;
import sh.brane.core.types.HexData;

/**
 * Decoded ERC-8004 {@code ValidationResponse} event from the Validation Registry.
 *
 * <p>Emitted when a validator responds:
 * {@code event ValidationResponse(address indexed validatorAddress, uint256 indexed agentId,
 * bytes32 indexed requestHash, uint8 response, string responseURI,
 * bytes32 responseHash, string tag)}
 *
 * <p>Constructor uses raw ABI types for compatibility with {@code Abi.decodeEvents()}.
 *
 * @param validator    the validator address
 * @param agentId      the agent token ID
 * @param requestHash  hash of the original request
 * @param response     the validation response score (uint8)
 * @param responseURI  URI to the validation response details
 * @param responseHash hash of the response content
 * @param tag          a classification tag for the validation
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
public record ValidationResponded(
    Address validator,
    BigInteger agentId,
    HexData requestHash,
    BigInteger response,
    String responseURI,
    HexData responseHash,
    String tag
) {}
