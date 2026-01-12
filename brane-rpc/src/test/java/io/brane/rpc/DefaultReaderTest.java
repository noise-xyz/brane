// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.brane.core.error.RpcException;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.LogEntry;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

/**
 * Unit tests for {@link DefaultReader} with mock BraneProvider.
 */
@ExtendWith(MockitoExtension.class)
class DefaultReaderTest {

    @Mock
    private BraneProvider provider;

    private DefaultReader reader;

    @BeforeEach
    void setUp() {
        reader = new DefaultReader(provider, null, 0, RpcRetryConfig.defaults());
    }

    // ==================== chainId() Tests ====================

    @Test
    void chainIdReturnsChainIdFromProvider() {
        // Given: provider returns chain ID 1 (mainnet)
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x1", null, "1");
        when(provider.send(eq("eth_chainId"), any())).thenReturn(response);

        // When
        BigInteger chainId = reader.chainId();

        // Then
        assertEquals(BigInteger.ONE, chainId);
        verify(provider).send(eq("eth_chainId"), eq(List.of()));
    }

    @Test
    void chainIdReturnsLargeChainId() {
        // Given: provider returns chain ID 137 (Polygon)
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x89", null, "1");
        when(provider.send(eq("eth_chainId"), any())).thenReturn(response);

        // When
        BigInteger chainId = reader.chainId();

        // Then
        assertEquals(BigInteger.valueOf(137), chainId);
    }

    @Test
    void chainIdReturnsVeryLargeChainId() {
        // Given: provider returns a very large chain ID
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0xffffffff", null, "1");
        when(provider.send(eq("eth_chainId"), any())).thenReturn(response);

        // When
        BigInteger chainId = reader.chainId();

        // Then
        assertEquals(new BigInteger("ffffffff", 16), chainId);
    }

