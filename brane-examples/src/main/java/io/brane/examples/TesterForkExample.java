// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.examples;

import java.math.BigDecimal;
import java.math.BigInteger;

import io.brane.core.AnsiColors;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Wei;
import io.brane.rpc.Brane;
import io.brane.rpc.ImpersonationSession;
import io.brane.rpc.SnapshotId;

/**
 * Example demonstrating mainnet fork testing with {@link Brane.Tester#reset(String, long)}.
 *
 * <p>Forking allows you to test against real mainnet state at a specific block. This is
 * essential for:
 * <ul>
 *   <li>Testing against actual deployed contracts</li>
 *   <li>Simulating whale transactions with real token balances</li>
 *   <li>Verifying protocol integrations without deploying to mainnet</li>
 *   <li>Reproducing and debugging mainnet issues</li>
 * </ul>
 *
 * <p>This example shows:
 * <ul>
 *   <li>{@link Brane.Tester#reset(String, long)} - Fork from mainnet at a specific block</li>
 *   <li>{@link Brane.Tester#reset()} - Reset chain to initial state</li>
 *   <li>Querying real mainnet state from a fork</li>
 *   <li>Combining fork with impersonation for whale testing</li>
 *   <li>Switching between different fork points</li>
 * </ul>
 *
 * <p><strong>Prerequisites:</strong>
 * <ul>
 *   <li>Anvil must be running on localhost:8545. Start it with: {@code anvil}</li>
 *   <li>A mainnet RPC URL must be provided via system property: {@code brane.fork.rpc.url}</li>
 * </ul>
 *
 * <p>Usage:
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *     -PmainClass=io.brane.examples.TesterForkExample \
 *     -Dbrane.examples.rpc=http://127.0.0.1:8545 \
 *     -Dbrane.fork.rpc.url=https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY
 * </pre>
 *
 * @see Brane.Tester#reset(String, long)
 * @see Brane.Tester#reset()
 */
public final class TesterForkExample {

    /**
     * Well-known USDC contract address on Ethereum mainnet.
     */
    private static final Address USDC_ADDRESS =
            new Address("0xA0b86991c6218b36c1d19D4a2e9Eb0cE3606eB48");

    /**
     * Well-known Binance hot wallet (a whale address with significant ETH holdings).
     */
    private static final Address BINANCE_WHALE =
            new Address("0xBE0eB53F46cd790Cd13851d5EFf43D12404d33E8");

    /**
     * WETH contract address on Ethereum mainnet.
     */
    private static final Address WETH_ADDRESS =
            new Address("0xC02aaA39b223FE8D0A0e5C4F27eAD9083C756Cc2");

    /**
     * A historical mainnet block number (around Dec 2023) for consistent testing.
     */
    private static final long FORK_BLOCK = 18_900_000L;

    private TesterForkExample() {
        // Prevent instantiation
    }

    public static void main(final String[] args) throws Exception {
        System.out.println("=== Brane Tester Fork Example ===\n");

        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        final String forkRpcUrl = System.getProperty("brane.fork.rpc.url");

        if (forkRpcUrl == null || forkRpcUrl.isEmpty()) {
            System.out.println("WARNING: No fork RPC URL configured.");
            System.out.println("Set -Dbrane.fork.rpc.url=<your-mainnet-rpc-url> to run fork examples.\n");
            System.out.println("Example: -Dbrane.fork.rpc.url=https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY");
            System.out.println("\nRunning basic reset() demonstration only...\n");

            demonstrateBasicReset(rpcUrl);
            return;
        }

        try (Brane.Tester tester = Brane.connectTest(rpcUrl)) {

            System.out.println("[1] Connected to test node");
            System.out.println("    Chain ID: " + tester.chainId());
            System.out.println("    Fork RPC: " + forkRpcUrl);
            System.out.println("    Fork block: " + FORK_BLOCK);

            // 1. Fork from mainnet at a specific block
            demonstrateForkSetup(tester, forkRpcUrl);

            // 2. Query real mainnet state from fork
            demonstrateQueryingForkedState(tester);

            // 3. Whale testing on fork with impersonation
            demonstrateWhaleTestingOnFork(tester);

            // 4. Switching between fork points
            demonstrateSwitchingForkPoints(tester, forkRpcUrl);

            // 5. Reset back to clean state
            demonstrateResetAfterFork(tester);

            System.out.println(AnsiColors.success("\nAll fork operations completed successfully!"));
        }
    }

