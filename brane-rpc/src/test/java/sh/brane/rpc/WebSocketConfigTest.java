// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class WebSocketConfigTest {

    @Test
    void testValidWsUrl() {
        WebSocketConfig config = WebSocketConfig.withDefaults("ws://localhost:8545");
        assertEquals("ws://localhost:8545", config.url());
    }

    @Test
    void testValidWssUrl() {
        WebSocketConfig config = WebSocketConfig.withDefaults("wss://eth-mainnet.example.com");
        assertEquals("wss://eth-mainnet.example.com", config.url());
    }

    @Test
    void testRejectsHttpUrl() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                WebSocketConfig.withDefaults("http://localhost:8545")
        );
        assertTrue(ex.getMessage().contains("url must use ws or wss scheme"));
        assertTrue(ex.getMessage().contains("got: http"));
    }

    @Test
    void testRejectsHttpsUrl() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                WebSocketConfig.withDefaults("https://eth-mainnet.example.com")
        );
        assertTrue(ex.getMessage().contains("url must use ws or wss scheme"));
        assertTrue(ex.getMessage().contains("got: https"));
    }

    @Test
    void testRejectsNoScheme() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                WebSocketConfig.withDefaults("localhost:8545")
        );
        assertTrue(ex.getMessage().contains("url must use ws or wss scheme"));
    }

    @Test
    void testRejectsNoHost() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                WebSocketConfig.withDefaults("ws:///path")
        );
        assertTrue(ex.getMessage().contains("url must have a valid host"));
    }

    @Test
    void testRejectsInvalidUri() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                WebSocketConfig.withDefaults("not a valid uri")
        );
        assertTrue(ex.getMessage().contains("url is not a valid URI")
                || ex.getMessage().contains("url must use ws or wss scheme"));
    }

    @Test
    void testRejectsNullUrl() {
        assertThrows(NullPointerException.class, () ->
                WebSocketConfig.withDefaults(null)
        );
    }

    @ParameterizedTest
    @ValueSource(strings = {"WS://localhost:8545", "WSS://example.com", "Ws://localhost", "WsS://example.com"})
    void testAcceptsCaseInsensitiveScheme(String url) {
        WebSocketConfig config = WebSocketConfig.withDefaults(url);
        assertNotNull(config);
    }

    @Test
    void testBuilderValidatesUrl() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                WebSocketConfig.builder("http://localhost:8545").build()
        );
        assertTrue(ex.getMessage().contains("url must use ws or wss scheme"));
    }

    // ==================== TransportType tests ====================

    @Test
    void testDefaultTransportTypeIsAuto() {
        WebSocketConfig config = WebSocketConfig.withDefaults("ws://localhost:8545");
        assertEquals(WebSocketConfig.TransportType.AUTO, config.transportType());
    }

    @Test
    void testBuilderSetsTransportTypeNio() {
        WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                .transportType(WebSocketConfig.TransportType.NIO)
                .build();
        assertEquals(WebSocketConfig.TransportType.NIO, config.transportType());
    }

    @Test
    void testBuilderSetsTransportTypeEpoll() {
        WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                .transportType(WebSocketConfig.TransportType.EPOLL)
                .build();
        assertEquals(WebSocketConfig.TransportType.EPOLL, config.transportType());
    }

    @Test
    void testBuilderSetsTransportTypeKqueue() {
        WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                .transportType(WebSocketConfig.TransportType.KQUEUE)
                .build();
        assertEquals(WebSocketConfig.TransportType.KQUEUE, config.transportType());
    }

    @Test
    void testBuilderSetsTransportTypeAuto() {
        WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                .transportType(WebSocketConfig.TransportType.AUTO)
                .build();
        assertEquals(WebSocketConfig.TransportType.AUTO, config.transportType());
    }

    @Test
    void testTransportTypeEnumValues() {
        WebSocketConfig.TransportType[] types = WebSocketConfig.TransportType.values();
        assertEquals(4, types.length);
        assertNotNull(WebSocketConfig.TransportType.AUTO);
        assertNotNull(WebSocketConfig.TransportType.NIO);
        assertNotNull(WebSocketConfig.TransportType.EPOLL);
        assertNotNull(WebSocketConfig.TransportType.KQUEUE);
    }

    @Test
    void testTransportTypeValueOf() {
        assertEquals(WebSocketConfig.TransportType.AUTO, WebSocketConfig.TransportType.valueOf("AUTO"));
        assertEquals(WebSocketConfig.TransportType.NIO, WebSocketConfig.TransportType.valueOf("NIO"));
        assertEquals(WebSocketConfig.TransportType.EPOLL, WebSocketConfig.TransportType.valueOf("EPOLL"));
        assertEquals(WebSocketConfig.TransportType.KQUEUE, WebSocketConfig.TransportType.valueOf("KQUEUE"));
    }

    @Test
    void testNullTransportTypeDefaultsToAuto() {
        // When transportType is null, the compact constructor defaults it to AUTO
        WebSocketConfig config = new WebSocketConfig(
                "ws://localhost:8545", 0, 0, null, null, null, null, 0, null, 0, 0, 0);
        assertEquals(WebSocketConfig.TransportType.AUTO, config.transportType());
    }

    // ==================== WaitStrategyType tests ====================

    @Test
    void testDefaultWaitStrategyIsYielding() {
        WebSocketConfig config = WebSocketConfig.withDefaults("ws://localhost:8545");
        assertEquals(WebSocketConfig.WaitStrategyType.YIELDING, config.waitStrategy());
    }

    @Test
    void testBuilderSetsWaitStrategyBusySpin() {
        WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                .waitStrategy(WebSocketConfig.WaitStrategyType.BUSY_SPIN)
                .build();
        assertEquals(WebSocketConfig.WaitStrategyType.BUSY_SPIN, config.waitStrategy());
    }

    @Test
    void testBuilderSetsWaitStrategyYielding() {
        WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                .waitStrategy(WebSocketConfig.WaitStrategyType.YIELDING)
                .build();
        assertEquals(WebSocketConfig.WaitStrategyType.YIELDING, config.waitStrategy());
    }

    @Test
    void testBuilderSetsWaitStrategyLiteBlocking() {
        WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                .waitStrategy(WebSocketConfig.WaitStrategyType.LITE_BLOCKING)
                .build();
        assertEquals(WebSocketConfig.WaitStrategyType.LITE_BLOCKING, config.waitStrategy());
    }

    @Test
    void testBuilderSetsWaitStrategyBlocking() {
        WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                .waitStrategy(WebSocketConfig.WaitStrategyType.BLOCKING)
                .build();
        assertEquals(WebSocketConfig.WaitStrategyType.BLOCKING, config.waitStrategy());
    }

    @Test
    void testWaitStrategyTypeEnumValues() {
        WebSocketConfig.WaitStrategyType[] types = WebSocketConfig.WaitStrategyType.values();
        assertEquals(4, types.length);
        assertNotNull(WebSocketConfig.WaitStrategyType.BUSY_SPIN);
        assertNotNull(WebSocketConfig.WaitStrategyType.YIELDING);
        assertNotNull(WebSocketConfig.WaitStrategyType.LITE_BLOCKING);
        assertNotNull(WebSocketConfig.WaitStrategyType.BLOCKING);
    }

    @Test
    void testWaitStrategyTypeValueOf() {
        assertEquals(WebSocketConfig.WaitStrategyType.BUSY_SPIN, WebSocketConfig.WaitStrategyType.valueOf("BUSY_SPIN"));
        assertEquals(WebSocketConfig.WaitStrategyType.YIELDING, WebSocketConfig.WaitStrategyType.valueOf("YIELDING"));
        assertEquals(WebSocketConfig.WaitStrategyType.LITE_BLOCKING, WebSocketConfig.WaitStrategyType.valueOf("LITE_BLOCKING"));
        assertEquals(WebSocketConfig.WaitStrategyType.BLOCKING, WebSocketConfig.WaitStrategyType.valueOf("BLOCKING"));
    }

    @Test
    void testNullWaitStrategyDefaultsToYielding() {
        // When waitStrategy is null, the compact constructor defaults it to YIELDING
        WebSocketConfig config = new WebSocketConfig(
                "ws://localhost:8545", 0, 0, null, null, null, null, 0, null, 0, 0, 0);
        assertEquals(WebSocketConfig.WaitStrategyType.YIELDING, config.waitStrategy());
    }

    // ==================== WriteBufferWaterMark tests ====================

    @Test
    void testDefaultWriteBufferWaterMarks() {
        WebSocketConfig config = WebSocketConfig.withDefaults("ws://localhost:8545");
        assertEquals(8 * 1024, config.writeBufferLowWaterMark());
        assertEquals(32 * 1024, config.writeBufferHighWaterMark());
    }

    @Test
    void testBuilderSetsWriteBufferWaterMarks() {
        WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                .writeBufferWaterMark(16 * 1024, 64 * 1024)
                .build();
        assertEquals(16 * 1024, config.writeBufferLowWaterMark());
        assertEquals(64 * 1024, config.writeBufferHighWaterMark());
    }

    @Test
    void testBuilderSetsWriteBufferLowWaterMark() {
        WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                .writeBufferLowWaterMark(4 * 1024)
                .build();
        assertEquals(4 * 1024, config.writeBufferLowWaterMark());
        assertEquals(32 * 1024, config.writeBufferHighWaterMark()); // default high
    }

    @Test
    void testBuilderSetsWriteBufferHighWaterMark() {
        WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                .writeBufferHighWaterMark(128 * 1024)
                .build();
        assertEquals(8 * 1024, config.writeBufferLowWaterMark()); // default low
        assertEquals(128 * 1024, config.writeBufferHighWaterMark());
    }

    @Test
    void testWriteBufferWaterMarkValidation() {
        // Low water mark cannot exceed high water mark
        assertThrows(IllegalArgumentException.class, () ->
                WebSocketConfig.builder("ws://localhost:8545")
                        .writeBufferWaterMark(64 * 1024, 16 * 1024)
                        .build());
    }

    @Test
    void testZeroWriteBufferWaterMarksApplyDefaults() {
        WebSocketConfig config = new WebSocketConfig(
                "ws://localhost:8545", 0, 0, null, null, null, null, 0, null, 0, 0, 0);
        assertEquals(8 * 1024, config.writeBufferLowWaterMark());
        assertEquals(32 * 1024, config.writeBufferHighWaterMark());
    }

    // ==================== maxFrameSize tests ====================

    @Test
    void testDefaultMaxFrameSize() {
        WebSocketConfig config = WebSocketConfig.withDefaults("ws://localhost:8545");
        assertEquals(64 * 1024, config.maxFrameSize());
    }

    @Test
    void testBuilderSetsMaxFrameSize() {
        WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                .maxFrameSize(128 * 1024)
                .build();
        assertEquals(128 * 1024, config.maxFrameSize());
    }

    @Test
    void testMaxFrameSizeValidation() {
        // 16MB limit
        int limit = 16 * 1024 * 1024;

        // Valid at exactly limit
        WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                .maxFrameSize(limit)
                .build();
        assertEquals(limit, config.maxFrameSize());

        // Invalid exceeding limit
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                WebSocketConfig.builder("ws://localhost:8545")
                        .maxFrameSize(limit + 1)
                        .build());
        assertTrue(ex.getMessage().contains("maxFrameSize"));
        assertTrue(ex.getMessage().contains("exceeds maximum"));
    }

    @Test
    void testZeroMaxFrameSizeAppliesDefault() {
        WebSocketConfig config = new WebSocketConfig(
                "ws://localhost:8545", 0, 0, null, null, null, null, 0, null, 0, 0, 0);
        assertEquals(64 * 1024, config.maxFrameSize());
    }

    @Test
    void testNegativeMaxFrameSizeAppliesDefault() {
        WebSocketConfig config = new WebSocketConfig(
                "ws://localhost:8545", 0, 0, null, null, null, null, 0, null, 0, 0, -1);
        assertEquals(64 * 1024, config.maxFrameSize());

        WebSocketConfig config2 = new WebSocketConfig(
                "ws://localhost:8545", 0, 0, null, null, null, null, 0, null, 0, 0, -100);
        assertEquals(64 * 1024, config2.maxFrameSize());
    }

    @Test
    void testMinimumValidMaxFrameSize() {
        // 1 byte should be valid (though impractical)
        WebSocketConfig config = WebSocketConfig.builder("ws://localhost:8545")
                .maxFrameSize(1)
                .build();
        assertEquals(1, config.maxFrameSize());
    }

    @Test
    void testMaxFrameSizeAtVariousValidValues() {
        // Small value (1KB)
        WebSocketConfig config1 = WebSocketConfig.builder("ws://localhost:8545")
                .maxFrameSize(1024)
                .build();
        assertEquals(1024, config1.maxFrameSize());

        // Medium value (1MB)
        WebSocketConfig config2 = WebSocketConfig.builder("ws://localhost:8545")
                .maxFrameSize(1024 * 1024)
                .build();
        assertEquals(1024 * 1024, config2.maxFrameSize());

        // Large value just under limit (15MB)
        WebSocketConfig config3 = WebSocketConfig.builder("ws://localhost:8545")
                .maxFrameSize(15 * 1024 * 1024)
                .build();
        assertEquals(15 * 1024 * 1024, config3.maxFrameSize());
    }

    @Test
    void testMaxFrameSizeErrorMessageContent() {
        int limit = 16 * 1024 * 1024;
        int overLimit = limit + 1000;

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                WebSocketConfig.builder("ws://localhost:8545")
                        .maxFrameSize(overLimit)
                        .build());

        // Verify error message contains the actual value and limit
        assertTrue(ex.getMessage().contains(String.valueOf(overLimit)),
                "Error message should contain the invalid value");
        assertTrue(ex.getMessage().contains("16MB"),
                "Error message should mention the 16MB limit");
    }

    @Test
    void testMaxFrameSizeDirectConstructorValidation() {
        // Direct constructor should also validate
        int limit = 16 * 1024 * 1024;
        assertThrows(IllegalArgumentException.class, () ->
                new WebSocketConfig(
                        "ws://localhost:8545", 0, 0, null, null, null, null, 0, null, 0, 0, limit + 1));
    }
}
