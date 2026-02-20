// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.erc8004;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class MetadataEntryTest {

    @Test
    void constructor_storesKeyAndValue() {
        var entry = new MetadataEntry("version", "1.0".getBytes());
        assertEquals("version", entry.key());
        assertArrayEquals("1.0".getBytes(), entry.value());
    }

    @Test
    void constructor_rejectsNullKey() {
        assertThrows(NullPointerException.class,
            () -> new MetadataEntry(null, new byte[0]));
    }

    @Test
    void constructor_rejectsNullValue() {
        assertThrows(NullPointerException.class,
            () -> new MetadataEntry("key", null));
    }

    @Test
    void value_returnsDefensiveCopy() {
        byte[] original = {1, 2, 3};
        var entry = new MetadataEntry("key", original);

        // Modify original — should not affect entry
        original[0] = 99;
        assertEquals(1, entry.value()[0]);

        // Modify returned copy — should not affect entry
        byte[] copy = entry.value();
        copy[0] = 99;
        assertEquals(1, entry.value()[0]);
    }

    @Test
    void equality_usesArrayContent() {
        var a = new MetadataEntry("key", new byte[]{1, 2, 3});
        var b = new MetadataEntry("key", new byte[]{1, 2, 3});
        var c = new MetadataEntry("key", new byte[]{1, 2, 4});

        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void hashCode_consistentWithEquals() {
        var a = new MetadataEntry("key", new byte[]{1, 2, 3});
        var b = new MetadataEntry("key", new byte[]{1, 2, 3});
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void toString_showsByteCount() {
        var entry = new MetadataEntry("version", new byte[]{1, 2, 3});
        assertTrue(entry.toString().contains("3 bytes"));
    }
}
