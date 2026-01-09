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
import io.brane.rpc.ImpersonationSession;
import io.brane.rpc.SnapshotId;

/**
 * Example demonstrating whale testing with impersonation.
 *
 * <p>Impersonation allows sending transactions from any address without possessing
 * its private key. This is essential for testing scenarios involving:
 * <ul>
 *   <li>Whale accounts with large balances</li>
 *   <li>DAO contracts and governance multisigs</li>
 *   <li>Protocol admin addresses</li>
 *   <li>External user simulations</li>
 * </ul>
 *
 * <p>This example shows:
 * <ul>
 *   <li>{@link Brane.Tester#impersonate(Address)} - Start impersonating an address</li>
 *   <li>{@link ImpersonationSession} - Try-with-resources pattern for automatic cleanup</li>
 *   <li>{@link ImpersonationSession#sendTransactionAndWait} - Send transactions as the impersonated address</li>
 *   <li>Whale testing pattern: fund, impersonate, transact, verify</li>
 * </ul>
 *
 * <p><strong>Prerequisites:</strong> Anvil must be running on localhost:8545.
 * Start it with: {@code anvil}
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *     -PmainClass=io.brane.examples.TesterImpersonationExample \
 *     -Dbrane.examples.rpc=http://127.0.0.1:8545
 * </pre>
 *
 * @see Brane.Tester#impersonate(Address)
 * @see ImpersonationSession
 */
public final class TesterImpersonationExample {

    /**
     * Example whale address (Binance hot wallet on mainnet).
     * In real tests, this could be any address with significant holdings.
     */
    private static final Address WHALE_ADDRESS =
            new Address("0xBE0eB53F46cd790Cd13851d5EFf43D12404d33E8");

    private TesterImpersonationExample() {
        // Prevent instantiation
    }

    public static void main(final String[] args) throws Exception {
        System.out.println("=== Brane Tester Impersonation Example ===\n");

        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");

        try (Brane.Tester tester = Brane.connectTest(rpcUrl)) {

            System.out.println("[1] Connected to test node");
            System.out.println("    Chain ID: " + tester.chainId());

            // 1. Basic whale impersonation with try-with-resources
            demonstrateBasicImpersonation(tester);

            // 2. Whale testing pattern with snapshot isolation
            demonstrateWhaleTestingPattern(tester);

            // 3. Multiple sequential impersonations
            demonstrateMultipleImpersonations(tester);

            // 4. ImpersonationSession API features
            demonstrateSessionApi(tester);

            System.out.println(AnsiColors.success("\nAll impersonation examples completed successfully!"));
        }
    }

    /**
     * Demonstrates basic impersonation with try-with-resources.
     *
     * <p>The key pattern: fund the address first (impersonation doesn't create ETH),
     * then use try-with-resources to ensure automatic cleanup.
     */
    private static void demonstrateBasicImpersonation(Brane.Tester tester) {
        System.out.println("\n[2] Basic Impersonation with try-with-resources");

        // Take snapshot for cleanup
        SnapshotId snapshot = tester.snapshot();

        try {
            Address recipient = AnvilSigners.keyAt(1).address();
            BigInteger recipientBalanceBefore = tester.getBalance(recipient);

            // Step 1: Fund the whale address (impersonation allows transactions,
            // but doesn't create ETH - the address needs funds for gas and value)
            Wei whaleBalance = Wei.fromEther(new BigDecimal("1000"));
            tester.setBalance(WHALE_ADDRESS, whaleBalance);
            System.out.println("    Funded whale with " + whaleBalance.toEther() + " ETH");
            System.out.println("    Whale address: " + WHALE_ADDRESS.value());

            // Step 2: Impersonate with try-with-resources for automatic cleanup
            try (ImpersonationSession session = tester.impersonate(WHALE_ADDRESS)) {
                System.out.println("    Impersonating whale address...");
                System.out.println("    Session address: " + session.address().value());

                // Step 3: Send transaction as the whale
                Wei transferAmount = Wei.fromEther(new BigDecimal("100"));
                TransactionRequest request = new TransactionRequest(
                        null, // from - automatically set by session to whale address
                        recipient,
                        transferAmount,
                        21_000L, // gas limit for simple transfer
                        null, null, null, null, null, false, null);

                System.out.println("    Sending " + transferAmount.toEther() + " ETH to " + recipient.value());

                TransactionReceipt receipt = session.sendTransactionAndWait(request);
                System.out.println("    Transaction hash: " + receipt.transactionHash().value());
                System.out.println("    Block number: " + receipt.blockNumber());
            }
            // Impersonation automatically stopped when session closes

            // Step 4: Verify the transfer
            BigInteger recipientBalanceAfter = tester.getBalance(recipient);
            Wei received = Wei.of(recipientBalanceAfter.subtract(recipientBalanceBefore));
            System.out.println("    Recipient received: " + received.toEther() + " ETH");

            System.out.println(AnsiColors.success("    Basic impersonation complete!"));

        } finally {
            tester.revert(snapshot);
        }
    }

