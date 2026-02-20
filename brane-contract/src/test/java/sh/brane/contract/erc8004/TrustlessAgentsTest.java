// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.contract.erc8004;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import sh.brane.core.erc8004.registration.AgentRegistration;

class TrustlessAgentsTest {

    // ═══════════════════════════════════════════════════════════════════
    // decodeFeedbackSummary — tuple decoding
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void decodeFeedbackSummary_zeroValues() {
        // 3 slots of 32 bytes each, all zero
        String hex = "0x" + "0".repeat(192);
        var summary = TrustlessAgents.decodeFeedbackSummary(hex);
        assertEquals(0L, summary.count());
        assertEquals(BigInteger.ZERO, summary.aggregateScore().value());
        assertEquals(0, summary.aggregateScore().decimals());
    }

    @Test
    void decodeFeedbackSummary_positiveValues() {
        // count=5 (slot 0), summaryValue=9977 (slot 1), decimals=2 (slot 2)
        String slot0 = leftPad64("5");       // uint64 count = 5
        String slot1 = leftPad64("26f9");    // int128 summaryValue = 9977
        String slot2 = leftPad64("2");       // uint8 decimals = 2
        String hex = "0x" + slot0 + slot1 + slot2;

        var summary = TrustlessAgents.decodeFeedbackSummary(hex);
        assertEquals(5L, summary.count());
        assertEquals(BigInteger.valueOf(9977), summary.aggregateScore().value());
        assertEquals(2, summary.aggregateScore().decimals());
    }

    @Test
    void decodeFeedbackSummary_negativeValue() {
        // int128 -32: ABI sign-extends to 256 bits → all ff in upper bytes
        String slot0 = leftPad64("1");       // count = 1
        String slot1 = "ffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffe0";
        String slot2 = leftPad64("1");       // decimals = 1
        String hex = "0x" + slot0 + slot1 + slot2;

        var summary = TrustlessAgents.decodeFeedbackSummary(hex);
        assertEquals(1L, summary.count());
        assertEquals(BigInteger.valueOf(-32), summary.aggregateScore().value());
        assertEquals(1, summary.aggregateScore().decimals());
    }

    @Test
    void decodeFeedbackSummary_rejectsTooShort() {
        String hex = "0x" + "0".repeat(64); // only 1 slot instead of 3
        assertThrows(IllegalArgumentException.class,
            () -> TrustlessAgents.decodeFeedbackSummary(hex));
    }

    // ═══════════════════════════════════════════════════════════════════
    // parseRegistration — static helper
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void parseRegistration_delegatesToAgentRegistration() {
        String json = """
            {
                "type": "https://eips.ethereum.org/EIPS/eip-8004",
                "name": "TestAgent"
            }
            """;
        AgentRegistration card = TrustlessAgents.parseRegistration(json);
        assertEquals("TestAgent", card.name());
    }

    @Test
    void parseRegistration_rejectsInvalidJson() {
        assertThrows(IllegalArgumentException.class,
            () -> TrustlessAgents.parseRegistration("invalid"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Summary records
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void feedbackSummary_recordFields() {
        var fv = sh.brane.core.erc8004.FeedbackValue.of(87, 0);
        var summary = new FeedbackSummary(10, fv);
        assertEquals(10L, summary.count());
        assertEquals(fv, summary.aggregateScore());
    }

    @Test
    void validationSummary_recordFields() {
        var summary = new ValidationSummary(5, 95);
        assertEquals(5L, summary.count());
        assertEquals(95, summary.averageResponse());
    }

    // ═══════════════════════════════════════════════════════════════════
    // ABI constants parse correctly
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void identityAbi_parsesSuccessfully() {
        var abi = sh.brane.core.abi.Abi.fromJson(Erc8004AbiConstants.IDENTITY_REGISTRY_ABI);
        assertNotNull(abi);
        assertTrue(abi.getFunction("register").isPresent());
        assertTrue(abi.getFunction("setMetadata").isPresent());
        assertTrue(abi.getFunction("getMetadata").isPresent());
        assertTrue(abi.getFunction("getAgentWallet").isPresent());
        assertTrue(abi.getFunction("tokenURI").isPresent());
    }

    @Test
    void reputationAbi_parsesSuccessfully() {
        var abi = sh.brane.core.abi.Abi.fromJson(Erc8004AbiConstants.REPUTATION_REGISTRY_ABI);
        assertNotNull(abi);
        assertTrue(abi.getFunction("giveFeedback").isPresent());
        assertTrue(abi.getFunction("revokeFeedback").isPresent());
        assertTrue(abi.getFunction("getSummary").isPresent());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Helpers
    // ═══════════════════════════════════════════════════════════════════

    /** Left-pads a hex string to 64 chars (32 bytes). */
    private static String leftPad64(String hex) {
        return "0".repeat(64 - hex.length()) + hex;
    }
}
