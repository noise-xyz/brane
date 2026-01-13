// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.examples;

import java.math.BigDecimal;
import java.math.BigInteger;

import io.brane.core.AnsiColors;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.AnvilSigners;
import io.brane.rpc.Brane;
import io.brane.rpc.ImpersonationSession;
import io.brane.rpc.SnapshotId;

/**
 * Comprehensive integration test covering all {@link Brane.Tester} features.
 *
 * <p>This test validates all Tester capabilities in a single run:
 * <ul>
 *   <li><strong>Connection:</strong> {@link Brane#connectTest(String)}</li>
 *   <li><strong>Account Manipulation:</strong> setBalance, setCode, setNonce, setStorageAt</li>
 *   <li><strong>Mining:</strong> mine(), mine(blocks), mine(blocks, interval), mineAt(timestamp)</li>
 *   <li><strong>Time Manipulation:</strong> increaseTime, setNextBlockTimestamp</li>
 *   <li><strong>Snapshot/Revert:</strong> snapshot(), revert(snapshotId)</li>
 *   <li><strong>Impersonation:</strong> impersonate(), ImpersonationSession</li>
 *   <li><strong>Automine Control:</strong> getAutomine(), setAutomine()</li>
 *   <li><strong>Block Configuration:</strong> setNextBlockBaseFee, setBlockGasLimit, setCoinbase</li>
 *   <li><strong>State Management:</strong> dumpState(), loadState()</li>
 *   <li><strong>Reset:</strong> reset()</li>
 *   <li><strong>Transaction Sending:</strong> sendTransaction, sendTransactionAndWait</li>
 * </ul>
 *
 * <p><strong>Prerequisites:</strong> Anvil must be running on localhost:8545.
 * Start it with: {@code anvil}
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *     -PmainClass=io.brane.examples.TesterIntegrationTest \
 *     -Dbrane.examples.rpc=http://127.0.0.1:8545
 * </pre>
 *
 * @see Brane.Tester
 * @see Brane#connectTest(String)
 */
public final class TesterIntegrationTest {

    /** Test counter for tracking progress. */
    private static int testNumber = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;

    private TesterIntegrationTest() {
        // Prevent instantiation
    }

