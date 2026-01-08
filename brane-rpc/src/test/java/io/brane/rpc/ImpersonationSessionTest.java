package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.crypto.Signer;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

/**
 * Unit tests for {@link ImpersonationSession} behavior.
 *
 * <p>Tests verify idempotent close and automatic from-address setting.
 */
@ExtendWith(MockitoExtension.class)
class ImpersonationSessionTest {

    @Mock
    private BraneProvider provider;

    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private static final Address IMPERSONATED_ADDRESS =
            new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");

    private static final Address RECIPIENT_ADDRESS =
            new Address("0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC");

    private static final Hash TX_HASH =
            new Hash("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");

    private Signer signer;

    @BeforeEach
    void setUp() {
        signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
    }

    private Brane.Tester createTester() {
        return Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();
    }

    private ImpersonationSession createSession() {
        JsonRpcResponse impersonateResponse = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_impersonateAccount"), any())).thenReturn(impersonateResponse);

        Brane.Tester tester = createTester();
        return tester.impersonate(IMPERSONATED_ADDRESS);
    }

    // ==================== Idempotent Close Tests ====================

    @Nested
    @DisplayName("Idempotent close behavior")
    class IdempotentClose {

        @Test
        @DisplayName("close() only calls stopImpersonating once when called multiple times")
        void closeIsIdempotent() {
            // Arrange
            JsonRpcResponse stopResponse = new JsonRpcResponse("2.0", null, null, "2");
            when(provider.send(eq("anvil_stopImpersonatingAccount"), any())).thenReturn(stopResponse);

            ImpersonationSession session = createSession();

            // Act - call close multiple times
            session.close();
            session.close();
            session.close();

            // Assert - stopImpersonating should only be called once
            verify(provider, times(1)).send(eq("anvil_stopImpersonatingAccount"), any());
        }

        @Test
        @DisplayName("close() does not throw even when called multiple times")
        void closeDoesNotThrowOnMultipleCalls() {
            // Arrange
            JsonRpcResponse stopResponse = new JsonRpcResponse("2.0", null, null, "2");
            when(provider.send(eq("anvil_stopImpersonatingAccount"), any())).thenReturn(stopResponse);

            ImpersonationSession session = createSession();

            // Act & Assert - no exception thrown
            assertDoesNotThrow(() -> {
                session.close();
                session.close();
                session.close();
            });
        }

        @Test
        @DisplayName("close() is safe even if RPC call fails")
        void closeHandlesRpcErrorGracefully() {
            // Arrange
            JsonRpcError error = new JsonRpcError(-32000, "stop failed", null);
            JsonRpcResponse errorResponse = new JsonRpcResponse("2.0", null, error, "2");
            when(provider.send(eq("anvil_stopImpersonatingAccount"), any())).thenReturn(errorResponse);

            ImpersonationSession session = createSession();

            // Act & Assert - close should not throw even if RPC fails
            assertDoesNotThrow(() -> session.close());
        }

        @Test
        @DisplayName("close() does not retry failed RPC calls on subsequent close")
        void closeDoesNotRetryAfterFailure() {
            // Arrange
            JsonRpcError error = new JsonRpcError(-32000, "stop failed", null);
            JsonRpcResponse errorResponse = new JsonRpcResponse("2.0", null, error, "2");
            when(provider.send(eq("anvil_stopImpersonatingAccount"), any())).thenReturn(errorResponse);

            ImpersonationSession session = createSession();

            // Act - call close multiple times after a failure
            session.close();
            session.close();

            // Assert - should still only call RPC once (idempotent behavior preserved)
            verify(provider, times(1)).send(eq("anvil_stopImpersonatingAccount"), any());
        }
    }

    // ==================== Auto-From Setting Tests ====================

    @Nested
    @DisplayName("Auto-from address setting")
    class AutoFromSetting {

        @Test
        @DisplayName("sendTransaction sets from address to impersonated address")
        @SuppressWarnings("unchecked")
        void sendTransactionSetsFromToImpersonatedAddress() {
            // Arrange
            JsonRpcResponse sendResponse = new JsonRpcResponse("2.0", TX_HASH.value(), null, "2");
            ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            when(provider.send(eq("eth_sendTransaction"), paramsCaptor.capture())).thenReturn(sendResponse);

            ImpersonationSession session = createSession();

            // TransactionRequest: from, to, value, gasLimit, gasPrice, maxPriorityFeePerGas, maxFeePerGas, nonce, data, isEip1559, accessList
            TransactionRequest request = new TransactionRequest(
                    null, // from - will be set by session
                    RECIPIENT_ADDRESS,
                    Wei.fromEther(BigDecimal.ONE),
                    null, null, null, null, null, null, true, null);

            // Act
            session.sendTransaction(request);

            // Assert
            List<?> params = paramsCaptor.getValue();
            assertEquals(1, params.size());
            Map<String, Object> txParams = (Map<String, Object>) params.get(0);
            assertEquals(IMPERSONATED_ADDRESS.value(), txParams.get("from"));
        }

