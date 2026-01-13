// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.crypto.Signer;

/**
 * Unit tests for {@link Brane} static factory methods.
 */
@ExtendWith(MockitoExtension.class)
class BraneTest {

    @Mock
    private BraneProvider provider;

    // Anvil default test private key
    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    @Test
    void connectWithUrlReturnsReader() {
        // When we call connect with just a URL, we get a Reader
        // We use the builder with a mock provider to avoid actual network calls
        Brane.Reader reader = Brane.builder()
                .provider(provider)
                .buildReader();

        assertNotNull(reader);
        assertInstanceOf(Brane.Reader.class, reader);
        assertFalse(reader.canSign());
    }

    @Test
    void connectWithUrlAndSignerReturnsSigner() {
        // When we call connect with URL and signer, we get a Signer
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Signer client = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildSigner();

        assertNotNull(client);
        assertInstanceOf(Brane.Signer.class, client);
        assertTrue(client.canSign());
        assertEquals(signer, client.signer());
    }

    @Test
    void connectStaticFactoryReturnsReader() throws Exception {
        // Brane.connect(url) returns a Reader
        // We can't easily test the actual URL-based factory without network,
        // but we can test that the builder path works correctly
        try (Brane.Reader reader = Brane.builder().provider(provider).buildReader()) {
            assertNotNull(reader);
            assertFalse(reader.canSign());
        }
    }

    @Test
    void connectStaticFactoryWithSignerReturnsSigner() throws Exception {
        // Brane.connect(url, signer) returns a Signer
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        try (Brane.Signer client = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildSigner()) {
            assertNotNull(client);
            assertTrue(client.canSign());
        }
    }

    @Test
    void readerCannotSign() {
        Brane.Reader reader = Brane.builder()
                .provider(provider)
                .buildReader();

        assertFalse(reader.canSign());
        assertInstanceOf(Brane.Reader.class, reader);
    }

    @Test
    void signerCanSign() {
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Signer client = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildSigner();

        assertTrue(client.canSign());
    }

    @Test
    void canSubscribeReturnsFalseForHttpProvider() {
        // HTTP provider does not support subscriptions
        Brane.Reader reader = Brane.builder()
                .provider(provider)
                .buildReader();

        assertFalse(reader.canSubscribe());
    }

    @Test
    void patternMatchingWorksWithSealed() {
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);

        // Test pattern matching with Reader
        // Note: Pattern order matters since DefaultSigner extends DefaultReader.
        // More specific types (Signer, Tester) must be checked before Reader.
        Brane reader = Brane.builder().provider(provider).buildReader();
        String readerResult = switch (reader) {
            case Brane.Tester t -> "tester";
            case Brane.Signer s -> "signer";
            case Brane.Reader r -> "reader";
        };
        assertEquals("reader", readerResult);