    public static void main(final String[] args) {
        System.out.println("=== Brane Tester Comprehensive Integration Test ===\n");

        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");

        try (Brane.Tester tester = Brane.connectTest(rpcUrl)) {

            System.out.println("Connected to test node");
            System.out.println("Chain ID: " + tester.chainId());
            System.out.println("Signer address: " + tester.signer().address().value());
            System.out.println();

            // Take initial snapshot for cleanup between test groups
            SnapshotId initialSnapshot = tester.snapshot();

            try {
                // Note: Anvil's revert() may not restore automine state, so we ensure
                // automine is enabled after each revert

                // 1. Account Manipulation Tests
                testAccountManipulation(tester);
                tester.revert(initialSnapshot);
                initialSnapshot = tester.snapshot();
                tester.setAutomine(true);

                // 2. Mining Tests
                testMining(tester);
                tester.revert(initialSnapshot);
                initialSnapshot = tester.snapshot();
                tester.setAutomine(true);

                // 3. Time Manipulation Tests
                testTimeManipulation(tester);
                tester.revert(initialSnapshot);
                initialSnapshot = tester.snapshot();
                tester.setAutomine(true);

                // 4. Snapshot/Revert Tests
                testSnapshotRevert(tester);
                tester.revert(initialSnapshot);
                initialSnapshot = tester.snapshot();
                tester.setAutomine(true);

                // 5. Impersonation Tests
                testImpersonation(tester);
                tester.revert(initialSnapshot);
                initialSnapshot = tester.snapshot();
                tester.setAutomine(true);

                // 6. Automine Control Tests
                testAutomineControl(tester);
                tester.revert(initialSnapshot);
                initialSnapshot = tester.snapshot();
                tester.setAutomine(true);

                // 7. Block Configuration Tests
                testBlockConfiguration(tester);
                tester.revert(initialSnapshot);
                initialSnapshot = tester.snapshot();
                tester.setAutomine(true);

                // 8. State Management Tests (dumpState/loadState)
                testStateManagement(tester);
                tester.revert(initialSnapshot);
                initialSnapshot = tester.snapshot();
                tester.setAutomine(true);

                // 9. Transaction Sending Tests
                testTransactionSending(tester);
                tester.revert(initialSnapshot);
                initialSnapshot = tester.snapshot();
                tester.setAutomine(true);

                // 10. Reset Tests (do this last as it affects all state)
                testReset(tester);

            } finally {
                // After testReset() runs, all snapshots are invalidated, so we can't
                // reliably revert. The best we can do is ensure automine is enabled.
                // Since this test runs last in the integration suite, leaving the chain
                // in reset state is acceptable.
                try {
                    tester.setAutomine(true);
                } catch (Exception ignored) {
                    // Ignore - chain may be in inconsistent state after reset tests
                }
            }

            // Print summary
            System.out.println("\n" + "=".repeat(50));
            System.out.println("Test Summary:");
            System.out.println("  Total: " + testNumber);
            System.out.println("  " + AnsiColors.success("Passed: " + passedTests));
            if (failedTests > 0) {
                System.out.println("  " + AnsiColors.error("Failed: " + failedTests));
            } else {
                System.out.println("  Failed: 0");
            }
            System.out.println("=".repeat(50));

            if (failedTests > 0) {
                System.out.println(AnsiColors.error("\nSome tests failed!"));
                System.exit(1);
            } else {
                System.out.println(AnsiColors.success("\nAll tests passed!"));
            }

        } catch (Exception e) {
            System.err.println(AnsiColors.error("Fatal error: " + e.getMessage()));
            e.printStackTrace();
            System.exit(1);
        }
    }

    // ==================== Account Manipulation Tests ====================

    private static void testAccountManipulation(Brane.Tester tester) {
        printSection("Account Manipulation Tests");

        // Test setBalance
        runTest("setBalance sets account balance", () -> {
            Address testAddr = new Address("0x1111111111111111111111111111111111111111");
            Wei newBalance = Wei.fromEther(new BigDecimal("100"));

            tester.setBalance(testAddr, newBalance);

            BigInteger actualBalance = tester.getBalance(testAddr);
            assertEqual(newBalance.value(), actualBalance, "Balance should match");
        });

        runTest("setBalance to zero", () -> {
            Address testAddr = new Address("0x1111111111111111111111111111111111111111");
            tester.setBalance(testAddr, Wei.fromEther(new BigDecimal("50")));

            tester.setBalance(testAddr, Wei.ZERO);

            BigInteger actualBalance = tester.getBalance(testAddr);
            assertEqual(BigInteger.ZERO, actualBalance, "Balance should be zero");
        });

        // Test setCode
        runTest("setCode deploys bytecode at address", () -> {
            Address testAddr = new Address("0x2222222222222222222222222222222222222222");
            HexData code = new HexData("0x602a60005260206000f3"); // Simple bytecode

            tester.setCode(testAddr, code);

            // Verify code is set (would need getCode to fully verify)
            // For now, just verify no exception is thrown
        });

        // Test setNonce
        runTest("setNonce changes account nonce", () -> {
            Address testAddr = new Address("0x3333333333333333333333333333333333333333");

            tester.setNonce(testAddr, 42);

            // Nonce verification would require getNonce - for now verify no exception
        });

        // Test setStorageAt
        runTest("setStorageAt sets storage slot", () -> {
            Address testAddr = new Address("0x4444444444444444444444444444444444444444");
            Hash slot = new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");
            Hash value = new Hash("0x000000000000000000000000000000000000000000000000000000000000002a");

            // Set code first (storage requires contract)
            tester.setCode(testAddr, new HexData("0x60016000"));
            tester.setStorageAt(testAddr, slot, value);

            // Storage verification would require getStorageAt - for now verify no exception
        });
    }

