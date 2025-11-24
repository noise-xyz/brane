package io.brane.core.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.brane.core.model.AccessListEntry;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import java.util.List;
import org.junit.jupiter.api.Test;

class TxBuilderTest {

    @Test
    void eip1559BuilderAccessList() {
        final AccessListEntry entry =
                new AccessListEntry(new Address("0xabc"), List.of(new Hash("0x01")));

        final TransactionRequest request = TxBuilder.eip1559().accessList(List.of(entry)).build();

        assertNotNull(request.accessList());
        assertEquals(List.of(entry), request.accessList());
    }

    @Test
    void accessListFluentChaining() {
        final TransactionRequest request = TxBuilder.eip1559().accessList(List.of()).gasLimit(1L).build();

        assertEquals(List.of(), request.accessList());
    }
}
