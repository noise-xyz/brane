// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.brane.core.error.RpcException;

class HttpBraneProviderTest {

    private HttpServer server;
    private URI baseUri;
    private static final ObjectMapper MAPPER = new ObjectMapper();

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
    void assignsMonotonicRequestIds() {
        final List<String> capturedBodies = new ArrayList<>();
        server.createContext(
                "/",
                exchange -> {
                    final String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
                    capturedBodies.add(requestBody);
                    final JsonNode bodyJson = MAPPER.readTree(requestBody);
                    respond(
                            exchange,
                            200,
                            """
                            {"jsonrpc":"2.0","result":"0x1","id":"%s"}
                            """.formatted(bodyJson.get("id").asText()));
                });

        BraneProvider provider = HttpBraneProvider.builder(baseUri.toString()).build();
        provider.send("eth_blockNumber", List.of());
        provider.send("eth_chainId", List.of());

        assertEquals(2, capturedBodies.size());
        assertTrue(capturedBodies.get(0).contains("\"id\":\"1\""));
        assertTrue(capturedBodies.get(1).contains("\"id\":\"2\""));
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
        assertEquals(1L, ex.requestId());
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
        assertEquals(1L, ex.requestId());
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
        assertEquals(1L, ex.requestId());
    }

    @Test
    void nullMethodThrowsNPE() {
        BraneProvider provider = HttpBraneProvider.builder(baseUri.toString()).build();
        NullPointerException ex =
                assertThrows(
                        NullPointerException.class,
                        () -> provider.send(null, List.of()));
        assertEquals("method", ex.getMessage());
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
