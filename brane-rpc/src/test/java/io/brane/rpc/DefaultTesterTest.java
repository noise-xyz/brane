package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

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
import io.brane.core.error.RpcException;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

/**
 * Unit tests for {@link DefaultTester} parameter validation with mock provider.
 *
 * <p>This test class focuses on testing parameter validation and null checks
 * across DefaultTester methods to ensure proper defensive programming.
 */
@ExtendWith(MockitoExtension.class)
class DefaultTesterTest {

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

    private static final Hash TEST_TX_HASH =
            new Hash("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef");

    private static final HexData TEST_CODE = new HexData("0x60806040");
    private static final HexData TEST_STATE = new HexData("0x1f8b08000000000000ff");

    private Signer signer;

    @BeforeEach
    void setUp() {
        signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
    }

    private Brane.Tester createTester() {
        return Brane.builder()
                .provider(provider)
                .signer(signer)
                .buildTester();
    }

    private Brane.Tester createTester(TestNodeMode mode) {
        return Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(mode)
                .buildTester();
    }

    // ==================== setBalance() Parameter Validation ====================

    @Nested
    @DisplayName("setBalance parameter validation")
    class SetBalanceValidation {

        @Test
        void throwsOnNullAddress() {
            Brane.Tester tester = createTester();

            NullPointerException ex = assertThrows(
                    NullPointerException.class,
                    () -> tester.setBalance(null, Wei.fromEther(java.math.BigDecimal.ONE)));
            assertEquals("address must not be null", ex.getMessage());
        }

        @Test
        void throwsOnNullBalance() {
            Brane.Tester tester = createTester();

            NullPointerException ex = assertThrows(
                    NullPointerException.class,
                    () -> tester.setBalance(TEST_ADDRESS, null));
            assertEquals("balance must not be null", ex.getMessage());
        }
    }

    // ==================== setCode() Parameter Validation ====================

    @Nested
    @DisplayName("setCode parameter validation")
    class SetCodeValidation {

        @Test
        void throwsOnNullAddress() {
            Brane.Tester tester = createTester();

            NullPointerException ex = assertThrows(
                    NullPointerException.class,
                    () -> tester.setCode(null, TEST_CODE));
            assertEquals("address must not be null", ex.getMessage());
        }

        @Test
        void throwsOnNullCode() {
            Brane.Tester tester = createTester();

            NullPointerException ex = assertThrows(
                    NullPointerException.class,
                    () -> tester.setCode(TEST_ADDRESS, null));
            assertEquals("code must not be null", ex.getMessage());
        }
    }

    // ==================== setNonce() Parameter Validation ====================

    @Nested
    @DisplayName("setNonce parameter validation")
    class SetNonceValidation {

        @Test
        void throwsOnNullAddress() {
            Brane.Tester tester = createTester();

            NullPointerException ex = assertThrows(
                    NullPointerException.class,
                    () -> tester.setNonce(null, 42));
            assertEquals("address must not be null", ex.getMessage());
        }
    }

    // ==================== setStorageAt() Parameter Validation ====================

    @Nested
    @DisplayName("setStorageAt parameter validation")
    class SetStorageAtValidation {

        @Test
        void throwsOnNullAddress() {
            Brane.Tester tester = createTester();

            NullPointerException ex = assertThrows(
                    NullPointerException.class,
                    () -> tester.setStorageAt(null, TEST_SLOT, TEST_VALUE));
            assertEquals("address must not be null", ex.getMessage());
        }

        @Test
        void throwsOnNullSlot() {
            Brane.Tester tester = createTester();

            NullPointerException ex = assertThrows(
                    NullPointerException.class,
                    () -> tester.setStorageAt(TEST_ADDRESS, null, TEST_VALUE));
            assertEquals("slot must not be null", ex.getMessage());
        }

        @Test
        void throwsOnNullValue() {
            Brane.Tester tester = createTester();

            NullPointerException ex = assertThrows(
                    NullPointerException.class,
                    () -> tester.setStorageAt(TEST_ADDRESS, TEST_SLOT, null));
            assertEquals("value must not be null", ex.getMessage());
        }
    }

    // ==================== setBlockGasLimit() Parameter Validation ====================

    @Nested
    @DisplayName("setBlockGasLimit parameter validation")
    class SetBlockGasLimitValidation {

        @Test
        void throwsOnNullGasLimit() {
            Brane.Tester tester = createTester();

            NullPointerException ex = assertThrows(
                    NullPointerException.class,
                    () -> tester.setBlockGasLimit(null));
            assertEquals("gasLimit must not be null", ex.getMessage());
        }
    }

    // ==================== loadState() Parameter Validation ====================

    @Nested
    @DisplayName("loadState parameter validation")
    class LoadStateValidation {

