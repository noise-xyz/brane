// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import sh.brane.core.BraneDebug;
import sh.brane.core.error.RpcException;

class HttpBraneProviderDebugTest {

    private HttpServer server;
    private URI baseUri;
    private final Logger debugLogger = (Logger) LoggerFactory.getLogger("sh.brane.debug");

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        BraneDebug.setEnabled(false);
        debugLogger.detachAndStopAllAppenders();
        server.stop(0);
    }

    @Test
    void logsRpcRequestAndResponseWhenEnabled() {
        server.createContext(
                "/",
                exchange -> respond(
                        exchange,
                        200,
                        """
                                {"jsonrpc":"2.0","result":"0x1","id":"1"}
                                """));

        BraneDebug.setEnabled(true);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        debugLogger.addAppender(appender);

        BraneProvider provider = HttpBraneProvider.builder(baseUri.toString()).build();
        provider.send("eth_blockNumber", List.of(java.util.Map.of("privateKey", "0x1234")));

        assertFalse(appender.list.isEmpty());
        final String message = appender.list.getFirst().getFormattedMessage();
        assertTrue(message.contains("[RPC]"));
        assertTrue(message.contains("method=eth_blockNumber"));
        // New format doesn't include request/response data for cleaner output
    }

    @Test
    void logsRpcErrorsWithRequestId() {
        server.createContext("/", exchange -> respond(exchange, 500, "oops"));

        BraneDebug.setEnabled(true);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        debugLogger.addAppender(appender);

        BraneProvider provider = HttpBraneProvider.builder(baseUri.toString()).build();
        assertThrows(RpcException.class, () -> provider.send("eth_blockNumber", List.of()));

        assertFalse(appender.list.isEmpty());
        final String message = appender.list.getFirst().getFormattedMessage();
        assertTrue(message.contains("[RPC-ERROR]"));
        assertTrue(message.contains("method=eth_blockNumber"));
    }

    private void respond(final com.sun.net.httpserver.HttpExchange exchange, final int statusCode, final String body)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, body.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body.getBytes());
        }
    }
}
