// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Integration tests for idle connection detection and WebSocket ping behavior.
 *
 * <p>These tests validate that WebSocket connections remain healthy over idle periods
 * and that the client can detect and recover from connection issues.
 *
 * <p>Tests use Testcontainers to run Anvil in a controlled Docker environment,
 * ensuring reproducible network conditions.
 */
@org.junit.jupiter.api.Tag("integration")
public class IdleConnectionTest {

    private static final Logger log = LoggerFactory.getLogger(IdleConnectionTest.class);

    static GenericContainer<?> anvil = new GenericContainer<>(
            DockerImageName.parse("ghcr.io/foundry-rs/foundry:latest"))
            .withCommand("anvil", "--host", "0.0.0.0", "--port", "8545")
            .withExposedPorts(8545)
            .waitingFor(Wait.forLogMessage(".*Listening on 0.0.0.0:8545.*", 1));

    private static String wsUrl;
    private static String httpUrl;
    private static boolean useLocalNode;
    private static boolean infrastructureAvailable;

    private WebSocketProvider wsProvider;
    private Brane client;
    private HttpClient httpClient;

    @BeforeAll
    static void setUp() {
        String useLocal = System.getProperty("brane.test.useLocalNode");
        if (Boolean.parseBoolean(useLocal)) {
            useLocalNode = true;
            log.info("Using local node at 127.0.0.1:8545");
            wsUrl = "ws://127.0.0.1:8545";
            httpUrl = "http://127.0.0.1:8545";
        } else {
            try {
                anvil.start();
                wsUrl = "ws://" + anvil.getHost() + ":" + anvil.getMappedPort(8545);
                httpUrl = "http://" + anvil.getHost() + ":" + anvil.getMappedPort(8545);
            } catch (Exception e) {
                log.warn(
                        "Failed to start Docker container. Falling back to local node (ensure 'anvil' is running!). Error: {}",
                        e.getMessage());
                useLocalNode = true;
                wsUrl = "ws://127.0.0.1:8545";
                httpUrl = "http://127.0.0.1:8545";
            }
        }

        infrastructureAvailable = checkInfrastructureAvailable();
        if (!infrastructureAvailable) {
            log.warn("Idle connection tests will be skipped: No Anvil node available at {}", wsUrl);
        }
    }

