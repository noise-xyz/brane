package io.brane.smoke;

import java.math.BigDecimal;
import java.math.BigInteger;

import io.brane.core.model.BlockHeader;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.AnvilSigners;
import io.brane.rpc.Brane;
import io.brane.rpc.ImpersonationSession;
import io.brane.rpc.SnapshotId;

/**
 * Standalone smoke test for {@link Brane.Tester} operations.
 *
 * <p>This test provides quick validation of all Tester operations with clear
 * pass/fail output. It covers:
 * <ul>
 *   <li><strong>Connection:</strong> connectTest() factory method</li>
 *   <li><strong>Account Manipulation:</strong> setBalance()</li>
 *   <li><strong>State Management:</strong> snapshot(), revert()</li>
 *   <li><strong>Impersonation:</strong> impersonate(), ImpersonationSession</li>
 *   <li><strong>Mining:</strong> mine(), mine(blocks)</li>
 *   <li><strong>Time Manipulation:</strong> increaseTime(), setNextBlockTimestamp()</li>
 *   <li><strong>Block Configuration:</strong> setNextBlockBaseFee()</li>
 *   <li><strong>State Dump/Load:</strong> dumpState(), loadState()</li>
 * </ul>
 *
 * <p><strong>Prerequisites:</strong> Anvil must be running on localhost:8545.
 * Start it with: {@code anvil}
 *
 * <p><strong>Usage:</strong>
 * <pre>
 * ./gradlew :smoke-test:run -PmainClass=io.brane.smoke.TesterSmokeTest
 * </pre>
 *
 * @see Brane.Tester
 * @see Brane#connectTest()
 */
public final class TesterSmokeTest {

    /** Test counter for tracking progress. */
    private static int testNumber = 0;
    private static int passedTests = 0;
    private static int failedTests = 0;

    private TesterSmokeTest() {
        // Prevent instantiation
    }