    // ==================== Mining Tests ====================

    private static void testMining(Brane.Tester tester) {
        printSection("Mining Tests");

        runTest("mine() advances block number by 1", () -> {
            BlockHeader before = tester.getLatestBlock();
            long beforeNumber = before.number();

            tester.mine();

            BlockHeader after = tester.getLatestBlock();
            assertEqual(beforeNumber + 1, after.number(), "Block should advance by 1");
        });

        runTest("mine(blocks) advances by specified amount", () -> {
            BlockHeader before = tester.getLatestBlock();
            long beforeNumber = before.number();

            tester.mine(10);

            BlockHeader after = tester.getLatestBlock();
            assertEqual(beforeNumber + 10, after.number(), "Block should advance by 10");
        });

        runTest("mine(blocks, interval) advances time with blocks", () -> {
            BlockHeader before = tester.getLatestBlock();
            long beforeNumber = before.number();
            long beforeTimestamp = before.timestamp();

            tester.mine(5, 12);

            BlockHeader after = tester.getLatestBlock();
            assertEqual(beforeNumber + 5, after.number(), "Block should advance by 5");
            assertTrue(after.timestamp() >= beforeTimestamp + 48, // (5-1)*12 = 48 minimum
                    "Timestamp should advance by at least 48 seconds");
        });

        runTest("mineAt(timestamp) mines block at specific time", () -> {
            BlockHeader before = tester.getLatestBlock();
            long futureTimestamp = before.timestamp() + 1000;

            tester.mineAt(futureTimestamp);

            BlockHeader after = tester.getLatestBlock();
            // mineAt should mine a block with timestamp >= the specified value
            assertTrue(after.timestamp() >= futureTimestamp,
                    "Block should have timestamp >= " + futureTimestamp + " (actual: " + after.timestamp() + ")");
            assertTrue(after.number() > before.number(), "Should have mined a block");
        });
    }

    // ==================== Time Manipulation Tests ====================

    private static void testTimeManipulation(Brane.Tester tester) {
        printSection("Time Manipulation Tests");

        runTest("increaseTime advances blockchain time", () -> {
            BlockHeader before = tester.getLatestBlock();
            long beforeTimestamp = before.timestamp();

            tester.increaseTime(3600); // 1 hour
            tester.mine();

            BlockHeader after = tester.getLatestBlock();
            assertTrue(after.timestamp() >= beforeTimestamp + 3600,
                    "Timestamp should advance by at least 3600 seconds");
        });

        runTest("setNextBlockTimestamp sets exact timestamp", () -> {
            BlockHeader before = tester.getLatestBlock();
            long targetTimestamp = before.timestamp() + 86400; // +1 day

            tester.setNextBlockTimestamp(targetTimestamp);
            tester.mine();

            BlockHeader after = tester.getLatestBlock();
            assertEqual(targetTimestamp, after.timestamp(), "Block should have exact timestamp");
        });

        runTest("setNextBlockTimestamp only affects next block", () -> {
            BlockHeader before = tester.getLatestBlock();
            long targetTimestamp = before.timestamp() + 1000;

            tester.setNextBlockTimestamp(targetTimestamp);
            tester.mine();

            BlockHeader first = tester.getLatestBlock();
            assertEqual(targetTimestamp, first.timestamp(), "First block should have set timestamp");

            // Mine another block with a different timestamp to verify first setNextBlockTimestamp
            // was consumed and doesn't persist
            long secondTimestamp = targetTimestamp + 500;
            tester.setNextBlockTimestamp(secondTimestamp);
            tester.mine();
            BlockHeader second = tester.getLatestBlock();

            assertEqual(secondTimestamp, second.timestamp(), "Second block should have its own set timestamp");
            assertTrue(second.timestamp() > first.timestamp(),
                    "Second block should have later timestamp");
        });
    }

    // ==================== Snapshot/Revert Tests ====================

