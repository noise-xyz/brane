// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.examples;

import java.math.BigDecimal;
import java.math.BigInteger;

import io.brane.core.AnsiColors;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Wei;
import io.brane.rpc.AnvilSigners;
import io.brane.rpc.Brane;
import io.brane.rpc.SnapshotId;

/**
 * Example demonstrating snapshot/revert pattern for test isolation.
 *
 * <p>Snapshots allow you to save the blockchain state at a point in time and
 * revert back to it later. This is essential for test isolation, ensuring each
 * test starts from a known state regardless of what previous tests did.
 *
 * <p>This example shows:
 * <ul>
 *   <li>{@link Brane.Tester#snapshot()} - Create a snapshot of current state</li>
 *   <li>{@link Brane.Tester#revert(SnapshotId)} - Revert to a previous snapshot</li>
 *   <li>{@link SnapshotId#revertUsing(Brane.Tester)} - Fluent revert API</li>
 *   <li>Nested snapshots for complex test scenarios</li>
 *   <li>Test isolation pattern used in real test suites</li>
 * </ul>
 *
 * <p><strong>Prerequisites:</strong> Anvil must be running on localhost:8545.
 * Start it with: {@code anvil}
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *     -PmainClass=io.brane.examples.TesterSnapshotExample \
 *     -Dbrane.examples.rpc=http://127.0.0.1:8545
 * </pre>
 *
 * @see Brane.Tester#snapshot()
 * @see Brane.Tester#revert(SnapshotId)
 * @see SnapshotId
 */
public final class TesterSnapshotExample {

    private TesterSnapshotExample() {
        // Prevent instantiation
    }

    public static void main(final String[] args) throws Exception {
        System.out.println("=== Brane Tester Snapshot/Revert Example ===\n");

        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");

        try (Brane.Tester tester = Brane.connectTest(rpcUrl)) {

            System.out.println("[1] Connected to test node");
            System.out.println("    Chain ID: " + tester.chainId());

            // 1. Basic snapshot and revert
            demonstrateBasicSnapshotRevert(tester);

            // 2. Nested snapshots for complex scenarios
            demonstrateNestedSnapshots(tester);

            // 3. Test isolation pattern
            demonstrateTestIsolationPattern(tester);

            // 4. Fluent API with SnapshotId.revertUsing()
            demonstrateFluentRevertApi(tester);

            System.out.println(AnsiColors.success("\nAll snapshot/revert operations completed successfully!"));
        }
    }

    /**
     * Demonstrates basic snapshot and revert workflow.
     *
     * <p>Shows how to capture state, make changes, and restore to the snapshot.
     */
    private static void demonstrateBasicSnapshotRevert(Brane.Tester tester) {
        System.out.println("\n[2] Basic Snapshot/Revert");

        Address testAddr = new Address("0x1111111111111111111111111111111111111111");

        // Get initial balance
        BigInteger initialBalance = tester.getBalance(testAddr);
        System.out.println("    Initial balance: " + Wei.of(initialBalance).toEther() + " ETH");

        // Create a snapshot
        SnapshotId snapshot = tester.snapshot();
        System.out.println("    Created snapshot: " + snapshot.value());

        // Modify state - set balance to 100 ETH
        Wei newBalance = Wei.fromEther(new BigDecimal("100"));
        tester.setBalance(testAddr, newBalance);
        System.out.println("    Modified balance: " + tester.getBalance(testAddr) + " wei");

        // Revert to snapshot
        boolean reverted = tester.revert(snapshot);
        System.out.println("    Revert successful: " + reverted);

        // Verify state is restored
        BigInteger restoredBalance = tester.getBalance(testAddr);
        System.out.println("    Restored balance: " + Wei.of(restoredBalance).toEther() + " ETH");

        if (restoredBalance.equals(initialBalance)) {
            System.out.println(AnsiColors.success("    State correctly restored!"));
        }
    }

    /**
     * Demonstrates nested snapshots for complex test scenarios.
     *
     * <p>Multiple snapshots can be created at different points to allow
     * reverting to various stages of test setup.
     */
    private static void demonstrateNestedSnapshots(Brane.Tester tester) {
        System.out.println("\n[3] Nested Snapshots");

        Address testAddr = new Address("0x2222222222222222222222222222222222222222");

        // Stage 0: Initial state
        BigInteger balance0 = tester.getBalance(testAddr);
        System.out.println("    Stage 0 - Initial: " + Wei.of(balance0).toEther() + " ETH");

        // Create snapshot at stage 0
        SnapshotId snap0 = tester.snapshot();

        // Stage 1: Set up with 50 ETH
        tester.setBalance(testAddr, Wei.fromEther(new BigDecimal("50")));
        BigInteger balance1 = tester.getBalance(testAddr);
        System.out.println("    Stage 1 - Setup: " + Wei.of(balance1).toEther() + " ETH");

        // Create snapshot at stage 1
        SnapshotId snap1 = tester.snapshot();

        // Stage 2: Modify to 200 ETH
        tester.setBalance(testAddr, Wei.fromEther(new BigDecimal("200")));
        BigInteger balance2 = tester.getBalance(testAddr);
        System.out.println("    Stage 2 - Modified: " + Wei.of(balance2).toEther() + " ETH");

        // Revert to snap1 (stage 1 - 50 ETH)
        tester.revert(snap1);
        System.out.println("    After revert to snap1: " + Wei.of(tester.getBalance(testAddr)).toEther() + " ETH");

        // Revert to snap0 (stage 0 - initial)
        tester.revert(snap0);
        System.out.println("    After revert to snap0: " + Wei.of(tester.getBalance(testAddr)).toEther() + " ETH");

        System.out.println(AnsiColors.success("    Nested snapshots work correctly!"));
    }

