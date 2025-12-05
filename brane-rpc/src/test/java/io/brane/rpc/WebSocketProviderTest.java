package io.brane.rpc;

import io.brane.core.model.BlockHeader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "brane.integration.tests", matches = "true")
public class WebSocketProviderTest {

    private static final String ANVIL_HTTP_URL = "http://127.0.0.1:8545";
    private static final String ANVIL_WS_URL = "ws://127.0.0.1:8545";

    private List<BraneProvider> providers = new ArrayList<>();
    private HttpClient httpClient;

    @BeforeEach
    void setUp() throws Exception {
        // Check if Anvil is reachable
        httpClient = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ANVIL_HTTP_URL))
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"jsonrpc\":\"2.0\",\"method\":\"eth_blockNumber\",\"params\":[],\"id\":1}"))
                .header("Content-Type", "application/json")
                .build();

        try {
            httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (Exception e) {
            // Skip test if Anvil is not running
            System.out.println("Anvil not running, skipping WebSocket integration test");
            org.junit.jupiter.api.Assumptions.assumeTrue(false, "Anvil not running");
        }
    }

    @AfterEach
    void tearDown() {
        for (BraneProvider p : providers) {
            try {
                if (p instanceof AutoCloseable) {
                    ((AutoCloseable) p).close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        providers.clear();
    }

    @Test
    void testWebSocketBraneProvider_NewHeads() throws Exception {
        runNewHeadsTest(WebSocketBraneProvider.create(ANVIL_WS_URL));
    }

    @Test
    void testUltraFastWebSocketProvider_NewHeads() throws Exception {
        runNewHeadsTest(UltraFastWebSocketProvider.create(ANVIL_WS_URL));
    }

    private void runNewHeadsTest(BraneProvider provider) throws Exception {
        providers.add(provider);
        PublicClient client = PublicClient.from(provider);

        CompletableFuture<BlockHeader> received = new CompletableFuture<>();
        Subscription sub = client.subscribeToNewHeads(received::complete);

        assertNotNull(sub.id());

        // Trigger a new block
        triggerMine();

        BlockHeader header = received.get(10, TimeUnit.SECONDS);
        assertNotNull(header);
        assertNotNull(header.hash());
        assertNotNull(header.number());

        sub.unsubscribe();
    }

    private void triggerMine() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(ANVIL_HTTP_URL))
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"jsonrpc\":\"2.0\",\"method\":\"evm_mine\",\"params\":[],\"id\":999}"))
                .header("Content-Type", "application/json")
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
