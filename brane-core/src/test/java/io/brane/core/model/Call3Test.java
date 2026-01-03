package io.brane.core.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Address;
import io.brane.core.types.HexData;

/**
 * Tests for Call3 validation.
 */
class Call3Test {

    private static final Address VALID_TARGET = new Address("0x" + "a".repeat(40));

    @Test
    void validCall3Creation() {
        Call3 call = new Call3(VALID_TARGET, false, HexData.EMPTY);

        assertNotNull(call);
        assertEquals(VALID_TARGET, call.target());
        assertFalse(call.allowFailure());
        assertEquals(HexData.EMPTY, call.callData());
    }

    @Test
    void allowFailureCanBeTrue() {
        Call3 call = new Call3(VALID_TARGET, true, HexData.EMPTY);

        assertTrue(call.allowFailure());
    }

    @Test
    void rejectsNullTarget() {
        assertThrows(NullPointerException.class, () -> new Call3(
                null,  // null target
                false,
                HexData.EMPTY
        ));
    }

    @Test
    void rejectsNullCallData() {
        assertThrows(NullPointerException.class, () -> new Call3(
                VALID_TARGET,
                false,
                null  // null callData
        ));
    }
}