    /**
     * Demonstrates the whale testing pattern commonly used in integration tests.
     *
     * <p>This pattern combines:
     * <ul>
     *   <li>Snapshot for test isolation</li>
     *   <li>Address funding for realistic testing</li>
     *   <li>Impersonation with try-with-resources</li>
     *   <li>State verification after transactions</li>
     * </ul>
     */
    private static void demonstrateWhaleTestingPattern(Brane.Tester tester) {
        System.out.println("\n[3] Whale Testing Pattern");
        System.out.println("    Complete pattern for integration tests\n");

        // Simulate a "protocol admin" address for testing admin functions
        Address protocolAdmin = new Address("0x1111111111111111111111111111111111111111");
        Address treasury = new Address("0x2222222222222222222222222222222222222222");

        // 1. Take snapshot BEFORE any modifications
        SnapshotId snapshot = tester.snapshot();
        System.out.println("    Step 1: Created snapshot for test isolation");

        try {
            // 2. Fund addresses for the test scenario
            tester.setBalance(protocolAdmin, Wei.fromEther(new BigDecimal("10")));
            tester.setBalance(treasury, Wei.fromEther(new BigDecimal("5000")));
            System.out.println("    Step 2: Funded test accounts");
            System.out.println("        Protocol admin: 10 ETH");
            System.out.println("        Treasury: 5000 ETH");

            BigInteger treasuryBefore = tester.getBalance(treasury);

            // 3. Impersonate treasury to simulate withdrawal
            try (ImpersonationSession session = tester.impersonate(treasury)) {
                System.out.println("    Step 3: Impersonating treasury for withdrawal test");

                // Simulate treasury paying out to protocol admin
                Wei payout = Wei.fromEther(new BigDecimal("500"));
                TransactionRequest request = new TransactionRequest(
                        null, protocolAdmin, payout,
                        21_000L, null, null, null, null, null, false, null);

                TransactionReceipt receipt = session.sendTransactionAndWait(request);
                System.out.println("        Treasury payout tx: " +
                        receipt.transactionHash().value().substring(0, 18) + "...");
            }

            // 4. Verify state changes
            BigInteger treasuryAfter = tester.getBalance(treasury);
            BigInteger adminBalance = tester.getBalance(protocolAdmin);

            System.out.println("    Step 4: Verifying state");
            System.out.println("        Treasury balance: " +
                    Wei.of(treasuryBefore).toEther() + " -> " + Wei.of(treasuryAfter).toEther() + " ETH");
            System.out.println("        Admin balance: " + Wei.of(adminBalance).toEther() + " ETH");

            // In a real test, you would assert these values
            boolean payoutSuccessful = adminBalance.compareTo(Wei.fromEther(new BigDecimal("500")).value()) >= 0;
            if (payoutSuccessful) {
                System.out.println(AnsiColors.success("    Whale testing pattern complete - assertions would pass!"));
            }

        } finally {
            // 5. Always revert to clean state for next test
            tester.revert(snapshot);
            System.out.println("    Step 5: Reverted to snapshot - state restored");
        }
    }