    private static void testSnapshotRevert(Brane.Tester tester) {
        printSection("Snapshot/Revert Tests");

        runTest("snapshot and revert restores balance", () -> {
            Address testAddr = new Address("0x5555555555555555555555555555555555555555");

            BigInteger originalBalance = tester.getBalance(testAddr);
            SnapshotId snapshot = tester.snapshot();

            tester.setBalance(testAddr, Wei.fromEther(new BigDecimal("999")));
            BigInteger modifiedBalance = tester.getBalance(testAddr);
            assertTrue(!modifiedBalance.equals(originalBalance), "Balance should be modified");

            boolean reverted = tester.revert(snapshot);
            assertTrue(reverted, "Revert should succeed");

            BigInteger restoredBalance = tester.getBalance(testAddr);
            assertEqual(originalBalance, restoredBalance, "Balance should be restored");
        });

        runTest("snapshot and revert restores block number", () -> {
            BlockHeader original = tester.getLatestBlock();
            SnapshotId snapshot = tester.snapshot();

            tester.mine(10);
            BlockHeader afterMining = tester.getLatestBlock();
            assertEqual(original.number() + 10, afterMining.number(), "Should mine 10 blocks");

            tester.revert(snapshot);

            BlockHeader restored = tester.getLatestBlock();
            assertEqual(original.number(), restored.number(), "Block number should be restored");
        });

        runTest("nested snapshots work correctly", () -> {
            Address testAddr = new Address("0x6666666666666666666666666666666666666666");

            BigInteger balance0 = tester.getBalance(testAddr);
            SnapshotId snap0 = tester.snapshot();

            tester.setBalance(testAddr, Wei.fromEther(new BigDecimal("100")));
            SnapshotId snap1 = tester.snapshot();

            tester.setBalance(testAddr, Wei.fromEther(new BigDecimal("200")));
            BigInteger balance2 = tester.getBalance(testAddr);
            assertEqual(Wei.fromEther(new BigDecimal("200")).value(), balance2, "Balance should be 200");

            tester.revert(snap1);
            BigInteger balanceAfterSnap1 = tester.getBalance(testAddr);
            assertEqual(Wei.fromEther(new BigDecimal("100")).value(), balanceAfterSnap1, "Balance should be 100");

            tester.revert(snap0);
            BigInteger balanceAfterSnap0 = tester.getBalance(testAddr);
            assertEqual(balance0, balanceAfterSnap0, "Balance should be original");
        });

        runTest("SnapshotId.revertUsing fluent API", () -> {
            Address testAddr = new Address("0x7777777777777777777777777777777777777777");
            BigInteger originalBalance = tester.getBalance(testAddr);

            SnapshotId snapshot = tester.snapshot();
            tester.setBalance(testAddr, Wei.fromEther(new BigDecimal("500")));

            boolean reverted = snapshot.revertUsing(tester);
            assertTrue(reverted, "Fluent revert should succeed");

            BigInteger restoredBalance = tester.getBalance(testAddr);
            assertEqual(originalBalance, restoredBalance, "Balance should be restored via fluent API");
        });
    }

    // ==================== Impersonation Tests ====================

