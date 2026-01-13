// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;

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

import sh.brane.core.crypto.PrivateKeySigner;
import sh.brane.core.crypto.Signer;
import sh.brane.core.error.RpcException;

/**
 * Unit tests for {@link DefaultTester} mining methods.
 */
@ExtendWith(MockitoExtension.class)
class DefaultTesterMiningTest {

    @Mock
    private BraneProvider provider;

    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private Signer signer;

    @BeforeEach
    void setUp() {
        signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
    }

    // ==================== mine() Tests ====================

    @Test
    void mineCallsCorrectRpcMethod() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_mine"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        tester.mine();

        verify(provider).send(eq("anvil_mine"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mineDefaultsToOneBlock() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("anvil_mine"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        tester.mine();

        List<?> params = paramsCaptor.getValue();
        assertEquals(1, params.size());
        assertEquals("0x1", params.get(0));
    }

    // ==================== mine(long blocks) Tests ====================

    @Test
    void mineBlocksCallsCorrectRpcMethod() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_mine"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        tester.mine(10);

        verify(provider).send(eq("anvil_mine"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mineBlocksPassesCorrectParameters() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("anvil_mine"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        tester.mine(100);

        List<?> params = paramsCaptor.getValue();
        assertEquals(1, params.size());
        assertEquals("0x64", params.get(0)); // 100 in hex
    }

    @Test
    void mineBlocksThrowsOnRpcError() {
        JsonRpcError error = new JsonRpcError(-32000, "mine failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("anvil_mine"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        RpcException ex = assertThrows(
                RpcException.class,
                () -> tester.mine(10));
        assertEquals(-32000, ex.code());
    }

    // ==================== mine(long blocks, long intervalSeconds) Tests ====================

    @Test
    void mineWithIntervalCallsCorrectRpcMethod() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_mine"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        tester.mine(10, 12);

        verify(provider).send(eq("anvil_mine"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void mineWithIntervalPassesCorrectParameters() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("anvil_mine"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        tester.mine(10, 12);

        List<?> params = paramsCaptor.getValue();
        assertEquals(2, params.size());
        assertEquals("0xa", params.get(0)); // 10 in hex
        assertEquals("0xc", params.get(1)); // 12 in hex
    }

    @Test
    void mineWithIntervalThrowsOnRpcError() {
        JsonRpcError error = new JsonRpcError(-32000, "mine failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("anvil_mine"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        RpcException ex = assertThrows(
                RpcException.class,
                () -> tester.mine(10, 12));
        assertEquals(-32000, ex.code());
    }

    // ==================== getAutomine() Tests ====================

    @Test
    void getAutomineCallsCorrectRpcMethodForAnvil() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", Boolean.TRUE, null, "1");
        when(provider.send(eq("anvil_getAutomine"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        boolean result = tester.getAutomine();

        assertTrue(result);
        verify(provider).send(eq("anvil_getAutomine"), any());
    }

    @Test
    void getAutomineCallsCorrectRpcMethodForHardhat() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", Boolean.TRUE, null, "1");
        when(provider.send(eq("hardhat_getAutomine"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        boolean result = tester.getAutomine();

        assertTrue(result);
        verify(provider).send(eq("hardhat_getAutomine"), any());
    }

    @Test
    void getAutomineReturnsFalseWhenDisabled() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", Boolean.FALSE, null, "1");
        when(provider.send(eq("anvil_getAutomine"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        boolean result = tester.getAutomine();

        assertFalse(result);
    }

    @Test
    void getAutomineThrowsOnRpcError() {
        JsonRpcError error = new JsonRpcError(-32000, "getAutomine failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("anvil_getAutomine"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        RpcException ex = assertThrows(
                RpcException.class,
                () -> tester.getAutomine());
        assertEquals(-32000, ex.code());
    }

    @Test
    void getAutomineThrowsOnNullResult() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_getAutomine"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        RpcException ex = assertThrows(
                RpcException.class,
                () -> tester.getAutomine());
        assertEquals(-32000, ex.code());
        assertTrue(ex.getMessage().contains("returned null"));
    }

    // ==================== setAutomine() Tests ====================

    @Test
    void setAutomineCallsCorrectRpcMethodForAnvil() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("evm_setAutomine"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        tester.setAutomine(false);

        verify(provider).send(eq("evm_setAutomine"), any());
    }

    @Test
    void setAutomineCallsCorrectRpcMethodForHardhat() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("hardhat_setAutomine"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        tester.setAutomine(false);

        verify(provider).send(eq("hardhat_setAutomine"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void setAutominePassesCorrectParameters() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("evm_setAutomine"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        tester.setAutomine(false);

        List<?> params = paramsCaptor.getValue();
        assertEquals(1, params.size());
        assertEquals(Boolean.FALSE, params.get(0));
    }

    @Test
    void setAutomineThrowsOnRpcError() {
        JsonRpcError error = new JsonRpcError(-32000, "setAutomine failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("evm_setAutomine"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        RpcException ex = assertThrows(
                RpcException.class,
                () -> tester.setAutomine(false));
        assertEquals(-32000, ex.code());
    }

    // ==================== setIntervalMining() Tests ====================

    @Test
    void setIntervalMiningCallsCorrectRpcMethodForAnvil() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("evm_setIntervalMining"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        tester.setIntervalMining(12_000);

        verify(provider).send(eq("evm_setIntervalMining"), any());
    }

    @Test
    void setIntervalMiningCallsCorrectRpcMethodForHardhat() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("hardhat_setIntervalMining"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        tester.setIntervalMining(12_000);

        verify(provider).send(eq("hardhat_setIntervalMining"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void setIntervalMiningPassesCorrectParameters() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("evm_setIntervalMining"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        tester.setIntervalMining(12_000);

        List<?> params = paramsCaptor.getValue();
        assertEquals(1, params.size());
        assertEquals(12_000L, params.get(0));
    }

    @Test
    void setIntervalMiningThrowsOnRpcError() {
        JsonRpcError error = new JsonRpcError(-32000, "setIntervalMining failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("evm_setIntervalMining"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        RpcException ex = assertThrows(
                RpcException.class,
                () -> tester.setIntervalMining(12_000));
        assertEquals(-32000, ex.code());
    }

    // ==================== Test Node Mode Tests ====================

    @Test
    void mineCallsHardhatPrefix() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("hardhat_mine"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        tester.mine(10);

        verify(provider).send(eq("hardhat_mine"), any());
    }

    @Test
    void mineCallsGanachePrefix() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("evm_mine"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.GANACHE)
                .buildTester();

        tester.mine(10);

        verify(provider).send(eq("evm_mine"), any());
    }

    @Test
    void mineWithIntervalCallsHardhatPrefix() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("hardhat_mine"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        tester.mine(10, 12);

        verify(provider).send(eq("hardhat_mine"), any());
    }
}
