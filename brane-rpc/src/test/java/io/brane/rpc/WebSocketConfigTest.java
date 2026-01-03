package io.brane.rpc;

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
}
