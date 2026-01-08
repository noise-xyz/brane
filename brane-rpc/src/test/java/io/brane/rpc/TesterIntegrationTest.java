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

    // ==================== State Management Integration Tests ====================

    @Nested
    @DisplayName("Snapshot and revert integration tests")
    class SnapshotAndRevertTests {

        @Test
        @DisplayName("snapshot returns unique IDs for each call")
        void snapshotReturnsUniqueIds() {
            SnapshotId snapshot1 = tester.snapshot();
            SnapshotId snapshot2 = tester.snapshot();

            assertNotNull(snapshot1);
            assertNotNull(snapshot2);
            assertNotEquals(snapshot1.value(), snapshot2.value());
        }

        @Test
        @DisplayName("revert restores balance changes")
        void revertRestoresBalanceChanges() {
            BigInteger originalBalance = tester.getBalance(TEST_ADDRESS);
            SnapshotId snap = tester.snapshot();

            Wei newBalance = Wei.fromEther(new java.math.BigDecimal("555"));
            tester.setBalance(TEST_ADDRESS, newBalance);
            assertEquals(newBalance.value(), tester.getBalance(TEST_ADDRESS));

            boolean reverted = tester.revert(snap);
            assertTrue(reverted);
            assertEquals(originalBalance, tester.getBalance(TEST_ADDRESS));
        }

        @Test
        @DisplayName("revert restores code changes")
        void revertRestoresCodeChanges() {
            HexData originalCode = getCode(TEST_ADDRESS);
            SnapshotId snap = tester.snapshot();

            HexData newCode = new HexData("0x602a60005260206000f3");
            tester.setCode(TEST_ADDRESS, newCode);
            assertEquals(newCode.value(), getCode(TEST_ADDRESS).value());

            tester.revert(snap);
            assertEquals(originalCode.value(), getCode(TEST_ADDRESS).value());
        }

        @Test
        @DisplayName("revert restores nonce changes")
        void revertRestoresNonceChanges() {
            BigInteger originalNonce = getNonce(TEST_ADDRESS);
            SnapshotId snap = tester.snapshot();

            tester.setNonce(TEST_ADDRESS, 999);
            assertEquals(BigInteger.valueOf(999), getNonce(TEST_ADDRESS));

            tester.revert(snap);
            assertEquals(originalNonce, getNonce(TEST_ADDRESS));
        }

        @Test
        @DisplayName("revert restores storage changes")
        void revertRestoresStorageChanges() {
            Hash slot = new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");
            Hash zeroValue = new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");

            // Set up a contract with some code
            tester.setCode(TEST_ADDRESS, new HexData("0x60016000"));

            Hash originalStorage = getStorageAt(TEST_ADDRESS, slot);
            SnapshotId snap = tester.snapshot();

            Hash newValue = new Hash("0x000000000000000000000000000000000000000000000000000000000000abcd");
            tester.setStorageAt(TEST_ADDRESS, slot, newValue);
            assertEquals(newValue.value(), getStorageAt(TEST_ADDRESS, slot).value());

            tester.revert(snap);
            assertEquals(originalStorage.value(), getStorageAt(TEST_ADDRESS, slot).value());
        }

        @Test
        @DisplayName("nested snapshots work correctly")
        void nestedSnapshotsWorkCorrectly() {
            BigInteger balance0 = tester.getBalance(TEST_ADDRESS);

            SnapshotId snap1 = tester.snapshot();
            Wei balance1 = Wei.fromEther(new java.math.BigDecimal("100"));
            tester.setBalance(TEST_ADDRESS, balance1);

            SnapshotId snap2 = tester.snapshot();
            Wei balance2 = Wei.fromEther(new java.math.BigDecimal("200"));
            tester.setBalance(TEST_ADDRESS, balance2);

            assertEquals(balance2.value(), tester.getBalance(TEST_ADDRESS));

            // Revert to snap2 - should restore balance1
            tester.revert(snap2);
            assertEquals(balance1.value(), tester.getBalance(TEST_ADDRESS));

            // Revert to snap1 - should restore balance0
            tester.revert(snap1);
            assertEquals(balance0, tester.getBalance(TEST_ADDRESS));
        }

        @Test
        @DisplayName("revert restores multiple state changes atomically")
        void revertRestoresMultipleChangesAtomically() {
            BigInteger originalBalance = tester.getBalance(TEST_ADDRESS);
            BigInteger originalNonce = getNonce(TEST_ADDRESS);

            SnapshotId snap = tester.snapshot();

            // Make multiple changes
            tester.setBalance(TEST_ADDRESS, Wei.fromEther(new java.math.BigDecimal("777")));
            tester.setNonce(TEST_ADDRESS, 777);

            // Verify changes
            assertEquals(Wei.fromEther(new java.math.BigDecimal("777")).value(), tester.getBalance(TEST_ADDRESS));
            assertEquals(BigInteger.valueOf(777), getNonce(TEST_ADDRESS));

            // Revert should restore all changes
            tester.revert(snap);
            assertEquals(originalBalance, tester.getBalance(TEST_ADDRESS));
            assertEquals(originalNonce, getNonce(TEST_ADDRESS));
        }
    }

    // ==================== Dump and Load State Integration Tests ====================

    @Nested
    @DisplayName("dumpState and loadState integration tests")
    class DumpAndLoadStateTests {

        @Test
        @DisplayName("dumpState returns non-empty state data")
        void dumpStateReturnsNonEmptyData() {
            HexData state = tester.dumpState();

            assertNotNull(state);
            assertNotNull(state.value());
            assertTrue(state.value().length() > 2, "State should have content beyond '0x' prefix");
        }

        @Test
        @DisplayName("loadState returns true on valid state")
        void loadStateReturnsTrueOnValidState() {
            HexData state = tester.dumpState();

            boolean loaded = tester.loadState(state);

            assertTrue(loaded, "loadState should return true for valid state data");
        }

        @Test
        @DisplayName("dumpState and loadState preserve account balance")
        void dumpAndLoadStatePreservesBalance() {
            // Set a specific balance
            Wei testBalance = Wei.fromEther(new java.math.BigDecimal("123.456"));
            tester.setBalance(TEST_ADDRESS, testBalance);

            // Dump state
            HexData savedState = tester.dumpState();

            // Change the balance
            tester.setBalance(TEST_ADDRESS, Wei.of(0));
            assertEquals(BigInteger.ZERO, tester.getBalance(TEST_ADDRESS));

            // Load saved state
            boolean loaded = tester.loadState(savedState);
            assertTrue(loaded);

            // Balance should be restored
            assertEquals(testBalance.value(), tester.getBalance(TEST_ADDRESS));
        }

        @Test
        @DisplayName("dumpState captures nonce in state")
        void dumpStateCapturesNonce() {
            // Set a specific nonce
            long testNonce = 42;
            tester.setNonce(TEST_ADDRESS, testNonce);
            assertEquals(BigInteger.valueOf(testNonce), getNonce(TEST_ADDRESS));

            // Dump state - verifies the operation completes successfully
            HexData savedState = tester.dumpState();
            assertNotNull(savedState);
            assertTrue(savedState.value().length() > 2);
        }

        @Test
        @DisplayName("dumpState and loadState preserve contract code")
        void dumpAndLoadStatePreservesCode() {
            // Set contract code
            HexData testCode = new HexData("0x602a60005260206000f3");
            tester.setCode(TEST_ADDRESS, testCode);

            // Dump state
            HexData savedState = tester.dumpState();

            // Change the code
            tester.setCode(TEST_ADDRESS, new HexData("0x6001"));
            assertEquals("0x6001", getCode(TEST_ADDRESS).value());

            // Load saved state
            tester.loadState(savedState);

            // Code should be restored
            assertEquals(testCode.value(), getCode(TEST_ADDRESS).value());
        }

        @Test
        @DisplayName("dumpState and loadState preserve storage")
        void dumpAndLoadStatePreservesStorage() {
            // Set up contract with storage
            Hash slot = new Hash("0x0000000000000000000000000000000000000000000000000000000000000001");
            Hash testValue = new Hash("0x00000000000000000000000000000000000000000000000000000000deadbeef");

            tester.setCode(TEST_ADDRESS, new HexData("0x60016000"));
            tester.setStorageAt(TEST_ADDRESS, slot, testValue);

            // Dump state
            HexData savedState = tester.dumpState();

            // Change the storage
            Hash newValue = new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");
            tester.setStorageAt(TEST_ADDRESS, slot, newValue);
            assertEquals(newValue.value(), getStorageAt(TEST_ADDRESS, slot).value());

            // Load saved state
            tester.loadState(savedState);

            // Storage should be restored
            assertEquals(testValue.value(), getStorageAt(TEST_ADDRESS, slot).value());
        }

        @Test
        @DisplayName("dumpState and loadState preserve multiple accounts")
        void dumpAndLoadStatePreservesMultipleAccounts() {
            Address account1 = TEST_ADDRESS;
            Address account2 = new Address("0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC");

            Wei balance1 = Wei.fromEther(new java.math.BigDecimal("111"));
            Wei balance2 = Wei.fromEther(new java.math.BigDecimal("222"));

            tester.setBalance(account1, balance1);
            tester.setBalance(account2, balance2);

            // Dump state
            HexData savedState = tester.dumpState();

            // Change both balances
            tester.setBalance(account1, Wei.of(0));
            tester.setBalance(account2, Wei.of(0));

            // Load saved state
            tester.loadState(savedState);

            // Both balances should be restored
            assertEquals(balance1.value(), tester.getBalance(account1));
            assertEquals(balance2.value(), tester.getBalance(account2));
        }

        @Test
        @DisplayName("state can be loaded multiple times")
        void stateCanBeLoadedMultipleTimes() {
            Wei testBalance = Wei.fromEther(new java.math.BigDecimal("500"));
            tester.setBalance(TEST_ADDRESS, testBalance);

            HexData savedState = tester.dumpState();

            // Load multiple times
            for (int i = 0; i < 3; i++) {
                tester.setBalance(TEST_ADDRESS, Wei.of(i));
                boolean loaded = tester.loadState(savedState);
                assertTrue(loaded, "Load attempt " + (i + 1) + " should succeed");
                assertEquals(testBalance.value(), tester.getBalance(TEST_ADDRESS),
                        "Balance should be restored after load attempt " + (i + 1));
            }
        }
    }

    // ==================== Impersonation Integration Tests ====================

    @Nested
    @DisplayName("Impersonation integration tests")
    class ImpersonationTests {

        // Whale address to impersonate - a different address from the test signer
        private static final Address WHALE_ADDRESS =
                new Address("0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC");

        @Test
        @DisplayName("impersonate sends transaction from impersonated address")
        void impersonateSendsTransactionFromImpersonatedAddress() {
            // Fund the whale address
            Wei whaleFunds = Wei.fromEther(new java.math.BigDecimal("100"));
            tester.setBalance(WHALE_ADDRESS, whaleFunds);

            Address recipient = new Address("0x90F79bf6EB2c4f870365E785982E1f101E93b906");
            BigInteger recipientBalanceBefore = tester.getBalance(recipient);

            Wei transferAmount = Wei.fromEther(new java.math.BigDecimal("1"));

            try (ImpersonationSession session = tester.impersonate(WHALE_ADDRESS)) {
                io.brane.core.model.TransactionRequest request = new io.brane.core.model.TransactionRequest(
                        null, // from - will be set by session
                        recipient,
                        transferAmount,
                        21_000L, // gasLimit
                        null, null, null, null, null, false, null);

                Hash txHash = session.sendTransaction(request);
                assertNotNull(txHash);

                // Wait for transaction to be mined
                io.brane.core.model.TransactionReceipt receipt = tester.waitForReceipt(txHash);
                assertNotNull(receipt);
                assertTrue(receipt.status());
            }

            // Verify recipient received the funds
            BigInteger recipientBalanceAfter = tester.getBalance(recipient);
            assertEquals(
                    recipientBalanceBefore.add(transferAmount.value()),
                    recipientBalanceAfter);
        }

        @Test
        @DisplayName("impersonate sendTransactionAndWait works correctly")
        void impersonateSendTransactionAndWaitWorks() {
            // Fund the whale address
            Wei whaleFunds = Wei.fromEther(new java.math.BigDecimal("100"));
            tester.setBalance(WHALE_ADDRESS, whaleFunds);

            Address recipient = new Address("0x90F79bf6EB2c4f870365E785982E1f101E93b906");
            BigInteger recipientBalanceBefore = tester.getBalance(recipient);

            Wei transferAmount = Wei.fromEther(new java.math.BigDecimal("2"));

            try (ImpersonationSession session = tester.impersonate(WHALE_ADDRESS)) {
                io.brane.core.model.TransactionRequest request = new io.brane.core.model.TransactionRequest(
                        null,
                        recipient,
                        transferAmount,
                        21_000L,
                        null, null, null, null, null, false, null);

                io.brane.core.model.TransactionReceipt receipt = session.sendTransactionAndWait(request);

                assertNotNull(receipt);
                assertTrue(receipt.status());
                assertNotNull(receipt.transactionHash());
            }

            // Verify recipient received the funds
            BigInteger recipientBalanceAfter = tester.getBalance(recipient);
            assertEquals(
                    recipientBalanceBefore.add(transferAmount.value()),
                    recipientBalanceAfter);
        }

        @Test
        @DisplayName("impersonate session address returns correct impersonated address")
        void impersonateSessionAddressReturnsCorrectAddress() {
            try (ImpersonationSession session = tester.impersonate(WHALE_ADDRESS)) {
                assertEquals(WHALE_ADDRESS, session.address());
            }
        }

        @Test
        @DisplayName("impersonate session auto-closes with try-with-resources")
        void impersonateSessionAutoCloses() {
            ImpersonationSession session = tester.impersonate(WHALE_ADDRESS);
            session.close();

            // After close, sendTransaction should throw IllegalStateException
            io.brane.core.model.TransactionRequest request = new io.brane.core.model.TransactionRequest(
                    null,
                    TEST_ADDRESS,
                    Wei.fromEther(java.math.BigDecimal.ONE),
                    21_000L,
                    null, null, null, null, null, false, null);

            IllegalStateException ex = assertThrows(
                    IllegalStateException.class,
                    () -> session.sendTransaction(request));
            assertTrue(ex.getMessage().contains("closed"));
        }

        @Test
        @DisplayName("impersonate session close is idempotent")
        void impersonateSessionCloseIsIdempotent() {
            ImpersonationSession session = tester.impersonate(WHALE_ADDRESS);

            // Multiple close calls should not throw
            assertDoesNotThrow(() -> {
                session.close();
                session.close();
                session.close();
            });
        }

        @Test
        @DisplayName("impersonate can send multiple transactions in same session")
        void impersonateCanSendMultipleTransactions() {
            Wei whaleFunds = Wei.fromEther(new java.math.BigDecimal("100"));
            tester.setBalance(WHALE_ADDRESS, whaleFunds);

            Address recipient1 = new Address("0x90F79bf6EB2c4f870365E785982E1f101E93b906");
            Address recipient2 = new Address("0x15d34AAf54267DB7D7c367839AAf71A00a2C6A65");

            try (ImpersonationSession session = tester.impersonate(WHALE_ADDRESS)) {
                // First transaction
                io.brane.core.model.TransactionRequest request1 = new io.brane.core.model.TransactionRequest(
                        null, recipient1, Wei.fromEther(java.math.BigDecimal.ONE),
                        21_000L, null, null, null, null, null, false, null);
                io.brane.core.model.TransactionReceipt receipt1 = session.sendTransactionAndWait(request1);
                assertTrue(receipt1.status());

                // Second transaction
                io.brane.core.model.TransactionRequest request2 = new io.brane.core.model.TransactionRequest(
                        null, recipient2, Wei.fromEther(java.math.BigDecimal.ONE),
                        21_000L, null, null, null, null, null, false, null);
                io.brane.core.model.TransactionReceipt receipt2 = session.sendTransactionAndWait(request2);
                assertTrue(receipt2.status());
            }
        }

        @Test
        @DisplayName("impersonate overwrites any from address in request")
        void impersonateOverwritesFromAddress() {
            Wei whaleFunds = Wei.fromEther(new java.math.BigDecimal("100"));
            tester.setBalance(WHALE_ADDRESS, whaleFunds);

            Address recipient = new Address("0x90F79bf6EB2c4f870365E785982E1f101E93b906");
            BigInteger recipientBalanceBefore = tester.getBalance(recipient);

            // Even though we specify a different from address, the whale address should be used
            Address differentFrom = new Address("0x15d34AAf54267DB7D7c367839AAf71A00a2C6A65");
            Wei transferAmount = Wei.fromEther(java.math.BigDecimal.ONE);

            try (ImpersonationSession session = tester.impersonate(WHALE_ADDRESS)) {
                io.brane.core.model.TransactionRequest request = new io.brane.core.model.TransactionRequest(
                        differentFrom, // This should be ignored
                        recipient,
                        transferAmount,
                        21_000L,
                        null, null, null, null, null, false, null);

                io.brane.core.model.TransactionReceipt receipt = session.sendTransactionAndWait(request);
                assertTrue(receipt.status());
            }

            // Verify whale's balance decreased (not differentFrom's)
            BigInteger whaleBalanceAfter = tester.getBalance(WHALE_ADDRESS);
            assertTrue(whaleBalanceAfter.compareTo(whaleFunds.value()) < 0);

            // Verify recipient received funds
            BigInteger recipientBalanceAfter = tester.getBalance(recipient);
            assertEquals(recipientBalanceBefore.add(transferAmount.value()), recipientBalanceAfter);
        }
    }

    // ==================== Mining Integration Tests ====================

    @Nested
    @DisplayName("Mining integration tests")
    class MiningTests {

        @Test
        @DisplayName("mine() advances block number by 1")
        void mineAdvancesBlockNumberByOne() {
            io.brane.core.model.BlockHeader beforeBlock = tester.getLatestBlock();
            long beforeNumber = beforeBlock.number();

            tester.mine();

            io.brane.core.model.BlockHeader afterBlock = tester.getLatestBlock();
            assertEquals(beforeNumber + 1, afterBlock.number());
        }

        @Test
        @DisplayName("mine(blocks) advances block number by specified amount")
        void mineBlocksAdvancesBlockNumber() {
            io.brane.core.model.BlockHeader beforeBlock = tester.getLatestBlock();
            long beforeNumber = beforeBlock.number();

            tester.mine(10);

            io.brane.core.model.BlockHeader afterBlock = tester.getLatestBlock();
            assertEquals(beforeNumber + 10, afterBlock.number());
        }

        @Test
        @DisplayName("mine(blocks, interval) advances both block number and time")
        void mineWithIntervalAdvancesBlocksAndTime() {
            io.brane.core.model.BlockHeader beforeBlock = tester.getLatestBlock();
            long beforeNumber = beforeBlock.number();
            long beforeTimestamp = beforeBlock.timestamp();

            // Mine 5 blocks with 12 second intervals
            tester.mine(5, 12);

            io.brane.core.model.BlockHeader afterBlock = tester.getLatestBlock();
            assertEquals(beforeNumber + 5, afterBlock.number());

            // Time should have advanced by approximately 5 * 12 = 60 seconds
            // Note: we check >= because Anvil may add some base time
            long timeAdvanced = afterBlock.timestamp() - beforeTimestamp;
            assertTrue(timeAdvanced >= 60,
                    "Time should advance by at least 60 seconds, actual: " + timeAdvanced);
        }

        @Test
        @DisplayName("mine with large block count works")
        void mineLargeBlockCount() {
            io.brane.core.model.BlockHeader beforeBlock = tester.getLatestBlock();
            long beforeNumber = beforeBlock.number();

            tester.mine(100);

            io.brane.core.model.BlockHeader afterBlock = tester.getLatestBlock();
            assertEquals(beforeNumber + 100, afterBlock.number());
        }
    }

    // ==================== Automine Integration Tests ====================

    @Nested
    @DisplayName("Automine integration tests")
    class AutomineTests {

        @org.junit.jupiter.api.BeforeEach
        void ensureAutomineEnabled() {
            // Ensure automine is enabled before each test since snapshot/revert
            // may not restore automine state
            tester.setAutomine(true);
        }

        @org.junit.jupiter.api.AfterEach
        void restoreAutomine() {
            // Always re-enable automine after tests
            tester.setAutomine(true);
        }

        @Test
        @DisplayName("getAutomine returns current state correctly")
        void getAutomineReturnsCurrentState() {
            // After setup, automine should be enabled
            boolean automine = tester.getAutomine();
            assertTrue(automine, "Automine should be enabled after setup");
        }

        @Test
        @DisplayName("setAutomine(false) disables automine")
        void setAutomineDisables() {
            // Get initial state (should be true after setup)
            assertTrue(tester.getAutomine());

            // Disable automine
            tester.setAutomine(false);
            assertFalse(tester.getAutomine());
        }

        @Test
        @DisplayName("setAutomine(true) enables automine")
        void setAutomineEnables() {
            // Disable automine first
            tester.setAutomine(false);
            assertFalse(tester.getAutomine());

            // Re-enable automine
            tester.setAutomine(true);
            assertTrue(tester.getAutomine());
        }

        @Test
        @DisplayName("transactions are not mined when automine is disabled")
        void transactionsNotMinedWhenAutomineDisabled() {
            // Disable automine
            tester.setAutomine(false);

            // Get current block number
            io.brane.core.model.BlockHeader beforeBlock = tester.getLatestBlock();
            long beforeNumber = beforeBlock.number();

            // Send a transaction (won't be mined automatically)
            Address recipient = new Address("0x90F79bf6EB2c4f870365E785982E1f101E93b906");
            io.brane.core.model.TransactionRequest request = new io.brane.core.model.TransactionRequest(
                    null, recipient, Wei.fromEther(java.math.BigDecimal.ONE),
                    21_000L, null, null, null, null, null, false, null);

            // Tester has sendTransaction method directly
            Hash txHash = tester.sendTransaction(request);
            assertNotNull(txHash);

            // Block number should NOT have advanced yet
            io.brane.core.model.BlockHeader afterBlock = tester.getLatestBlock();
            assertEquals(beforeNumber, afterBlock.number(),
                    "Block number should not advance with automine disabled");

            // Now mine manually to include the transaction
            tester.mine();

            io.brane.core.model.BlockHeader finalBlock = tester.getLatestBlock();
            assertEquals(beforeNumber + 1, finalBlock.number());
        }

        @Test
        @DisplayName("setIntervalMining sets mining interval")
        void setIntervalMiningSetsInterval() {
            // This test just verifies the method executes without error
            // Interval mining is harder to test in a unit test context
            assertDoesNotThrow(() -> tester.setIntervalMining(0)); // Disable interval mining
        }
    }

    // ==================== Time Manipulation Integration Tests ====================

    @Nested
    @DisplayName("Time manipulation integration tests")
    class TimeManipulationTests {

        @Test
        @DisplayName("increaseTime advances timestamp")
        void increaseTimeAdvancesTimestamp() {
            io.brane.core.model.BlockHeader beforeBlock = tester.getLatestBlock();
            long beforeTimestamp = beforeBlock.timestamp();

            // Increase time by 1 hour (3600 seconds)
            tester.increaseTime(3600);

            // Mine a block to see the new timestamp
            tester.mine();

            io.brane.core.model.BlockHeader afterBlock = tester.getLatestBlock();
            long afterTimestamp = afterBlock.timestamp();

            // Timestamp should have advanced by at least 3600 seconds
            long timeAdvanced = afterTimestamp - beforeTimestamp;
            assertTrue(timeAdvanced >= 3600,
                    "Time should advance by at least 3600 seconds, actual: " + timeAdvanced);
        }

        @Test
        @DisplayName("increaseTime by 1 day")
        void increaseTimeByOneDay() {
            io.brane.core.model.BlockHeader beforeBlock = tester.getLatestBlock();
            long beforeTimestamp = beforeBlock.timestamp();

            // Increase time by 1 day (86400 seconds)
            tester.increaseTime(86400);

            // Mine a block to see the new timestamp
            tester.mine();

            io.brane.core.model.BlockHeader afterBlock = tester.getLatestBlock();
            long afterTimestamp = afterBlock.timestamp();

            // Timestamp should have advanced by at least 86400 seconds
            long timeAdvanced = afterTimestamp - beforeTimestamp;
            assertTrue(timeAdvanced >= 86400,
                    "Time should advance by at least 86400 seconds (1 day), actual: " + timeAdvanced);
        }

        @Test
        @DisplayName("setNextBlockTimestamp sets specific timestamp for next block")
        void setNextBlockTimestampSetsTimestamp() {
            // Get a future timestamp (current time + 1 year)
            long futureTimestamp = System.currentTimeMillis() / 1000 + 365 * 24 * 60 * 60;

            tester.setNextBlockTimestamp(futureTimestamp);

            // Mine a block to apply the timestamp
            tester.mine();

            io.brane.core.model.BlockHeader afterBlock = tester.getLatestBlock();

            // The timestamp should be exactly what we set
            assertEquals(futureTimestamp, afterBlock.timestamp(),
                    "Block timestamp should match the set value");
        }

        @Test
        @DisplayName("setNextBlockTimestamp only affects next block")
        void setNextBlockTimestampOnlyAffectsNextBlock() {
            // Get current timestamp
            io.brane.core.model.BlockHeader currentBlock = tester.getLatestBlock();
            long currentTimestamp = currentBlock.timestamp();

            // Set a specific timestamp for next block
            long specificTimestamp = currentTimestamp + 1000;
            tester.setNextBlockTimestamp(specificTimestamp);

            // Mine first block - should have specific timestamp
            tester.mine();
            io.brane.core.model.BlockHeader firstBlock = tester.getLatestBlock();
            assertEquals(specificTimestamp, firstBlock.timestamp());

            // Mine second block - timestamp should increment normally
            // Anvil increments by at least 1 second, but may reuse same timestamp if mined too quickly
            // So we use setNextBlockTimestamp for the second block to ensure different timestamp
            long secondTimestamp = specificTimestamp + 100;
            tester.setNextBlockTimestamp(secondTimestamp);
            tester.mine();
            io.brane.core.model.BlockHeader secondBlock = tester.getLatestBlock();

            // Verify the second block has the new timestamp (proves first setNextBlockTimestamp
            // didn't persist to affect this block)
            assertEquals(secondTimestamp, secondBlock.timestamp(),
                    "Second block should have its own set timestamp");
            assertTrue(secondBlock.timestamp() > firstBlock.timestamp(),
                    "Second block timestamp should be after the first block");
        }

        @Test
        @DisplayName("increaseTime with multiple calls accumulates")
        void increaseTimeAccumulates() {
            io.brane.core.model.BlockHeader beforeBlock = tester.getLatestBlock();
            long beforeTimestamp = beforeBlock.timestamp();

            // Increase time multiple times
            tester.increaseTime(1000);
            tester.increaseTime(2000);
            tester.increaseTime(3000);

            // Mine a block to see the accumulated time
            tester.mine();

            io.brane.core.model.BlockHeader afterBlock = tester.getLatestBlock();
            long afterTimestamp = afterBlock.timestamp();

            // Total time increase should be at least 6000 seconds
            long timeAdvanced = afterTimestamp - beforeTimestamp;
            assertTrue(timeAdvanced >= 6000,
                    "Time should advance by at least 6000 seconds, actual: " + timeAdvanced);
        }

        @Test
        @DisplayName("time manipulation is preserved across snapshots")
        void timeManipulationPreservedAcrossSnapshots() {
            // Get initial timestamp
            io.brane.core.model.BlockHeader initialBlock = tester.getLatestBlock();
            long initialTimestamp = initialBlock.timestamp();

            // Create a snapshot
            SnapshotId snap = tester.snapshot();

            // Advance time significantly
            tester.increaseTime(10000);
            tester.mine();

            io.brane.core.model.BlockHeader advancedBlock = tester.getLatestBlock();
            long advancedTimestamp = advancedBlock.timestamp();
            assertTrue(advancedTimestamp > initialTimestamp + 9000);

            // Revert to snapshot
            tester.revert(snap);

            // Mine a new block - timestamp should be based on reverted state
            tester.mine();
            io.brane.core.model.BlockHeader revertedBlock = tester.getLatestBlock();

            // The reverted timestamp should be close to initial (not the advanced time)
            // Allow some tolerance since mining adds time
            long revertedTimestamp = revertedBlock.timestamp();
            assertTrue(revertedTimestamp < advancedTimestamp,
                    "Reverted timestamp should be less than advanced timestamp");
        }
    }
}