    private static void testImpersonation(Brane.Tester tester) {
        printSection("Impersonation Tests");

        runTest("impersonate sends transaction from impersonated address", () -> {
            Address whale = new Address("0x8888888888888888888888888888888888888888");
            Address recipient = AnvilSigners.keyAt(1).address();

            // Fund the whale
            tester.setBalance(whale, Wei.fromEther(new BigDecimal("100")));
            BigInteger recipientBalanceBefore = tester.getBalance(recipient);

            Wei transferAmount = Wei.fromEther(new BigDecimal("1"));

            try (ImpersonationSession session = tester.impersonate(whale)) {
                assertEqual(whale, session.address(), "Session address should match whale");

                TransactionRequest request = new TransactionRequest(
                        null, recipient, transferAmount,
                        21_000L, null, null, null, null, null, false, null);

                TransactionReceipt receipt = session.sendTransactionAndWait(request);
                assertTrue(receipt.status(), "Transaction should succeed");
            }

            BigInteger recipientBalanceAfter = tester.getBalance(recipient);
            assertEqual(recipientBalanceBefore.add(transferAmount.value()), recipientBalanceAfter,
                    "Recipient should receive funds");
        });

        runTest("impersonation session auto-closes", () -> {
            Address target = new Address("0x9999999999999999999999999999999999999999");
            tester.setBalance(target, Wei.fromEther(new BigDecimal("10")));

            ImpersonationSession session = tester.impersonate(target);
            session.close();

            // After close, operations should fail
            TransactionRequest request = new TransactionRequest(
                    null, AnvilSigners.keyAt(0).address(), Wei.fromEther(BigDecimal.ONE),
                    21_000L, null, null, null, null, null, false, null);

            boolean threwException = false;
            try {
                session.sendTransaction(request);
            } catch (IllegalStateException e) {
                threwException = true;
                assertTrue(e.getMessage().contains("closed"), "Exception should mention closed");
            }
            assertTrue(threwException, "Should throw IllegalStateException after close");
        });

        runTest("multiple sequential impersonations", () -> {
            Address alice = new Address("0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA");
            Address bob = new Address("0xBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBBB");

            tester.setBalance(alice, Wei.fromEther(new BigDecimal("100")));
            tester.setBalance(bob, Wei.fromEther(new BigDecimal("100")));

            // Alice sends to bob
            try (ImpersonationSession aliceSession = tester.impersonate(alice)) {
                TransactionRequest request = new TransactionRequest(
                        null, bob, Wei.fromEther(new BigDecimal("10")),
                        21_000L, null, null, null, null, null, false, null);
                TransactionReceipt receipt = aliceSession.sendTransactionAndWait(request);
                assertTrue(receipt.status(), "Alice's transaction should succeed");
            }

            // Bob sends to alice
            try (ImpersonationSession bobSession = tester.impersonate(bob)) {
                TransactionRequest request = new TransactionRequest(
                        null, alice, Wei.fromEther(new BigDecimal("5")),
                        21_000L, null, null, null, null, null, false, null);
                TransactionReceipt receipt = bobSession.sendTransactionAndWait(request);
                assertTrue(receipt.status(), "Bob's transaction should succeed");
            }
        });
    }

    // ==================== Automine Control Tests ====================

    private static void testAutomineControl(Brane.Tester tester) {
        printSection("Automine Control Tests");

        // Ensure automine is enabled at start of test section
        tester.setAutomine(true);

        runTest("getAutomine returns current state", () -> {
            // Default should be enabled
            boolean automine = tester.getAutomine();
            assertTrue(automine, "Automine should be enabled by default");
        });

        runTest("setAutomine(false) disables automatic mining", () -> {
            try {
                tester.setAutomine(false);
                assertFalse(tester.getAutomine(), "Automine should be disabled");
            } finally {
                // Re-enable for subsequent tests
                tester.setAutomine(true);
            }
        });

        runTest("transactions pending when automine disabled", () -> {
            try {
                tester.setAutomine(false);

                BlockHeader before = tester.getLatestBlock();
                long beforeNumber = before.number();

                // Send a transaction
                Address recipient = AnvilSigners.keyAt(1).address();
                TransactionRequest request = new TransactionRequest(
                        null, recipient, Wei.fromEther(new BigDecimal("0.001")),
                        21_000L, null, null, null, null, null, false, null);

                Hash txHash = tester.sendTransaction(request);
                assertTrue(txHash != null && !txHash.value().isEmpty(), "Should return tx hash");

                // Block should NOT have advanced
                BlockHeader afterSend = tester.getLatestBlock();
                assertEqual(beforeNumber, afterSend.number(), "Block should not advance with automine off");

                // Mine manually
                tester.mine();

                BlockHeader afterMine = tester.getLatestBlock();
                assertEqual(beforeNumber + 1, afterMine.number(), "Block should advance after manual mine");
            } finally {
                // Re-enable automine
                tester.setAutomine(true);
            }
        });

        runTest("setIntervalMining sets interval", () -> {
            // Just verify no exception - interval mining is harder to test
            tester.setIntervalMining(0); // Disable
        });

        // Ensure automine is enabled at end of test section
        tester.setAutomine(true);
    }