        @Test
        void throwsOnNullState() {
            Brane.Tester tester = createTester();

            NullPointerException ex = assertThrows(
                    NullPointerException.class,
                    () -> tester.loadState(null));
            assertEquals("state must not be null", ex.getMessage());
        }
    }

    // ==================== dropTransaction() Parameter Validation ====================

    @Nested
    @DisplayName("dropTransaction parameter validation")
    class DropTransactionValidation {

        @Test
        void throwsOnNullTxHash() {
            Brane.Tester tester = createTester();

            NullPointerException ex = assertThrows(
                    NullPointerException.class,
                    () -> tester.dropTransaction(null));
            assertEquals("txHash must not be null", ex.getMessage());
        }
    }

    // ==================== Snapshot/Revert Parameter Validation ====================

    @Nested
    @DisplayName("revert parameter validation")
    class RevertValidation {

        @Test
        @SuppressWarnings("unchecked")
        void passesCorrectSnapshotIdToRpc() {
            JsonRpcResponse response = new JsonRpcResponse("2.0", Boolean.TRUE, null, "1");
            ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            when(provider.send(eq("evm_revert"), paramsCaptor.capture())).thenReturn(response);

            Brane.Tester tester = createTester();
            SnapshotId snapshotId = SnapshotId.from("0x1");

            boolean result = tester.revert(snapshotId);

            assertTrue(result);
            List<?> params = paramsCaptor.getValue();
            assertEquals(1, params.size());
            assertEquals("0x1", params.get(0));
        }

        @Test
        void returnsFalseOnRpcError() {
            JsonRpcError error = new JsonRpcError(-32000, "revert failed", null);
            JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
            when(provider.send(eq("evm_revert"), any())).thenReturn(response);

            Brane.Tester tester = createTester();

            boolean result = tester.revert(SnapshotId.from("0x1"));

            assertFalse(result);
        }
    }

    // ==================== Impersonate Parameter Validation ====================

    @Nested
    @DisplayName("impersonate parameter validation")
    class ImpersonateValidation {

        @Test
        void callsCorrectRpcMethod() {
            JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
            when(provider.send(eq("anvil_impersonateAccount"), any())).thenReturn(response);

            Brane.Tester tester = createTester(TestNodeMode.ANVIL);

            ImpersonationSession session = tester.impersonate(TEST_ADDRESS);

            assertNotNull(session);
            assertEquals(TEST_ADDRESS, session.address());
            verify(provider).send(eq("anvil_impersonateAccount"), eq(List.of(TEST_ADDRESS.value())));
        }

        @Test
        void throwsOnRpcError() {
            JsonRpcError error = new JsonRpcError(-32000, "impersonate failed", null);
            JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
            when(provider.send(eq("anvil_impersonateAccount"), any())).thenReturn(response);

            Brane.Tester tester = createTester(TestNodeMode.ANVIL);

            RpcException ex = assertThrows(
                    RpcException.class,
                    () -> tester.impersonate(TEST_ADDRESS));
            assertEquals(-32000, ex.code());
        }
    }

    // ==================== stopImpersonating Parameter Validation ====================

    @Nested
    @DisplayName("stopImpersonating parameter validation")
    class StopImpersonatingValidation {

        @Test
        void callsCorrectRpcMethod() {
            JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
            when(provider.send(eq("anvil_stopImpersonatingAccount"), any())).thenReturn(response);

            Brane.Tester tester = createTester(TestNodeMode.ANVIL);

            tester.stopImpersonating(TEST_ADDRESS);

            verify(provider).send(eq("anvil_stopImpersonatingAccount"), eq(List.of(TEST_ADDRESS.value())));
        }

        @Test
        void throwsOnRpcError() {
            JsonRpcError error = new JsonRpcError(-32000, "stopImpersonating failed", null);
            JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
            when(provider.send(eq("anvil_stopImpersonatingAccount"), any())).thenReturn(response);

            Brane.Tester tester = createTester(TestNodeMode.ANVIL);

            RpcException ex = assertThrows(
                    RpcException.class,
                    () -> tester.stopImpersonating(TEST_ADDRESS));
            assertEquals(-32000, ex.code());
        }
    }

    // ==================== setCoinbase Parameter Validation ====================

    @Nested
    @DisplayName("setCoinbase parameter validation")
    class SetCoinbaseValidation {

        @Test
        void setCoinbaseWithNullThrowsNullPointerException() {
            Brane.Tester tester = createTester();

            NullPointerException ex = assertThrows(
                    NullPointerException.class,
                    () -> tester.setCoinbase(null));
            assertEquals("coinbase", ex.getMessage());
        }

        @Test
        void callsCorrectRpcMethod() {
            JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
            when(provider.send(eq("anvil_setCoinbase"), any())).thenReturn(response);

            Brane.Tester tester = createTester(TestNodeMode.ANVIL);

            tester.setCoinbase(TEST_ADDRESS);

            verify(provider).send(eq("anvil_setCoinbase"), eq(List.of(TEST_ADDRESS.value())));
        }

