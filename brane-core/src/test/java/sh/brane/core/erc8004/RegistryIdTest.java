// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import sh.brane.core.types.Address;

class RegistryIdTest {

    private static final Address IDENTITY_ADDR =
        new Address("0x8004a169fb4a3325136eb29fa0ceb6d2e539a432");

    @Test
    void parse_validIdentifier() {
        var id = RegistryId.parse("eip155:1:0x8004a169fb4a3325136eb29fa0ceb6d2e539a432");
        assertEquals(1L, id.chainId());
        assertEquals(IDENTITY_ADDR, id.address());
    }

    @Test
    void parse_basMainnet() {
        var id = RegistryId.parse("eip155:8453:0x8004a169fb4a3325136eb29fa0ceb6d2e539a432");
        assertEquals(8453L, id.chainId());
    }

    @Test
    void parse_rejectsNull() {
        assertThrows(NullPointerException.class, () -> RegistryId.parse(null));
    }

    @Test
    void parse_rejectsBadFormat_tooFewParts() {
        assertThrows(IllegalArgumentException.class,
            () -> RegistryId.parse("eip155:1"));
    }

    @Test
    void parse_rejectsBadFormat_tooManyParts() {
        assertThrows(IllegalArgumentException.class,
            () -> RegistryId.parse("eip155:1:0xabc:extra"));
    }

    @Test
    void parse_rejectsUnsupportedNamespace() {
        assertThrows(IllegalArgumentException.class,
            () -> RegistryId.parse("cosmos:1:0x8004a169fb4a3325136eb29fa0ceb6d2e539a432"));
    }

    @Test
    void constructor_rejectsZeroChainId() {
        assertThrows(IllegalArgumentException.class,
            () -> new RegistryId(0, IDENTITY_ADDR));
    }

    @Test
    void constructor_rejectsNegativeChainId() {
        assertThrows(IllegalArgumentException.class,
            () -> new RegistryId(-1, IDENTITY_ADDR));
    }

    @Test
    void constructor_rejectsNullAddress() {
        assertThrows(NullPointerException.class,
            () -> new RegistryId(1, null));
    }

    @Test
    void toString_matchesExpectedFormat() {
        var id = new RegistryId(1, IDENTITY_ADDR);
        assertEquals("eip155:1:0x8004a169fb4a3325136eb29fa0ceb6d2e539a432", id.toString());
    }

    @Test
    void parse_roundTrip() {
        String original = "eip155:8453:0x8004a169fb4a3325136eb29fa0ceb6d2e539a432";
        assertEquals(original, RegistryId.parse(original).toString());
    }

    @Test
    void equality() {
        var a = new RegistryId(1, IDENTITY_ADDR);
        var b = new RegistryId(1, IDENTITY_ADDR);
        var c = new RegistryId(8453, IDENTITY_ADDR);

        assertEquals(a, b);
        assertNotEquals(a, c);
    }
}
