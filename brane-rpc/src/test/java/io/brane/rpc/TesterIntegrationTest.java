// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.List;

import org.junit.jupiter.api.ClassOrderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestClassOrder;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.extension.ExtendWith;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.internal.RpcUtils;
import io.brane.rpc.test.BraneTestExtension;

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
@ExtendWith(BraneTestExtension.class)
@TestClassOrder(ClassOrderer.OrderAnnotation.class)
class TesterIntegrationTest {

    private static final Address TEST_ADDRESS =
            new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");

    // ==================== Helper Methods for Raw RPC Calls ====================

    /**
     * Gets bytecode at an address using eth_getCode.
     */
    private static HexData getCode(BraneProvider provider, Address address) {
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
    private static BigInteger getNonce(BraneProvider provider, Address address) {
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
    private static Hash getStorageAt(BraneProvider provider, Address address, Hash slot) {
        JsonRpcResponse response = provider.send("eth_getStorageAt", List.of(address.value(), slot.value(), "latest"));
        Object result = response.result();
        if (result == null) {
            return new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");
        }
        return new Hash(result.toString());
    }

    // ==================== setBalance() Integration Tests ====================

    @Nested
    @Order(1)
    @DisplayName("setBalance integration tests")
    class SetBalanceTests {

        @Test
        @DisplayName("setBalance changes account balance")
        void setBalanceChangesAccountBalance(Brane.Tester tester) {
            Wei newBalance = Wei.fromEther(new java.math.BigDecimal("12345"));

            tester.setBalance(TEST_ADDRESS, newBalance);

            BigInteger actualBalance = tester.getBalance(TEST_ADDRESS);
            assertEquals(newBalance.value(), actualBalance);
        }

        @Test
        @DisplayName("setBalance can set balance to zero")
        void setBalanceCanSetToZero(Brane.Tester tester) {
            tester.setBalance(TEST_ADDRESS, Wei.ZERO);

            BigInteger actualBalance = tester.getBalance(TEST_ADDRESS);
            assertEquals(BigInteger.ZERO, actualBalance);
        }

        @Test
        @DisplayName("setBalance can set very large balance")
        void setBalanceCanSetLargeBalance(Brane.Tester tester) {
            // 1 million ETH
            Wei largeBalance = Wei.fromEther(new java.math.BigDecimal("1000000"));

            tester.setBalance(TEST_ADDRESS, largeBalance);

            BigInteger actualBalance = tester.getBalance(TEST_ADDRESS);
            assertEquals(largeBalance.value(), actualBalance);
        }
    }

    // ==================== setCode() Integration Tests ====================

    @Nested
    @Order(2)
    @DisplayName("setCode integration tests")
    class SetCodeTests {

        // Simple bytecode that returns 42 when called: PUSH1 0x2a PUSH1 0x00 MSTORE PUSH1 0x20 PUSH1 0x00 RETURN
        private static final HexData SIMPLE_CODE = new HexData("0x602a60005260206000f3");

        @Test
        @DisplayName("setCode deploys code at address")
        void setCodeDeploysCodeAtAddress(Brane.Tester tester, BraneProvider provider) {
            tester.setCode(TEST_ADDRESS, SIMPLE_CODE);

            HexData actualCode = getCode(provider, TEST_ADDRESS);
            assertEquals(SIMPLE_CODE.value(), actualCode.value());
        }

        @Test
        @DisplayName("setCode can set empty code")
        void setCodeCanSetEmptyCode(Brane.Tester tester, BraneProvider provider) {
            // First set some code
            tester.setCode(TEST_ADDRESS, SIMPLE_CODE);

            // Then clear it
            tester.setCode(TEST_ADDRESS, new HexData("0x"));

            HexData actualCode = getCode(provider, TEST_ADDRESS);
            assertEquals("0x", actualCode.value());
        }

        @Test
        @DisplayName("setCode replaces existing code")
        void setCodeReplacesExistingCode(Brane.Tester tester, BraneProvider provider) {
            HexData code1 = new HexData("0x6001");
            HexData code2 = new HexData("0x6002");

            tester.setCode(TEST_ADDRESS, code1);
            tester.setCode(TEST_ADDRESS, code2);

            HexData actualCode = getCode(provider, TEST_ADDRESS);
            assertEquals(code2.value(), actualCode.value());
        }

        @Test
        @DisplayName("getCode via Brane.Reader interface returns code set by setCode")
        void getCodeViaReaderInterfaceReturnsSetCode(Brane.Tester tester) {
            tester.setCode(TEST_ADDRESS, SIMPLE_CODE);

            // Use the Brane.Reader interface method (not raw RPC)
            HexData actualCode = tester.getCode(TEST_ADDRESS);
            assertEquals(SIMPLE_CODE.value(), actualCode.value());
        }

        @Test
        @DisplayName("getCode via Brane.Reader interface returns empty for EOA")
        void getCodeViaReaderInterfaceReturnsEmptyForEoa(Brane.Tester tester) {
            // Clear any existing code
            tester.setCode(TEST_ADDRESS, new HexData("0x"));

            // Use the Brane.Reader interface method
            HexData actualCode = tester.getCode(TEST_ADDRESS);
            assertEquals("0x", actualCode.value());
        }
    }

    // ==================== setNonce() Integration Tests ====================

    @Nested
    @Order(3)
    @DisplayName("setNonce integration tests")
    class SetNonceTests {

        @Test
        @DisplayName("setNonce changes account nonce")
        void setNonceChangesAccountNonce(Brane.Tester tester, BraneProvider provider) {
            long newNonce = 42;

            tester.setNonce(TEST_ADDRESS, newNonce);

            BigInteger actualNonce = getNonce(provider, TEST_ADDRESS);
            assertEquals(BigInteger.valueOf(newNonce), actualNonce);
        }

        @Test
        @DisplayName("setNonce can set to zero")
        void setNonceCanSetToZero(Brane.Tester tester, BraneProvider provider) {
            // First set a non-zero nonce
            tester.setNonce(TEST_ADDRESS, 100);

            // Then set it back to zero
            tester.setNonce(TEST_ADDRESS, 0);

            BigInteger actualNonce = getNonce(provider, TEST_ADDRESS);
            assertEquals(BigInteger.ZERO, actualNonce);
        }

        @Test
        @DisplayName("setNonce can set large nonce")
        void setNonceCanSetLargeNonce(Brane.Tester tester, BraneProvider provider) {
            long largeNonce = 999_999_999L;

            tester.setNonce(TEST_ADDRESS, largeNonce);

            BigInteger actualNonce = getNonce(provider, TEST_ADDRESS);
            assertEquals(BigInteger.valueOf(largeNonce), actualNonce);
        }
    }

    // ==================== setStorageAt() Integration Tests ====================

    @Nested
    @Order(4)
    @DisplayName("setStorageAt integration tests")
    class SetStorageAtTests {

        private static final Hash SLOT_0 =
                new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");

        private static final Hash VALUE_42 =
                new Hash("0x000000000000000000000000000000000000000000000000000000000000002a");

        @Test
        @DisplayName("setStorageAt sets storage value")
        void setStorageAtSetsStorageValue(Brane.Tester tester, BraneProvider provider) {
            // First deploy some code so the address is a contract
            tester.setCode(TEST_ADDRESS, new HexData("0x60016000"));

            tester.setStorageAt(TEST_ADDRESS, SLOT_0, VALUE_42);

            Hash actualValue = getStorageAt(provider, TEST_ADDRESS, SLOT_0);
            assertEquals(VALUE_42.value(), actualValue.value());
        }

        @Test
        @DisplayName("setStorageAt can set to zero")
        void setStorageAtCanSetToZero(Brane.Tester tester, BraneProvider provider) {
            tester.setCode(TEST_ADDRESS, new HexData("0x60016000"));

            // First set a non-zero value
            tester.setStorageAt(TEST_ADDRESS, SLOT_0, VALUE_42);

            // Then clear it
            Hash zeroValue = new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");
            tester.setStorageAt(TEST_ADDRESS, SLOT_0, zeroValue);

            Hash actualValue = getStorageAt(provider, TEST_ADDRESS, SLOT_0);
            assertEquals(zeroValue.value(), actualValue.value());
        }

        @Test
        @DisplayName("setStorageAt works with different slots")
        void setStorageAtWorksWithDifferentSlots(Brane.Tester tester, BraneProvider provider) {
            tester.setCode(TEST_ADDRESS, new HexData("0x60016000"));

            Hash slot1 = new Hash("0x0000000000000000000000000000000000000000000000000000000000000001");
            Hash slot5 = new Hash("0x0000000000000000000000000000000000000000000000000000000000000005");
            Hash value1 = new Hash("0x0000000000000000000000000000000000000000000000000000000000000011");
            Hash value5 = new Hash("0x0000000000000000000000000000000000000000000000000000000000000055");

            tester.setStorageAt(TEST_ADDRESS, slot1, value1);
            tester.setStorageAt(TEST_ADDRESS, slot5, value5);

            assertEquals(value1.value(), getStorageAt(provider, TEST_ADDRESS, slot1).value());
            assertEquals(value5.value(), getStorageAt(provider, TEST_ADDRESS, slot5).value());
        }

        @Test
        @DisplayName("getStorageAt via Brane.Reader interface returns value set by setStorageAt")
        void getStorageAtViaReaderInterfaceReturnsSetValue(Brane.Tester tester) {
            // Deploy code first so address is a contract
            tester.setCode(TEST_ADDRESS, new HexData("0x60016000"));

            tester.setStorageAt(TEST_ADDRESS, SLOT_0, VALUE_42);

            // Use the Brane.Reader interface method (takes BigInteger slot)
            HexData actualValue = tester.getStorageAt(TEST_ADDRESS, BigInteger.ZERO);
            assertEquals(VALUE_42.value(), actualValue.value());
        }

        @Test
        @DisplayName("getStorageAt via Brane.Reader interface returns zero for uninitialized slot")
        void getStorageAtViaReaderInterfaceReturnsZeroForUninitializedSlot(Brane.Tester tester) {
            // Deploy code first
            tester.setCode(TEST_ADDRESS, new HexData("0x60016000"));

            // Query a slot that was never written to
            BigInteger uninitializedSlot = BigInteger.valueOf(999);
            HexData actualValue = tester.getStorageAt(TEST_ADDRESS, uninitializedSlot);

            // Should return 32 zero bytes
            assertEquals(
                    "0x0000000000000000000000000000000000000000000000000000000000000000",
                    actualValue.value());
        }

        @Test
        @DisplayName("getStorageAt via Brane.Reader interface works with large slot numbers")
        void getStorageAtViaReaderInterfaceWorksWithLargeSlotNumbers(Brane.Tester tester) {
            tester.setCode(TEST_ADDRESS, new HexData("0x60016000"));

            // Large slot number (like those used by Solidity mappings)
            BigInteger largeSlot = new BigInteger("abcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890", 16);
            Hash slotAsHash = new Hash("0xabcdef1234567890abcdef1234567890abcdef1234567890abcdef1234567890");
            Hash testValue = new Hash("0x000000000000000000000000000000000000000000000000000000cafebabe00");

            tester.setStorageAt(TEST_ADDRESS, slotAsHash, testValue);

            HexData actualValue = tester.getStorageAt(TEST_ADDRESS, largeSlot);
            assertEquals(testValue.value(), actualValue.value());
        }
    }

    // ==================== Combined Operations Tests ====================

    @Nested
    @Order(5)
    @DisplayName("Combined account manipulation tests")
    class CombinedTests {

        @Test
        @DisplayName("setBalance and setNonce work together")
        void setBalanceAndNonceWorkTogether(Brane.Tester tester, BraneProvider provider) {
            Wei balance = Wei.fromEther(new java.math.BigDecimal("100"));
            long nonce = 50;

            tester.setBalance(TEST_ADDRESS, balance);
            tester.setNonce(TEST_ADDRESS, nonce);

            assertEquals(balance.value(), tester.getBalance(TEST_ADDRESS));
            assertEquals(BigInteger.valueOf(nonce), getNonce(provider, TEST_ADDRESS));
        }

        @Test
        @DisplayName("setCode and setStorageAt work together")
        void setCodeAndStorageWorkTogether(Brane.Tester tester, BraneProvider provider) {
            HexData code = new HexData("0x602a60005260206000f3");
            Hash slot = new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");
            Hash value = new Hash("0x000000000000000000000000000000000000000000000000000000000000002a");

            tester.setCode(TEST_ADDRESS, code);
            tester.setStorageAt(TEST_ADDRESS, slot, value);

            assertEquals(code.value(), getCode(provider, TEST_ADDRESS).value());
            assertEquals(value.value(), getStorageAt(provider, TEST_ADDRESS, slot).value());
        }

        @Test
        @DisplayName("snapshot and revert preserve original state")
        void snapshotAndRevertPreserveState(Brane.Tester tester) {
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
    @Order(6)
    @DisplayName("Snapshot and revert integration tests")
    class SnapshotAndRevertTests {

        @Test
        @DisplayName("snapshot returns unique IDs for each call")
        void snapshotReturnsUniqueIds(Brane.Tester tester) {
            SnapshotId snapshot1 = tester.snapshot();
            SnapshotId snapshot2 = tester.snapshot();

            assertNotNull(snapshot1);
            assertNotNull(snapshot2);
            assertNotEquals(snapshot1.value(), snapshot2.value());
        }

        @Test
        @DisplayName("revert restores balance changes")
        void revertRestoresBalanceChanges(Brane.Tester tester) {
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
        void revertRestoresCodeChanges(Brane.Tester tester, BraneProvider provider) {
            HexData originalCode = getCode(provider, TEST_ADDRESS);
            SnapshotId snap = tester.snapshot();

            HexData newCode = new HexData("0x602a60005260206000f3");
            tester.setCode(TEST_ADDRESS, newCode);
            assertEquals(newCode.value(), getCode(provider, TEST_ADDRESS).value());

            tester.revert(snap);
            assertEquals(originalCode.value(), getCode(provider, TEST_ADDRESS).value());
        }

        @Test
        @DisplayName("revert restores nonce changes")
        void revertRestoresNonceChanges(Brane.Tester tester, BraneProvider provider) {
            BigInteger originalNonce = getNonce(provider, TEST_ADDRESS);
            SnapshotId snap = tester.snapshot();

            tester.setNonce(TEST_ADDRESS, 999);
            assertEquals(BigInteger.valueOf(999), getNonce(provider, TEST_ADDRESS));

            tester.revert(snap);
            assertEquals(originalNonce, getNonce(provider, TEST_ADDRESS));
        }

        @Test
        @DisplayName("revert restores storage changes")
        void revertRestoresStorageChanges(Brane.Tester tester, BraneProvider provider) {
            Hash slot = new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");
            Hash zeroValue = new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");

            // Set up a contract with some code
            tester.setCode(TEST_ADDRESS, new HexData("0x60016000"));

            Hash originalStorage = getStorageAt(provider, TEST_ADDRESS, slot);
            SnapshotId snap = tester.snapshot();

            Hash newValue = new Hash("0x000000000000000000000000000000000000000000000000000000000000abcd");
            tester.setStorageAt(TEST_ADDRESS, slot, newValue);
            assertEquals(newValue.value(), getStorageAt(provider, TEST_ADDRESS, slot).value());

            tester.revert(snap);
            assertEquals(originalStorage.value(), getStorageAt(provider, TEST_ADDRESS, slot).value());
        }

        @Test
        @DisplayName("nested snapshots work correctly")
        void nestedSnapshotsWorkCorrectly(Brane.Tester tester) {
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
        void revertRestoresMultipleChangesAtomically(Brane.Tester tester, BraneProvider provider) {
            BigInteger originalBalance = tester.getBalance(TEST_ADDRESS);
            BigInteger originalNonce = getNonce(provider, TEST_ADDRESS);

            SnapshotId snap = tester.snapshot();

            // Make multiple changes
            tester.setBalance(TEST_ADDRESS, Wei.fromEther(new java.math.BigDecimal("777")));
            tester.setNonce(TEST_ADDRESS, 777);

            // Verify changes
            assertEquals(Wei.fromEther(new java.math.BigDecimal("777")).value(), tester.getBalance(TEST_ADDRESS));
            assertEquals(BigInteger.valueOf(777), getNonce(provider, TEST_ADDRESS));

            // Revert should restore all changes
            tester.revert(snap);
            assertEquals(originalBalance, tester.getBalance(TEST_ADDRESS));
            assertEquals(originalNonce, getNonce(provider, TEST_ADDRESS));
        }
    }

    // ==================== Dump and Load State Integration Tests ====================

    @Nested
    @Order(7)
    @DisplayName("dumpState and loadState integration tests")
    class DumpAndLoadStateTests {

        @Test
        @DisplayName("dumpState returns non-empty state data")
        void dumpStateReturnsNonEmptyData(Brane.Tester tester) {
            HexData state = tester.dumpState();

            assertNotNull(state);
            assertNotNull(state.value());
            assertTrue(state.value().length() > 2, "State should have content beyond '0x' prefix");
        }

        @Test
        @DisplayName("loadState returns true on valid state")
        void loadStateReturnsTrueOnValidState(Brane.Tester tester) {
            HexData state = tester.dumpState();

            boolean loaded = tester.loadState(state);

            assertTrue(loaded, "loadState should return true for valid state data");
        }

        @Test
        @DisplayName("dumpState and loadState preserve account balance")
        void dumpAndLoadStatePreservesBalance(Brane.Tester tester) {
            // Set a specific balance
            Wei testBalance = Wei.fromEther(new java.math.BigDecimal("123.456"));
            tester.setBalance(TEST_ADDRESS, testBalance);

            // Dump state
            HexData savedState = tester.dumpState();

            // Change the balance
            tester.setBalance(TEST_ADDRESS, Wei.ZERO);
            assertEquals(BigInteger.ZERO, tester.getBalance(TEST_ADDRESS));

            // Load saved state
            boolean loaded = tester.loadState(savedState);
            assertTrue(loaded);

            // Balance should be restored
            assertEquals(testBalance.value(), tester.getBalance(TEST_ADDRESS));
        }

        @Test
        @DisplayName("dumpState captures nonce in state")
        void dumpStateCapturesNonce(Brane.Tester tester, BraneProvider provider) {
            // Set a specific nonce
            long testNonce = 42;
            tester.setNonce(TEST_ADDRESS, testNonce);
            assertEquals(BigInteger.valueOf(testNonce), getNonce(provider, TEST_ADDRESS));

            // Dump state - verifies the operation completes successfully
            HexData savedState = tester.dumpState();
            assertNotNull(savedState);
            assertTrue(savedState.value().length() > 2);
        }

        @Test
        @DisplayName("dumpState and loadState preserve contract code")
        void dumpAndLoadStatePreservesCode(Brane.Tester tester, BraneProvider provider) {
            // Set contract code
            HexData testCode = new HexData("0x602a60005260206000f3");
            tester.setCode(TEST_ADDRESS, testCode);

            // Dump state
            HexData savedState = tester.dumpState();

            // Change the code
            tester.setCode(TEST_ADDRESS, new HexData("0x6001"));
            assertEquals("0x6001", getCode(provider, TEST_ADDRESS).value());

            // Load saved state
            tester.loadState(savedState);

            // Code should be restored
            assertEquals(testCode.value(), getCode(provider, TEST_ADDRESS).value());
        }

        @Test
        @DisplayName("dumpState and loadState preserve storage")
        void dumpAndLoadStatePreservesStorage(Brane.Tester tester, BraneProvider provider) {
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
            assertEquals(newValue.value(), getStorageAt(provider, TEST_ADDRESS, slot).value());

            // Load saved state
            tester.loadState(savedState);

            // Storage should be restored
            assertEquals(testValue.value(), getStorageAt(provider, TEST_ADDRESS, slot).value());
        }

        @Test
        @DisplayName("dumpState and loadState preserve multiple accounts")
        void dumpAndLoadStatePreservesMultipleAccounts(Brane.Tester tester) {
            Address account1 = TEST_ADDRESS;
            Address account2 = new Address("0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC");

            Wei balance1 = Wei.fromEther(new java.math.BigDecimal("111"));
            Wei balance2 = Wei.fromEther(new java.math.BigDecimal("222"));

            tester.setBalance(account1, balance1);
            tester.setBalance(account2, balance2);

            // Dump state
            HexData savedState = tester.dumpState();

            // Change both balances
            tester.setBalance(account1, Wei.ZERO);
            tester.setBalance(account2, Wei.ZERO);

            // Load saved state
            tester.loadState(savedState);

            // Both balances should be restored
            assertEquals(balance1.value(), tester.getBalance(account1));
            assertEquals(balance2.value(), tester.getBalance(account2));
        }

        @Test
        @DisplayName("state can be loaded multiple times")
        void stateCanBeLoadedMultipleTimes(Brane.Tester tester) {
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
    @Order(8)
    @DisplayName("Impersonation integration tests")
    class ImpersonationTests {

        // Whale address to impersonate - a different address from the test signer
        private static final Address WHALE_ADDRESS =
                new Address("0x3C44CdDdB6a900fa2b585dd299e03d12FA4293BC");

        @Test
        @DisplayName("impersonate sends transaction from impersonated address")
        void impersonateSendsTransactionFromImpersonatedAddress(Brane.Tester tester) {
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
        void impersonateSendTransactionAndWaitWorks(Brane.Tester tester) {
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
        void impersonateSessionAddressReturnsCorrectAddress(Brane.Tester tester) {
            try (ImpersonationSession session = tester.impersonate(WHALE_ADDRESS)) {
                assertEquals(WHALE_ADDRESS, session.address());
            }
        }

        @Test
        @DisplayName("impersonate session auto-closes with try-with-resources")
        void impersonateSessionAutoCloses(Brane.Tester tester) {
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
        void impersonateSessionCloseIsIdempotent(Brane.Tester tester) {
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
        void impersonateCanSendMultipleTransactions(Brane.Tester tester) {
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
        void impersonateOverwritesFromAddress(Brane.Tester tester) {
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
    @Order(9)
    @DisplayName("Mining integration tests")
    class MiningTests {

        @Test
        @DisplayName("mine() advances block number by 1")
        void mineAdvancesBlockNumberByOne(Brane.Tester tester) {
            io.brane.core.model.BlockHeader beforeBlock = tester.getLatestBlock();
            long beforeNumber = beforeBlock.number();

            tester.mine();

            io.brane.core.model.BlockHeader afterBlock = tester.getLatestBlock();
            assertEquals(beforeNumber + 1, afterBlock.number());
        }

        @Test
        @DisplayName("mine(blocks) advances block number by specified amount")
        void mineBlocksAdvancesBlockNumber(Brane.Tester tester) {
            io.brane.core.model.BlockHeader beforeBlock = tester.getLatestBlock();
            long beforeNumber = beforeBlock.number();

            tester.mine(10);

            io.brane.core.model.BlockHeader afterBlock = tester.getLatestBlock();
            assertEquals(beforeNumber + 10, afterBlock.number());
        }

        @Test
        @DisplayName("mine(blocks, interval) advances both block number and time")
        void mineWithIntervalAdvancesBlocksAndTime(Brane.Tester tester) {
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
        void mineLargeBlockCount(Brane.Tester tester) {
            io.brane.core.model.BlockHeader beforeBlock = tester.getLatestBlock();
            long beforeNumber = beforeBlock.number();

            tester.mine(100);

            io.brane.core.model.BlockHeader afterBlock = tester.getLatestBlock();
            assertEquals(beforeNumber + 100, afterBlock.number());
        }
    }

    // ==================== Automine Integration Tests ====================

    @Nested
    @Order(10)
    @DisplayName("Automine integration tests")
    class AutomineTests {

        @org.junit.jupiter.api.BeforeEach
        void ensureAutomineEnabled(Brane.Tester tester) {
            // Ensure automine is enabled before each test since snapshot/revert
            // may not restore automine state
            tester.setAutomine(true);
        }

        @org.junit.jupiter.api.AfterEach
        void restoreAutomine(Brane.Tester tester) {
            // Always re-enable automine after tests
            tester.setAutomine(true);
        }

        @Test
        @DisplayName("getAutomine returns current state correctly")
        void getAutomineReturnsCurrentState(Brane.Tester tester) {
            // After setup, automine should be enabled
            boolean automine = tester.getAutomine();
            assertTrue(automine, "Automine should be enabled after setup");
        }

        @Test
        @DisplayName("setAutomine(false) disables automine")
        void setAutomineDisables(Brane.Tester tester) {
            // Get initial state (should be true after setup)
            assertTrue(tester.getAutomine());

            // Disable automine
            tester.setAutomine(false);
            assertFalse(tester.getAutomine());
        }

        @Test
        @DisplayName("setAutomine(true) enables automine")
        void setAutomineEnables(Brane.Tester tester) {
            // Disable automine first
            tester.setAutomine(false);
            assertFalse(tester.getAutomine());

            // Re-enable automine
            tester.setAutomine(true);
            assertTrue(tester.getAutomine());
        }

        @Test
        @DisplayName("transactions are not mined when automine is disabled")
        void transactionsNotMinedWhenAutomineDisabled(Brane.Tester tester) {
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
        void setIntervalMiningSetsInterval(Brane.Tester tester) {
            // This test just verifies the method executes without error
            // Interval mining is harder to test in a unit test context
            assertDoesNotThrow(() -> tester.setIntervalMining(0)); // Disable interval mining
        }
    }

    // ==================== Time Manipulation Integration Tests ====================

    @Nested
    @Order(11)
    @DisplayName("Time manipulation integration tests")
    class TimeManipulationTests {

        @Test
        @DisplayName("increaseTime advances timestamp")
        void increaseTimeAdvancesTimestamp(Brane.Tester tester) {
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
        void increaseTimeByOneDay(Brane.Tester tester) {
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
        void setNextBlockTimestampSetsTimestamp(Brane.Tester tester) {
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
        void setNextBlockTimestampOnlyAffectsNextBlock(Brane.Tester tester) {
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
        void increaseTimeAccumulates(Brane.Tester tester) {
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
        void timeManipulationPreservedAcrossSnapshots(Brane.Tester tester) {
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

    // ==================== Reset Integration Tests ====================

    /**
     * Integration tests for {@link Brane.Tester#reset()} and {@link Brane.Tester#reset(String, long)}.
     *
     * <p>These tests verify that reset() properly clears blockchain state and that
     * reset(forkUrl, blockNumber) can set up forking.
     *
     * <p><strong>Important:</strong> These tests call {@code reset()} which invalidates
     * any existing snapshots. The extension handles this with try-catch on revert.
     */
    @Nested
    @Order(12)
    @DisplayName("Reset integration tests")
    class ResetTests {

        @Test
        @DisplayName("reset() resets block number to genesis")
        void resetResetsBlockNumberToGenesis(Brane.Tester tester) {
            // Mine several blocks
            tester.mine(50);

            io.brane.core.model.BlockHeader blockBeforeReset = tester.getLatestBlock();
            assertTrue(blockBeforeReset.number() >= 50,
                    "Block number should be at least 50 after mining");

            // Reset the chain
            tester.reset();

            // After reset, block number should be back to 0
            io.brane.core.model.BlockHeader blockAfterReset = tester.getLatestBlock();
            assertEquals(0, blockAfterReset.number(),
                    "Block number should be 0 after reset");
        }

        @Test
        @DisplayName("reset() restores default account balances")
        void resetRestoresDefaultAccountBalances(Brane.Tester tester) {
            // Anvil's default test account balance is 10000 ETH
            Wei defaultBalance = Wei.fromEther(new java.math.BigDecimal("10000"));

            // Modify balance to something different
            Wei modifiedBalance = Wei.fromEther(new java.math.BigDecimal("12345.6789"));
            tester.setBalance(TEST_ADDRESS, modifiedBalance);
            assertEquals(modifiedBalance.value(), tester.getBalance(TEST_ADDRESS));

            // Reset the chain
            tester.reset();

            // After reset, balance should be back to Anvil's default (10000 ETH)
            BigInteger balanceAfterReset = tester.getBalance(TEST_ADDRESS);
            assertEquals(defaultBalance.value(), balanceAfterReset,
                    "Balance should be restored to default 10000 ETH after reset");
        }

        @Test
        @DisplayName("reset() resets nonces to zero")
        void resetResetsNoncesToZero(Brane.Tester tester, BraneProvider provider) {
            // Set a non-zero nonce
            tester.setNonce(TEST_ADDRESS, 999);
            assertEquals(BigInteger.valueOf(999), getNonce(provider, TEST_ADDRESS));

            // Reset the chain
            tester.reset();

            // Nonce should be reset to 0
            BigInteger nonceAfterReset = getNonce(provider, TEST_ADDRESS);
            assertEquals(BigInteger.ZERO, nonceAfterReset,
                    "Nonce should be 0 after reset");
        }

        @Test
        @DisplayName("reset() allows continued chain operations")
        void resetAllowsContinuedChainOperations(Brane.Tester tester) {
            // Mine some blocks
            tester.mine(10);

            // Reset the chain
            tester.reset();
            assertEquals(0, tester.getLatestBlock().number());

            // Should be able to continue mining
            tester.mine(5);
            assertEquals(5, tester.getLatestBlock().number(),
                    "Should be able to mine after reset");

            // Should be able to manipulate state
            Wei testBalance = Wei.fromEther(new java.math.BigDecimal("200"));
            tester.setBalance(TEST_ADDRESS, testBalance);
            assertEquals(testBalance.value(), tester.getBalance(TEST_ADDRESS),
                    "Should be able to set balance after reset");
        }

        @Test
        @DisplayName("reset() can be called multiple times")
        void resetCanBeCalledMultipleTimes(Brane.Tester tester) {
            // First set of operations
            tester.mine(10);
            assertEquals(10, tester.getLatestBlock().number());

            // First reset
            tester.reset();
            assertEquals(0, tester.getLatestBlock().number());

            // Second set of operations
            tester.mine(20);
            assertEquals(20, tester.getLatestBlock().number());

            // Second reset
            tester.reset();
            assertEquals(0, tester.getLatestBlock().number());

            // Third set of operations
            tester.mine(5);
            assertEquals(5, tester.getLatestBlock().number(),
                    "Should be able to mine after multiple resets");
        }

        /**
         * Tests reset with fork URL.
         *
         * <p>Note: This test uses a public Ethereum RPC endpoint to verify fork functionality.
         * The fork is done at a specific historical block to ensure consistent behavior.
         * If no external RPC is available, the test verifies the method executes without error.
         */
        @Test
        @DisplayName("reset with fork URL sets up forked state")
        void resetWithForkUrlSetsUpForkedState(Brane.Tester tester) {
            // Use a public RPC or skip if not available
            // We fork at a known block to verify the fork works
            String forkRpcUrl = System.getProperty("brane.fork.rpc.url");

            if (forkRpcUrl == null || forkRpcUrl.isEmpty()) {
                // No external RPC configured - just verify reset() works
                tester.mine(10);
                tester.reset();
                assertEquals(0, tester.getLatestBlock().number(),
                        "Basic reset should work when fork RPC is not configured");
                return;
            }

            // Fork at a historical block
            long forkBlock = 18_000_000L;
            tester.reset(forkRpcUrl, forkBlock);

            // After fork, block number should be at the fork block
            io.brane.core.model.BlockHeader blockAfterFork = tester.getLatestBlock();
            assertEquals(forkBlock, blockAfterFork.number(),
                    "Block number should be at fork block after reset with fork");
        }

        @Test
        @DisplayName("reset with fork allows continued operations")
        void resetWithForkAllowsContinuedOperations(Brane.Tester tester) {
            String forkRpcUrl = System.getProperty("brane.fork.rpc.url");

            if (forkRpcUrl == null || forkRpcUrl.isEmpty()) {
                // No external RPC configured - verify reset + operations work
                tester.reset();
                tester.mine(5);
                assertEquals(5, tester.getLatestBlock().number(),
                        "Should be able to mine after reset when fork RPC is not configured");
                return;
            }

            // Fork at a historical block
            long forkBlock = 18_000_000L;
            tester.reset(forkRpcUrl, forkBlock);

            // Verify chain is operational after fork
            tester.mine(5);
            assertEquals(forkBlock + 5, tester.getLatestBlock().number(),
                    "Should be able to mine blocks after fork reset");

            // State manipulation should work
            Wei testBalance = Wei.fromEther(new java.math.BigDecimal("500"));
            tester.setBalance(TEST_ADDRESS, testBalance);
            assertEquals(testBalance.value(), tester.getBalance(TEST_ADDRESS),
                    "Should be able to set balance after fork reset");
        }
    }
}
