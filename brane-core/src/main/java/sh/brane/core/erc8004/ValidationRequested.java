// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import java.math.BigInteger;

import sh.brane.core.types.Address;
import sh.brane.core.types.HexData;

/**
 * Decoded ERC-8004 {@code ValidationRequest} event from the Validation Registry.
 *
 * <p>Emitted when a validation is requested:
 * {@code event ValidationRequest(address indexed validatorAddress, uint256 indexed agentId,
 * string requestURI, bytes32 indexed requestHash)}
 *
 * <p>Constructor uses raw ABI types for compatibility with {@code Abi.decodeEvents()}.
 *
 * @param validator   the designated validator address
 * @param agentId     the agent token ID
 * @param requestURI  URI to the validation request details
 * @param requestHash hash of the request content
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
public record ValidationRequested(
    Address validator,
    BigInteger agentId,
    String requestURI,
    HexData requestHash
) {}
