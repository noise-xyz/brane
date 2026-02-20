// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004.registration;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class AgentRegistrationTest {

    private static final String FULL_JSON = """
        {
            "type": "https://eips.ethereum.org/EIPS/eip-8004",
            "name": "TestAgent",
            "description": "A test agent for unit testing",
            "image": "https://example.com/avatar.png",
            "services": [
                {
                    "name": "a2a",
                    "endpoint": "https://agent.example.com/a2a",
                    "version": "1.0",
                    "skills": ["search", "summarize"],
                    "domains": ["finance"]
                },
                {
                    "name": "mcp",
                    "endpoint": "https://agent.example.com/mcp"
                }
            ],
            "x402Support": true,
            "active": true,
            "registrations": [
                {
                    "agentId": 42,
                    "agentRegistry": "eip155:1:0x8004a169fb4a3325136eb29fa0ceb6d2e539a432"
                },
                {
                    "agentId": 42,
                    "agentRegistry": "eip155:8453:0x8004a169fb4a3325136eb29fa0ceb6d2e539a432"
                }
            ],
            "supportedTrust": ["erc8004", "coinbase-attestation"]
        }
        """;

    private static final String MINIMAL_JSON = """
        {
            "type": "https://eips.ethereum.org/EIPS/eip-8004"
        }
        """;

    // ═══════════════════════════════════════════════════════════════════
    // Full JSON parsing
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void fromJson_parsesFullDocument() {
        var card = AgentRegistration.fromJson(FULL_JSON);

        assertEquals("https://eips.ethereum.org/EIPS/eip-8004", card.type());
        assertEquals("TestAgent", card.name());
        assertEquals("A test agent for unit testing", card.description());
        assertEquals("https://example.com/avatar.png", card.image());
        assertTrue(card.x402Support());
        assertTrue(card.active());
    }

    @Test
    void fromJson_parsesServices() {
        var card = AgentRegistration.fromJson(FULL_JSON);
        assertNotNull(card.services());
        assertEquals(2, card.services().size());

        var a2a = card.services().get(0);
        assertEquals("a2a", a2a.name());
        assertEquals("https://agent.example.com/a2a", a2a.endpoint());
        assertEquals("1.0", a2a.version());
        assertEquals(2, a2a.skills().size());
        assertTrue(a2a.skills().contains("search"));
        assertEquals(1, a2a.domains().size());

        var mcp = card.services().get(1);
        assertEquals("mcp", mcp.name());
        assertNull(mcp.version());
        assertNull(mcp.skills());
    }

    @Test
    void fromJson_parsesRegistrations() {
        var card = AgentRegistration.fromJson(FULL_JSON);
        assertNotNull(card.registrations());
        assertEquals(2, card.registrations().size());

        var reg = card.registrations().get(0);
        assertEquals(BigInteger.valueOf(42), reg.agentId());
        assertEquals("eip155:1:0x8004a169fb4a3325136eb29fa0ceb6d2e539a432", reg.agentRegistry());
    }

    @Test
    void fromJson_parsesSupportedTrust() {
        var card = AgentRegistration.fromJson(FULL_JSON);
        assertNotNull(card.supportedTrust());
        assertEquals(2, card.supportedTrust().size());
        assertTrue(card.supportedTrust().contains("erc8004"));
    }

    // ═══════════════════════════════════════════════════════════════════
    // Minimal JSON
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void fromJson_parsesMinimalDocument() {
        var card = AgentRegistration.fromJson(MINIMAL_JSON);
        assertEquals("https://eips.ethereum.org/EIPS/eip-8004", card.type());
        assertNull(card.name());
        assertNull(card.services());
        assertNull(card.x402Support());
        assertNull(card.registrations());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Unknown properties
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void fromJson_ignoresUnknownProperties() {
        String json = """
            {
                "type": "https://eips.ethereum.org/EIPS/eip-8004",
                "name": "TestAgent",
                "futureField": "some value",
                "anotherUnknown": 123
            }
            """;
        var card = AgentRegistration.fromJson(json);
        assertEquals("TestAgent", card.name());
    }

    @Test
    void fromJson_ignoresUnknownServiceProperties() {
        String json = """
            {
                "type": "https://eips.ethereum.org/EIPS/eip-8004",
                "services": [
                    {
                        "name": "a2a",
                        "endpoint": "https://example.com",
                        "unknownField": true
                    }
                ]
            }
            """;
        var card = AgentRegistration.fromJson(json);
        assertEquals("a2a", card.services().get(0).name());
    }

    // ═══════════════════════════════════════════════════════════════════
    // Error handling
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void fromJson_rejectsInvalidJson() {
        assertThrows(IllegalArgumentException.class,
            () -> AgentRegistration.fromJson("not json"));
    }

    @Test
    void fromJson_rejectsNull() {
        assertThrows(NullPointerException.class,
            () -> AgentRegistration.fromJson(null));
    }

    // ═══════════════════════════════════════════════════════════════════
    // ChainRegistration bridge methods
    // ═══════════════════════════════════════════════════════════════════

    @Test
    void chainRegistration_toRegistryId() {
        var card = AgentRegistration.fromJson(FULL_JSON);
        var reg = card.registrations().get(0);
        var registryId = reg.toRegistryId();

        assertEquals(1L, registryId.chainId());
        assertEquals("0x8004a169fb4a3325136eb29fa0ceb6d2e539a432", registryId.address().value());
    }

    @Test
    void chainRegistration_toAgentIdentifier() {
        var card = AgentRegistration.fromJson(FULL_JSON);
        var reg = card.registrations().get(1);
        var identifier = reg.toAgentIdentifier();

        assertEquals(8453L, identifier.chainId());
        assertEquals(BigInteger.valueOf(42), identifier.agentId().value());
    }
}
