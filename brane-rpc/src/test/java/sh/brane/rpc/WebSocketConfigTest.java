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
                "ws://localhost:8545", 0, 0, null, null, null, null, 0, null);
        assertEquals(WebSocketConfig.TransportType.AUTO, config.transportType());
    }
}
