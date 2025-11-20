package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.brane.core.error.RpcException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class HttpBraneProviderTest {

    private HttpServer server;
    private URI baseUri;

    @BeforeEach
    void setUp() throws IOException {
        server = HttpServer.create(new InetSocketAddress(0), 0);
        server.start();
        baseUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort());
    }

    @AfterEach
    void tearDown() {
        server.stop(0);
    }

    @Test
    void sendSuccessResponse() {
        server.createContext(
                "/",
                exchange ->
                        respond(
                                exchange,
                                200,
                                """
                                {"jsonrpc":"2.0","result":"0x1","id":"1"}
                                """));

        BraneProvider provider = HttpBraneProvider.builder(baseUri.toString()).build();
        JsonRpcResponse response = provider.send("eth_blockNumber", List.of());
        assertEquals("0x1", response.result());
        assertNull(response.error());
    }

    @Test
    void realisticSuccessResponse() {
        server.createContext(
                "/",
                exchange ->
                        respond(
                                exchange,
                                200,
                                """
                                {"jsonrpc":"2.0","id":"123","result":"0x1","extra":"ignored"}
                                """));

        BraneProvider provider = HttpBraneProvider.builder(baseUri.toString()).build();
        JsonRpcResponse response = provider.send("eth_blockNumber", List.of());
        assertEquals("0x1", response.result());
        assertNull(response.error());
    }

    @Test
    void jsonRpcErrorThrows() {
        server.createContext(
                "/",
                exchange -> {
                    respond(
                            exchange,
                            200,
                            """
                            {"jsonrpc":"2.0","error":{"code":-32000,"message":"boom","data":"0xdead"},"id":"1"}
                            """);
                });

        BraneProvider provider = HttpBraneProvider.builder(baseUri.toString()).build();
        RpcException ex =
                assertThrows(
                        RpcException.class,
                        () -> provider.send("eth_blockNumber", List.of()));
        assertEquals(-32000, ex.code());
        assertEquals("0xdead", ex.data());
    }

    @Test
    void nullResultWithErrorThrows() {
        server.createContext(
                "/",
                exchange ->
                        respond(
                                exchange,
                                200,
                                """
                                {
                                  "jsonrpc":"2.0",
                                  "id":"1",
                                  "result":null,
                                  "error":{"code":-32000,"message":"oops","data":null}
                                }
                                """));

        BraneProvider provider = HttpBraneProvider.builder(baseUri.toString()).build();
        RpcException ex =
                assertThrows(
                        RpcException.class,
                        () -> provider.send("eth_blockNumber", List.of()));
        assertEquals(-32000, ex.code());
        assertTrue(ex.getMessage().contains("oops"));
    }

    @Test
    void nullResultWithoutError() {
        server.createContext(
                "/",
                exchange ->
                        respond(
                                exchange,
                                200,
                                """
                                {"jsonrpc":"2.0","id":"1","result":null}
                                """));

        BraneProvider provider = HttpBraneProvider.builder(baseUri.toString()).build();
        JsonRpcResponse response = provider.send("eth_call", List.of());
        assertNull(response.result());
        assertNull(response.error());
    }

    @Test
    void httpErrorThrows() {
        server.createContext(
                "/",
                exchange -> respond(exchange, 500, "oops"));

        BraneProvider provider = HttpBraneProvider.builder(baseUri.toString()).build();
        RpcException ex =
                assertThrows(
                        RpcException.class,
                        () -> provider.send("eth_blockNumber", List.of()));
        assertEquals(-32001, ex.code());
    }

    private void respond(final HttpExchange exchange, final int statusCode, final String body)
            throws IOException {
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, body.getBytes().length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(body.getBytes());
        }
    }
}
