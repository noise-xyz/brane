// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.types;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class AddressTest {

    @Test
    void acceptsValidAddress() {
        Address address = new Address("0x000000000000000000000000000000000000dEaD");
        assertEquals("0x000000000000000000000000000000000000dead", address.value());
        assertEquals(20, address.toBytes().length);
    }

    @Test
    void rejectsInvalidLength() {
        assertThrows(IllegalArgumentException.class, () -> new Address("0x1234"));
    }

    @Test
    void rejectsMissingPrefix() {
        assertThrows(IllegalArgumentException.class, () -> new Address("1234567890abcdef1234567890abcdef12345678"));
    }

    @Test
    void roundTripBytes() {
        Address original = new Address("0x1234567890abcdef1234567890abcdef12345678");
        Address copy = Address.fromBytes(original.toBytes());
        assertEquals(original, copy);
    }
}
