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
import sh.brane.core.types.Hash;

/**
 * Unit tests for {@link DefaultTester} transaction pool methods.
 */
@ExtendWith(MockitoExtension.class)
class DefaultTesterTransactionPoolTest {

    @Mock
    private BraneProvider provider;

    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private static final Hash TEST_TX_HASH =
            new Hash("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");

    private Signer signer;

    @BeforeEach
    void setUp() {
        signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
    }

    // ==================== dropTransaction() Tests ====================

    @Test
    void dropTransactionThrowsOnNullTxHash() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        NullPointerException ex = assertThrows(
                NullPointerException.class,
                () -> tester.dropTransaction(null));
        assertEquals("txHash must not be null", ex.getMessage());
    }

    @Test
    void dropTransactionCallsCorrectRpcMethod() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", Boolean.TRUE, null, "1");
        when(provider.send(eq("anvil_dropTransaction"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        boolean result = tester.dropTransaction(TEST_TX_HASH);

        verify(provider).send(eq("anvil_dropTransaction"), any());
        assertTrue(result);
    }

    @Test
    @SuppressWarnings("unchecked")
    void dropTransactionPassesCorrectParameters() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", Boolean.TRUE, null, "1");
        ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
        when(provider.send(eq("anvil_dropTransaction"), paramsCaptor.capture())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        tester.dropTransaction(TEST_TX_HASH);

        List<?> params = paramsCaptor.getValue();
        assertEquals(1, params.size());
        assertEquals(TEST_TX_HASH.value(), params.get(0));
    }

    @Test
    void dropTransactionReturnsFalseOnRpcError() {
        JsonRpcError error = new JsonRpcError(-32000, "dropTransaction failed", null);
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
        when(provider.send(eq("anvil_dropTransaction"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        boolean result = tester.dropTransaction(TEST_TX_HASH);

        assertFalse(result);
    }

    @Test
    void dropTransactionReturnsFalseOnNullResult() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("anvil_dropTransaction"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        boolean result = tester.dropTransaction(TEST_TX_HASH);

        assertFalse(result);
    }

    @Test
    void dropTransactionReturnsFalseOnFalseResult() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", Boolean.FALSE, null, "1");
        when(provider.send(eq("anvil_dropTransaction"), any())).thenReturn(response);

        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();

        boolean result = tester.dropTransaction(TEST_TX_HASH);

        assertFalse(result);
    }

    @Test
    void dropTransactionThrowsOnNonAnvilMode() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.HARDHAT)
                .buildTester();

        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> tester.dropTransaction(TEST_TX_HASH));
        assertTrue(ex.getMessage().contains("only supported by Anvil"));
    }

    @Test
    void dropTransactionThrowsOnGanacheMode() {
        Brane.Tester tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.GANACHE)
                .buildTester();

        UnsupportedOperationException ex = assertThrows(
                UnsupportedOperationException.class,
                () -> tester.dropTransaction(TEST_TX_HASH));
        assertTrue(ex.getMessage().contains("only supported by Anvil"));
    }
}