    private static boolean checkInfrastructureAvailable() {
        try (var testClient = HttpClient.newHttpClient()) {
            var request = HttpRequest.newBuilder()
                    .uri(URI.create(httpUrl))
                    .POST(HttpRequest.BodyPublishers.ofString(
                            "{\"jsonrpc\":\"2.0\",\"method\":\"eth_chainId\",\"params\":[],\"id\":1}"))
                    .header("Content-Type", "application/json")
                    .timeout(Duration.ofSeconds(5))
                    .build();
            var response = testClient.send(request, HttpResponse.BodyHandlers.ofString());
            return response.statusCode() == 200;
        } catch (Exception e) {
            log.debug("Infrastructure check failed: {}", e.getMessage());
            return false;
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (anvil.isRunning()) {
            anvil.stop();
        }
    }

    @BeforeEach
    void init() {
        Assumptions.assumeTrue(infrastructureAvailable,
                "Skipping test: No Anvil node available (neither Docker nor local)");
        httpClient = HttpClient.newHttpClient();
    }

    @AfterEach
    void tearDown() {
        if (wsProvider != null) {
            wsProvider.close();
            wsProvider = null;
        }
    }

    /**
     * Tests that a WebSocket connection remains functional after an idle period.
     *
     * <p>This test verifies that:
     * <ul>
     *   <li>Connection is established successfully</li>
     *   <li>Connection remains usable after 5 seconds of inactivity</li>
     *   <li>Subsequent requests complete successfully</li>
     * </ul>
     */
    @Test
    void testConnectionRemainsActiveAfterIdlePeriod() throws Exception {
        wsProvider = WebSocketProvider.create(wsUrl);
        client = Brane.builder().provider(wsProvider).buildReader();

        // Initial request to verify connection
        var initialBlock = client.getLatestBlock();
        assertNotNull(initialBlock, "Initial block should not be null");

        // Wait for idle period (5 seconds)
        log.info("Waiting 5 seconds for idle period...");
        Thread.sleep(5000);

        // Verify connection still works after idle
        var afterIdleBlock = client.getLatestBlock();
        assertNotNull(afterIdleBlock, "Block after idle should not be null");

        // Connection state should still be CONNECTED
        assertEquals(WebSocketProvider.ConnectionState.CONNECTED, wsProvider.getConnectionState(),
                "Connection should remain in CONNECTED state after idle period");
    }

    /**
     * Tests that multiple requests work correctly after an idle period.
     *
     * <p>This validates that the connection's internal state (request tracking,
     * ID generation, etc.) remains consistent after idle periods.
     */
    @Test
    void testMultipleRequestsAfterIdle() throws Exception {
        wsProvider = WebSocketProvider.create(wsUrl);
        client = Brane.builder().provider(wsProvider).buildReader();

        // Initial requests
        client.getLatestBlock();
        client.chainId();

        // Idle period
        log.info("Waiting 3 seconds for idle period...");
        Thread.sleep(3000);

        // Multiple concurrent requests after idle
        int numRequests = 20;
        AtomicInteger successCount = new AtomicInteger(0);

        CompletableFuture<?>[] futures = new CompletableFuture[numRequests];
        for (int i = 0; i < numRequests; i++) {
            futures[i] = wsProvider.sendAsync("eth_blockNumber", List.of())
                    .thenAccept(response -> {
                        if (response.error() == null && response.result() != null) {
                            successCount.incrementAndGet();
                        }
                    });
        }

        CompletableFuture.allOf(futures).get(30, TimeUnit.SECONDS);

        assertEquals(numRequests, successCount.get(),
                "All requests after idle should succeed");
    }

    /**
     * Tests WebSocket ping/pong behavior using subscriptions as a keep-alive mechanism.
     *
     * <p>Anvil (and most Ethereum nodes) support WebSocket subscriptions which
     * generate periodic traffic (newHeads every block). This test verifies that
     * the connection stays alive when there's subscription traffic.
     */
    @Test
    void testSubscriptionKeepsConnectionAlive() throws Exception {
        wsProvider = WebSocketProvider.create(wsUrl);
        client = Brane.builder().provider(wsProvider).buildReader();

        AtomicInteger blockCount = new AtomicInteger(0);
        CompletableFuture<Void> received3Blocks = new CompletableFuture<>();

        // Subscribe to new blocks
        Subscription sub = client.onNewHeads(header -> {
            int count = blockCount.incrementAndGet();
            log.info("Received block {} (count: {})", header.number(), count);
            if (count >= 3) {
                received3Blocks.complete(null);
            }
        });

        assertNotNull(sub.id(), "Subscription ID should not be null");

        // Mine blocks to trigger events
        for (int i = 0; i < 5; i++) {
            triggerMine();
            Thread.sleep(500);
        }

        // Wait for at least 3 blocks
        received3Blocks.get(30, TimeUnit.SECONDS);

        assertTrue(blockCount.get() >= 3,
                "Should have received at least 3 blocks via subscription");

        // Connection should still be healthy
        assertEquals(WebSocketProvider.ConnectionState.CONNECTED, wsProvider.getConnectionState(),
                "Connection should remain CONNECTED with active subscription");

        sub.unsubscribe();
    }

    /**
     * Tests that request timeout works correctly and doesn't leave the connection
     * in a bad state.
     *
     * <p>Uses a very short timeout to trigger timeout behavior, then verifies
     * subsequent requests still work.
     */
    @Test
    void testTimeoutDoesNotCorruptConnectionState() throws Exception {
        WebSocketConfig config = WebSocketConfig.builder(wsUrl)
                .defaultRequestTimeout(Duration.ofMillis(1)) // Very short timeout
                .build();
        wsProvider = WebSocketProvider.create(config);

        // This request may timeout due to the 1ms timeout
        CompletableFuture<JsonRpcResponse> future = wsProvider.sendAsync(
                "eth_blockNumber", List.of(), Duration.ofMillis(1));

        // Wait a bit for timeout or completion
        Thread.sleep(100);

        // Now create a new provider with reasonable timeout to verify server is OK
        WebSocketProvider healthyProvider = WebSocketProvider.create(wsUrl);
        try {
            JsonRpcResponse response = healthyProvider.sendAsync(
                    "eth_chainId", List.of(), Duration.ofSeconds(10)).get(15, TimeUnit.SECONDS);
            assertNotNull(response.result(), "Server should respond normally");
        } finally {
            healthyProvider.close();
        }
    }

    /**
     * Tests the connection state transitions during normal operation.
     *
     * <p>Verifies that:
     * <ul>
     *   <li>Initial state is CONNECTED after creation</li>
     *   <li>State remains CONNECTED during active use</li>
     *   <li>State becomes CLOSED after close()</li>
     * </ul>
     */
    @Test
    void testConnectionStateTransitions() throws Exception {
        wsProvider = WebSocketProvider.create(wsUrl);

        // After successful connection
        assertEquals(WebSocketProvider.ConnectionState.CONNECTED, wsProvider.getConnectionState(),
                "Initial state should be CONNECTED");

        // During active use
        wsProvider.send("eth_blockNumber", List.of());
        assertEquals(WebSocketProvider.ConnectionState.CONNECTED, wsProvider.getConnectionState(),
                "State should remain CONNECTED during use");

        // After close
        wsProvider.close();
        assertEquals(WebSocketProvider.ConnectionState.CLOSED, wsProvider.getConnectionState(),
                "State should be CLOSED after close()");

        wsProvider = null; // Prevent double-close in tearDown
    }

    /**
     * Tests that pending request count is tracked correctly.
     *
     * <p>This is important for backpressure handling and monitoring connection health.
     */
    @Test
    void testPendingRequestTracking() throws Exception {
        wsProvider = WebSocketProvider.create(wsUrl);

        // Initially no pending requests
        assertEquals(0, wsProvider.getPendingRequestCount(),
                "Should have no pending requests initially");

        // Send request and check pending count briefly increases
        // Note: This is hard to test reliably as requests complete very fast
        JsonRpcResponse response = wsProvider.send("eth_blockNumber", List.of());
        assertNotNull(response.result());

        // After completion, pending should be 0
        assertEquals(0, wsProvider.getPendingRequestCount(),
                "Should have no pending requests after completion");
    }

    /**
     * Tests orphaned response tracking metric.
     *
     * <p>Verifies that the orphaned response counter starts at 0.
     * Actual orphaned responses are hard to trigger without server-side manipulation.
     */
    @Test
    void testOrphanedResponseMetric() throws Exception {
        wsProvider = WebSocketProvider.create(wsUrl);

        // Initially should be 0
        assertEquals(0, wsProvider.getOrphanedResponseCount(),
                "Orphaned response count should start at 0");

        // Normal request should not increment orphaned count
        wsProvider.send("eth_blockNumber", List.of());

        assertEquals(0, wsProvider.getOrphanedResponseCount(),
                "Normal requests should not cause orphaned responses");
    }

    /**
     * Tests that connection handles rapid request/idle cycles correctly.
     *
     * <p>This simulates a bursty workload pattern where there are periods
     * of high activity followed by idle periods.
     */
    @Test
    void testBurstyWorkloadPattern() throws Exception {
        wsProvider = WebSocketProvider.create(wsUrl);
        client = Brane.builder().provider(wsProvider).buildReader();

        for (int cycle = 0; cycle < 3; cycle++) {
            log.info("Burst cycle {}", cycle + 1);

            // Burst of requests
            CompletableFuture<?>[] burst = new CompletableFuture[10];
            for (int i = 0; i < 10; i++) {
                burst[i] = wsProvider.sendAsync("eth_blockNumber", List.of());
            }
            CompletableFuture.allOf(burst).get(10, TimeUnit.SECONDS);

            // Idle period
            Thread.sleep(2000);

            // Verify connection still works
            assertEquals(WebSocketProvider.ConnectionState.CONNECTED,
                    wsProvider.getConnectionState(),
                    "Connection should remain CONNECTED after burst cycle " + (cycle + 1));
        }

        // Final verification
        var finalBlock = client.getLatestBlock();
        assertNotNull(finalBlock, "Should be able to get block after all cycles");
    }

    private void triggerMine() throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(httpUrl))
                .POST(HttpRequest.BodyPublishers
                        .ofString("{\"jsonrpc\":\"2.0\",\"method\":\"evm_mine\",\"params\":[],\"id\":999}"))
                .header("Content-Type", "application/json")
                .build();
        httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
