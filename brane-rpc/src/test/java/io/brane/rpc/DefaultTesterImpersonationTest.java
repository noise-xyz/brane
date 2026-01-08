package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.crypto.Signer;
import io.brane.core.error.RpcException;
import io.brane.core.types.Address;

/**
 * Unit tests for {@link DefaultTester} impersonation methods.
 */
@ExtendWith(MockitoExtension.class)
class DefaultTesterImpersonationTest {

    @Mock
    private BraneProvider provider;

    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private static final Address TEST_ADDRESS =
            new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");

    private Signer signer;

    @BeforeEach
    void setUp() {
        signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
    }

    // ==================== impersonate() Tests ====================

    @Test
    void impersonateCallsCorrectRpcMethodForAnvil() {
        // Arrange
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_impersonateAccount"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        // Act
        ImpersonationSession session = tester.impersonate(TEST_ADDRESS);

        // Assert
        assertNotNull(session);
        assertEquals(TEST_ADDRESS, session.address());
        verify(provider).send(eq("anvil_impersonateAccount"), any());
    }

    @Test
    void impersonateCallsCorrectRpcMethodForHardhat() {
        // Arrange
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("hardhat_impersonateAccount"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        // Act
        ImpersonationSession session = tester.impersonate(TEST_ADDRESS);

        // Assert
        assertNotNull(session);
        assertEquals(TEST_ADDRESS, session.address());
        verify(provider).send(eq("hardhat_impersonateAccount"), any());
    }

    @Test
    void impersonateCallsCorrectRpcMethodForGanache() {
        // Arrange
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("evm_impersonateAccount"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.GANACHE)
                .buildTester();

        // Act
        ImpersonationSession session = tester.impersonate(TEST_ADDRESS);

        // Assert
        assertNotNull(session);
        assertEquals(TEST_ADDRESS, session.address());
        verify(provider).send(eq("evm_impersonateAccount"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void impersonatePassesAddressAsParameter() {
        // Arrange
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("anvil_impersonateAccount"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        // Act
        tester.impersonate(TEST_ADDRESS);

        // Assert
        List<?> params = paramsCaptor.getValue();
        assertEquals(1, params.size());
        assertEquals(TEST_ADDRESS.value(), params.get(0));
    }

    @Test
    void impersonateThrowsOnRpcError() {
        // Arrange
        JsonRpcError error = new JsonRpcError(-32000, "Impersonation failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("anvil_impersonateAccount"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        // Act & Assert
        RpcException ex = assertThrows(RpcException.class, () -> tester.impersonate(TEST_ADDRESS));
        assertEquals(-32000, ex.code());
        assertTrue(ex.getMessage().contains("Impersonation failed"));
    }

    @Test
    void impersonateReturnsSessionWithCorrectAddress() {
        // Arrange
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_impersonateAccount"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        // Act
        ImpersonationSession session = tester.impersonate(TEST_ADDRESS);

        // Assert
        assertEquals(TEST_ADDRESS, session.address());
    }

    // ==================== stopImpersonating() Tests ====================

    @Test
    void stopImpersonatingCallsCorrectRpcMethodForAnvil() {
        // Arrange
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_stopImpersonatingAccount"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        // Act
        tester.stopImpersonating(TEST_ADDRESS);

        // Assert
        verify(provider).send(eq("anvil_stopImpersonatingAccount"), any());
    }

    @Test
    void stopImpersonatingCallsCorrectRpcMethodForHardhat() {
        // Arrange
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("hardhat_stopImpersonatingAccount"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        // Act
        tester.stopImpersonating(TEST_ADDRESS);

        // Assert
        verify(provider).send(eq("hardhat_stopImpersonatingAccount"), any());
    }

    @Test
    void stopImpersonatingCallsCorrectRpcMethodForGanache() {
        // Arrange
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("evm_stopImpersonatingAccount"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.GANACHE)
                .buildTester();

        // Act
        tester.stopImpersonating(TEST_ADDRESS);

        // Assert
        verify(provider).send(eq("evm_stopImpersonatingAccount"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void stopImpersonatingPassesAddressAsParameter() {
        // Arrange
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("anvil_stopImpersonatingAccount"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        // Act
        tester.stopImpersonating(TEST_ADDRESS);

        // Assert
        List<?> params = paramsCaptor.getValue();
        assertEquals(1, params.size());
        assertEquals(TEST_ADDRESS.value(), params.get(0));
    }

    @Test
    void stopImpersonatingThrowsOnRpcError() {
        // Arrange
        JsonRpcError error = new JsonRpcError(-32000, "Stop impersonation failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("anvil_stopImpersonatingAccount"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        // Act & Assert
        RpcException ex = assertThrows(RpcException.class, () -> tester.stopImpersonating(TEST_ADDRESS));
        assertEquals(-32000, ex.code());
        assertTrue(ex.getMessage().contains("Stop impersonation failed"));
    }

    // ==================== enableAutoImpersonate() Tests ====================

    @Test
    void enableAutoImpersonateCallsCorrectRpcMethodForAnvil() {
        // Arrange
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_autoImpersonateAccount"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        // Act
        tester.enableAutoImpersonate();

        // Assert
        verify(provider).send(eq("anvil_autoImpersonateAccount"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void enableAutoImpersonatePassesTrueAsParameter() {
        // Arrange
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("anvil_autoImpersonateAccount"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        // Act
        tester.enableAutoImpersonate();

        // Assert
        List<?> params = paramsCaptor.getValue();
        assertEquals(1, params.size());
        assertEquals(true, params.get(0));
    }

    @Test
    void enableAutoImpersonateThrowsForHardhat() {
        // Arrange
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        // Act & Assert
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                tester::enableAutoImpersonate);
        assertTrue(ex.getMessage().contains("Anvil"));
    }

    @Test
    void enableAutoImpersonateThrowsForGanache() {
        // Arrange
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.GANACHE)
                .buildTester();

        // Act & Assert
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                tester::enableAutoImpersonate);
        assertTrue(ex.getMessage().contains("Anvil"));
    }

    @Test
    void enableAutoImpersonateThrowsOnRpcError() {
        // Arrange
        JsonRpcError error = new JsonRpcError(-32000, "Auto-impersonate failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("anvil_autoImpersonateAccount"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        // Act & Assert
        RpcException ex = assertThrows(RpcException.class, tester::enableAutoImpersonate);
        assertEquals(-32000, ex.code());
    }

    // ==================== disableAutoImpersonate() Tests ====================

    @Test
    void disableAutoImpersonateCallsCorrectRpcMethodForAnvil() {
        // Arrange
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_autoImpersonateAccount"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        // Act
        tester.disableAutoImpersonate();

        // Assert
        verify(provider).send(eq("anvil_autoImpersonateAccount"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void disableAutoImpersonatePassesFalseAsParameter() {
        // Arrange
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("anvil_autoImpersonateAccount"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        // Act
        tester.disableAutoImpersonate();

        // Assert
        List<?> params = paramsCaptor.getValue();
        assertEquals(1, params.size());
        assertEquals(false, params.get(0));
    }

    @Test
    void disableAutoImpersonateThrowsForHardhat() {
        // Arrange
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        // Act & Assert
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                tester::disableAutoImpersonate);
        assertTrue(ex.getMessage().contains("Anvil"));
    }

    @Test
    void disableAutoImpersonateThrowsForGanache() {
        // Arrange
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.GANACHE)
                .buildTester();

        // Act & Assert
        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                tester::disableAutoImpersonate);
        assertTrue(ex.getMessage().contains("Anvil"));
    }

    @Test
    void disableAutoImpersonateThrowsOnRpcError() {
        // Arrange
        JsonRpcError error = new JsonRpcError(-32000, "Disable auto-impersonate failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("anvil_autoImpersonateAccount"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        // Act & Assert
        RpcException ex = assertThrows(RpcException.class, tester::disableAutoImpersonate);
        assertEquals(-32000, ex.code());
    }

    // ==================== ImpersonationSession Integration Tests ====================

    @Test
    void impersonationSessionCloseCallsStopImpersonating() {
        // Arrange
        JsonRpcResponse impersonateResponse = new JsonRpcResponse("2.0", null, null, "1");
        JsonRpcResponse stopResponse = new JsonRpcResponse("2.0", null, null, "2");
        when(provider.send(eq("anvil_impersonateAccount"), any())).thenReturn(impersonateResponse);
        when(provider.send(eq("anvil_stopImpersonatingAccount"), any())).thenReturn(stopResponse);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        // Act
        ImpersonationSession session = tester.impersonate(TEST_ADDRESS);
        session.close();

        // Assert
        verify(provider).send(eq("anvil_stopImpersonatingAccount"), any());
    }

    @Test
    void impersonationSessionTryWithResourcesCallsStopImpersonating() {
        // Arrange
        JsonRpcResponse impersonateResponse = new JsonRpcResponse("2.0", null, null, "1");
        JsonRpcResponse stopResponse = new JsonRpcResponse("2.0", null, null, "2");
        when(provider.send(eq("anvil_impersonateAccount"), any())).thenReturn(impersonateResponse);
        when(provider.send(eq("anvil_stopImpersonatingAccount"), any())).thenReturn(stopResponse);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        // Act
        try (ImpersonationSession session = tester.impersonate(TEST_ADDRESS)) {
            assertEquals(TEST_ADDRESS, session.address());
        }

        // Assert
        verify(provider).send(eq("anvil_stopImpersonatingAccount"), any());
    }
}
