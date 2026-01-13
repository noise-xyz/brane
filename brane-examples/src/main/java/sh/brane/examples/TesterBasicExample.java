// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.examples;

import java.math.BigDecimal;
import java.math.BigInteger;

import sh.brane.core.AnsiColors;
import sh.brane.core.model.BlockHeader;
import sh.brane.core.model.TransactionReceipt;
import sh.brane.core.model.TransactionRequest;
import sh.brane.core.types.Address;
import sh.brane.core.types.Wei;
import sh.brane.rpc.AnvilSigners;
import sh.brane.rpc.Brane;

/**
 * Basic example demonstrating core {@link Brane.Tester} operations.
 *
 * <p>This example shows the fundamental testing capabilities:
 * <ul>
 *   <li>{@link Brane#connectTest()} - Connect to a local test node</li>
 *   <li>{@link Brane.Tester#setBalance} - Manipulate account balances</li>
 *   <li>{@link Brane.Tester#sendTransaction} - Send transactions</li>
 *   <li>{@link Brane.Tester#mine()} - Manual block mining</li>
 * </ul>
 *
 * <p><strong>Prerequisites:</strong> Anvil must be running on localhost:8545.
 * Start it with: {@code anvil}
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *     -PmainClass=sh.brane.examples.TesterBasicExample \
 *     -Dbrane.examples.rpc=http://127.0.0.1:8545
 * </pre>
 *
 * @see Brane.Tester
 * @see Brane#connectTest()
 */
public final class TesterBasicExample {

    private TesterBasicExample() {
        // Prevent instantiation
    }

    public static void main(final String[] args) throws Exception {
        System.out.println("=== Brane Tester Basic Example ===\n");

        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");

        // 1. Connect to test node using connectTest()
        // This is the simplest way to connect to Anvil with a default funded account
        try (Brane.Tester tester = Brane.connectTest(rpcUrl)) {

            System.out.println("[1] Connected to test node");
            System.out.println("    Chain ID: " + tester.chainId());
            System.out.println("    Signer address: " + tester.signer().address().value());

            // 2. Set balance for a test address
            demonstrateSetBalance(tester);

            // 3. Send a transaction
            demonstrateSendTransaction(tester);

            // 4. Mine blocks manually
            demonstrateMining(tester);

            System.out.println(AnsiColors.success("\nAll operations completed successfully!"));
        }
    }

    /**
     * Demonstrates setting an account's ETH balance.
     */
    private static void demonstrateSetBalance(Brane.Tester tester) {
        System.out.println("\n[2] Set Balance");

        // Create a fresh test address
        Address testAddr = new Address("0x1111111111111111111111111111111111111111");

        // Check initial balance (should be 0)
        BigInteger initialBalance = tester.getBalance(testAddr);
        System.out.println("    Initial balance: " + Wei.of(initialBalance).toEther() + " ETH");

        // Set balance to 100 ETH
        Wei newBalance = Wei.fromEther(new BigDecimal("100"));
        tester.setBalance(testAddr, newBalance);

        // Verify the balance was set
        BigInteger updatedBalance = tester.getBalance(testAddr);
        System.out.println("    After setBalance: " + Wei.of(updatedBalance).toEther() + " ETH");
        System.out.println(AnsiColors.success("    Balance set successfully!"));
    }

    /**
     * Demonstrates sending a transaction.
     */
    private static void demonstrateSendTransaction(Brane.Tester tester) {
        System.out.println("\n[3] Send Transaction");

        // Use a test account as recipient
        Address recipient = AnvilSigners.keyAt(1).address();
        BigInteger recipientBalanceBefore = tester.getBalance(recipient);

        // Create and send a transaction
        Wei amount = Wei.fromEther(new BigDecimal("0.5"));
        TransactionRequest request = new TransactionRequest(
                null, // from - filled by tester
                recipient,
                amount,
                21_000L, // gas limit for simple transfer
                null, null, null, null, null, false, null);

        System.out.println("    Sending " + amount.toEther() + " ETH to " + recipient.value());

        // Send and wait for receipt
        TransactionReceipt receipt = tester.sendTransactionAndWait(request);

        System.out.println("    Transaction hash: " + receipt.transactionHash().value());
        System.out.println("    Block number: " + receipt.blockNumber());
        System.out.println("    Gas used: " + receipt.cumulativeGasUsed().value());

        // Verify recipient received the ETH
        BigInteger recipientBalanceAfter = tester.getBalance(recipient);
        Wei received = Wei.of(recipientBalanceAfter.subtract(recipientBalanceBefore));
        System.out.println("    Recipient received: " + received.toEther() + " ETH");
        System.out.println(AnsiColors.success("    Transaction successful!"));
    }

    /**
     * Demonstrates manual block mining.
     */
    private static void demonstrateMining(Brane.Tester tester) {
        System.out.println("\n[4] Mine Blocks");

        BlockHeader before = tester.getLatestBlock();
        System.out.println("    Current block: " + before.number());

        // Mine a single block
        tester.mine();
        System.out.println("    After mine(): block " + tester.getLatestBlock().number());

        // Mine multiple blocks at once
        tester.mine(5);
        System.out.println("    After mine(5): block " + tester.getLatestBlock().number());

        // Mine blocks with time intervals (3 blocks, 12 seconds apart)
        tester.mine(3, 12);
        BlockHeader after = tester.getLatestBlock();
        System.out.println("    After mine(3, 12): block " + after.number());

        long totalBlocksMined = after.number() - before.number();
        System.out.println(AnsiColors.success("    Mined " + totalBlocksMined + " blocks total!"));
    }
}
