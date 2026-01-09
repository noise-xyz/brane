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

/**
 * Unit tests for {@link DefaultTester} time manipulation methods.
 */
@ExtendWith(MockitoExtension.class)
class DefaultTesterTimeTest {

    @Mock
    private BraneProvider provider;

    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private Signer signer;

    @BeforeEach
    void setUp() {
        signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
    }

    // ==================== setNextBlockTimestamp() Tests ====================

    @Test
    void setNextBlockTimestampCallsCorrectRpcMethodForAnvil() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("evm_setNextBlockTimestamp"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        tester.setNextBlockTimestamp(1700000000L);

        verify(provider).send(eq("evm_setNextBlockTimestamp"), any());
    }

    @Test
    void setNextBlockTimestampCallsCorrectRpcMethodForHardhat() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("hardhat_setNextBlockTimestamp"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        tester.setNextBlockTimestamp(1700000000L);

        verify(provider).send(eq("hardhat_setNextBlockTimestamp"), any());
    }

    @Test
    void setNextBlockTimestampCallsCorrectRpcMethodForGanache() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("evm_setNextBlockTimestamp"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.GANACHE)
                .buildTester();

        tester.setNextBlockTimestamp(1700000000L);

        verify(provider).send(eq("evm_setNextBlockTimestamp"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void setNextBlockTimestampPassesCorrectParameters() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("evm_setNextBlockTimestamp"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        tester.setNextBlockTimestamp(1700000000L);

        List<?> params = paramsCaptor.getValue();
        assertEquals(1, params.size());
        assertEquals("0x6553f100", params.get(0)); // 1700000000 in hex
    }

    @Test
    void setNextBlockTimestampThrowsOnRpcError() {
        JsonRpcError error = new JsonRpcError(-32000, "setNextBlockTimestamp failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("evm_setNextBlockTimestamp"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        RpcException ex = assertThrows(
                RpcException.class,
                () -> tester.setNextBlockTimestamp(1700000000L));
        assertEquals(-32000, ex.code());
    }

    // ==================== increaseTime() Tests ====================

    @Test
    void increaseTimeCallsCorrectRpcMethodForAnvil() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("evm_increaseTime"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        tester.increaseTime(3600L);

        verify(provider).send(eq("evm_increaseTime"), any());
    }

    @Test
    void increaseTimeCallsCorrectRpcMethodForHardhat() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("hardhat_increaseTime"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        tester.increaseTime(3600L);

        verify(provider).send(eq("hardhat_increaseTime"), any());
    }

    @Test
    void increaseTimeCallsCorrectRpcMethodForGanache() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("evm_increaseTime"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.GANACHE)
                .buildTester();

        tester.increaseTime(3600L);

        verify(provider).send(eq("evm_increaseTime"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void increaseTimePassesCorrectParameters() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("evm_increaseTime"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        tester.increaseTime(3600L);

        List<?> params = paramsCaptor.getValue();
        assertEquals(1, params.size());
        assertEquals("0xe10", params.get(0)); // 3600 in hex
    }

    @Test
    @SuppressWarnings("unchecked")
    void increaseTimePassesCorrectParametersForLargeValue() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("evm_increaseTime"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        tester.increaseTime(86400L); // 1 day in seconds

        List<?> params = paramsCaptor.getValue();
        assertEquals(1, params.size());
        assertEquals("0x15180", params.get(0)); // 86400 in hex
    }

    @Test
    void increaseTimeThrowsOnRpcError() {
        JsonRpcError error = new JsonRpcError(-32000, "increaseTime failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("evm_increaseTime"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        RpcException ex = assertThrows(
                RpcException.class,
                () -> tester.increaseTime(3600L));
        assertEquals(-32000, ex.code());
    }
}
