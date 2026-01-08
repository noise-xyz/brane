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
 * Demonstrates {@link Brane.Tester} usage patterns for test node operations.
 *
 * <p>This example showcases the key testing capabilities available when working
 * with test nodes like Anvil:
 * <ul>
 *   <li><strong>Snapshot/Revert:</strong> Save and restore blockchain state</li>
 *   <li><strong>Account Manipulation:</strong> Set balance, nonce, code, and storage</li>
 *   <li><strong>Impersonation:</strong> Send transactions as any address</li>
 *   <li><strong>Time Manipulation:</strong> Control block timestamps</li>
 *   <li><strong>Mining Control:</strong> Manual and automated block production</li>
 * </ul>
 *
 * <p><strong>Prerequisites:</strong> Anvil must be running on localhost:8545.
 * Start it with: {@code anvil}
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *     -PmainClass=io.brane.examples.TestingExample \
 *     -Dbrane.examples.rpc=http://127.0.0.1:8545
 * </pre>
 *
 * @see Brane.Tester
 * @see SnapshotId
 * @see ImpersonationSession
 */
public final class TestingExample {

    private TestingExample() {
        // Prevent instantiation
    }

    public static void main(final String[] args) throws Exception {
        System.out.println("=== Brane Tester Example ===\n");

        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");

        // Create a Tester client - the simplest way to connect to Anvil
        // This uses the default funded test account (Account #0)
        try (Brane.Tester tester = Brane.connectTest(rpcUrl)) {
            demonstrateSnapshotRevert(tester);
            demonstrateAccountManipulation(tester);
            demonstrateImpersonation(tester);
            demonstrateTimeManipulation(tester);
            demonstrateMiningControl(tester);

            System.out.println(AnsiColors.success("\nAll examples completed successfully!"));
        }
    }

    /**
     * Demonstrates snapshot and revert functionality.
     *
     * <p>Snapshots allow you to save blockchain state and restore it later,
     * which is essential for isolated test cases.
     */
    private static void demonstrateSnapshotRevert(Brane.Tester tester) {
        System.out.println("[1] Snapshot and Revert");
        System.out.println("    Saving and restoring blockchain state\n");

        Address testAccount = AnvilSigners.keyAt(1).address();
        BigInteger originalBalance = tester.getBalance(testAccount);
        System.out.println("    Original balance: " + Wei.of(originalBalance).toEther() + " ETH");

        // Take a snapshot before making changes
        SnapshotId snapshot = tester.snapshot();
        System.out.println("    Snapshot created: " + snapshot.value());

        // Modify state
        Wei newBalance = Wei.fromEther(new BigDecimal("999"));
        tester.setBalance(testAccount, newBalance);
        System.out.println("    Balance modified to: " + newBalance.toEther() + " ETH");

        // Revert to snapshot - restores original state
        boolean reverted = tester.revert(snapshot);
        System.out.println("    Reverted: " + reverted);

        BigInteger restoredBalance = tester.getBalance(testAccount);
        System.out.println("    Restored balance: " + Wei.of(restoredBalance).toEther() + " ETH");
        System.out.println(AnsiColors.success("    Balance successfully restored!\n"));
    }

    /**
     * Demonstrates account manipulation capabilities.
     *
     * <p>Test nodes allow direct manipulation of account state:
     * balance, nonce, bytecode, and storage slots.
     */
    private static void demonstrateAccountManipulation(Brane.Tester tester) {
        System.out.println("[2] Account Manipulation");
        System.out.println("    Direct state manipulation for testing\n");

        // Take snapshot for cleanup
        SnapshotId snapshot = tester.snapshot();

        try {
            Address testAddr = new Address("0x1234567890123456789012345678901234567890");

            // Set balance
            Wei balance = Wei.fromEther(new BigDecimal("1000"));
            tester.setBalance(testAddr, balance);
            System.out.println("    Set balance to 1000 ETH: " +
                    Wei.of(tester.getBalance(testAddr)).toEther() + " ETH");

            // Set nonce
            tester.setNonce(testAddr, 42);
            System.out.println("    Set nonce to 42");

            // Set code (simple bytecode that returns 42)
            HexData code = new HexData("0x602a60005260206000f3");
            tester.setCode(testAddr, code);
            System.out.println("    Deployed bytecode at address");

            // Set storage
            Hash slot = new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");
            Hash value = new Hash("0x000000000000000000000000000000000000000000000000000000000000002a");
            tester.setStorageAt(testAddr, slot, value);
            System.out.println("    Set storage slot 0 to value 42");

            System.out.println(AnsiColors.success("    Account manipulation complete!\n"));

        } finally {
            tester.revert(snapshot);
        }
    }