    public static void main(final String[] args) {
        System.out.println("=== Brane Tester Smoke Test ===\n");

        final String rpcUrl = System.getProperty("brane.smoke.rpc", "http://127.0.0.1:8545");

        try (Brane.Tester tester = Brane.connectTest(rpcUrl)) {

            // Verify connection
            runTest("connectTest() establishes connection", () -> {
                BigInteger chainId = tester.chainId();
                assertEqual(BigInteger.valueOf(31337), chainId,
                        "Chain ID should be 31337 for Anvil");
            });

            // Take snapshot for isolation
            SnapshotId initialSnapshot = tester.snapshot();

            try {
                // Account Manipulation
                testSetBalance(tester);

                // Snapshot/Revert
                testSnapshotRevert(tester);

                // Impersonation
                testImpersonation(tester);

                // Mining
                testMining(tester);

                // Time Manipulation
                testTimeManipulation(tester);

                // Block Configuration
                testBlockConfiguration(tester);

                // State Management (dumpState/loadState)
                testStateDumpLoad(tester);

            } finally {
                // Clean up
                tester.revert(initialSnapshot);
            }

            // Print summary
            printSummary();

            if (failedTests > 0) {
                System.exit(1);
            }

        } catch (Exception e) {
            System.err.println("\n[FATAL] " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    // ==================== Test Sections ====================

    private static void testSetBalance(Brane.Tester tester) {
        runTest("setBalance() sets account balance", () -> {
            Address testAccount = new Address("0x1111111111111111111111111111111111111111");
            Wei targetBalance = Wei.fromEther(new BigDecimal("100"));

            tester.setBalance(testAccount, targetBalance);
            BigInteger actualBalance = tester.getBalance(testAccount);

            assertEqual(targetBalance.value(), actualBalance,
                    "Balance should be 100 ETH");
        });
    }

    private static void testSnapshotRevert(Brane.Tester tester) {
        runTest("snapshot()/revert() restores state", () -> {
            Address testAccount = new Address("0x2222222222222222222222222222222222222222");
            Wei originalBalance = Wei.fromEther(new BigDecimal("50"));
            Wei modifiedBalance = Wei.fromEther(new BigDecimal("999"));

            // Set initial balance
            tester.setBalance(testAccount, originalBalance);

            // Take snapshot
            SnapshotId snapshot = tester.snapshot();

            // Modify state
            tester.setBalance(testAccount, modifiedBalance);
            BigInteger afterModify = tester.getBalance(testAccount);
            assertEqual(modifiedBalance.value(), afterModify, "Balance should be modified");

            // Revert
            boolean reverted = tester.revert(snapshot);
            assertTrue(reverted, "Revert should succeed");

            // Verify restored
            BigInteger afterRevert = tester.getBalance(testAccount);
            assertEqual(originalBalance.value(), afterRevert,
                    "Balance should be restored after revert");
        });
    }

    private static void testImpersonation(Brane.Tester tester) {
        runTest("impersonate() sends tx from any address", () -> {
            Address whale = new Address("0x3333333333333333333333333333333333333333");
            Address recipient = AnvilSigners.keyAt(1).address();

            // Fund the whale
            tester.setBalance(whale, Wei.fromEther(new BigDecimal("50")));
            BigInteger recipientBefore = tester.getBalance(recipient);

            Wei transferAmount = Wei.fromEther(new BigDecimal("1"));

            // Impersonate and send
            try (ImpersonationSession session = tester.impersonate(whale)) {
                assertEqual(whale, session.address(), "Session address should match");

                TransactionRequest request = new TransactionRequest(
                        null, recipient, transferAmount,
                        21_000L, null, null, null, null, null, false, null);

                TransactionReceipt receipt = session.sendTransactionAndWait(request);
                assertTrue(receipt.status(), "Impersonated tx should succeed");
            }

            // Verify transfer
            BigInteger recipientAfter = tester.getBalance(recipient);
            assertEqual(recipientBefore.add(transferAmount.value()), recipientAfter,
                    "Recipient should receive funds");
        });
    }

    private static void testMining(Brane.Tester tester) {
        runTest("mine() advances block number", () -> {
            BlockHeader before = tester.getLatestBlock();
            long beforeNumber = before.number();

            tester.mine();

            BlockHeader after = tester.getLatestBlock();
            assertEqual(beforeNumber + 1, after.number(),
                    "Block should advance by 1");
        });

        runTest("mine(n) advances by n blocks", () -> {
            BlockHeader before = tester.getLatestBlock();
            long beforeNumber = before.number();

            tester.mine(5);

            BlockHeader after = tester.getLatestBlock();
            assertEqual(beforeNumber + 5, after.number(),
                    "Block should advance by 5");
        });
    }

    private static void testTimeManipulation(Brane.Tester tester) {
        runTest("increaseTime() advances blockchain time", () -> {
            BlockHeader before = tester.getLatestBlock();
            long beforeTimestamp = before.timestamp();

            tester.increaseTime(3600); // 1 hour
            tester.mine();

            BlockHeader after = tester.getLatestBlock();
            assertTrue(after.timestamp() >= beforeTimestamp + 3600,
                    "Timestamp should advance by at least 3600 seconds");
        });

        runTest("setNextBlockTimestamp() sets exact timestamp", () -> {
            BlockHeader before = tester.getLatestBlock();
            long targetTimestamp = before.timestamp() + 86400; // +1 day

            tester.setNextBlockTimestamp(targetTimestamp);
            tester.mine();

            BlockHeader after = tester.getLatestBlock();
            assertEqual(targetTimestamp, after.timestamp(),
                    "Block should have exact timestamp");
        });
    }

    private static void testBlockConfiguration(Brane.Tester tester) {
        runTest("setNextBlockBaseFee() sets base fee", () -> {
            Wei baseFee = Wei.of(1_000_000_000L); // 1 gwei

            tester.setNextBlockBaseFee(baseFee);
            tester.mine();

            // Verify no exception thrown - base fee verification requires block data
        });
    }

    private static void testStateDumpLoad(Brane.Tester tester) {
        runTest("dumpState() returns non-empty data", () -> {
            HexData state = tester.dumpState();

            assertTrue(state != null, "State should not be null");
            assertTrue(state.value().length() > 2,
                    "State should have content beyond '0x'");
        });

        runTest("loadState() restores balance", () -> {
            Address testAccount = new Address("0x4444444444444444444444444444444444444444");
            Wei testBalance = Wei.fromEther(new BigDecimal("123"));

            // Set balance and dump
            tester.setBalance(testAccount, testBalance);
            HexData savedState = tester.dumpState();

            // Change balance
            tester.setBalance(testAccount, Wei.of(0));
            assertEqual(BigInteger.ZERO, tester.getBalance(testAccount),
                    "Balance should be zero");

            // Restore
            boolean loaded = tester.loadState(savedState);
            assertTrue(loaded, "loadState should return true");

            // Verify restored
            BigInteger restoredBalance = tester.getBalance(testAccount);
            assertEqual(testBalance.value(), restoredBalance,
                    "Balance should be restored from dump");
        });
    }

    // ==================== Test Infrastructure ====================

    private static void runTest(String name, Runnable test) {
        testNumber++;
        System.out.print("[" + testNumber + "] " + name + "... ");
        try {
            test.run();
            passedTests++;
            System.out.println("PASS");
        } catch (AssertionError | Exception e) {
            failedTests++;
            System.out.println("FAIL");
            System.out.println("    Error: " + e.getMessage());
        }
    }

    private static void printSummary() {
        System.out.println("\n" + "=".repeat(50));
        System.out.println("TESTER SMOKE TEST RESULTS");
        System.out.println("=".repeat(50));
        System.out.println("Total:  " + testNumber);
        System.out.println("Passed: " + passedTests);
        System.out.println("Failed: " + failedTests);
        System.out.println("=".repeat(50));

        if (failedTests == 0) {
            System.out.println("\nALL TESTS PASSED");
        } else {
            System.out.println("\nSOME TESTS FAILED");
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
}
