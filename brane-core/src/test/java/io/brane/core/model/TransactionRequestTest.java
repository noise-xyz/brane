package io.brane.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionRequestTest {

    @Test
    void accessListPropagates() {
        final List<AccessListEntry> entries =
                List.of(new AccessListEntry(new Address("0xabc"), List.of(new Hash("0x01"))));

        final TransactionRequest request =
                new TransactionRequest(null, null, null, null, null, null, null, null, null, true, entries);

        assertEquals(entries, request.accessList());
        assertEquals(entries, request.accessListOrEmpty());
    }

    @Test
    void accessListOrEmptyNullSafe() {
        final TransactionRequest request =
                new TransactionRequest(null, null, null, null, null, null, null, null, null, true, null);

        assertTrue(request.accessListOrEmpty().isEmpty());
    }
}