    /**
     * Demonstrates impersonation - sending transactions as any address.
     *
     * <p>Impersonation is useful for testing interactions with whale accounts,
     * DAO contracts, or any address without needing its private key.
     */
    private static void demonstrateImpersonation(Brane.Tester tester) {
        System.out.println("[3] Impersonation");
        System.out.println("    Send transactions as any address\n");

        // Take snapshot for cleanup
        SnapshotId snapshot = tester.snapshot();

        try {
            // Impersonate a random address (simulating a "whale")
            Address whale = new Address("0xDEADBEEF00000000000000000000000000000001");
            Address recipient = AnvilSigners.keyAt(2).address();

            // Fund the whale first
            tester.setBalance(whale, Wei.fromEther(new BigDecimal("100")));
            System.out.println("    Whale address: " + whale.value());
            System.out.println("    Whale balance: " + Wei.of(tester.getBalance(whale)).toEther() + " ETH");

            BigInteger recipientBefore = tester.getBalance(recipient);

            // Use try-with-resources for automatic cleanup
            try (ImpersonationSession session = tester.impersonate(whale)) {
                System.out.println("    Impersonating whale...");

                TransactionRequest request = new TransactionRequest(
                        null, // from - set by session
                        recipient,
                        Wei.fromEther(BigDecimal.ONE),
                        21_000L,
                        null, null, null, null, null, false, null);

                TransactionReceipt receipt = session.sendTransactionAndWait(request);
                System.out.println("    Transaction sent: " + receipt.transactionHash().value());
                System.out.println("    Gas used: " + receipt.cumulativeGasUsed().value());
            }
            // Impersonation automatically stopped

            BigInteger recipientAfter = tester.getBalance(recipient);
            System.out.println("    Recipient received: " +
                    Wei.of(recipientAfter.subtract(recipientBefore)).toEther() + " ETH");
            System.out.println(AnsiColors.success("    Impersonation complete!\n"));

        } finally {
            tester.revert(snapshot);
        }
    }

    /**
     * Demonstrates time manipulation for testing time-dependent logic.
     *
     * <p>Useful for testing vesting schedules, timelocks, expiration logic, etc.
     */
    private static void demonstrateTimeManipulation(Brane.Tester tester) {
        System.out.println("[4] Time Manipulation");
        System.out.println("    Control block timestamps\n");

        // Take snapshot for cleanup
        SnapshotId snapshot = tester.snapshot();

        try {
            BlockHeader before = tester.getLatestBlock();
            System.out.println("    Current block: " + before.number());
            System.out.println("    Current timestamp: " + before.timestamp());

            // Advance time by 1 day (86400 seconds)
            tester.increaseTime(86400);
            tester.mine();

            BlockHeader after = tester.getLatestBlock();
            long timeAdvanced = after.timestamp() - before.timestamp();
            System.out.println("    After time travel: block " + after.number());
            System.out.println("    Time advanced: " + timeAdvanced + " seconds (~" + (timeAdvanced / 3600) + " hours)");

            // Set exact timestamp for next block
            long futureTimestamp = after.timestamp() + 365 * 24 * 60 * 60; // +1 year
            tester.setNextBlockTimestamp(futureTimestamp);
            tester.mine();

            BlockHeader futureBlock = tester.getLatestBlock();
            System.out.println("    Jumped to timestamp: " + futureBlock.timestamp());
            System.out.println(AnsiColors.success("    Time manipulation complete!\n"));

        } finally {
            tester.revert(snapshot);
        }
    }

    /**
     * Demonstrates mining control for precise block production.
     *
     * <p>Useful for testing gas price changes, block-dependent logic,
     * and batching multiple transactions in a single block.
     */
    private static void demonstrateMiningControl(Brane.Tester tester) {
        System.out.println("[5] Mining Control");
        System.out.println("    Control block production\n");

        // Take snapshot for cleanup
        SnapshotId snapshot = tester.snapshot();

        try {
            BlockHeader before = tester.getLatestBlock();
            System.out.println("    Starting block: " + before.number());

            // Mine a single block
            tester.mine();
            System.out.println("    After mine(): block " + tester.getLatestBlock().number());

            // Mine multiple blocks
            tester.mine(5);
            System.out.println("    After mine(5): block " + tester.getLatestBlock().number());

            // Mine blocks with time interval (simulates realistic block times)
            tester.mine(3, 12); // 3 blocks, 12 seconds apart
            BlockHeader afterInterval = tester.getLatestBlock();
            System.out.println("    After mine(3, 12): block " + afterInterval.number());

            // Disable automine for batching transactions
            System.out.println("\n    Testing automine control:");
            boolean originalAutomine = tester.getAutomine();
            System.out.println("    Automine enabled: " + originalAutomine);

            tester.setAutomine(false);
            System.out.println("    Automine disabled");

            long blockBefore = tester.getLatestBlock().number();

            // Send transaction - won't be mined automatically
            Address recipient = AnvilSigners.keyAt(3).address();
            TransactionRequest request = new TransactionRequest(
                    null, recipient, Wei.of(100), 21_000L,
                    null, null, null, null, null, false, null);
            Hash txHash = tester.sendTransaction(request);
            System.out.println("    Transaction sent (pending): " + txHash.value().substring(0, 18) + "...");

            System.out.println("    Block number (unchanged): " + tester.getLatestBlock().number());

            // Mine manually to include transaction
            tester.mine();
            System.out.println("    After manual mine: block " + tester.getLatestBlock().number());

            // Restore automine
            tester.setAutomine(true);
            System.out.println("    Automine re-enabled");

            System.out.println(AnsiColors.success("    Mining control complete!\n"));

        } finally {
            tester.revert(snapshot);
        }
    }
}
