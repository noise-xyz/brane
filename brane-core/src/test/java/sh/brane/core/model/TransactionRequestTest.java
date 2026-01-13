// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import sh.brane.core.types.Address;
import sh.brane.core.types.Hash;
import sh.brane.core.types.Wei;

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

    @Test
    void toUnsignedTransactionThrowsWhenNonceIsNull() {
        final TransactionRequest request = new TransactionRequest(
                new Address("0x" + "a".repeat(40)), // from
                new Address("0x" + "b".repeat(40)), // to
                Wei.of(1000), // value
                21000L, // gasLimit
                Wei.of(20_000_000_000L), // gasPrice
                null, // maxPriorityFeePerGas
                null, // maxFeePerGas
                null, // nonce - null (should fail)
                null, // data
                false, // isEip1559
                null // accessList
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> request.toUnsignedTransaction(1L));

        assertEquals("nonce must be set", ex.getMessage());
    }

    @Test
    void toUnsignedTransactionThrowsWhenGasLimitIsNull() {
        final TransactionRequest request = new TransactionRequest(
                new Address("0x" + "a".repeat(40)), // from
                new Address("0x" + "b".repeat(40)), // to
                Wei.of(1000), // value
                null, // gasLimit - null (should fail)
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

        assertEquals("gasLimit must be set", ex.getMessage());
    }

    @Test
    void toUnsignedTransactionThrowsWhenLegacyGasPriceIsNull() {
        final TransactionRequest request = new TransactionRequest(
                new Address("0x" + "a".repeat(40)), // from
                new Address("0x" + "b".repeat(40)), // to
                Wei.of(1000), // value
                21000L, // gasLimit
                null, // gasPrice - null (should fail for legacy)
                null, // maxPriorityFeePerGas
                null, // maxFeePerGas
                1L, // nonce
                null, // data
                false, // isEip1559 = false (legacy)
                null // accessList
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> request.toUnsignedTransaction(1L));

        assertEquals("gasPrice must be set for legacy transactions", ex.getMessage());
    }

    @Test
    void toUnsignedTransactionThrowsWhenEip1559FeesAreNull() {
        final TransactionRequest request = new TransactionRequest(
                new Address("0x" + "a".repeat(40)), // from
                new Address("0x" + "b".repeat(40)), // to
                Wei.of(1000), // value
                21000L, // gasLimit
                null, // gasPrice
                null, // maxPriorityFeePerGas - null (should fail for EIP-1559)
                null, // maxFeePerGas - null (should fail for EIP-1559)
                1L, // nonce
                null, // data
                true, // isEip1559 = true
                null // accessList
        );

        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> request.toUnsignedTransaction(1L));

        assertEquals("maxPriorityFeePerGas and maxFeePerGas must be set for EIP-1559 transactions", ex.getMessage());
    }
}
