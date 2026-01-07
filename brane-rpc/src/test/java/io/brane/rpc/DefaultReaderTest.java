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

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.brane.core.error.RpcException;
import io.brane.core.model.BlockHeader;
import io.brane.core.types.Address;

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
}
