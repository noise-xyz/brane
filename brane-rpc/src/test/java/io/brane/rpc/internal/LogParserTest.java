// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc.internal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.brane.core.error.AbiDecodingException;
import io.brane.core.model.LogEntry;

class LogParserTest {

    private static final String SAMPLE_ADDRESS = "0x" + "a".repeat(40);
    private static final String SAMPLE_TX_HASH = "0x" + "c".repeat(64);
    private static final String SAMPLE_BLOCK_HASH = "0x" + "d".repeat(64);

    @Test
    void parseLogsReturnsEmptyListForNull() {
        List<LogEntry> logs = LogParser.parseLogs(null);
        assertTrue(logs.isEmpty());
    }

    @Test
    void parseLogsReturnsEmptyListForEmptyList() {
        List<LogEntry> logs = LogParser.parseLogs(List.of());
        assertTrue(logs.isEmpty());
    }

    @Test
    void parseLogsParsesValidLog() {
        String topic = "0x" + "b".repeat(64);

        Map<String, Object> logMap = new HashMap<>();
        logMap.put("address", SAMPLE_ADDRESS);
        logMap.put("data", "0x1234");
        logMap.put("topics", List.of(topic));
        logMap.put("transactionHash", SAMPLE_TX_HASH);
        logMap.put("blockHash", SAMPLE_BLOCK_HASH);
        logMap.put("logIndex", "0x5");
        logMap.put("removed", false);

        List<LogEntry> logs = LogParser.parseLogs(List.of(logMap));

        assertEquals(1, logs.size());
        LogEntry log = logs.get(0);
        assertEquals(SAMPLE_ADDRESS, log.address().value());
        assertEquals("0x1234", log.data().value());
        assertEquals(1, log.topics().size());
        assertEquals(topic, log.topics().get(0).value());
        assertEquals(SAMPLE_TX_HASH, log.transactionHash().value());
        assertEquals(SAMPLE_BLOCK_HASH, log.blockHash().value());
        assertEquals(5L, log.logIndex());
        assertFalse(log.removed());
    }

    @Test
    void parseLogsParsesMultipleLogs() {
        Map<String, Object> log1 = new HashMap<>();
        log1.put("address", "0x" + "1".repeat(40));
        log1.put("data", "0x1111");
        log1.put("topics", List.of());
        log1.put("logIndex", "0x0");
        log1.put("transactionHash", "0x" + "a".repeat(64));
        log1.put("blockHash", SAMPLE_BLOCK_HASH);

        Map<String, Object> log2 = new HashMap<>();
        log2.put("address", "0x" + "2".repeat(40));
        log2.put("data", "0x2222");
        log2.put("topics", List.of());
        log2.put("logIndex", "0x1");
        log2.put("transactionHash", "0x" + "b".repeat(64));
        log2.put("blockHash", SAMPLE_BLOCK_HASH);

        List<LogEntry> logs = LogParser.parseLogs(List.of(log1, log2));

        assertEquals(2, logs.size());
        assertEquals("0x" + "1".repeat(40), logs.get(0).address().value());
        assertEquals("0x" + "2".repeat(40), logs.get(1).address().value());
    }

    @Test
    void parseLogsHandlesMissingLogIndexWithDefault() {
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("address", SAMPLE_ADDRESS);
        logMap.put("data", "0x1234");
        logMap.put("topics", List.of());
        logMap.put("logIndex", null);
        logMap.put("transactionHash", SAMPLE_TX_HASH);
        logMap.put("blockHash", null);

        List<LogEntry> logs = LogParser.parseLogs(List.of(logMap));

        assertEquals(1, logs.size());
        assertEquals(0L, logs.get(0).logIndex());
    }

    @Test
    void parseLogsStrictThrowsForMissingLogIndex() {
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("address", SAMPLE_ADDRESS);
        logMap.put("data", "0x1234");
        logMap.put("topics", List.of());
        logMap.put("logIndex", null);
        logMap.put("transactionHash", SAMPLE_TX_HASH);

        AbiDecodingException ex = assertThrows(AbiDecodingException.class,
                () -> LogParser.parseLogs(List.of(logMap), true));

        assertTrue(ex.getMessage().contains("Missing logIndex"));
    }

    @Test
    void parseLogStrictReturnsLogWithValidIndex() {
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("address", SAMPLE_ADDRESS);
        logMap.put("data", "0x1234");
        logMap.put("topics", List.of());
        logMap.put("logIndex", "0xa");
        logMap.put("transactionHash", SAMPLE_TX_HASH);
        logMap.put("blockHash", null);

        LogEntry log = LogParser.parseLogStrict(logMap);

        assertEquals(10L, log.logIndex());
    }

    @Test
    void parseLogHandlesPendingLog() {
        // Pending logs have null blockHash
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("address", SAMPLE_ADDRESS);
        logMap.put("data", "0x1234");
        logMap.put("topics", List.of());
        logMap.put("logIndex", "0x0");
        logMap.put("transactionHash", SAMPLE_TX_HASH);
        logMap.put("blockHash", null);

        LogEntry log = LogParser.parseLog(logMap);

        assertEquals(SAMPLE_ADDRESS, log.address().value());
        assertNull(log.blockHash());
    }

    @Test
    void parseLogHandlesRemovedFlag() {
        Map<String, Object> removedLog = new HashMap<>();
        removedLog.put("address", SAMPLE_ADDRESS);
        removedLog.put("data", "0x1234");
        removedLog.put("topics", List.of());
        removedLog.put("logIndex", "0x0");
        removedLog.put("removed", true);
        removedLog.put("transactionHash", SAMPLE_TX_HASH);
        removedLog.put("blockHash", SAMPLE_BLOCK_HASH);

        LogEntry log = LogParser.parseLog(removedLog);

        assertTrue(log.removed());
    }

    @Test
    void parseLogHandlesEmptyData() {
        Map<String, Object> logMap = new HashMap<>();
        logMap.put("address", SAMPLE_ADDRESS);
        logMap.put("data", null);
        logMap.put("topics", List.of());
        logMap.put("logIndex", "0x0");
        logMap.put("transactionHash", SAMPLE_TX_HASH);
        logMap.put("blockHash", SAMPLE_BLOCK_HASH);

        LogEntry log = LogParser.parseLog(logMap);

        // Empty data defaults to HexData.EMPTY
        assertEquals("0x", log.data().value());
    }

    @Test
    void parseLogHandlesMultipleTopics() {
        String topic0 = "0x" + "1".repeat(64);
        String topic1 = "0x" + "2".repeat(64);
        String topic2 = "0x" + "3".repeat(64);

        Map<String, Object> logMap = new HashMap<>();
        logMap.put("address", SAMPLE_ADDRESS);
        logMap.put("data", "0x");
        logMap.put("topics", List.of(topic0, topic1, topic2));
        logMap.put("logIndex", "0x0");
        logMap.put("transactionHash", SAMPLE_TX_HASH);
        logMap.put("blockHash", SAMPLE_BLOCK_HASH);

        LogEntry log = LogParser.parseLog(logMap);

        assertEquals(3, log.topics().size());
        assertEquals(topic0, log.topics().get(0).value());
        assertEquals(topic1, log.topics().get(1).value());
        assertEquals(topic2, log.topics().get(2).value());
    }
}