    // ==================== Block Configuration Tests ====================

    private static void testBlockConfiguration(Brane.Tester tester) {
        printSection("Block Configuration Tests");

        runTest("setNextBlockBaseFee sets base fee", () -> {
            Wei baseFee = Wei.of(1_000_000_000L); // 1 gwei

            tester.setNextBlockBaseFee(baseFee);
            tester.mine();

            // Base fee verification would require reading block - verify no exception
        });

        runTest("setBlockGasLimit sets gas limit", () -> {
            BigInteger gasLimit = BigInteger.valueOf(30_000_000L);

            tester.setBlockGasLimit(gasLimit);
            tester.mine();

            // Verify the gas limit was set (would need to check block header)
        });

        runTest("setCoinbase sets coinbase address", () -> {
            Address coinbase = new Address("0xCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCCC");

            tester.setCoinbase(coinbase);
            tester.mine();

            // Coinbase verification would require reading block - verify no exception
        });
    }

    // ==================== State Management Tests ====================

    private static void testStateManagement(Brane.Tester tester) {
        printSection("State Management Tests");

        runTest("dumpState returns non-empty data", () -> {
            HexData state = tester.dumpState();

            assertTrue(state != null, "State should not be null");
            assertTrue(state.value() != null, "State value should not be null");
            assertTrue(state.value().length() > 2, "State should have content beyond '0x'");
        });

        runTest("loadState returns true on valid state", () -> {
            HexData state = tester.dumpState();

            boolean loaded = tester.loadState(state);
            assertTrue(loaded, "loadState should return true for valid state");
        });

        runTest("dumpState and loadState preserve balance", () -> {
            Address testAddr = new Address("0xDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");
            Wei testBalance = Wei.fromEther(new BigDecimal("123.456"));

            tester.setBalance(testAddr, testBalance);
            HexData savedState = tester.dumpState();

            // Change the balance
            tester.setBalance(testAddr, Wei.ZERO);
            assertEqual(BigInteger.ZERO, tester.getBalance(testAddr), "Balance should be zero");

            // Restore state
            tester.loadState(savedState);

            BigInteger restoredBalance = tester.getBalance(testAddr);
            assertEqual(testBalance.value(), restoredBalance, "Balance should be restored from dump");
        });
    }

    // ==================== Transaction Sending Tests ====================

