// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.examples;

import io.brane.core.AnsiColors;
import io.brane.core.model.BlockHeader;
import io.brane.rpc.Brane;
import io.brane.rpc.SnapshotId;

/**
 * Example demonstrating time manipulation for time-locked contract testing.
 *
 * <p>Time manipulation is essential for testing time-dependent smart contracts:
 * <ul>
 *   <li>Vesting contracts with unlock schedules</li>
 *   <li>Timelocks and governance delays</li>
 *   <li>Staking rewards with time-based accrual</li>
 *   <li>Auction and deadline-based contracts</li>
 *   <li>Interest calculations and yield farming</li>
 * </ul>
 *
 * <p>This example shows:
 * <ul>
 *   <li>{@link Brane.Tester#increaseTime(long)} - Advance time by a duration</li>
 *   <li>{@link Brane.Tester#setNextBlockTimestamp(long)} - Set exact timestamp for next block</li>
 *   <li>Combined time manipulation with mining</li>
 *   <li>Testing patterns for time-locked functionality</li>
 * </ul>
 *
 * <p><strong>Prerequisites:</strong> Anvil must be running on localhost:8545.
 * Start it with: {@code anvil}
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *     -PmainClass=io.brane.examples.TesterTimeExample \
 *     -Dbrane.examples.rpc=http://127.0.0.1:8545
 * </pre>
 *
 * @see Brane.Tester#increaseTime(long)
 * @see Brane.Tester#setNextBlockTimestamp(long)
 */
public final class TesterTimeExample {

    /** One hour in seconds. */
    private static final long ONE_HOUR = 3600L;

    /** One day in seconds. */
    private static final long ONE_DAY = 86400L;

    /** 30 days in seconds (common vesting period). */
    private static final long THIRTY_DAYS = 30 * ONE_DAY;

    private TesterTimeExample() {
        // Prevent instantiation
    }

    public static void main(final String[] args) throws Exception {
        System.out.println("=== Brane Tester Time Manipulation Example ===\n");

        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");

        try (Brane.Tester tester = Brane.connectTest(rpcUrl)) {

            System.out.println("[1] Connected to test node");
            System.out.println("    Chain ID: " + tester.chainId());

            // 1. Basic increaseTime usage
            demonstrateIncreaseTime(tester);

            // 2. Basic setNextBlockTimestamp usage
            demonstrateSetNextBlockTimestamp(tester);

            // 3. Time-locked contract simulation
            demonstrateTimelockPattern(tester);

            // 4. Vesting schedule simulation
            demonstrateVestingPattern(tester);

            System.out.println(AnsiColors.success("\nAll time manipulation examples completed successfully!"));
        }
    }

    /**
     * Demonstrates basic increaseTime usage.
     *
     * <p>increaseTime advances the blockchain time by a relative amount.
     * The new timestamp takes effect when the next block is mined.
     */
    private static void demonstrateIncreaseTime(Brane.Tester tester) {
        System.out.println("\n[2] increaseTime - Relative Time Advancement");

        SnapshotId snapshot = tester.snapshot();

        try {
            // Get current block timestamp
            BlockHeader before = tester.getLatestBlock();
            long timestampBefore = before.timestamp();
            System.out.println("    Current block: " + before.number());
            System.out.println("    Current timestamp: " + timestampBefore + " (" + formatTimestamp(timestampBefore) + ")");

            // Advance time by 1 hour
            System.out.println("\n    Calling increaseTime(" + ONE_HOUR + ") // 1 hour");
            tester.increaseTime(ONE_HOUR);

            // Mine a block to see the effect
            tester.mine();
            BlockHeader after = tester.getLatestBlock();
            long timestampAfter = after.timestamp();

            System.out.println("    After mining:");
            System.out.println("    New block: " + after.number());
            System.out.println("    New timestamp: " + timestampAfter + " (" + formatTimestamp(timestampAfter) + ")");

            long elapsed = timestampAfter - timestampBefore;
            System.out.println("    Time elapsed: " + elapsed + " seconds (~" + (elapsed / 60) + " minutes)");

            System.out.println(AnsiColors.success("    increaseTime works correctly!"));

        } finally {
            tester.revert(snapshot);
        }
    }

    /**
     * Demonstrates basic setNextBlockTimestamp usage.
     *
     * <p>setNextBlockTimestamp sets an exact timestamp for the next block.
     * This is useful when you need to test at a specific point in time.
     */
    private static void demonstrateSetNextBlockTimestamp(Brane.Tester tester) {
        System.out.println("\n[3] setNextBlockTimestamp - Absolute Time Setting");

        SnapshotId snapshot = tester.snapshot();

        try {
            // Get current timestamp
            BlockHeader before = tester.getLatestBlock();
            long timestampBefore = before.timestamp();
            System.out.println("    Current timestamp: " + timestampBefore);

            // Set a specific future timestamp (current + 1 day)
            long targetTimestamp = timestampBefore + ONE_DAY;
            System.out.println("\n    Calling setNextBlockTimestamp(" + targetTimestamp + ")");
            System.out.println("    Target: " + formatTimestamp(targetTimestamp));

            tester.setNextBlockTimestamp(targetTimestamp);

            // Mine a block to see the effect
            tester.mine();
            BlockHeader after = tester.getLatestBlock();
            long timestampAfter = after.timestamp();

            System.out.println("    After mining:");
            System.out.println("    Actual timestamp: " + timestampAfter);
            System.out.println("    Expected timestamp: " + targetTimestamp);

            if (timestampAfter == targetTimestamp) {
                System.out.println(AnsiColors.success("    setNextBlockTimestamp works correctly!"));
            }

        } finally {
            tester.revert(snapshot);
        }
    }

