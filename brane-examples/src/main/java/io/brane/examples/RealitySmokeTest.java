package io.brane.examples;

import io.brane.core.AnsiColors;
import io.brane.core.builder.TxBuilder;
import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.crypto.Signer;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Wei;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Reality Smoke Test.
 * <p>
 * Simulates a "real-world" burst of activity to verify the stability and
 * performance
 * of the new Signer interface in an integration context.
 * <p>
 * Sends a batch of transactions concurrently and waits for all receipts.
 */
public final class RealitySmokeTest {

    private static final int TX_COUNT = 50;
    private static final int CONCURRENCY = 10;

    private RealitySmokeTest() {
    }

    public static void main(final String[] args) {
        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        final String privateKey = System.getProperty("brane.examples.pk",
                "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");

        try {
            System.out.println("=== Reality Smoke Test ===");
            System.out.println("Target: " + TX_COUNT + " transactions with " + CONCURRENCY + " threads.");

            // 1. Initialize Clients
            final BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
            final PublicClient publicClient = PublicClient.from(provider);
            final Signer signer = new PrivateKeySigner(privateKey);

            // Note: We create a single WalletClient. In a real app, this might be shared.
            // The nonce management in DefaultWalletClient handles concurrency via a
            // mutex/lock if implemented correctly,
            // or we might need to manage nonces manually for extreme concurrency.
            // For this test, we'll let WalletClient handle it (or fail if it can't, which
            // is a good test).
            final WalletClient walletClient = io.brane.rpc.DefaultWalletClient.create(
                    provider, publicClient, signer, signer.address());

            System.out.println("Signer: " + signer.address().value());

            // 2. Prepare Transactions
            final Address recipient = new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");
            final Wei value = Wei.fromEther(new BigDecimal("0.0001"));

            // Fetch initial nonce once
            final io.brane.rpc.JsonRpcResponse response = provider.send("eth_getTransactionCount",
                    java.util.List.of(signer.address().value(), "pending"));
            if (response.hasError()) {
                throw new RuntimeException("Failed to fetch nonce: " + response.error().message());
            }
            String nonceHex = (String) response.result();
            if (nonceHex.startsWith("0x")) {
                nonceHex = nonceHex.substring(2);
            }
            final java.math.BigInteger initialNonce = new java.math.BigInteger(nonceHex, 16);
            final java.util.concurrent.atomic.AtomicLong nonceCounter = new java.util.concurrent.atomic.AtomicLong(
                    initialNonce.longValue());

            final ExecutorService executor = Executors.newFixedThreadPool(CONCURRENCY);
            final List<CompletableFuture<TransactionReceipt>> futures = new ArrayList<>();

            final long startTime = System.currentTimeMillis();

            for (int i = 0; i < TX_COUNT; i++) {
                final int index = i;
                CompletableFuture<TransactionReceipt> future = CompletableFuture.supplyAsync(() -> {
                    try {
                        // Assign unique nonce
                        long nonce = nonceCounter.getAndIncrement();

                        // We build a fresh request for each tx with explicit nonce
                        TransactionRequest tx = TxBuilder.eip1559()
                                .to(recipient)
                                .value(value)
                                .nonce(nonce)
                                .build();

                        // Send and wait
                        // Note: In a high-concurrency real scenario, we might just send and wait later,
                        // but here we want to stress the full cycle.
                        return walletClient.sendTransactionAndWait(tx, 60_000, 500);
                    } catch (Exception e) {
                        throw new RuntimeException("Tx " + index + " failed", e);
                    }
                }, executor);
                futures.add(future);
            }

            // 3. Wait for all
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            final long endTime = System.currentTimeMillis();
            final long duration = endTime - startTime;

            System.out
                    .println("\n" + AnsiColors.success("✓ All " + TX_COUNT + " transactions completed successfully."));
            System.out.println("Total Time: " + duration + " ms");
            System.out
                    .println("Throughput: " + (TX_COUNT * 1000.0 / duration) + " tx/s (end-to-end including network)");

            executor.shutdown();
            executor.awaitTermination(1, TimeUnit.SECONDS);

            System.exit(0);

        } catch (Exception e) {
            System.err.println("❌ Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
