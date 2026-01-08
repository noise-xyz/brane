package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.internal.RpcUtils;

/**
 * Integration tests for {@link Brane.Tester} account manipulation methods against Anvil.
 *
 * <p>Requires Anvil running:
 * <pre>
 * anvil
 * </pre>
 *
 * <p>Run with:
 * <pre>
 * ./gradlew :brane-rpc:test -Dbrane.integration.tests=true --tests "*TesterIntegrationTest"
 * </pre>
 */
@EnabledIfSystemProperty(named = "brane.integration.tests", matches = "true")
class TesterIntegrationTest {

    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private static final Address TEST_ADDRESS =
            new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");

    private static Brane.Tester tester;
    private static BraneProvider provider;
    private SnapshotId snapshot;

    @BeforeAll
    static void setupClient() {
        String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        provider = HttpBraneProvider.builder(rpcUrl).build();
        tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();
    }

    @BeforeEach
    void createSnapshot() {
        snapshot = tester.snapshot();
    }

    @org.junit.jupiter.api.AfterEach
    void revertSnapshot() {
        if (snapshot != null) {
            tester.revert(snapshot);
        }
    }

    // ==================== Helper Methods for Raw RPC Calls ====================

    /**
     * Gets bytecode at an address using eth_getCode.
     */
    private static HexData getCode(Address address) {
        JsonRpcResponse response = provider.send("eth_getCode", List.of(address.value(), "latest"));
        Object result = response.result();
        if (result == null) {
            return new HexData("0x");
        }
        return new HexData(result.toString());
    }

    /**
     * Gets the nonce of an address using eth_getTransactionCount.
     */
    private static BigInteger getNonce(Address address) {
        JsonRpcResponse response = provider.send("eth_getTransactionCount", List.of(address.value(), "latest"));
        Object result = response.result();
        if (result == null) {
            return BigInteger.ZERO;
        }
        return RpcUtils.decodeHexBigInteger(result.toString());
    }

    /**
     * Gets storage at a slot using eth_getStorageAt.
     */
    private static Hash getStorageAt(Address address, Hash slot) {
        JsonRpcResponse response = provider.send("eth_getStorageAt", List.of(address.value(), slot.value(), "latest"));
        Object result = response.result();
        if (result == null) {
            return new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");
        }
        return new Hash(result.toString());
    }

    // ==================== setBalance() Integration Tests ====================

    @Nested
    @DisplayName("setBalance integration tests")
    class SetBalanceTests {

        @Test
        @DisplayName("setBalance changes account balance")
        void setBalanceChangesAccountBalance() {
            Wei newBalance = Wei.fromEther(new java.math.BigDecimal("12345"));

            tester.setBalance(TEST_ADDRESS, newBalance);

            BigInteger actualBalance = tester.getBalance(TEST_ADDRESS);
            assertEquals(newBalance.value(), actualBalance);
        }

        @Test
        @DisplayName("setBalance can set balance to zero")
        void setBalanceCanSetToZero() {
            tester.setBalance(TEST_ADDRESS, Wei.of(0));

            BigInteger actualBalance = tester.getBalance(TEST_ADDRESS);
            assertEquals(BigInteger.ZERO, actualBalance);
        }

        @Test
        @DisplayName("setBalance can set very large balance")
        void setBalanceCanSetLargeBalance() {
            // 1 million ETH
            Wei largeBalance = Wei.fromEther(new java.math.BigDecimal("1000000"));

            tester.setBalance(TEST_ADDRESS, largeBalance);

            BigInteger actualBalance = tester.getBalance(TEST_ADDRESS);
            assertEquals(largeBalance.value(), actualBalance);
        }
    }

    // ==================== setCode() Integration Tests ====================

    @Nested
    @DisplayName("setCode integration tests")
    class SetCodeTests {

        // Simple bytecode that returns 42 when called: PUSH1 0x2a PUSH1 0x00 MSTORE PUSH1 0x20 PUSH1 0x00 RETURN
        private static final HexData SIMPLE_CODE = new HexData("0x602a60005260206000f3");

        @Test
        @DisplayName("setCode deploys code at address")
        void setCodeDeploysCodeAtAddress() {
            tester.setCode(TEST_ADDRESS, SIMPLE_CODE);

            HexData actualCode = getCode(TEST_ADDRESS);
            assertEquals(SIMPLE_CODE.value(), actualCode.value());
        }

        @Test
        @DisplayName("setCode can set empty code")
        void setCodeCanSetEmptyCode() {
            // First set some code
            tester.setCode(TEST_ADDRESS, SIMPLE_CODE);

            // Then clear it
            tester.setCode(TEST_ADDRESS, new HexData("0x"));

            HexData actualCode = getCode(TEST_ADDRESS);
            assertEquals("0x", actualCode.value());
        }

        @Test
        @DisplayName("setCode replaces existing code")
        void setCodeReplacesExistingCode() {
            HexData code1 = new HexData("0x6001");
            HexData code2 = new HexData("0x6002");

            tester.setCode(TEST_ADDRESS, code1);
            tester.setCode(TEST_ADDRESS, code2);

            HexData actualCode = getCode(TEST_ADDRESS);
            assertEquals(code2.value(), actualCode.value());
        }
    }

    // ==================== setNonce() Integration Tests ====================

    @Nested
    @DisplayName("setNonce integration tests")
    class SetNonceTests {

