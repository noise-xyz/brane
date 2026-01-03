package io.brane.core.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.brane.core.model.AccessListEntry;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;

class TxBuilderTest {

    @Test
    void eip1559BuilderAccessList() {
        final AccessListEntry entry =
                new AccessListEntry(new Address("0x" + "a".repeat(40)), List.of(new Hash("0x" + "1".repeat(64))));

        final TransactionRequest request = TxBuilder.eip1559()
                .to(new Address("0x" + "b".repeat(40)))
                .accessList(List.of(entry))
                .build();

        assertNotNull(request.accessList());
        assertEquals(List.of(entry), request.accessList());
    }

    @Test
    void accessListFluentChaining() {
        final TransactionRequest request = TxBuilder.eip1559()
                .to(new Address("0x" + "b".repeat(40)))
                .accessList(List.of())
                .gasLimit(1L)
                .build();

        assertEquals(List.of(), request.accessList());
    }
}
