// SPDX-License-Identifier: MIT OR Apache-2.0
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
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

/**
 * Unit tests for {@link DefaultTester} account manipulation methods.
 */
@ExtendWith(MockitoExtension.class)
class DefaultTesterAccountManipulationTest {

    @Mock
    private BraneProvider provider;

    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private static final Address TEST_ADDRESS =
            new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");

    private static final Hash TEST_SLOT =
            new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");

    private static final Hash TEST_VALUE =
            new Hash("0x000000000000000000000000000000000000000000000000000000000000002a");

    private Signer signer;

    @BeforeEach
    void setUp() {
        signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
    }

    // ==================== setBalance() Tests ====================

    @Test
    void setBalanceThrowsOnNullAddress() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> tester.setBalance(null, Wei.fromEther(java.math.BigDecimal.ONE)));
        assertEquals("address must not be null", ex.getMessage());
    }

    @Test
    void setBalanceThrowsOnNullBalance() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> tester.setBalance(TEST_ADDRESS, null));
        assertEquals("balance must not be null", ex.getMessage());
    }

    @Test
    void setBalanceCallsCorrectRpcMethod() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_setBalance"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        tester.setBalance(TEST_ADDRESS, Wei.fromEther(java.math.BigDecimal.ONE));

        verify(provider).send(eq("anvil_setBalance"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void setBalancePassesCorrectParameters() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("anvil_setBalance"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        Wei balance = Wei.fromEther(java.math.BigDecimal.ONE);
        tester.setBalance(TEST_ADDRESS, balance);

        List<?> params = paramsCaptor.getValue();
        assertEquals(2, params.size());
        assertEquals(TEST_ADDRESS.value(), params.get(0));
        assertEquals("0x" + balance.value().toString(16), params.get(1));
    }

    @Test
    void setBalanceThrowsOnRpcError() {
        JsonRpcError error = new JsonRpcError(-32000, "setBalance failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("anvil_setBalance"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        RpcException ex = assertThrows(
                RpcException.class,
                () -> tester.setBalance(TEST_ADDRESS, Wei.fromEther(java.math.BigDecimal.ONE)));
        assertEquals(-32000, ex.code());
    }

    // ==================== setCode() Tests ====================

    @Test
    void setCodeThrowsOnNullAddress() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> tester.setCode(null, new HexData("0x60806040")));
        assertEquals("address must not be null", ex.getMessage());
    }

    @Test
    void setCodeThrowsOnNullCode() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> tester.setCode(TEST_ADDRESS, null));
        assertEquals("code must not be null", ex.getMessage());
    }

    @Test
    void setCodeCallsCorrectRpcMethod() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_setCode"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        tester.setCode(TEST_ADDRESS, new HexData("0x60806040"));

        verify(provider).send(eq("anvil_setCode"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void setCodePassesCorrectParameters() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("anvil_setCode"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        HexData code = new HexData("0x60806040");
        tester.setCode(TEST_ADDRESS, code);

        List<?> params = paramsCaptor.getValue();
        assertEquals(2, params.size());
        assertEquals(TEST_ADDRESS.value(), params.get(0));
        assertEquals(code.value(), params.get(1));
    }

    @Test
    void setCodeThrowsOnRpcError() {
        JsonRpcError error = new JsonRpcError(-32000, "setCode failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("anvil_setCode"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        RpcException ex = assertThrows(
                RpcException.class,
                () -> tester.setCode(TEST_ADDRESS, new HexData("0x60806040")));
        assertEquals(-32000, ex.code());
    }

    // ==================== setNonce() Tests ====================

    @Test
    void setNonceThrowsOnNullAddress() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> tester.setNonce(null, 42));
        assertEquals("address must not be null", ex.getMessage());
    }

    @Test
    void setNonceCallsCorrectRpcMethod() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_setNonce"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        tester.setNonce(TEST_ADDRESS, 42);

        verify(provider).send(eq("anvil_setNonce"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void setNoncePassesCorrectParameters() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("anvil_setNonce"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        tester.setNonce(TEST_ADDRESS, 42);

        List<?> params = paramsCaptor.getValue();
        assertEquals(2, params.size());
        assertEquals(TEST_ADDRESS.value(), params.get(0));
        assertEquals("0x2a", params.get(1));
    }

    @Test
    void setNonceThrowsOnRpcError() {
        JsonRpcError error = new JsonRpcError(-32000, "setNonce failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("anvil_setNonce"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        RpcException ex = assertThrows(
                RpcException.class,
                () -> tester.setNonce(TEST_ADDRESS, 42));
        assertEquals(-32000, ex.code());
    }

    // ==================== setStorageAt() Tests ====================

    @Test
    void setStorageAtThrowsOnNullAddress() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> tester.setStorageAt(null, TEST_SLOT, TEST_VALUE));
        assertEquals("address must not be null", ex.getMessage());
    }

    @Test
    void setStorageAtThrowsOnNullSlot() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> tester.setStorageAt(TEST_ADDRESS, null, TEST_VALUE));
        assertEquals("slot must not be null", ex.getMessage());
    }

    @Test
    void setStorageAtThrowsOnNullValue() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> tester.setStorageAt(TEST_ADDRESS, TEST_SLOT, null));
        assertEquals("value must not be null", ex.getMessage());
    }

    @Test
    void setStorageAtCallsCorrectRpcMethod() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_setStorageAt"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        tester.setStorageAt(TEST_ADDRESS, TEST_SLOT, TEST_VALUE);

        verify(provider).send(eq("anvil_setStorageAt"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void setStorageAtPassesCorrectParameters() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("anvil_setStorageAt"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        tester.setStorageAt(TEST_ADDRESS, TEST_SLOT, TEST_VALUE);

        List<?> params = paramsCaptor.getValue();
        assertEquals(3, params.size());
        assertEquals(TEST_ADDRESS.value(), params.get(0));
        assertEquals(TEST_SLOT.value(), params.get(1));
        assertEquals(TEST_VALUE.value(), params.get(2));
    }

    @Test
    void setStorageAtThrowsOnRpcError() {
        JsonRpcError error = new JsonRpcError(-32000, "setStorageAt failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("anvil_setStorageAt"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        RpcException ex = assertThrows(
                RpcException.class,
                () -> tester.setStorageAt(TEST_ADDRESS, TEST_SLOT, TEST_VALUE));
        assertEquals(-32000, ex.code());
    }

    // ==================== Test Node Mode Tests ====================

    @Test
    void setBalanceCallsHardhatPrefix() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("hardhat_setBalance"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        tester.setBalance(TEST_ADDRESS, Wei.fromEther(java.math.BigDecimal.ONE));

        verify(provider).send(eq("hardhat_setBalance"), any());
    }

    @Test
    void setCodeCallsGanachePrefix() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("evm_setCode"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.GANACHE)
                .buildTester();

        tester.setCode(TEST_ADDRESS, new HexData("0x60806040"));

        verify(provider).send(eq("evm_setCode"), any());
    }
}
