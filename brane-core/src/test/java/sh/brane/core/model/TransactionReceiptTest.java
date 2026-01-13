// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import sh.brane.core.types.Address;
import sh.brane.core.types.Hash;
import sh.brane.core.types.Wei;

/**
 * Tests for TransactionReceipt validation.
 */
class TransactionReceiptTest {

    private static final Hash VALID_HASH = new Hash("0x" + "a".repeat(64));
    private static final Address VALID_ADDRESS = new Address("0x" + "b".repeat(40));
    private static final Wei VALID_GAS = Wei.of(21000L);

    @Test
    void validReceiptCreation() {
        TransactionReceipt receipt = new TransactionReceipt(
                VALID_HASH,
                VALID_HASH,
                12345L,
                VALID_ADDRESS,
                VALID_ADDRESS,
                null,
                Collections.emptyList(),
                true,
                VALID_GAS
        );

        assertNotNull(receipt);
        assertEquals(VALID_HASH, receipt.transactionHash());
        assertTrue(receipt.logs().isEmpty());
    }

    @Test
    void allowsNullToForContractCreation() {
        Address deployedContract = new Address("0x" + "c".repeat(40));
        TransactionReceipt receipt = new TransactionReceipt(
                VALID_HASH,
                VALID_HASH,
                12345L,
                VALID_ADDRESS,
                null,  // null to for contract creation
                deployedContract,
                Collections.emptyList(),
                true,
                VALID_GAS
        );

        assertNull(receipt.to());
        assertEquals(deployedContract, receipt.contractAddress());
    }

    @Test
    void allowsNullContractAddressForRegularTransaction() {
        TransactionReceipt receipt = new TransactionReceipt(
                VALID_HASH,
                VALID_HASH,
                12345L,
                VALID_ADDRESS,
                VALID_ADDRESS,
                null,  // null contractAddress for non-creation
                Collections.emptyList(),
                true,
                VALID_GAS
        );

        assertNotNull(receipt.to());
        assertNull(receipt.contractAddress());
    }

    @Test
    void rejectsNullTransactionHash() {
        assertThrows(NullPointerException.class, () -> new TransactionReceipt(
                null,  // null transactionHash
                VALID_HASH,
                12345L,
                VALID_ADDRESS,
                VALID_ADDRESS,
                null,
                Collections.emptyList(),
                true,
                VALID_GAS
        ));
    }

    @Test
    void rejectsNullBlockHash() {
        assertThrows(NullPointerException.class, () -> new TransactionReceipt(
                VALID_HASH,
                null,  // null blockHash
                12345L,
                VALID_ADDRESS,
                VALID_ADDRESS,
                null,
                Collections.emptyList(),
                true,
                VALID_GAS
        ));
    }

    @Test
    void rejectsNullFrom() {
        assertThrows(NullPointerException.class, () -> new TransactionReceipt(
                VALID_HASH,
                VALID_HASH,
                12345L,
                null,  // null from
                VALID_ADDRESS,
                null,
                Collections.emptyList(),
                true,
                VALID_GAS
        ));
    }

    @Test
    void rejectsNullCumulativeGasUsed() {
        assertThrows(NullPointerException.class, () -> new TransactionReceipt(
                VALID_HASH,
                VALID_HASH,
                12345L,
                VALID_ADDRESS,
                VALID_ADDRESS,
                null,
                Collections.emptyList(),
                true,
                null  // null cumulativeGasUsed
        ));
    }

    @Test
    void rejectsNullLogs() {
        assertThrows(NullPointerException.class, () -> new TransactionReceipt(
                VALID_HASH,
                VALID_HASH,
                12345L,
                VALID_ADDRESS,
                VALID_ADDRESS,
                null,
                null,  // null logs
                true,
                VALID_GAS
        ));
    }

    @Test
    void logsDefensiveCopy() {
        List<LogEntry> mutableLogs = new ArrayList<>();
        TransactionReceipt receipt = new TransactionReceipt(
                VALID_HASH,
                VALID_HASH,
                12345L,
                VALID_ADDRESS,
                VALID_ADDRESS,
                null,
                mutableLogs,
                true,
                VALID_GAS
        );

        // Modifying original list should not affect receipt
        mutableLogs.add(null);

        assertTrue(receipt.logs().isEmpty());
    }

    @Test
    void logsAreImmutable() {
        TransactionReceipt receipt = new TransactionReceipt(
                VALID_HASH,
                VALID_HASH,
                12345L,
                VALID_ADDRESS,
                VALID_ADDRESS,
                null,
                Collections.emptyList(),
                true,
                VALID_GAS
        );

        assertThrows(UnsupportedOperationException.class, () -> receipt.logs().add(null));
    }
}