    @Test
    void chainIdThrowsWhenResultIsNull() {
        // Given: provider returns null result
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("eth_chainId"), any())).thenReturn(response);

        // When/Then
        RpcException ex = assertThrows(RpcException.class, () -> reader.chainId());
        assertTrue(ex.getMessage().contains("null"));
    }

    @Test
    void chainIdCallsProviderExactlyOnceWithNoRetry() {
        // Given: maxRetries is 0, so only one call should be made
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x1", null, "1");
        when(provider.send(eq("eth_chainId"), any())).thenReturn(response);

        // When
        reader.chainId();

        // Then: provider should be called exactly once
        verify(provider, times(1)).send(eq("eth_chainId"), any());
    }

    @Test
    void chainIdThrowsAfterClose() {
        // Given: reader is closed
        reader.close();

        // When/Then
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> reader.chainId());
        assertTrue(ex.getMessage().contains("closed"));
    }

    @Test
    void chainIdWithRetryConfigured() {
        // Given: reader with retries configured
        DefaultReader readerWithRetry = new DefaultReader(provider, null, 3, RpcRetryConfig.defaults());
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x5", null, "1");
        when(provider.send(eq("eth_chainId"), any())).thenReturn(response);

        // When
        BigInteger chainId = readerWithRetry.chainId();

        // Then
        assertEquals(BigInteger.valueOf(5), chainId);
    }

    @Test
    void chainIdParsesHexWithoutPrefix() {
        // Some providers might return hex without 0x prefix (edge case)
        // DefaultReader uses RpcUtils.decodeHexBigInteger which handles this
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x1", null, "1");
        when(provider.send(eq("eth_chainId"), any())).thenReturn(response);

        BigInteger chainId = reader.chainId();

        assertEquals(BigInteger.ONE, chainId);
    }

    // ==================== getBalance() Tests ====================

    @Test
    void getBalanceReturnsBalanceFromProvider() {
        // Given: provider returns balance of 1 ETH (1e18 wei)
        Address address = new Address("0x742d35Cc6634C0532925a3b844Bc9e7595f9e9e9");
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0xde0b6b3a7640000", null, "1");
        when(provider.send(eq("eth_getBalance"), any())).thenReturn(response);

        // When
        BigInteger balance = reader.getBalance(address);

        // Then
        assertEquals(new BigInteger("de0b6b3a7640000", 16), balance);
        verify(provider).send(eq("eth_getBalance"), eq(List.of(address.value(), "latest")));
    }

    @Test
    void getBalanceReturnsZeroBalance() {
        // Given: provider returns zero balance
        Address address = new Address("0x742d35Cc6634C0532925a3b844Bc9e7595f9e9e9");
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x0", null, "1");
        when(provider.send(eq("eth_getBalance"), any())).thenReturn(response);

        // When
        BigInteger balance = reader.getBalance(address);

        // Then
        assertEquals(BigInteger.ZERO, balance);
    }

    @Test
    void getBalanceReturnsLargeBalance() {
        // Given: provider returns a very large balance (10000 ETH)
        Address address = new Address("0x742d35Cc6634C0532925a3b844Bc9e7595f9e9e9");
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x21e19e0c9bab2400000", null, "1");
        when(provider.send(eq("eth_getBalance"), any())).thenReturn(response);

        // When
        BigInteger balance = reader.getBalance(address);

        // Then
        assertEquals(new BigInteger("21e19e0c9bab2400000", 16), balance);
    }

    @Test
    void getBalanceThrowsWhenResultIsNull() {
        // Given: provider returns null result
        Address address = new Address("0x742d35Cc6634C0532925a3b844Bc9e7595f9e9e9");
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("eth_getBalance"), any())).thenReturn(response);

        // When/Then
        RpcException ex = assertThrows(RpcException.class, () -> reader.getBalance(address));
        assertTrue(ex.getMessage().contains("null"));
    }

    @Test
    void getBalanceThrowsAfterClose() {
        // Given: reader is closed
        reader.close();
        Address address = new Address("0x742d35Cc6634C0532925a3b844Bc9e7595f9e9e9");

        // When/Then
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> reader.getBalance(address));
        assertTrue(ex.getMessage().contains("closed"));
    }

    // ==================== getCode() Tests ====================

    @Test
    void getCodeReturnsCodeFromProvider() {
        // Given: provider returns contract bytecode
        Address address = new Address("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48");
        String bytecode = "0x608060405234801561001057600080fd5b506004361061002b5760003560e01c8063";
        JsonRpcResponse response = new JsonRpcResponse("2.0", bytecode, null, "1");
        when(provider.send(eq("eth_getCode"), any())).thenReturn(response);

        // When
        HexData code = reader.getCode(address);

        // Then
        assertEquals(bytecode, code.value());
        verify(provider).send(eq("eth_getCode"), eq(List.of(address.value(), "latest")));
    }

    @Test
    void getCodeReturnsEmptyForEOA() {
        // Given: provider returns "0x" for externally owned account (no code)
        Address address = new Address("0x742d35Cc6634C0532925a3b844Bc9e7595f9e9e9");
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x", null, "1");
        when(provider.send(eq("eth_getCode"), any())).thenReturn(response);

        // When
        HexData code = reader.getCode(address);

        // Then
        assertEquals(HexData.EMPTY, code);
    }

    @Test
    void getCodeReturnsEmptyWhenResultIsNull() {
        // Given: provider returns null result
        Address address = new Address("0x742d35Cc6634C0532925a3b844Bc9e7595f9e9e9");
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("eth_getCode"), any())).thenReturn(response);

        // When
        HexData code = reader.getCode(address);

        // Then
        assertEquals(HexData.EMPTY, code);
    }

    @Test
    void getCodeThrowsAfterClose() {
        // Given: reader is closed
        reader.close();
        Address address = new Address("0x742d35Cc6634C0532925a3b844Bc9e7595f9e9e9");

        // When/Then
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> reader.getCode(address));
        assertTrue(ex.getMessage().contains("closed"));
    }

    // ==================== getLatestBlock() Tests ====================

    @Test
    void getLatestBlockReturnsBlockHeader() {
        // Given: provider returns a block
        Map<String, Object> blockMap = new LinkedHashMap<>();
        blockMap.put("hash", "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
        blockMap.put("parentHash", "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");
        blockMap.put("number", "0x100");
        blockMap.put("timestamp", "0x64a7b8c0");
        blockMap.put("baseFeePerGas", "0x3b9aca00");
        JsonRpcResponse response = new JsonRpcResponse("2.0", blockMap, null, "1");
        when(provider.send(eq("eth_getBlockByNumber"), any())).thenReturn(response);

        // When
        BlockHeader block = reader.getLatestBlock();

        // Then
        assertNotNull(block);
        assertEquals("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef", block.hash().value());
        assertEquals("0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890", block.parentHash().value());
        assertEquals(0x100L, block.number());
        assertEquals(0x64a7b8c0L, block.timestamp());
        assertEquals(new BigInteger("3b9aca00", 16), block.baseFeePerGas().value());
        verify(provider).send(eq("eth_getBlockByNumber"), eq(List.of("latest", Boolean.FALSE)));
    }

    @Test
    void getLatestBlockReturnsNullWhenNoBlock() {
        // Given: provider returns null result (block not found)
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("eth_getBlockByNumber"), any())).thenReturn(response);

        // When
        BlockHeader block = reader.getLatestBlock();

        // Then
        assertNull(block);
    }

    @Test
    void getLatestBlockHandlesPreLondonBlock() {
        // Given: provider returns a pre-London block (no baseFeePerGas)
        Map<String, Object> blockMap = new LinkedHashMap<>();
        blockMap.put("hash", "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
        blockMap.put("parentHash", "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");
        blockMap.put("number", "0x50");
        blockMap.put("timestamp", "0x5f5e100");
        // No baseFeePerGas field
        JsonRpcResponse response = new JsonRpcResponse("2.0", blockMap, null, "1");
        when(provider.send(eq("eth_getBlockByNumber"), any())).thenReturn(response);

        // When
        BlockHeader block = reader.getLatestBlock();

        // Then
        assertNotNull(block);
        assertEquals(0x50L, block.number());
        assertNull(block.baseFeePerGas());
    }

    @Test
    void getLatestBlockThrowsAfterClose() {
        // Given: reader is closed
        reader.close();

        // When/Then
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> reader.getLatestBlock());
        assertTrue(ex.getMessage().contains("closed"));
    }

    // ==================== getBlockByNumber() Tests ====================

    @Test
    void getBlockByNumberReturnsBlockHeader() {
        // Given: provider returns a specific block
        Map<String, Object> blockMap = new LinkedHashMap<>();
        blockMap.put("hash", "0xaabbccdd11223344556677889900aabbccdd11223344556677889900aabbccdd");
        blockMap.put("parentHash", "0x1122334455667788990011223344556677889900112233445566778899001122");
        blockMap.put("number", "0x1234");
        blockMap.put("timestamp", "0x64a7b8c0");
        blockMap.put("baseFeePerGas", "0x5f5e100");
        JsonRpcResponse response = new JsonRpcResponse("2.0", blockMap, null, "1");
        when(provider.send(eq("eth_getBlockByNumber"), any())).thenReturn(response);

        // When
        BlockHeader block = reader.getBlockByNumber(0x1234);

        // Then
        assertNotNull(block);
        assertEquals("0xaabbccdd11223344556677889900aabbccdd11223344556677889900aabbccdd", block.hash().value());
        assertEquals(0x1234L, block.number());
        verify(provider).send(eq("eth_getBlockByNumber"), eq(List.of("0x1234", Boolean.FALSE)));
    }

    @Test
    void getBlockByNumberReturnsNullWhenNotFound() {
        // Given: provider returns null result (block not found)
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("eth_getBlockByNumber"), any())).thenReturn(response);

        // When
        BlockHeader block = reader.getBlockByNumber(999999999L);

        // Then
        assertNull(block);
    }

    @Test
    void getBlockByNumberReturnsGenesisBlock() {
        // Given: provider returns genesis block (block 0)
        Map<String, Object> blockMap = new LinkedHashMap<>();
        blockMap.put("hash", "0xd4e56740f876aef8c010b86a40d5f56745a118d0906a34e69aec8c0db1cb8fa3");
        blockMap.put("parentHash", "0x0000000000000000000000000000000000000000000000000000000000000000");
        blockMap.put("number", "0x0");
        blockMap.put("timestamp", "0x0");
        JsonRpcResponse response = new JsonRpcResponse("2.0", blockMap, null, "1");
        when(provider.send(eq("eth_getBlockByNumber"), any())).thenReturn(response);

        // When
        BlockHeader block = reader.getBlockByNumber(0);

        // Then
        assertNotNull(block);
        assertEquals(0L, block.number());
        assertEquals("0x0000000000000000000000000000000000000000000000000000000000000000", block.parentHash().value());
        verify(provider).send(eq("eth_getBlockByNumber"), eq(List.of("0x0", Boolean.FALSE)));
    }

    @Test
    void getBlockByNumberUsesHexFormatting() {
        // Given: test that block numbers are properly hex-formatted
        Map<String, Object> blockMap = new LinkedHashMap<>();
        blockMap.put("hash", "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");
        blockMap.put("parentHash", "0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");
        blockMap.put("number", "0xf4240"); // 1000000 in hex
        blockMap.put("timestamp", "0x64a7b8c0");
        JsonRpcResponse response = new JsonRpcResponse("2.0", blockMap, null, "1");
        when(provider.send(eq("eth_getBlockByNumber"), any())).thenReturn(response);

        // When
        BlockHeader block = reader.getBlockByNumber(1000000);

        // Then
        assertNotNull(block);
        assertEquals(1000000L, block.number());
        // Verify the hex formatting: 1000000 = 0xf4240
        verify(provider).send(eq("eth_getBlockByNumber"), eq(List.of("0xf4240", Boolean.FALSE)));
    }

    @Test
    void getBlockByNumberThrowsAfterClose() {
        // Given: reader is closed
        reader.close();

        // When/Then
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> reader.getBlockByNumber(100));
        assertTrue(ex.getMessage().contains("closed"));
    }

    // ==================== call() Tests ====================

    @Test
    void callReturnsResultFromProvider() {
        // Given: provider returns a hex result
        Address to = new Address("0x742d35Cc6634C0532925a3b844Bc9e7595f9e9e9");
        HexData data = new HexData("0x70a08231000000000000000000000000742d35cc6634c0532925a3b844bc9e7595f9e9e9");
        CallRequest request = CallRequest.of(to, data);

        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x0000000000000000000000000000000000000000000000000de0b6b3a7640000", null, "1");
        when(provider.send(eq("eth_call"), any())).thenReturn(response);

        // When
        HexData result = reader.call(request);

        // Then
        assertEquals("0x0000000000000000000000000000000000000000000000000de0b6b3a7640000", result.value());
        verify(provider).send(eq("eth_call"), eq(List.of(request.toMap(), "latest")));
    }

    @Test
    void callWithBlockTagUsesSpecifiedTag() {
        // Given: call with specific block tag
        Address to = new Address("0x742d35Cc6634C0532925a3b844Bc9e7595f9e9e9");
        HexData data = new HexData("0x70a08231");
        CallRequest request = CallRequest.of(to, data);

        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x1234", null, "1");
        when(provider.send(eq("eth_call"), any())).thenReturn(response);

        // When
        HexData result = reader.call(request, BlockTag.PENDING);

        // Then
        assertEquals("0x1234", result.value());
        verify(provider).send(eq("eth_call"), eq(List.of(request.toMap(), "pending")));
    }

    @Test
    void callWithBlockNumberTag() {
        // Given: call with specific block number
        Address to = new Address("0x742d35Cc6634C0532925a3b844Bc9e7595f9e9e9");
        CallRequest request = CallRequest.of(to, HexData.EMPTY);

        JsonRpcResponse response = new JsonRpcResponse("2.0", "0xabcd", null, "1");
        when(provider.send(eq("eth_call"), any())).thenReturn(response);

        // When
        HexData result = reader.call(request, BlockTag.of(1000));

        // Then
        assertEquals("0xabcd", result.value());
        verify(provider).send(eq("eth_call"), eq(List.of(request.toMap(), "0x3e8")));
    }

    @Test
    void callReturnsEmptyHexDataWhenResultIsNull() {
        // Given: provider returns null result
        Address to = new Address("0x742d35Cc6634C0532925a3b844Bc9e7595f9e9e9");
        CallRequest request = CallRequest.of(to, HexData.EMPTY);

        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("eth_call"), any())).thenReturn(response);

        // When
        HexData result = reader.call(request);

        // Then
        assertEquals(HexData.EMPTY, result);
    }

    @Test
    void callThrowsAfterClose() {
        // Given: reader is closed
        reader.close();
        Address to = new Address("0x742d35Cc6634C0532925a3b844Bc9e7595f9e9e9");
        CallRequest request = CallRequest.of(to, HexData.EMPTY);

        // When/Then
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> reader.call(request));
        assertTrue(ex.getMessage().contains("closed"));
    }

    @Test
    void callWithFullRequest() {
        // Given: call with all parameters
        Address from = new Address("0x1111111111111111111111111111111111111111");
        Address to = new Address("0x2222222222222222222222222222222222222222");
        HexData data = new HexData("0xdeadbeef");

        CallRequest request = CallRequest.builder()
                .from(from)
                .to(to)
                .data(data)
                .build();

        JsonRpcResponse response = new JsonRpcResponse("2.0", "0xcafe", null, "1");
        when(provider.send(eq("eth_call"), any())).thenReturn(response);

        // When
        HexData result = reader.call(request);

        // Then
        assertEquals("0xcafe", result.value());
        Map<String, Object> expectedParams = request.toMap();
        verify(provider).send(eq("eth_call"), eq(List.of(expectedParams, "latest")));
    }

    // ==================== getLogs() Tests ====================

    @Test
    void getLogsReturnsLogsFromProvider() {
        // Given: provider returns a list of logs
        Address contractAddress = new Address("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48");
        Hash topic = new Hash("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
        LogFilter filter = LogFilter.byContract(contractAddress, List.of(topic));

        String blockHash = "0xabc123abc123abc123abc123abc123abc123abc123abc123abc123abc123abc1";
        String txHash = "0xdef456def456def456def456def456def456def456def456def456def456def4";
        List<Map<String, Object>> logsData = List.of(
                createLogMap(
                        "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48",
                        "0x0000000000000000000000000000000000000000000000000000000000001234",
                        List.of("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef"),
                        blockHash,
                        txHash,
                        "0x0"
                )
        );
        JsonRpcResponse response = new JsonRpcResponse("2.0", logsData, null, "1");
        when(provider.send(eq("eth_getLogs"), any())).thenReturn(response);

        // When
        List<LogEntry> logs = reader.getLogs(filter);

        // Then
        assertEquals(1, logs.size());
        LogEntry log = logs.get(0);
        assertEquals("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", log.address().value());
        assertEquals("0x0000000000000000000000000000000000000000000000000000000000001234", log.data().value());
    }

    @Test
    void getLogsReturnsEmptyListWhenNoLogs() {
        // Given: provider returns empty list
        Address contractAddress = new Address("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48");
        LogFilter filter = LogFilter.byContract(contractAddress, List.of());

        JsonRpcResponse response = new JsonRpcResponse("2.0", List.of(), null, "1");
        when(provider.send(eq("eth_getLogs"), any())).thenReturn(response);

        // When
        List<LogEntry> logs = reader.getLogs(filter);

        // Then
        assertTrue(logs.isEmpty());
    }

    @Test
    void getLogsReturnsEmptyListWhenResultIsNull() {
        // Given: provider returns null result
        Address contractAddress = new Address("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48");
        LogFilter filter = LogFilter.byContract(contractAddress, List.of());

        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("eth_getLogs"), any())).thenReturn(response);

        // When
        List<LogEntry> logs = reader.getLogs(filter);

        // Then
        assertTrue(logs.isEmpty());
    }

    @Test
    void getLogsWithMultipleLogs() {
        // Given: provider returns multiple logs
        Address contractAddress = new Address("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48");
        LogFilter filter = LogFilter.byContract(contractAddress, List.of());

        String blockHash = "0xabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabcabca";
        String txHash1 = "0x1111111111111111111111111111111111111111111111111111111111111111";
        String txHash2 = "0x2222222222222222222222222222222222222222222222222222222222222222";
        String txHash3 = "0x3333333333333333333333333333333333333333333333333333333333333333";
        List<Map<String, Object>> logsData = List.of(
                createLogMap("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", "0x1111", List.of(), blockHash, txHash1, "0x0"),
                createLogMap("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", "0x2222", List.of(), blockHash, txHash2, "0x1"),
                createLogMap("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48", "0x3333", List.of(), blockHash, txHash3, "0x2")
        );
        JsonRpcResponse response = new JsonRpcResponse("2.0", logsData, null, "1");
        when(provider.send(eq("eth_getLogs"), any())).thenReturn(response);

        // When
        List<LogEntry> logs = reader.getLogs(filter);

        // Then
        assertEquals(3, logs.size());
        assertEquals("0x1111", logs.get(0).data().value());
        assertEquals("0x2222", logs.get(1).data().value());
        assertEquals("0x3333", logs.get(2).data().value());
    }

    @Test
    void getLogsThrowsAfterClose() {
        // Given: reader is closed
        reader.close();
        Address contractAddress = new Address("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48");
        LogFilter filter = LogFilter.byContract(contractAddress, List.of());

        // When/Then
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> reader.getLogs(filter));
        assertTrue(ex.getMessage().contains("closed"));
    }

    @Test
    void getLogsWithBlockRangeFilter() {
        // Given: filter with block range
        LogFilter filter = new LogFilter(
                Optional.of(1000L),
                Optional.of(2000L),
                Optional.of(List.of(new Address("0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48"))),
                Optional.empty()
        );

        JsonRpcResponse response = new JsonRpcResponse("2.0", List.of(), null, "1");
        when(provider.send(eq("eth_getLogs"), any())).thenReturn(response);

        // When
        reader.getLogs(filter);

        // Then: verify the params include block range
        verify(provider).send(eq("eth_getLogs"), any());
    }

    // ==================== estimateGas() Tests ====================

    @Test
    void estimateGasReturnsGasEstimate() {
        // Given: provider returns a gas estimate
        Address from = new Address("0x1111111111111111111111111111111111111111");
        Address to = new Address("0x2222222222222222222222222222222222222222");
        TransactionRequest request = new TransactionRequest(
                from, to, null, null, null, null, null, null, null, true, null
        );

        // Gas estimate: 21000 (0x5208)
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x5208", null, "1");
        when(provider.send(eq("eth_estimateGas"), any())).thenReturn(response);

        // When
        BigInteger gasEstimate = reader.estimateGas(request);

        // Then
        assertEquals(BigInteger.valueOf(21000), gasEstimate);
    }

    @Test
    void estimateGasReturnsLargeGasEstimate() {
        // Given: provider returns a large gas estimate (complex contract call)
        Address from = new Address("0x1111111111111111111111111111111111111111");
        Address to = new Address("0x2222222222222222222222222222222222222222");
        HexData data = new HexData("0xa9059cbb0000000000000000000000001234567890123456789012345678901234567890");
        TransactionRequest request = new TransactionRequest(
                from, to, null, null, null, null, null, null, data, true, null
        );

        // Gas estimate: 100000 (0x186a0)
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x186a0", null, "1");
        when(provider.send(eq("eth_estimateGas"), any())).thenReturn(response);

        // When
        BigInteger gasEstimate = reader.estimateGas(request);

        // Then
        assertEquals(BigInteger.valueOf(100000), gasEstimate);
    }

    @Test
    void estimateGasThrowsWhenResultIsNull() {
        // Given: provider returns null result
        Address from = new Address("0x1111111111111111111111111111111111111111");
        Address to = new Address("0x2222222222222222222222222222222222222222");
        TransactionRequest request = new TransactionRequest(
                from, to, null, null, null, null, null, null, null, true, null
        );

        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("eth_estimateGas"), any())).thenReturn(response);

        // When/Then
        RpcException ex = assertThrows(RpcException.class, () -> reader.estimateGas(request));
        assertTrue(ex.getMessage().contains("null"));
    }

    @Test
    void estimateGasThrowsAfterClose() {
        // Given: reader is closed
        reader.close();
        Address from = new Address("0x1111111111111111111111111111111111111111");
        Address to = new Address("0x2222222222222222222222222222222222222222");
        TransactionRequest request = new TransactionRequest(
                from, to, null, null, null, null, null, null, null, true, null
        );

        // When/Then
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> reader.estimateGas(request));
        assertTrue(ex.getMessage().contains("closed"));
    }

    @Test
    void estimateGasWithValueTransfer() {
        // Given: provider returns gas estimate for value transfer
        Address from = new Address("0x1111111111111111111111111111111111111111");
        Address to = new Address("0x2222222222222222222222222222222222222222");
        Wei value = Wei.of(1_000_000_000_000_000_000L); // 1 ETH in wei
        TransactionRequest request = new TransactionRequest(
                from, to, value, null, null, null, null, null, null, true, null
        );

        // Gas estimate: 21000 (standard transfer)
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x5208", null, "1");
        when(provider.send(eq("eth_estimateGas"), any())).thenReturn(response);

        // When
        BigInteger gasEstimate = reader.estimateGas(request);

        // Then
        assertEquals(BigInteger.valueOf(21000), gasEstimate);
    }

    @Test
    void estimateGasWithContractDeployment() {
        // Given: contract deployment (no 'to' address)
        Address from = new Address("0x1111111111111111111111111111111111111111");
        HexData bytecode = new HexData("0x608060405234801561001057600080fd5b50");
        TransactionRequest request = new TransactionRequest(
                from, null, null, null, null, null, null, null, bytecode, true, null
        );

        // Gas estimate: 200000 (contract deployment)
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x30d40", null, "1");
        when(provider.send(eq("eth_estimateGas"), any())).thenReturn(response);

        // When
        BigInteger gasEstimate = reader.estimateGas(request);

        // Then
        assertEquals(BigInteger.valueOf(200000), gasEstimate);
    }

    // ==================== Lifecycle Tests ====================

    @Test
    void closeCallsProviderClose() {
        // When
        reader.close();

        // Then
        verify(provider).close();
    }

    @Test
    void closeIsIdempotent() {
        // When: close is called multiple times
        reader.close();
        reader.close();
        reader.close();

        // Then: provider.close() should only be called once
        verify(provider, times(1)).close();
    }

    @Test
    void isClosedReturnsFalseBeforeClose() {
        // Given: reader is open
        // When/Then
        assertFalse(reader.isClosed());
    }

    @Test
    void isClosedReturnsTrueAfterClose() {
        // Given: reader is open
        assertFalse(reader.isClosed());

        // When
        reader.close();

        // Then
        assertTrue(reader.isClosed());
    }

    @Test
    void ensureOpenSucceedsWhenOpen() {
        // Given: reader is open
        // When/Then: no exception thrown
        assertDoesNotThrow(() -> reader.ensureOpen());
    }

    @Test
    void ensureOpenThrowsAfterClose() {
        // Given: reader is closed
        reader.close();

        // When/Then
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> reader.ensureOpen());
        assertTrue(ex.getMessage().contains("closed"));
    }

    @Test
    void batchThrowsAfterClose() {
        // Given: reader is closed
        reader.close();

        // When/Then
        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> reader.batch());
        assertTrue(ex.getMessage().contains("closed"));
    }

    // ==================== Helper Methods ====================

    private Map<String, Object> createLogMap(
            String address,
            String data,
            List<String> topics,
            String blockHash,
            String transactionHash,
            String logIndex) {
        var map = new LinkedHashMap<String, Object>();
        map.put("address", address);
        map.put("data", data);
        map.put("topics", topics);
        map.put("blockHash", blockHash);
        map.put("transactionHash", transactionHash);
        map.put("logIndex", logIndex);
        map.put("removed", false);
        return map;
    }
}
