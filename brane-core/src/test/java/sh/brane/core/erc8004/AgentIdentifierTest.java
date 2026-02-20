// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import static org.junit.jupiter.api.Assertions.*;

import sh.brane.core.types.Address;

import org.junit.jupiter.api.Test;

class AgentIdentifierTest {

    private static final Address REGISTRY_ADDR =
        new Address("0x8004a169fb4a3325136eb29fa0ceb6d2e539a432");

    @Test
    void of_createsFromComponents() {
        var id = AgentIdentifier.of(1L, REGISTRY_ADDR, AgentId.of(42));
        assertEquals(1L, id.chainId());
        assertEquals(REGISTRY_ADDR, id.registryAddress());
        assertEquals(AgentId.of(42), id.agentId());
    }

    @Test
    void constructor_rejectsNullRegistry() {
        assertThrows(NullPointerException.class,
            () -> new AgentIdentifier(null, AgentId.of(1)));
    }

    @Test
    void constructor_rejectsNullAgentId() {
        assertThrows(NullPointerException.class,
            () -> new AgentIdentifier(new RegistryId(1, REGISTRY_ADDR), null));
    }

    @Test
    void chainId_delegatesToRegistry() {
        var id = AgentIdentifier.of(8453L, REGISTRY_ADDR, AgentId.of(7));
        assertEquals(8453L, id.chainId());
    }

    @Test
    void registryAddress_delegatesToRegistry() {
        var id = AgentIdentifier.of(1L, REGISTRY_ADDR, AgentId.of(7));
        assertEquals(REGISTRY_ADDR, id.registryAddress());
    }

    @Test
    void equality() {
        var a = AgentIdentifier.of(1L, REGISTRY_ADDR, AgentId.of(42));
        var b = AgentIdentifier.of(1L, REGISTRY_ADDR, AgentId.of(42));
        var c = AgentIdentifier.of(1L, REGISTRY_ADDR, AgentId.of(99));

        assertEquals(a, b);
        assertNotEquals(a, c);
    }
}
