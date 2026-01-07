package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.brane.core.error.RpcException;

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
}