        // Test pattern matching with Signer
        Brane signerClient = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildSigner();
        String signerResult = switch (signerClient) {
            case Brane.Tester t -> "tester";
            case Brane.Signer s -> "signer";
            case Brane.Reader r -> "reader";
        };
        assertEquals("signer", signerResult);
    }

    @Test
    void chainIdDelegatesCorrectly() throws Exception {
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x1", null, "1");
        when(provider.send(eq("eth_chainId"), any())).thenReturn(response);

        Brane.Reader reader = Brane.builder()
                .provider(provider)
                .buildReader();

        assertEquals(java.math.BigInteger.ONE, reader.chainId());
    }

    @Test
    void chainIdDelegatesCorrectlyForSigner() throws Exception {
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x1", null, "1");
        when(provider.send(eq("eth_chainId"), any())).thenReturn(response);

        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Signer client = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildSigner();

        assertEquals(java.math.BigInteger.ONE, client.chainId());
    }

    // ==================== Builder Pattern Tests (P5-02) ====================

    @Test
    void buildReaderReturnsReaderInstance() {
        // buildReader() should always return a Reader
        Brane.Reader reader = Brane.builder()
                .provider(provider)
                .buildReader();

        assertNotNull(reader);
        assertInstanceOf(Brane.Reader.class, reader);
        assertFalse(reader.canSign());
    }

    @Test
    void buildSignerReturnsSignerInstance() {
        // buildSigner() should return a Signer when signer is provided
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Signer client = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildSigner();

        assertNotNull(client);
        assertInstanceOf(Brane.Signer.class, client);
        assertTrue(client.canSign());
        assertEquals(signer, client.signer());
    }

    @Test
    void buildWithoutSignerReturnsReader() {
        // build() without a signer should return a Reader
        Brane client = Brane.builder()
                .provider(provider)
                .build();

        assertNotNull(client);
        assertInstanceOf(Brane.Reader.class, client);
        assertFalse(client.canSign());
    }

    @Test
    void buildWithSignerReturnsSigner() {
        // build() with a signer should return a Signer
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane client = Brane.builder()
                .provider(provider)
                .signer(signer)
                .build();

        assertNotNull(client);
        assertInstanceOf(Brane.Signer.class, client);
        assertTrue(client.canSign());
    }

    @Test
    void builderWithChainProfilePropagates() {
        // Verify chain profile is set correctly
        io.brane.core.chain.ChainProfile chainProfile = io.brane.core.chain.ChainProfiles.ETH_MAINNET;
        Brane.Reader reader = Brane.builder()
                .provider(provider)
                .chain(chainProfile)
                .buildReader();

        assertTrue(reader.chain().isPresent());
        assertEquals(chainProfile, reader.chain().get());
    }

    @Test
    void builderWithRetryConfigPropagates() {
        // Verify retry config can be set (no exception thrown)
        RpcRetryConfig customConfig = RpcRetryConfig.builder()
                .backoffBaseMs(100)
                .backoffMaxMs(5000)
                .build();

        Brane.Reader reader = Brane.builder()
                .provider(provider)
                .retries(5)
                .retryConfig(customConfig)
                .buildReader();

        assertNotNull(reader);
    }

    @Test
    void buildReaderIgnoresSigner() {
        // buildReader() should return Reader even if signer is set
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Reader reader = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildReader();

        assertNotNull(reader);
        assertInstanceOf(Brane.Reader.class, reader);
        assertFalse(reader.canSign());
    }

    // ==================== Builder Validation Tests (P5-03) ====================

    @Test
    void buildReaderWithoutRpcUrlOrProviderThrows() {
        // buildReader() without rpcUrl or provider should throw IllegalStateException
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> Brane.builder().buildReader());

        assertTrue(ex.getMessage().contains("rpcUrl") || ex.getMessage().contains("provider"),
                "Exception message should mention rpcUrl or provider");
    }

    @Test
    void buildSignerWithoutRpcUrlOrProviderThrows() {
        // buildSigner() without rpcUrl or provider should throw IllegalStateException
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> Brane.builder().signer(signer).buildSigner());

        assertTrue(ex.getMessage().contains("rpcUrl") || ex.getMessage().contains("provider"),
                "Exception message should mention rpcUrl or provider");
    }

    @Test
    void buildWithoutRpcUrlOrProviderThrows() {
        // build() without rpcUrl or provider should throw IllegalStateException
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> Brane.builder().build());

        assertTrue(ex.getMessage().contains("rpcUrl") || ex.getMessage().contains("provider"),
                "Exception message should mention rpcUrl or provider");
    }

    @Test
    void buildSignerWithoutSignerThrows() {
        // buildSigner() without signer should throw IllegalStateException
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> Brane.builder().provider(provider).buildSigner());

        assertTrue(ex.getMessage().contains("signer"),
                "Exception message should mention signer");
    }

    @Test
    void buildSignerWithoutSignerAndWithRpcUrlThrows() {
        // buildSigner() with rpcUrl but without signer should throw IllegalStateException
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> Brane.builder().rpcUrl("http://localhost:8545").buildSigner());

        assertTrue(ex.getMessage().contains("signer"),
                "Exception message should mention signer");
    }

    // ==================== WebSocket URL Builder Tests (P1-04) ====================

    @Test
    void wsUrlCreatesWebSocketBasedClient() {
        // Mock WebSocketProvider.create() to avoid actual network connection
        try (MockedStatic<WebSocketProvider> mockedStatic = mockStatic(WebSocketProvider.class)) {
            WebSocketProvider mockWsProvider = org.mockito.Mockito.mock(WebSocketProvider.class);
            mockedStatic.when(() -> WebSocketProvider.create("wss://eth.example.com"))
                    .thenReturn(mockWsProvider);

            // Build a client using wsUrl
            Brane.Reader reader = Brane.builder()
                    .wsUrl("wss://eth.example.com")
                    .buildReader();

            assertNotNull(reader);
            // WebSocket clients should support subscriptions
            assertTrue(reader.canSubscribe(),
                    "Client created with wsUrl should support subscriptions (canSubscribe=true)");
        }
    }

    @Test
    void wsUrlWithSignerCreatesWebSocketBasedSigner() {
        // Mock WebSocketProvider.create() to avoid actual network connection
        try (MockedStatic<WebSocketProvider> mockedStatic = mockStatic(WebSocketProvider.class)) {
            WebSocketProvider mockWsProvider = org.mockito.Mockito.mock(WebSocketProvider.class);
            mockedStatic.when(() -> WebSocketProvider.create("wss://eth.example.com"))
                    .thenReturn(mockWsProvider);

            Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
            Brane.Signer client = Brane.builder()
                    .wsUrl("wss://eth.example.com")
                    .signer(signer)
                    .buildSigner();

            assertNotNull(client);
            assertTrue(client.canSign());
            assertTrue(client.canSubscribe(),
                    "Signer created with wsUrl should support subscriptions");
        }
    }

    @Test
    void wsUrlAlonePassesValidation() {
        // Verify that wsUrl alone (without rpcUrl or provider) passes validation
        // We use MockedStatic to avoid actual connection
        try (MockedStatic<WebSocketProvider> mockedStatic = mockStatic(WebSocketProvider.class)) {
            WebSocketProvider mockWsProvider = org.mockito.Mockito.mock(WebSocketProvider.class);
            mockedStatic.when(() -> WebSocketProvider.create("wss://eth.example.com"))
                    .thenReturn(mockWsProvider);

            // Should not throw - wsUrl alone is sufficient
            assertDoesNotThrow(() -> Brane.builder()
                    .wsUrl("wss://eth.example.com")
                    .build());
        }
    }

    @Test
    void wsUrlHasPriorityOverRpcUrl() {
        // When both wsUrl and rpcUrl are set, wsUrl should take precedence
        try (MockedStatic<WebSocketProvider> mockedStatic = mockStatic(WebSocketProvider.class)) {
            WebSocketProvider mockWsProvider = org.mockito.Mockito.mock(WebSocketProvider.class);
            mockedStatic.when(() -> WebSocketProvider.create("wss://eth.example.com"))
                    .thenReturn(mockWsProvider);

            Brane.Reader reader = Brane.builder()
                    .rpcUrl("http://eth.example.com")
                    .wsUrl("wss://eth.example.com")
                    .buildReader();

            // Since wsUrl takes precedence, the provider should be WebSocket-based
            assertTrue(reader.canSubscribe(),
                    "wsUrl should have priority over rpcUrl, resulting in subscription-capable client");
        }
    }

    @Test
    void explicitProviderHasPriorityOverWsUrl() {
        // When both provider and wsUrl are set, explicit provider should take precedence
        // Use the mock HTTP provider (which doesn't support subscriptions)
        Brane.Reader reader = Brane.builder()
                .wsUrl("wss://eth.example.com")  // This should be ignored
                .provider(provider)              // This should take precedence
                .buildReader();

        // Since explicit provider (mock) takes precedence, canSubscribe should be false
        assertFalse(reader.canSubscribe(),
                "Explicit provider should have priority over wsUrl");
    }

    // ==================== Tester Builder Tests (TESTER-09) ====================

    @Test
    void buildTesterReturnsTesterInstance() {
        // buildTester() should return a Tester when signer is provided
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        assertNotNull(tester);
        assertInstanceOf(Brane.Tester.class, tester);
        assertTrue(tester.canSign());
    }

    @Test
    void buildTesterWithoutSignerThrows() {
        // buildTester() without signer should throw IllegalStateException
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> Brane.builder().provider(provider).buildTester());

        assertTrue(ex.getMessage().contains("signer"),
                "Exception message should mention signer");
    }

    @Test
    void buildTesterWithoutRpcUrlOrProviderThrows() {
        // buildTester() without rpcUrl or provider should throw IllegalStateException
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        IllegalStateException ex = assertThrows(
                IllegalStateException.class,
                () -> Brane.builder().signer(signer).buildTester());

        assertTrue(ex.getMessage().contains("rpcUrl") || ex.getMessage().contains("provider"),
                "Exception message should mention rpcUrl or provider");
    }

    @Test
    void testModeDefaultsToAnvil() {
        // Default testMode should be ANVIL
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        assertNotNull(tester);
        // Tester should be created successfully with default ANVIL mode
        assertInstanceOf(Brane.Tester.class, tester);
    }

    @Test
    void testModeCanBeSetToHardhat() {
        // testMode() should accept HARDHAT
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        assertNotNull(tester);
        assertInstanceOf(Brane.Tester.class, tester);
    }

    @Test
    void testModeCanBeSetToGanache() {
        // testMode() should accept GANACHE
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.GANACHE)
                .buildTester();

        assertNotNull(tester);
        assertInstanceOf(Brane.Tester.class, tester);
    }

    @Test
    void testerCanSignReturnsTrue() {
        // Tester.canSign() should return true
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        assertTrue(tester.canSign(), "Tester should be able to sign transactions");
    }

    @Test
    void patternMatchingWorksWithTester() {
        // Test pattern matching includes Tester case
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        String result = switch (tester) {
            case Brane.Reader r -> "reader";
            case Brane.Signer s -> "signer";
            case Brane.Tester t -> "tester";
        };
        assertEquals("tester", result);
    }

    // ==================== Tester Delegation Tests (TESTER-11) ====================

    @Test
    void testerChainIdDelegatesCorrectly() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x1", null, "1");
        when(provider.send(eq("eth_chainId"), any())).thenReturn(response);

        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        assertEquals(java.math.BigInteger.ONE, tester.chainId());
    }

    @Test
    void testerGetBalanceDelegatesCorrectly() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0xde0b6b3a7640000", null, "1");
        when(provider.send(eq("eth_getBalance"), any())).thenReturn(response);

        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        io.brane.core.types.Address address = new io.brane.core.types.Address("0x0000000000000000000000000000000000000001");
        java.math.BigInteger balance = tester.getBalance(address);
        assertEquals(new java.math.BigInteger("1000000000000000000"), balance);
    }

    @Test
    void testerGetLatestBlockDelegatesCorrectly() {
        // Create a mock response with block data
        java.util.Map<String, Object> blockData = new java.util.LinkedHashMap<>();
        blockData.put("number", "0x1");
        blockData.put("hash", "0x" + "a".repeat(64));
        blockData.put("parentHash", "0x" + "b".repeat(64));
        blockData.put("timestamp", "0x5f5e100");
        blockData.put("gasLimit", "0x1c9c380");
        blockData.put("gasUsed", "0x5208");
        blockData.put("baseFeePerGas", "0x3b9aca00");
        JsonRpcResponse response = new JsonRpcResponse("2.0", blockData, null, "1");
        when(provider.send(eq("eth_getBlockByNumber"), any())).thenReturn(response);

        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        io.brane.core.model.BlockHeader block = tester.getLatestBlock();
        assertNotNull(block);
        assertEquals(1L, block.number());
    }

    @Test
    void testerCallDelegatesCorrectly() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x" + "00".repeat(32), null, "1");
        when(provider.send(eq("eth_call"), any())).thenReturn(response);

        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        io.brane.core.types.Address contractAddress = new io.brane.core.types.Address("0x0000000000000000000000000000000000000001");
        CallRequest request = CallRequest.builder()
                .to(contractAddress)
                .data(new io.brane.core.types.HexData("0x"))
                .build();

        io.brane.core.types.HexData result = tester.call(request);
        assertNotNull(result);
    }

    @Test
    void testerGetLogsDelegatesCorrectly() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", java.util.List.of(), null, "1");
        when(provider.send(eq("eth_getLogs"), any())).thenReturn(response);

        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        io.brane.core.types.Address contractAddress = new io.brane.core.types.Address("0x0000000000000000000000000000000000000001");
        LogFilter filter = LogFilter.byContract(contractAddress, java.util.List.of());

        java.util.List<io.brane.core.model.LogEntry> logs = tester.getLogs(filter);
        assertNotNull(logs);
        assertTrue(logs.isEmpty());
    }

    @Test
    void testerEstimateGasDelegatesCorrectly() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x5208", null, "1");
        when(provider.send(eq("eth_estimateGas"), any())).thenReturn(response);

        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        io.brane.core.types.Address from = new io.brane.core.types.Address("0x0000000000000000000000000000000000000001");
        io.brane.core.types.Address to = new io.brane.core.types.Address("0x0000000000000000000000000000000000000002");
        io.brane.core.model.TransactionRequest request = io.brane.core.builder.TxBuilder.eip1559()
                .from(from)
                .to(to)
                .build();

        java.math.BigInteger gasEstimate = tester.estimateGas(request);
        assertEquals(java.math.BigInteger.valueOf(21000), gasEstimate);
    }

    @Test
    void testerCanSubscribeDelegatesCorrectly() {
        // HTTP provider does not support subscriptions
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        assertFalse(tester.canSubscribe());
    }

    @Test
    void testerChainDelegatesCorrectly() {
        io.brane.core.chain.ChainProfile chainProfile = io.brane.core.chain.ChainProfiles.ETH_MAINNET;
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .chain(chainProfile)
                .buildTester();

        assertTrue(tester.chain().isPresent());
        assertEquals(chainProfile, tester.chain().get());
    }

    @Test
    void testerAsSignerReturnsSignerInstance() {
        Signer signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        Brane.Signer returnedSigner = tester.asSigner();
        assertNotNull(returnedSigner);
        assertTrue(returnedSigner.canSign());
        assertEquals(signer, returnedSigner.signer());
    }
}
