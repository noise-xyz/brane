// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class AgentIdTest {

    @Test
    void of_createsFromLong() {
        var id = AgentId.of(42);
        assertEquals(BigInteger.valueOf(42), id.value());
    }

    @Test
    void of_zero_isValid() {
        var id = AgentId.of(0);
        assertEquals(BigInteger.ZERO, id.value());
    }

    @Test
    void constructor_rejectsNull() {
        assertThrows(NullPointerException.class, () -> new AgentId(null));
    }

    @Test
    void constructor_rejectsNegative() {
        assertThrows(IllegalArgumentException.class,
            () -> new AgentId(BigInteger.valueOf(-1)));
    }

    @Test
    void of_rejectsNegativeLong() {
        assertThrows(IllegalArgumentException.class, () -> AgentId.of(-1));
    }

    @Test
    void equality() {
        assertEquals(AgentId.of(7), AgentId.of(7));
        assertNotEquals(AgentId.of(7), AgentId.of(8));
    }

    @Test
    void toString_includesValue() {
        assertEquals("AgentId(42)", AgentId.of(42).toString());
    }
}