    /**
     * Demonstrates basic reset() without forking.
     *
     * <p>This runs when no fork RPC URL is configured.
     */
    private static void demonstrateBasicReset(String rpcUrl) throws Exception {
        try (Brane.Tester tester = Brane.connectTest(rpcUrl)) {
            System.out.println("[1] Connected to test node");
            System.out.println("    Chain ID: " + tester.chainId());

            // Mine some blocks
            BlockHeader before = tester.getLatestBlock();
            System.out.println("\n[2] Mining blocks...");
            System.out.println("    Initial block: " + before.number());

            tester.mine(10);
            System.out.println("    After mining: " + tester.getLatestBlock().number());

            // Modify state
            Address testAddr = new Address("0x1111111111111111111111111111111111111111");
            tester.setBalance(testAddr, Wei.fromEther(new BigDecimal("999")));
            System.out.println("    Set test address balance to 999 ETH");

            // Reset to initial state
            System.out.println("\n[3] Calling reset()...");
            tester.reset();

            BlockHeader after = tester.getLatestBlock();
            BigInteger balanceAfterReset = tester.getBalance(testAddr);

            System.out.println("    Block after reset: " + after.number());
            System.out.println("    Test address balance: " + Wei.of(balanceAfterReset).toEther() + " ETH");

            System.out.println(AnsiColors.success("\nBasic reset demonstration complete!"));
        }
    }

    /**
     * Demonstrates forking from mainnet using reset(forkUrl, blockNumber).
     *
     * <p>After calling reset with a fork URL and block number, Anvil will fetch
     * state from mainnet and start the local chain at that block.
     */
    private static void demonstrateForkSetup(Brane.Tester tester, String forkRpcUrl) {
        System.out.println("\n[2] Fork Setup with reset(forkUrl, blockNumber)");

        BlockHeader beforeFork = tester.getLatestBlock();
        System.out.println("    Block before fork: " + beforeFork.number());

        // Fork from mainnet at a specific block
        System.out.println("    Forking from mainnet at block " + FORK_BLOCK + "...");
        tester.reset(forkRpcUrl, FORK_BLOCK);

        BlockHeader afterFork = tester.getLatestBlock();
        System.out.println("    Block after fork: " + afterFork.number());
        System.out.println("    Block timestamp: " + afterFork.timestamp());

        if (afterFork.number() == FORK_BLOCK) {
            System.out.println(AnsiColors.success("    Fork established at block " + FORK_BLOCK + "!"));
        }
    }

    /**
     * Demonstrates querying real mainnet state from a fork.
     *
     * <p>Once forked, you can query balances, contract storage, and other state
     * exactly as it existed at the fork block.
     */
    private static void demonstrateQueryingForkedState(Brane.Tester tester) {
        System.out.println("\n[3] Query Real Mainnet State");

        // Query Binance whale balance (should be significant at this block)
        BigInteger whaleBalance = tester.getBalance(BINANCE_WHALE);
        System.out.println("    Binance whale (" + BINANCE_WHALE.value().substring(0, 10) + "...):");
        System.out.println("        ETH balance: " + Wei.of(whaleBalance).toEther() + " ETH");

        // Note: To query ERC-20 balances, you would need to call the contract.
        // This example shows ETH balance which doesn't require contract calls.
        System.out.println("\n    USDC contract address: " + USDC_ADDRESS.value());
        System.out.println("    WETH contract address: " + WETH_ADDRESS.value());
        System.out.println("    (Contract interactions would require ABI encoding)");

        System.out.println(AnsiColors.success("    Mainnet state query complete!"));
    }