    /**
     * Demonstrates a timelock pattern commonly used in governance.
     *
     * <p>Simulates a scenario where an action is queued and can only be
     * executed after a delay period (e.g., 24 hours).
     */
    private static void demonstrateTimelockPattern(Brane.Tester tester) {
        System.out.println("\n[4] Timelock Pattern - Governance Delay Simulation");
        System.out.println("    Simulating a 24-hour governance timelock\n");

        SnapshotId snapshot = tester.snapshot();

        try {
            // Step 1: "Queue" an action (record the current time)
            BlockHeader queueBlock = tester.getLatestBlock();
            long queueTime = queueBlock.timestamp();
            long executeAfter = queueTime + ONE_DAY; // Can execute after 24 hours

            System.out.println("    Step 1: Action queued");
            System.out.println("        Queue time: " + formatTimestamp(queueTime));
            System.out.println("        Execute after: " + formatTimestamp(executeAfter));

            // Step 2: Try to "execute" before timelock expires
            System.out.println("\n    Step 2: Attempting early execution (should fail in real contract)");
            tester.increaseTime(ONE_HOUR); // Only 1 hour has passed
            tester.mine();
            BlockHeader earlyBlock = tester.getLatestBlock();
            long earlyTime = earlyBlock.timestamp();
            System.out.println("        Current time: " + formatTimestamp(earlyTime));
            System.out.println("        Time remaining: " + formatDuration(executeAfter - earlyTime));
            System.out.println(AnsiColors.AMBER + "        Status: LOCKED (timelock not expired)" + AnsiColors.RESET);

            // Step 3: Advance past the timelock
            System.out.println("\n    Step 3: Advancing time past the timelock");
            tester.setNextBlockTimestamp(executeAfter + 1); // Just past the deadline
            tester.mine();
            BlockHeader readyBlock = tester.getLatestBlock();
            long readyTime = readyBlock.timestamp();
            System.out.println("        Current time: " + formatTimestamp(readyTime));
            System.out.println(AnsiColors.success("        Status: UNLOCKED (ready to execute)"));

            // Step 4: Execute (in a real test, you would call the contract here)
            System.out.println("\n    Step 4: Execute queued action");
            System.out.println("        In a real test: contract.execute(proposalId)");
            System.out.println(AnsiColors.success("        Action would succeed - timelock expired!"));

        } finally {
            tester.revert(snapshot);
        }
    }

    /**
     * Demonstrates a vesting schedule simulation.
     *
     * <p>Shows how to test a vesting contract that releases tokens
     * over a 30-day cliff with monthly unlocks.
     */
    private static void demonstrateVestingPattern(Brane.Tester tester) {
        System.out.println("\n[5] Vesting Schedule - Cliff and Linear Unlock");
        System.out.println("    Simulating a 30-day cliff vesting contract\n");

        SnapshotId snapshot = tester.snapshot();

        try {
            // Setup: Record vesting start time
            BlockHeader startBlock = tester.getLatestBlock();
            long vestingStart = startBlock.timestamp();
            long cliffEnd = vestingStart + THIRTY_DAYS;

            System.out.println("    Vesting Configuration:");
            System.out.println("        Start: " + formatTimestamp(vestingStart));
            System.out.println("        Cliff (30 days): " + formatTimestamp(cliffEnd));
            System.out.println("        Total vested: 1000 tokens (simulated)\n");

            // Test point 1: Before cliff (0% vested)
            System.out.println("    Test Point 1: Before cliff (Day 15)");
            tester.increaseTime(15 * ONE_DAY);
            tester.mine();
            BlockHeader day15Block = tester.getLatestBlock();
            System.out.println("        Current time: " + formatTimestamp(day15Block.timestamp()));
            System.out.println("        Vested amount: 0 tokens (cliff not reached)");
            System.out.println(AnsiColors.AMBER + "        Withdraw: Would revert" + AnsiColors.RESET);

            // Test point 2: At cliff (100% vested)
            System.out.println("\n    Test Point 2: At cliff (Day 30)");
            tester.setNextBlockTimestamp(cliffEnd);
            tester.mine();
            BlockHeader day30Block = tester.getLatestBlock();
            System.out.println("        Current time: " + formatTimestamp(day30Block.timestamp()));
            System.out.println("        Vested amount: 1000 tokens (100%)");
            System.out.println(AnsiColors.success("        Withdraw: Would succeed"));

            // Test point 3: Well past cliff
            System.out.println("\n    Test Point 3: After cliff (Day 60)");
            tester.increaseTime(THIRTY_DAYS);
            tester.mine();
            BlockHeader day60Block = tester.getLatestBlock();
            System.out.println("        Current time: " + formatTimestamp(day60Block.timestamp()));
            System.out.println("        Vested amount: 1000 tokens (fully vested)");
            System.out.println(AnsiColors.success("        Withdraw: Would succeed"));

            System.out.println("\n    Vesting pattern demonstration complete!");

        } finally {
            tester.revert(snapshot);
        }
    }

    /**
     * Formats a Unix timestamp as a readable date string.
     */
    private static String formatTimestamp(long timestamp) {
        java.time.Instant instant = java.time.Instant.ofEpochSecond(timestamp);
        return instant.toString();
    }

    /**
     * Formats a duration in seconds as a readable string.
     */
    private static String formatDuration(long seconds) {
        if (seconds < 60) {
            return seconds + " seconds";
        } else if (seconds < ONE_HOUR) {
            return (seconds / 60) + " minutes";
        } else if (seconds < ONE_DAY) {
            return (seconds / ONE_HOUR) + " hours";
        } else {
            return (seconds / ONE_DAY) + " days";
        }
    }
}