        @Test
        void throwsOnRpcError() {
            JsonRpcError error = new JsonRpcError(-32000, "setCoinbase failed", null);
            JsonRpcResponse response = new JsonRpcResponse("2.0", null, error, "1");
            when(provider.send(eq("anvil_setCoinbase"), any())).thenReturn(response);

            Brane.Tester tester = createTester(TestNodeMode.ANVIL);

            RpcException ex = assertThrows(
                    RpcException.class,
                    () -> tester.setCoinbase(TEST_ADDRESS));
            assertEquals(-32000, ex.code());
        }
    }

    // ==================== setNextBlockBaseFee Parameter Validation ====================

    @Nested
    @DisplayName("setNextBlockBaseFee parameter validation")
    class SetNextBlockBaseFeeValidation {

        @Test
        @SuppressWarnings("unchecked")
        void passesCorrectParameters() {
            JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
            ArgumentCaptor<List<?>> paramsCaptor = ArgumentCaptor.forClass(List.class);
            when(provider.send(eq("anvil_setNextBlockBaseFeePerGas"), paramsCaptor.capture())).thenReturn(response);

            Brane.Tester tester = createTester(TestNodeMode.ANVIL);

            Wei baseFee = Wei.of(1_000_000_000L); // 1 gwei
            tester.setNextBlockBaseFee(baseFee);

            List<?> params = paramsCaptor.getValue();
            assertEquals(1, params.size());
            assertEquals("0x3b9aca00", params.get(0));
        }
    }

    // ==================== Anvil-only Feature Validation ====================

    @Nested
    @DisplayName("Anvil-only feature validation")
    class AnvilOnlyFeatureValidation {

        @Test
        void enableAutoImpersonateThrowsOnHardhat() {
            Brane.Tester tester = createTester(TestNodeMode.HARDHAT);

            UnsupportedOperationException ex = assertThrows(
                    UnsupportedOperationException.class,
                    () -> tester.enableAutoImpersonate());
            assertTrue(ex.getMessage().contains("only supported by Anvil"));
        }

        @Test
        void disableAutoImpersonateThrowsOnHardhat() {
            Brane.Tester tester = createTester(TestNodeMode.HARDHAT);

            UnsupportedOperationException ex = assertThrows(
                    UnsupportedOperationException.class,
                    () -> tester.disableAutoImpersonate());
            assertTrue(ex.getMessage().contains("only supported by Anvil"));
        }

        @Test
        void dumpStateThrowsOnHardhat() {
            Brane.Tester tester = createTester(TestNodeMode.HARDHAT);

            UnsupportedOperationException ex = assertThrows(
                    UnsupportedOperationException.class,
                    () -> tester.dumpState());
            assertTrue(ex.getMessage().contains("only supported by Anvil"));
        }

        @Test
        void loadStateThrowsOnHardhat() {
            Brane.Tester tester = createTester(TestNodeMode.HARDHAT);

            UnsupportedOperationException ex = assertThrows(
                    UnsupportedOperationException.class,
                    () -> tester.loadState(TEST_STATE));
            assertTrue(ex.getMessage().contains("only supported by Anvil"));
        }

        @Test
        void dropTransactionThrowsOnHardhat() {
            Brane.Tester tester = createTester(TestNodeMode.HARDHAT);

            UnsupportedOperationException ex = assertThrows(
                    UnsupportedOperationException.class,
                    () -> tester.dropTransaction(TEST_TX_HASH));
            assertTrue(ex.getMessage().contains("only supported by Anvil"));
        }
    }

    // ==================== TestNodeMode Prefix Validation ====================

    @Nested
    @DisplayName("TestNodeMode prefix validation")
    class TestNodeModePrefixValidation {

        @Test
        void anvilUsesAnvilPrefix() {
            JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
            when(provider.send(eq("anvil_setBalance"), any())).thenReturn(response);

            Brane.Tester tester = createTester(TestNodeMode.ANVIL);
            tester.setBalance(TEST_ADDRESS, Wei.of(1));

            verify(provider).send(eq("anvil_setBalance"), any());
        }

        @Test
        void hardhatUsesHardhatPrefix() {
            JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
            when(provider.send(eq("hardhat_setBalance"), any())).thenReturn(response);

            Brane.Tester tester = createTester(TestNodeMode.HARDHAT);
            tester.setBalance(TEST_ADDRESS, Wei.of(1));

            verify(provider).send(eq("hardhat_setBalance"), any());
        }

        @Test
        void ganacheUsesEvmPrefix() {
            JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
            when(provider.send(eq("evm_setBalance"), any())).thenReturn(response);

            Brane.Tester tester = createTester(TestNodeMode.GANACHE);
            tester.setBalance(TEST_ADDRESS, Wei.of(1));

            verify(provider).send(eq("evm_setBalance"), any());
        }
    }
}
