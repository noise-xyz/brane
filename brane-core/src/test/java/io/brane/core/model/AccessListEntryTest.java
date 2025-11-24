package io.brane.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class AccessListEntryTest {

    @Test
    void constructorCopiesList() {
        final List<Hash> keys = new ArrayList<>();
        keys.add(new Hash("0x01"));
        final AccessListEntry entry = new AccessListEntry(new Address("0xabc"), keys);

        keys.add(new Hash("0x02"));

        assertEquals(List.of(new Hash("0x01")), entry.storageKeys());
        assertNotSame(keys, entry.storageKeys());
    }

    @Test
    void nullChecks() {
        assertThrows(NullPointerException.class, () -> new AccessListEntry(null, List.of()));
        assertThrows(NullPointerException.class, () -> new AccessListEntry(new Address("0xabc"), null));
    }
}