        @Test
        @DisplayName("sendTransaction overrides any existing from address with impersonated address")
        @SuppressWarnings("unchecked")
        void sendTransactionOverridesExistingFromAddress() {
            // Arrange
            JsonRpcResponse sendResponse = new JsonRpcResponse("2.0", TX_HASH.value(), null, "2");
            ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            when(provider.send(eq("eth_sendTransaction"), paramsCaptor.capture())).thenReturn(sendResponse);

            ImpersonationSession session = createSession();

            // Request with a different from address
            Address differentFrom = new Address("0x90F79bf6EB2c4f870365E785982E1f101E93b906");
            TransactionRequest request = new TransactionRequest(
                    differentFrom, // different from address
                    RECIPIENT_ADDRESS,
                    Wei.fromEther(BigDecimal.ONE),
                    null, null, null, null, null, null, true, null);

            // Act
            session.sendTransaction(request);

            // Assert - from should be the impersonated address, not the one in request
            List<?> params = paramsCaptor.getValue();
            Map<String, Object> txParams = (Map<String, Object>) params.get(0);
            assertEquals(IMPERSONATED_ADDRESS.value(), txParams.get("from"));
        }

        @Test
        @DisplayName("sendTransaction includes all transaction fields")
        @SuppressWarnings("unchecked")
        void sendTransactionIncludesAllFields() {
            // Arrange
            JsonRpcResponse sendResponse = new JsonRpcResponse("2.0", TX_HASH.value(), null, "2");
            ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            when(provider.send(eq("eth_sendTransaction"), paramsCaptor.capture())).thenReturn(sendResponse);

            ImpersonationSession session = createSession();

            HexData calldata = new HexData("0xa9059cbb");
            TransactionRequest request = new TransactionRequest(
                    null,
                    RECIPIENT_ADDRESS,
                    Wei.fromEther(new BigDecimal("1.5")),
                    100_000L,
                    null, // gasPrice
                    Wei.of(1_000_000_000L), // maxPriorityFeePerGas
                    Wei.of(20_000_000_000L), // maxFeePerGas
                    42L, // nonce
                    calldata,
                    true,
                    null);

            // Act
            session.sendTransaction(request);

            // Assert
            List<?> params = paramsCaptor.getValue();
            Map<String, Object> txParams = (Map<String, Object>) params.get(0);

            assertEquals(IMPERSONATED_ADDRESS.value(), txParams.get("from"));
            assertEquals(RECIPIENT_ADDRESS.value(), txParams.get("to"));
            assertEquals(calldata.value(), txParams.get("data"));
            assertNotNull(txParams.get("value"));
            assertNotNull(txParams.get("gas"));
            assertNotNull(txParams.get("maxFeePerGas"));
            assertNotNull(txParams.get("maxPriorityFeePerGas"));
            assertNotNull(txParams.get("nonce"));
        }

        @Test
        @DisplayName("sendTransaction returns transaction hash")
        void sendTransactionReturnsTxHash() {
            // Arrange
            JsonRpcResponse sendResponse = new JsonRpcResponse("2.0", TX_HASH.value(), null, "2");
            when(provider.send(eq("eth_sendTransaction"), any())).thenReturn(sendResponse);

            ImpersonationSession session = createSession();

            TransactionRequest request = new TransactionRequest(
                    null, RECIPIENT_ADDRESS, Wei.fromEther(BigDecimal.ONE),
                    null, null, null, null, null, null, true, null);

            // Act
            Hash result = session.sendTransaction(request);

            // Assert
            assertEquals(TX_HASH, result);
        }
    }

    // ==================== Session State Tests ====================

    @Nested
    @DisplayName("Session state management")
    class SessionState {

        @Test
        @DisplayName("sendTransaction throws IllegalStateException after close")
        void sendTransactionThrowsAfterClose() {
            // Arrange
            JsonRpcResponse stopResponse = new JsonRpcResponse("2.0", null, null, "2");
            when(provider.send(eq("anvil_stopImpersonatingAccount"), any())).thenReturn(stopResponse);

            ImpersonationSession session = createSession();
            session.close();

            TransactionRequest request = new TransactionRequest(
                    null, RECIPIENT_ADDRESS, Wei.fromEther(BigDecimal.ONE),
                    null, null, null, null, null, null, true, null);

            // Act & Assert
            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> session.sendTransaction(request));
            assertTrue(ex.getMessage().contains("closed"));
        }

        @Test
        @DisplayName("address() returns correct impersonated address")
        void addressReturnsImpersonatedAddress() {
            // Arrange
            ImpersonationSession session = createSession();

            // Act & Assert
            assertEquals(IMPERSONATED_ADDRESS, session.address());
        }
    }
}
