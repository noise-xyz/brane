package io.brane.rpc;

import io.brane.core.model.BlockHeader;
import io.brane.core.model.LogEntry;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.WebSocketProvider;
import io.brane.rpc.JsonRpcResponse;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

@org.junit.jupiter.api.Tag("integration")
public class WebSocketIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(WebSocketIntegrationTest.class);

    // Use Anvil for a lightweight local node
    static GenericContainer<?> anvil = new GenericContainer<>(
            DockerImageName.parse("ghcr.io/foundry-rs/foundry:latest"))
            .withCommand("anvil", "--host", "0.0.0.0", "--port", "8545", "--block-time", "1")
            .withExposedPorts(8545)
            .waitingFor(Wait.forLogMessage(".*Listening on 0.0.0.0:8545.*", 1));

    private static String wsUrl;
    private static String ANVIL_HTTP_URL;
    private static boolean useLocalNode;

    private WebSocketProvider wsProvider;
    private PublicClient client;
    private HttpClient httpClient;

    @BeforeAll
    static void setUp() {
        // Check if we should use a local node (e.g. running 'anvil' in terminal)
        // or if Docker is not available.
        String useLocal = System.getProperty("brane.test.useLocalNode");
        if (Boolean.parseBoolean(useLocal)) {
            useLocalNode = true;
            log.info("Using local node at 127.0.0.1:8545");
            wsUrl = "ws://127.0.0.1:8545";
            ANVIL_HTTP_URL = "http://127.0.0.1:8545";
        } else {
            try {
                anvil.start();
                wsUrl = "ws://" + anvil.getHost() + ":" + anvil.getMappedPort(8545);
                ANVIL_HTTP_URL = "http://" + anvil.getHost() + ":" + anvil.getMappedPort(8545);
            } catch (Exception e) {
                log.warn(
                        "Failed to start Docker container. Falling back to local node (ensure 'anvil' is running!). Error: {}",
                        e.getMessage());
                useLocalNode = true;
                wsUrl = "ws://127.0.0.1:8545";
                ANVIL_HTTP_URL = "http://127.0.0.1:8545";
            }
        }
    }

    @AfterAll
    static void tearDownAll() {
        if (anvil.isRunning()) {
            anvil.stop();
        }
    }

    @AfterEach
    void tearDown() {
        if (wsProvider != null) {
            wsProvider.close();
        }
    }

    @org.junit.jupiter.api.BeforeEach
    void init() {
        wsProvider = WebSocketProvider.create(wsUrl);
        client = PublicClient.from(wsProvider);
        httpClient = HttpClient.newHttpClient();
    }

    @Test
    void testSubscribeToNewHeads() throws Exception {
        CompletableFuture<BlockHeader> received = new CompletableFuture<>();
        Subscription sub = client.subscribeToNewHeads(header -> {
            received.complete(header);
        });

        assertNotNull(sub.id());

        // Trigger a new block
        triggerMine();

        BlockHeader header = received.get(10, TimeUnit.SECONDS);
        assertNotNull(header);
        assertNotNull(header.hash());
        assertNotNull(header.number());

        sub.unsubscribe();
    }

    @Test
    void testSubscribeToLogs() throws Exception {
        // Use pre-deployed Token contract from integration script, or skip if not set
        String contractAddrStr = System.getProperty("brane.examples.erc20.contract");
        if (contractAddrStr == null || contractAddrStr.isEmpty()
                || "0x0000000000000000000000000000000000000000".equalsIgnoreCase(contractAddrStr)) {
            System.out.println("Skipping testSubscribeToLogs: No pre-deployed token contract configured");
            System.out.println("Set -Dbrane.examples.erc20.contract=<address> to run this test");
            return;
        }
        Address contractAddress = new Address(contractAddrStr);

        // Setup WalletClient for transfers
        io.brane.core.crypto.Signer signer = new io.brane.core.crypto.PrivateKeySigner(
                "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
        Address owner = new Address("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266");
        io.brane.rpc.WalletClient wallet = io.brane.rpc.DefaultWalletClient.create(wsProvider, client, signer, owner);

        // Subscribe to logs from the token contract
        CompletableFuture<LogEntry> received = new CompletableFuture<>();
        Subscription sub = client.subscribeToLogs(
                new io.brane.rpc.LogFilter(java.util.Optional.empty(), java.util.Optional.empty(),
                        java.util.Optional.of(contractAddress), java.util.Optional.empty()),
                log -> received.complete(log));

        // Wait for subscription to be active
        Thread.sleep(200);

        // Trigger Transfer event (transfer 100 tokens to random address)
        Address recipient = new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");
        // transfer(address,uint256) selector: a9059cbb
        // args: recipient, 100
        String transferData = "a9059cbb" +
                "000000000000000000000000" + recipient.value().substring(2) +
                "0000000000000000000000000000000000000000000000000000000000000064";

        io.brane.core.model.TransactionRequest transferReq = io.brane.core.builder.TxBuilder.legacy()
                .to(contractAddress)
                .data(new HexData("0x" + transferData))
                .build();

        wallet.sendTransactionAndWait(transferReq, 30_000, 1_000);

        // Verify log received
        LogEntry log = received.get(10, TimeUnit.SECONDS);
        assertNotNull(log);
        assertEquals(contractAddress, log.address());
        // Transfer event signature:
        // ddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef
        assertEquals("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef", log.topics().get(0).value());

        sub.unsubscribe();
    }

    @Test
    void testAsyncBatch() throws Exception {
        int batchSize = 100;
        List<CompletableFuture<JsonRpcResponse>> futures = new java.util.ArrayList<>();

        for (int i = 0; i < batchSize; i++) {
            futures.add(wsProvider.sendAsyncBatch("eth_blockNumber", List.of()));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(10, TimeUnit.SECONDS);

        for (CompletableFuture<JsonRpcResponse> f : futures) {
            JsonRpcResponse response = f.get();
            assertNull(response.error());
            assertNotNull(response.result());
        }
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
