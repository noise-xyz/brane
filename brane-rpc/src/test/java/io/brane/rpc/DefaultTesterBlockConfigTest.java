package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
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
import io.brane.core.types.Wei;

/**
 * Unit tests for {@link DefaultTester} block configuration methods.
 */
@ExtendWith(MockitoExtension.class)
class DefaultTesterBlockConfigTest {

    @Mock
    private BraneProvider provider;

    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private Signer signer;

    @BeforeEach
    void setUp() {
        signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
    }

    // ==================== setNextBlockBaseFee Tests ====================

    @Test
    void setNextBlockBaseFeeCallsCorrectRpcMethodForAnvil() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_setNextBlockBaseFeePerGas"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        tester.setNextBlockBaseFee(Wei.of(1_000_000_000L)); // 1 gwei

        verify(provider).send(eq("anvil_setNextBlockBaseFeePerGas"), any());
    }

    @Test
    void setNextBlockBaseFeeCallsCorrectRpcMethodForHardhat() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("hardhat_setNextBlockBaseFeePerGas"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        tester.setNextBlockBaseFee(Wei.of(1_000_000_000L)); // 1 gwei

        verify(provider).send(eq("hardhat_setNextBlockBaseFeePerGas"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void setNextBlockBaseFeePassesCorrectParameters() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("anvil_setNextBlockBaseFeePerGas"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        tester.setNextBlockBaseFee(Wei.of(1_000_000_000L)); // 1 gwei = 0x3b9aca00

        List<?> params = paramsCaptor.getValue();
        assertEquals(1, params.size());
        assertEquals("0x3b9aca00", params.get(0));
    }

    @Test
    void setNextBlockBaseFeeThrowsOnRpcError() {
        JsonRpcError error = new JsonRpcError(-32000, "setNextBlockBaseFee failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("anvil_setNextBlockBaseFeePerGas"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        RpcException ex = assertThrows(
                RpcException.class,
                () -> tester.setNextBlockBaseFee(Wei.of(1_000_000_000L)));
        assertEquals(-32000, ex.code());
    }

    // ==================== setBlockGasLimit Tests ====================

    @Test
    void setBlockGasLimitCallsCorrectRpcMethodForAnvil() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_setBlockGasLimit"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        tester.setBlockGasLimit(BigInteger.valueOf(30_000_000L));

        verify(provider).send(eq("anvil_setBlockGasLimit"), any());
    }

    @Test
    void setBlockGasLimitCallsCorrectRpcMethodForHardhat() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("hardhat_setBlockGasLimit"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        tester.setBlockGasLimit(BigInteger.valueOf(30_000_000L));

        verify(provider).send(eq("hardhat_setBlockGasLimit"), any());
    }

    @Test
    void setBlockGasLimitCallsCorrectRpcMethodForGanache() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("evm_setBlockGasLimit"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.GANACHE)
                .buildTester();

        tester.setBlockGasLimit(BigInteger.valueOf(30_000_000L));

        verify(provider).send(eq("evm_setBlockGasLimit"), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void setBlockGasLimitPassesCorrectParameters() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("anvil_setBlockGasLimit"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        tester.setBlockGasLimit(BigInteger.valueOf(30_000_000L)); // 30M = 0x1c9c380

        List<?> params = paramsCaptor.getValue();
        assertEquals(1, params.size());
        assertEquals("0x1c9c380", params.get(0));
    }

    @Test
    @SuppressWarnings("unchecked")
    void setBlockGasLimitPassesCorrectParametersForLargeValues() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("anvil_setBlockGasLimit"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        // Test with a large value (100 billion)
        BigInteger largeGasLimit = new BigInteger("100000000000");
        tester.setBlockGasLimit(largeGasLimit);

        List<?> params = paramsCaptor.getValue();
        assertEquals(1, params.size());
        assertEquals("0x174876e800", params.get(0));
    }

    @Test
    void setBlockGasLimitThrowsOnRpcError() {
        JsonRpcError error = new JsonRpcError(-32000, "setBlockGasLimit failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("anvil_setBlockGasLimit"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        RpcException ex = assertThrows(
                RpcException.class,
                () -> tester.setBlockGasLimit(BigInteger.valueOf(30_000_000L)));
        assertEquals(-32000, ex.code());
    }

    @Test
    void setBlockGasLimitThrowsOnNullGasLimit() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        assertThrows(
                NullPointerException.class,
                () -> tester.setBlockGasLimit(null));
    }
}