    /**
     * Demonstrates multiple sequential impersonations.
     *
     * <p>You can impersonate multiple different addresses, but only one at a time
     * (or multiple simultaneously if using separate sessions).
     */
    private static void demonstrateMultipleImpersonations(Brane.Tester tester) {
        System.out.println("\n[4] Multiple Sequential Impersonations");
        System.out.println("    Simulating multi-party transaction flow\n");

        SnapshotId snapshot = tester.snapshot();

        try {
            // Set up three parties
            Address alice = new Address("0x3333333333333333333333333333333333333333");
            Address bob = new Address("0x4444444444444444444444444444444444444444");
            Address charlie = new Address("0x5555555555555555555555555555555555555555");

            // Fund all parties
            tester.setBalance(alice, Wei.fromEther(new BigDecimal("100")));
            tester.setBalance(bob, Wei.fromEther(new BigDecimal("100")));
            tester.setBalance(charlie, Wei.fromEther(BigDecimal.ZERO));
            System.out.println("    Funded Alice and Bob with 100 ETH each");
            System.out.println("    Charlie starts with 0 ETH\n");

            // Alice sends to Bob
            System.out.println("    Transaction 1: Alice -> Bob (10 ETH)");
            try (ImpersonationSession aliceSession = tester.impersonate(alice)) {
                TransactionRequest request = new TransactionRequest(
                        null, bob, Wei.fromEther(new BigDecimal("10")),
                        21_000L, null, null, null, null, null, false, null);
                aliceSession.sendTransactionAndWait(request);
                System.out.println("        Alice sent 10 ETH to Bob");
            }

            // Bob sends to Charlie
            System.out.println("    Transaction 2: Bob -> Charlie (15 ETH)");
            try (ImpersonationSession bobSession = tester.impersonate(bob)) {
                TransactionRequest request = new TransactionRequest(
                        null, charlie, Wei.fromEther(new BigDecimal("15")),
                        21_000L, null, null, null, null, null, false, null);
                bobSession.sendTransactionAndWait(request);
                System.out.println("        Bob sent 15 ETH to Charlie");
            }

            // Charlie sends back to Alice
            System.out.println("    Transaction 3: Charlie -> Alice (5 ETH)");
            try (ImpersonationSession charlieSession = tester.impersonate(charlie)) {
                TransactionRequest request = new TransactionRequest(
                        null, alice, Wei.fromEther(new BigDecimal("5")),
                        21_000L, null, null, null, null, null, false, null);
                charlieSession.sendTransactionAndWait(request);
                System.out.println("        Charlie sent 5 ETH to Alice");
            }

            // Final balances (approximately, minus gas)
            System.out.println("\n    Final balances:");
            System.out.println("        Alice: ~" + Wei.of(tester.getBalance(alice)).toEther() + " ETH");
            System.out.println("        Bob: ~" + Wei.of(tester.getBalance(bob)).toEther() + " ETH");
            System.out.println("        Charlie: ~" + Wei.of(tester.getBalance(charlie)).toEther() + " ETH");

            System.out.println(AnsiColors.success("    Multi-party flow complete!"));

        } finally {
            tester.revert(snapshot);
        }
    }

    /**
     * Demonstrates the ImpersonationSession API features.
     *
     * <p>Shows the different methods available on the session object.
     */
    private static void demonstrateSessionApi(Brane.Tester tester) {
        System.out.println("\n[5] ImpersonationSession API");

        SnapshotId snapshot = tester.snapshot();

        try {
            Address target = new Address("0x6666666666666666666666666666666666666666");
            Address recipient = AnvilSigners.keyAt(2).address();

            tester.setBalance(target, Wei.fromEther(new BigDecimal("50")));

            try (ImpersonationSession session = tester.impersonate(target)) {
                // address() - Get the impersonated address
                System.out.println("    session.address(): " + session.address().value());

                // sendTransactionAndWait(request) - Send and wait with default timeout
                TransactionRequest request1 = new TransactionRequest(
                        null, recipient, Wei.fromEther(BigDecimal.ONE),
                        21_000L, null, null, null, null, null, false, null);
                TransactionReceipt receipt1 = session.sendTransactionAndWait(request1);
                System.out.println("    sendTransactionAndWait(): confirmed in block " + receipt1.blockNumber());

                // sendTransactionAndWait(request, timeout, pollInterval) - Custom timeout
                TransactionRequest request2 = new TransactionRequest(
                        null, recipient, Wei.fromEther(BigDecimal.ONE),
                        21_000L, null, null, null, null, null, false, null);
                TransactionReceipt receipt2 = session.sendTransactionAndWait(request2, 30_000L, 500L);
                System.out.println("    sendTransactionAndWait(30s, 500ms): confirmed in block " + receipt2.blockNumber());

                // sendTransaction(request) - Returns immediately with hash
                TransactionRequest request3 = new TransactionRequest(
                        null, recipient, Wei.fromEther(BigDecimal.ONE),
                        21_000L, null, null, null, null, null, false, null);
                var txHash = session.sendTransaction(request3);
                System.out.println("    sendTransaction(): hash " + txHash.value().substring(0, 18) + "...");

                // Mine to confirm the pending transaction
                tester.mine();
            }
            // close() - Called automatically, stops impersonation

            System.out.println(AnsiColors.success("    Session API demonstration complete!"));

        } finally {
            tester.revert(snapshot);
        }
    }
}