        @Test
        @DisplayName("setNonce changes account nonce")
        void setNonceChangesAccountNonce() {
            long newNonce = 42;

            tester.setNonce(TEST_ADDRESS, newNonce);

            BigInteger actualNonce = getNonce(TEST_ADDRESS);
            assertEquals(BigInteger.valueOf(newNonce), actualNonce);
        }

        @Test
        @DisplayName("setNonce can set to zero")
        void setNonceCanSetToZero() {
            // First set a non-zero nonce
            tester.setNonce(TEST_ADDRESS, 100);

            // Then set it back to zero
            tester.setNonce(TEST_ADDRESS, 0);

            BigInteger actualNonce = getNonce(TEST_ADDRESS);
            assertEquals(BigInteger.ZERO, actualNonce);
        }

        @Test
        @DisplayName("setNonce can set large nonce")
        void setNonceCanSetLargeNonce() {
            long largeNonce = 999_999_999L;

            tester.setNonce(TEST_ADDRESS, largeNonce);

            BigInteger actualNonce = getNonce(TEST_ADDRESS);
            assertEquals(BigInteger.valueOf(largeNonce), actualNonce);
        }
    }

    // ==================== setStorageAt() Integration Tests ====================

    @Nested
    @DisplayName("setStorageAt integration tests")
    class SetStorageAtTests {

        private static final Hash SLOT_0 =
                new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");

        private static final Hash VALUE_42 =
                new Hash("0x000000000000000000000000000000000000000000000000000000000000002a");

        @Test
        @DisplayName("setStorageAt sets storage value")
        void setStorageAtSetsStorageValue() {
            // First deploy some code so the address is a contract
            tester.setCode(TEST_ADDRESS, new HexData("0x60016000"));

            tester.setStorageAt(TEST_ADDRESS, SLOT_0, VALUE_42);

            Hash actualValue = getStorageAt(TEST_ADDRESS, SLOT_0);
            assertEquals(VALUE_42.value(), actualValue.value());
        }

        @Test
        @DisplayName("setStorageAt can set to zero")
        void setStorageAtCanSetToZero() {
            tester.setCode(TEST_ADDRESS, new HexData("0x60016000"));

            // First set a non-zero value
            tester.setStorageAt(TEST_ADDRESS, SLOT_0, VALUE_42);

            // Then clear it
            Hash zeroValue = new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");
            tester.setStorageAt(TEST_ADDRESS, SLOT_0, zeroValue);

            Hash actualValue = getStorageAt(TEST_ADDRESS, SLOT_0);
            assertEquals(zeroValue.value(), actualValue.value());
        }

        @Test
        @DisplayName("setStorageAt works with different slots")
        void setStorageAtWorksWithDifferentSlots() {
            tester.setCode(TEST_ADDRESS, new HexData("0x60016000"));

            Hash slot1 = new Hash("0x0000000000000000000000000000000000000000000000000000000000000001");
            Hash slot5 = new Hash("0x0000000000000000000000000000000000000000000000000000000000000005");
            Hash value1 = new Hash("0x0000000000000000000000000000000000000000000000000000000000000011");
            Hash value5 = new Hash("0x0000000000000000000000000000000000000000000000000000000000000055");

            tester.setStorageAt(TEST_ADDRESS, slot1, value1);
            tester.setStorageAt(TEST_ADDRESS, slot5, value5);

            assertEquals(value1.value(), getStorageAt(TEST_ADDRESS, slot1).value());
            assertEquals(value5.value(), getStorageAt(TEST_ADDRESS, slot5).value());
        }
    }

    // ==================== Combined Operations Tests ====================

    @Nested
    @DisplayName("Combined account manipulation tests")
    class CombinedTests {

        @Test
        @DisplayName("setBalance and setNonce work together")
        void setBalanceAndNonceWorkTogether() {
            Wei balance = Wei.fromEther(new java.math.BigDecimal("100"));
            long nonce = 50;

            tester.setBalance(TEST_ADDRESS, balance);
            tester.setNonce(TEST_ADDRESS, nonce);

            assertEquals(balance.value(), tester.getBalance(TEST_ADDRESS));
            assertEquals(BigInteger.valueOf(nonce), getNonce(TEST_ADDRESS));
        }

        @Test
        @DisplayName("setCode and setStorageAt work together")
        void setCodeAndStorageWorkTogether() {
            HexData code = new HexData("0x602a60005260206000f3");
            Hash slot = new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");
            Hash value = new Hash("0x000000000000000000000000000000000000000000000000000000000000002a");

            tester.setCode(TEST_ADDRESS, code);
            tester.setStorageAt(TEST_ADDRESS, slot, value);

            assertEquals(code.value(), getCode(TEST_ADDRESS).value());
            assertEquals(value.value(), getStorageAt(TEST_ADDRESS, slot).value());
        }

        @Test
        @DisplayName("snapshot and revert preserve original state")
        void snapshotAndRevertPreserveState() {
            // Get original balance
            BigInteger originalBalance = tester.getBalance(TEST_ADDRESS);

            // Create a new snapshot (separate from the one in @BeforeEach)
            SnapshotId innerSnapshot = tester.snapshot();

            // Modify state
            Wei newBalance = Wei.fromEther(new java.math.BigDecimal("999"));
            tester.setBalance(TEST_ADDRESS, newBalance);
            assertEquals(newBalance.value(), tester.getBalance(TEST_ADDRESS));

            // Revert to inner snapshot
            tester.revert(innerSnapshot);

            // Balance should be back to original
            assertEquals(originalBalance, tester.getBalance(TEST_ADDRESS));
        }
    }
}
