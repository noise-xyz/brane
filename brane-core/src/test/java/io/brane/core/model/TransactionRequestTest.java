package io.brane.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.Wei;
import java.util.List;
import org.junit.jupiter.api.Test;

class TransactionRequestTest {

    @Test
    void accessListPropagates() {
        final List<AccessListEntry> entries =
                List.of(new AccessListEntry(new Address("0x" + "a".repeat(40)), List.of(new Hash("0x" + "1".repeat(64)))));

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

    @Test
    void toUnsignedTransactionThrowsWhenFromIsNull() {
        // Create a request with null 'from' but other fields populated
        final TransactionRequest request = new TransactionRequest(
                null, // from - null (should fail)
                new Address("0x" + "b".repeat(40)), // to
                Wei.of(1000), // value
                21000L, // gasLimit
                Wei.of(20_000_000_000L), // gasPrice
                null, // maxPriorityFeePerGas
                null, // maxFeePerGas
                1L, // nonce
                null, // data
                false, // isEip1559
                null // accessList
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> request.toUnsignedTransaction(1L));

        assertEquals("from address is required for unsigned transaction", ex.getMessage());
    }
}
