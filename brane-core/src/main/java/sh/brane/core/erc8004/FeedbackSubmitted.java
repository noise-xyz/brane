// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import sh.brane.core.types.Address;

import java.math.BigInteger;

/**
 * Decoded ERC-8004 {@code NewFeedback} event from the Reputation Registry.
 *
 * <p>Emitted when feedback is submitted:
 * {@code event NewFeedback(uint256 indexed agentId, address indexed clientAddress,
 * uint64 feedbackIndex, int128 value, uint8 valueDecimals, string indexed indexedTag1,
 * string tag1, string tag2, string endpoint, string feedbackURI, bytes32 feedbackHash)}
 *
 * <p>Constructor uses raw ABI types for compatibility with {@code Abi.decodeEvents()}.
 *
 * @param agentId        the agent token ID
 * @param client         the feedback submitter address
 * @param feedbackIndex  the index of this feedback entry
 * @param value          the raw score value (int128)
 * @param valueDecimals  the number of decimal places (uint8)
 * @param tag1           primary tag
 * @param tag2           secondary tag
 * @param endpoint       the endpoint being rated
 * @param feedbackURI    URI to off-chain feedback details
 * @param feedbackHash   hash of off-chain feedback content
 * @see <a href="https://eips.ethereum.org/EIPS/eip-8004">EIP-8004</a>
 */
public record FeedbackSubmitted(
    BigInteger agentId,
    Address client,
    BigInteger feedbackIndex,
    BigInteger value,
    BigInteger valueDecimals,
    String tag1,
    String tag2,
    String endpoint,
    String feedbackURI,
    byte[] feedbackHash
) {

    /** Converts the raw {@code agentId} to a typed {@link AgentId}. */
    public AgentId toAgentId() {
        return new AgentId(agentId);
    }

    /** Converts the raw value/decimals to a typed {@link FeedbackValue}. */
    public FeedbackValue toFeedbackValue() {
        return new FeedbackValue(value, valueDecimals.intValue());
    }
}