    /**
     * Demonstrates the test isolation pattern commonly used in test suites.
     *
     * <p>This pattern creates a snapshot before each test and reverts after,
     * ensuring each test starts from a clean, known state.
     */
    private static void demonstrateTestIsolationPattern(Brane.Tester tester) {
        System.out.println("\n[4] Test Isolation Pattern");
        System.out.println("    Simulating 3 isolated tests...\n");

        Address testAddr = AnvilSigners.keyAt(1).address();

        // Record the "pristine" state before any tests
        BigInteger pristineBalance = tester.getBalance(testAddr);
        System.out.println("    Pristine balance before tests: " + Wei.of(pristineBalance).toEther() + " ETH");

        // Simulate Test 1
        runIsolatedTest(tester, "Test 1: Transfer 1 ETH", () -> {
            // This test sends 1 ETH
            Address recipient = new Address("0x3333333333333333333333333333333333333333");
            TransactionRequest request = new TransactionRequest(
                    null, recipient, Wei.fromEther(BigDecimal.ONE),
                    21_000L, null, null, null, null, null, false, null);
            TransactionReceipt receipt = tester.sendTransactionAndWait(request);
            System.out.println("        Sent 1 ETH, tx: " + receipt.transactionHash().value().substring(0, 18) + "...");
        });

        // Simulate Test 2 (starts from pristine state, not affected by Test 1)
        runIsolatedTest(tester, "Test 2: Set balance to 999 ETH", () -> {
            // This test modifies the balance
            tester.setBalance(testAddr, Wei.fromEther(new BigDecimal("999")));
            System.out.println("        Balance now: " + Wei.of(tester.getBalance(testAddr)).toEther() + " ETH");
        });

        // Simulate Test 3 (starts from pristine state)
        runIsolatedTest(tester, "Test 3: Mine 10 blocks", () -> {
            // This test mines blocks
            long beforeBlock = tester.getLatestBlock().number();
            tester.mine(10);
            long afterBlock = tester.getLatestBlock().number();
            System.out.println("        Mined " + (afterBlock - beforeBlock) + " blocks");
        });

        // Verify pristine state is preserved after all tests
        BigInteger finalBalance = tester.getBalance(testAddr);
        System.out.println("\n    Final balance after all tests: " + Wei.of(finalBalance).toEther() + " ETH");

        if (finalBalance.equals(pristineBalance)) {
            System.out.println(AnsiColors.success("    Test isolation successful - state preserved!"));
        }
    }

    /**
     * Helper method implementing the test isolation pattern.
     *
     * <p>Creates a snapshot before the test, runs it, then reverts regardless
     * of whether the test succeeds or fails.
     */
    private static void runIsolatedTest(Brane.Tester tester, String testName, Runnable testBody) {
        System.out.println("    Running: " + testName);

        // Create snapshot before test
        SnapshotId snapshot = tester.snapshot();

        try {
            testBody.run();
            System.out.println(AnsiColors.success("        PASSED"));
        } catch (Exception e) {
            System.out.println(AnsiColors.error("        FAILED: " + e.getMessage()));
        } finally {
            // Always revert to ensure isolation
            tester.revert(snapshot);
        }
    }

    /**
     * Demonstrates the fluent revert API using SnapshotId.revertUsing().
     *
     * <p>The {@link SnapshotId#revertUsing(Brane.Tester)} method provides a
     * convenient fluent way to revert snapshots.
     */
    private static void demonstrateFluentRevertApi(Brane.Tester tester) {
        System.out.println("\n[5] Fluent Revert API");

        Address testAddr = new Address("0x4444444444444444444444444444444444444444");

        // Get initial state
        BigInteger initialBalance = tester.getBalance(testAddr);
        System.out.println("    Initial balance: " + Wei.of(initialBalance).toEther() + " ETH");

        // Create snapshot and modify state
        SnapshotId snapshot = tester.snapshot();
        tester.setBalance(testAddr, Wei.fromEther(new BigDecimal("500")));
        System.out.println("    After modification: " + Wei.of(tester.getBalance(testAddr)).toEther() + " ETH");

        // Use fluent API to revert
        boolean reverted = snapshot.revertUsing(tester);
        System.out.println("    snapshot.revertUsing(tester) returned: " + reverted);
        System.out.println("    After fluent revert: " + Wei.of(tester.getBalance(testAddr)).toEther() + " ETH");

        System.out.println(AnsiColors.success("    Fluent API works correctly!"));
    }
}
