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
import io.brane.core.types.HexData;

/**
 * Unit tests for {@link DefaultTester} state management methods.
 */
@ExtendWith(MockitoExtension.class)
class DefaultTesterStateManagementTest {

    @Mock
    private BraneProvider provider;

    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private static final HexData TEST_STATE = new HexData("0x1f8b08000000000000ff");

    private Signer signer;

    @BeforeEach
    void setUp() {
        signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
    }

    // ==================== dumpState() Tests ====================

    @Test
    void dumpStateCallsCorrectRpcMethod() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", "0x1f8b08000000000000ff", null, "1");
        when(provider.send(eq("anvil_dumpState"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        HexData state = tester.dumpState();

        verify(provider).send(eq("anvil_dumpState"), eq(List.of()));
        assertEquals("0x1f8b08000000000000ff", state.value());
    }

    @Test
    void dumpStateThrowsOnRpcError() {
        JsonRpcError error = new JsonRpcError(-32000, "dumpState failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("anvil_dumpState"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        RpcException ex = assertThrows(
                RpcException.class,
                () -> tester.dumpState());
        assertEquals(-32000, ex.code());
    }

    @Test
    void dumpStateThrowsOnNullResult() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_dumpState"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        RpcException ex = assertThrows(
                RpcException.class,
                () -> tester.dumpState());
        assertEquals(-32000, ex.code());
        assertTrue(ex.getMessage().contains("returned null"));
    }

    @Test
    void dumpStateThrowsOnNonAnvilMode() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> tester.dumpState());
        assertTrue(ex.getMessage().contains("only supported by Anvil"));
    }

    @Test
    void dumpStateThrowsOnGanacheMode() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.GANACHE)
                .buildTester();

        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> tester.dumpState());
        assertTrue(ex.getMessage().contains("only supported by Anvil"));
    }

    // ==================== loadState() Tests ====================

    @Test
    void loadStateThrowsOnNullState() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> tester.loadState(null));
        assertEquals("state must not be null", ex.getMessage());
    }

    @Test
    void loadStateCallsCorrectRpcMethod() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", Boolean.TRUE, null, "1");
        when(provider.send(eq("anvil_loadState"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        boolean result = tester.loadState(TEST_STATE);

        verify(provider).send(eq("anvil_loadState"), any());
        assertTrue(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void loadStatePassesCorrectParameters() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", Boolean.TRUE, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("anvil_loadState"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        tester.loadState(TEST_STATE);

        List<?> params = paramsCaptor.getValue();
        assertEquals(1, params.size());
        assertEquals(TEST_STATE.value(), params.get(0));
    }

    @Test
    void loadStateReturnsFalseOnRpcError() {
        JsonRpcError error = new JsonRpcError(-32000, "loadState failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("anvil_loadState"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        boolean result = tester.loadState(TEST_STATE);

        assertFalse(result);
    }

    @Test
    void loadStateReturnsFalseOnNullResult() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_loadState"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        boolean result = tester.loadState(TEST_STATE);

        assertFalse(result);
    }

    @Test
    void loadStateReturnsFalseOnFalseResult() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", Boolean.FALSE, null, "1");
        when(provider.send(eq("anvil_loadState"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        boolean result = tester.loadState(TEST_STATE);

        assertFalse(result);
    }

    @Test
    void loadStateThrowsOnNonAnvilMode() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> tester.loadState(TEST_STATE));
        assertTrue(ex.getMessage().contains("only supported by Anvil"));
    }

    @Test
    void loadStateThrowsOnGanacheMode() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.GANACHE)
                .buildTester();

        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> tester.loadState(TEST_STATE));
        assertTrue(ex.getMessage().contains("only supported by Anvil"));
    }
}
