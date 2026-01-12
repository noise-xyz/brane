// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;

/**
 * Tests for LogEntry validation.
 */
class LogEntryTest {

    private static final Address VALID_ADDRESS = new Address("0x" + "a".repeat(40));
    private static final Hash VALID_TX_HASH = new Hash("0x" + "b".repeat(64));
    private static final Hash VALID_BLOCK_HASH = new Hash("0x" + "c".repeat(64));
    private static final Hash VALID_TOPIC = new Hash("0x" + "d".repeat(64));

    @Test
    void validLogEntryCreation() {
        LogEntry log = new LogEntry(
                VALID_ADDRESS,
                HexData.EMPTY,
                List.of(VALID_TOPIC),
                VALID_BLOCK_HASH,
                VALID_TX_HASH,
                0L,
                false
        );

        assertNotNull(log);
        assertEquals(VALID_ADDRESS, log.address());
        assertEquals(VALID_TX_HASH, log.transactionHash());
    }

    @Test
    void allowsNullBlockHashForPendingLogs() {
        LogEntry log = new LogEntry(
                VALID_ADDRESS,
                HexData.EMPTY,
                Collections.emptyList(),
                null,  // null blockHash for pending logs
                VALID_TX_HASH,
                0L,
                false
        );

        assertNull(log.blockHash());
    }

    @Test
    void rejectsNullAddress() {
        assertThrows(NullPointerException.class, () -> new LogEntry(
                null,  // null address
                HexData.EMPTY,
                Collections.emptyList(),
                VALID_BLOCK_HASH,
                VALID_TX_HASH,
                0L,
                false
        ));
    }

    @Test
    void rejectsNullData() {
        assertThrows(NullPointerException.class, () -> new LogEntry(
                VALID_ADDRESS,
                null,  // null data
                Collections.emptyList(),
                VALID_BLOCK_HASH,
                VALID_TX_HASH,
                0L,
                false
        ));
    }

    @Test
    void rejectsNullTopics() {
        assertThrows(NullPointerException.class, () -> new LogEntry(
                VALID_ADDRESS,
                HexData.EMPTY,
                null,  // null topics
                VALID_BLOCK_HASH,
                VALID_TX_HASH,
                0L,
                false
        ));
    }

    @Test
    void rejectsNullTransactionHash() {
        assertThrows(NullPointerException.class, () -> new LogEntry(
                VALID_ADDRESS,
                HexData.EMPTY,
                Collections.emptyList(),
                VALID_BLOCK_HASH,
                null,  // null transactionHash
                0L,
                false
        ));
    }

    @Test
    void topicsDefensiveCopy() {
        List<Hash> mutableTopics = new ArrayList<>();
        mutableTopics.add(VALID_TOPIC);

        LogEntry log = new LogEntry(
                VALID_ADDRESS,
                HexData.EMPTY,
                mutableTopics,
                VALID_BLOCK_HASH,
                VALID_TX_HASH,
                0L,
                false
        );

        // Modifying original list should not affect log
        mutableTopics.clear();

        assertEquals(1, log.topics().size());
        assertEquals(VALID_TOPIC, log.topics().get(0));
    }

    @Test
    void topicsAreImmutable() {
        LogEntry log = new LogEntry(
                VALID_ADDRESS,
                HexData.EMPTY,
                List.of(VALID_TOPIC),
                VALID_BLOCK_HASH,
                VALID_TX_HASH,
                0L,
                false
        );

        assertThrows(UnsupportedOperationException.class, () -> log.topics().add(VALID_TOPIC));
    }
}
