// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import sh.brane.core.types.Address;
import sh.brane.core.types.Hash;

class AccessListEntryTest {

    @Test
    void constructorCopiesList() {
        final List<Hash> keys = new ArrayList<>();
        keys.add(new Hash("0x" + "1".repeat(64)));
        final AccessListEntry entry = new AccessListEntry(new Address("0x" + "a".repeat(40)), keys);

        keys.add(new Hash("0x" + "2".repeat(64)));

        assertEquals(List.of(new Hash("0x" + "1".repeat(64))), entry.storageKeys());
        assertNotSame(keys, entry.storageKeys());
    }

    @Test
    void nullChecks() {
        assertThrows(NullPointerException.class, () -> new AccessListEntry(null, List.of()));
        assertThrows(NullPointerException.class, () -> new AccessListEntry(new Address("0x" + "a".repeat(40)), null));
    }
}