    private static void testTransactionSending(Brane.Tester tester) {
        printSection("Transaction Sending Tests");

        runTest("sendTransaction returns hash immediately", () -> {
            Address recipient = AnvilSigners.keyAt(1).address();
            TransactionRequest request = new TransactionRequest(
                    null, recipient, Wei.fromEther(new BigDecimal("0.01")),
                    21_000L, null, null, null, null, null, false, null);

            Hash txHash = tester.sendTransaction(request);

            assertTrue(txHash != null, "Transaction hash should not be null");
            assertTrue(txHash.value().startsWith("0x"), "Hash should start with 0x");
            assertEqual(66, txHash.value().length(), "Hash should be 66 chars (0x + 64 hex)");
        });

        runTest("sendTransactionAndWait returns receipt", () -> {
            Address recipient = AnvilSigners.keyAt(2).address();
            BigInteger balanceBefore = tester.getBalance(recipient);

            Wei amount = Wei.fromEther(new BigDecimal("0.5"));
            TransactionRequest request = new TransactionRequest(
                    null, recipient, amount,
                    21_000L, null, null, null, null, null, false, null);

            TransactionReceipt receipt = tester.sendTransactionAndWait(request);

            assertTrue(receipt != null, "Receipt should not be null");
            assertTrue(receipt.status(), "Transaction should succeed");
            assertTrue(receipt.blockNumber() > 0, "Block number should be positive");

            BigInteger balanceAfter = tester.getBalance(recipient);
            assertEqual(balanceBefore.add(amount.value()), balanceAfter, "Recipient should receive funds");
        });

        runTest("sendTransactionAndWait with custom timeout", () -> {
            Address recipient = AnvilSigners.keyAt(3).address();
            TransactionRequest request = new TransactionRequest(
                    null, recipient, Wei.fromEther(new BigDecimal("0.1")),
                    21_000L, null, null, null, null, null, false, null);

            TransactionReceipt receipt = tester.sendTransactionAndWait(request, 30_000L, 500L);

            assertTrue(receipt != null, "Receipt should not be null");
            assertTrue(receipt.status(), "Transaction should succeed");
        });

        runTest("asSigner returns functional signer", () -> {
            Brane.Signer signer = tester.asSigner();

            assertTrue(signer != null, "Signer should not be null");
            assertTrue(signer.signer().address().equals(tester.signer().address()),
                    "Signer address should match");
        });
    }

    // ==================== Reset Tests ====================

    private static void testReset(Brane.Tester tester) {
        printSection("Reset Tests");

        runTest("reset() resets chain to initial state", () -> {
            // Mine some blocks
            tester.mine(50);
            BlockHeader beforeReset = tester.getLatestBlock();
            assertTrue(beforeReset.number() >= 50, "Should have mined blocks");

            // Modify state
            Address testAddr = new Address("0xEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEEE");
            tester.setBalance(testAddr, Wei.fromEther(new BigDecimal("999")));

            // Reset
            tester.reset();

            // Block number should be 0
            BlockHeader afterReset = tester.getLatestBlock();
            assertEqual(0L, afterReset.number(), "Block number should be 0 after reset");

            // Balance should be 0 (address didn't exist in initial state)
            BigInteger balanceAfterReset = tester.getBalance(testAddr);
            assertEqual(BigInteger.ZERO, balanceAfterReset, "Balance should be 0 after reset");
        });

        runTest("reset() allows continued operations", () -> {
            tester.reset();

            // Should be able to mine
            tester.mine(5);
            assertEqual(5L, tester.getLatestBlock().number(), "Should mine 5 blocks after reset");

            // Should be able to set balance
            Address testAddr = new Address("0xFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFFF");
            tester.setBalance(testAddr, Wei.fromEther(new BigDecimal("100")));
            assertEqual(Wei.fromEther(new BigDecimal("100")).value(), tester.getBalance(testAddr),
                    "Should set balance after reset");
        });
    }

    // ==================== Test Infrastructure ====================

    private static void printSection(String name) {
        System.out.println("\n" + "-".repeat(50));
        System.out.println(name);
        System.out.println("-".repeat(50));
    }

    private static void runTest(String name, Runnable test) {
        testNumber++;
        System.out.print("[" + testNumber + "] " + name + "... ");
        try {
            test.run();
            passedTests++;
            System.out.println(AnsiColors.success("PASSED"));
        } catch (AssertionError | Exception e) {
            failedTests++;
            System.out.println(AnsiColors.error("FAILED"));
            System.out.println("    " + AnsiColors.error("Error: " + e.getMessage()));
        }
    }

    private static void assertEqual(Object expected, Object actual, String message) {
        if (!expected.equals(actual)) {
            throw new AssertionError(message + " (expected: " + expected + ", actual: " + actual + ")");
        }
    }

    private static void assertEqual(long expected, long actual, String message) {
        if (expected != actual) {
            throw new AssertionError(message + " (expected: " + expected + ", actual: " + actual + ")");
        }
    }

    private static void assertTrue(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    private static void assertFalse(boolean condition, String message) {
        if (condition) {
            throw new AssertionError(message);
        }
    }
}