    /**
     * Demonstrates whale testing on a fork using impersonation.
     *
     * <p>This is the most powerful fork testing pattern: impersonate a real whale
     * address and send transactions as if you were them.
     */
    private static void demonstrateWhaleTestingOnFork(Brane.Tester tester) {
        System.out.println("\n[4] Whale Testing on Fork");
        System.out.println("    Impersonating Binance whale to send ETH\n");

        // Take snapshot for cleanup
        SnapshotId snapshot = tester.snapshot();

        try {
            Address recipient = new Address("0x1111111111111111111111111111111111111111");

            // Check balances before
            BigInteger whaleBalanceBefore = tester.getBalance(BINANCE_WHALE);
            BigInteger recipientBalanceBefore = tester.getBalance(recipient);

            System.out.println("    Before transaction:");
            System.out.println("        Whale balance: " + Wei.of(whaleBalanceBefore).toEther() + " ETH");
            System.out.println("        Recipient balance: " + Wei.of(recipientBalanceBefore).toEther() + " ETH");

            // Impersonate whale and send ETH
            Wei transferAmount = Wei.fromEther(new BigDecimal("100"));

            try (ImpersonationSession session = tester.impersonate(BINANCE_WHALE)) {
                System.out.println("\n    Impersonating whale to send " + transferAmount.toEther() + " ETH...");

                TransactionRequest request = new TransactionRequest(
                        null, // from - set by session
                        recipient,
                        transferAmount,
                        21_000L,
                        null, null, null, null, null, false, null);

                TransactionReceipt receipt = session.sendTransactionAndWait(request);
                System.out.println("    Transaction hash: " + receipt.transactionHash().value());
                System.out.println("    Block: " + receipt.blockNumber());
                System.out.println("    Gas used: " + receipt.cumulativeGasUsed().value());
            }

            // Check balances after
            BigInteger whaleBalanceAfter = tester.getBalance(BINANCE_WHALE);
            BigInteger recipientBalanceAfter = tester.getBalance(recipient);

            System.out.println("\n    After transaction:");
            System.out.println("        Whale balance: " + Wei.of(whaleBalanceAfter).toEther() + " ETH");
            System.out.println("        Recipient balance: " + Wei.of(recipientBalanceAfter).toEther() + " ETH");

            Wei received = Wei.of(recipientBalanceAfter.subtract(recipientBalanceBefore));
            System.out.println("        Recipient received: " + received.toEther() + " ETH");

            System.out.println(AnsiColors.success("\n    Whale testing on fork complete!"));

        } finally {
            tester.revert(snapshot);
            System.out.println("    (Reverted to snapshot for cleanup)");
        }
    }

    /**
     * Demonstrates switching between different fork points.
     *
     * <p>You can call reset(forkUrl, blockNumber) multiple times to switch
     * between different historical states.
     */
    private static void demonstrateSwitchingForkPoints(Brane.Tester tester, String forkRpcUrl) {
        System.out.println("\n[5] Switching Fork Points");

        // Fork to a different block
        long olderBlock = FORK_BLOCK - 100_000L; // ~100k blocks earlier

        System.out.println("    Current fork block: " + tester.getLatestBlock().number());
        System.out.println("    Switching to older block " + olderBlock + "...");

        tester.reset(forkRpcUrl, olderBlock);

        System.out.println("    New fork block: " + tester.getLatestBlock().number());

        // Query state at the older block
        BigInteger whaleBalanceOlder = tester.getBalance(BINANCE_WHALE);
        System.out.println("    Whale balance at older block: " + Wei.of(whaleBalanceOlder).toEther() + " ETH");

        // Switch back to the original block
        System.out.println("\n    Switching back to block " + FORK_BLOCK + "...");
        tester.reset(forkRpcUrl, FORK_BLOCK);

        System.out.println("    Fork block: " + tester.getLatestBlock().number());

        BigInteger whaleBalanceNewer = tester.getBalance(BINANCE_WHALE);
        System.out.println("    Whale balance at newer block: " + Wei.of(whaleBalanceNewer).toEther() + " ETH");

        System.out.println(AnsiColors.success("    Fork point switching complete!"));
    }

    /**
     * Demonstrates resetting back to a clean (non-forked) state.
     *
     * <p>Calling reset() without parameters clears the fork and returns to
     * Anvil's default initial state.
     */
    private static void demonstrateResetAfterFork(Brane.Tester tester) {
        System.out.println("\n[6] Reset to Clean State");

        System.out.println("    Current block (forked): " + tester.getLatestBlock().number());

        // Reset to clean state (no fork)
        tester.reset();

        BlockHeader afterReset = tester.getLatestBlock();
        System.out.println("    Block after reset: " + afterReset.number());

        // Verify we're on a clean chain (whale balance should be 0 now)
        BigInteger whaleBalanceAfterReset = tester.getBalance(BINANCE_WHALE);
        System.out.println("    Whale balance (no fork): " + Wei.of(whaleBalanceAfterReset).toEther() + " ETH");

        if (afterReset.number() == 0 && whaleBalanceAfterReset.equals(BigInteger.ZERO)) {
            System.out.println(AnsiColors.success("    Clean state restored!"));
        }
    }
}
