package io.brane.examples;

import java.math.BigDecimal;
import java.math.BigInteger;

import io.brane.core.AnsiColors;
import io.brane.core.builder.TxBuilder;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Wei;
import io.brane.rpc.Brane;
import io.brane.core.error.RpcException;

/**
 * Canonical "Low-Level" Example for Brane 0.1.0-alpha.
 * <p>
 * Demonstrates the idiomatic way to perform raw operations:
 * 1. Direct RPC calls via {@link PublicClient}.
 * 2. Building transactions via {@link TxBuilder}.
 * 3. Signing and sending via {@link WalletClient}.
 * <p>
 * Usage:
 * ./gradlew :brane-examples:run --no-daemon \
 * -PmainClass=io.brane.examples.CanonicalRawExample \
 * -Dbrane.examples.rpc=http://127.0.0.1:8545 \
 * -Dbrane.examples.pk=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
 */
public final class CanonicalRawExample {

    private CanonicalRawExample() {
        // Prevent instantiation
    }

    public static void main(final String[] args) {
        // 0. Setup configuration
        final String rpcUrl = System.getProperty("brane.examples.rpc");
        final String privateKey = System.getProperty("brane.examples.pk");

        if (isBlank(rpcUrl) || isBlank(privateKey)) {
            printUsage();
            return;
        }

        try {
            // 1. Initialize Client
            final var signer = new io.brane.core.crypto.PrivateKeySigner(privateKey);
            final Brane.Signer client = Brane.connect(rpcUrl, signer);

            System.out.println("=== Canonical Low-Level Example ===");
            System.out.println("Signer: " + signer.address().value());

            // 2. Direct RPC Calls (Brane client)
            System.out.println("\n[1] Reading Chain State...");

            final BigInteger blockNumber = BigInteger.valueOf(client.getLatestBlock().number());
            System.out.println(AnsiColors.success("Current Block: " + blockNumber));

            // Note: Brane client provides both read and write operations.

            // 3. Build & Send Transaction (Brane.Signer + TxBuilder)
            System.out.println("\n[2] Sending Native ETH Transfer...");

            final Address recipient = new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");
            final Wei value = Wei.fromEther(new BigDecimal("0.001")); // 0.001 ETH

            // Build EIP-1559 Transaction
            // Note: Gas limit and fees are auto-filled by Brane.Signer if omitted
            final TransactionRequest tx = TxBuilder.eip1559()
                    .to(recipient)
                    .value(value)
                    .build();

            System.out.println("   Sending " + value.toEther() + " ETH to " + recipient.value());

            // Send and wait for receipt
            // We wait up to 60 seconds, polling every 1 second
            final TransactionReceipt receipt = client.sendTransactionAndWait(tx, 60_000, 1_000);

            System.out.println(AnsiColors.success("Tx Hash: " + receipt.transactionHash().value()));
            System.out.println(AnsiColors.success("Status:  " + (receipt.status() ? "SUCCESS" : "FAILED")));
            System.out.println(AnsiColors.success("Block:   " + receipt.blockNumber()));
            System.out.println(AnsiColors.success("Gas Used:" + receipt.cumulativeGasUsed().value()));

        } catch (final RpcException e) {
            System.err.println("❌ RPC error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static boolean isBlank(final String value) {
        return value == null || value.isBlank();
    }

    private static void printUsage() {
        System.out.println("""
                Usage:
                  ./gradlew :brane-examples:run --no-daemon \\
                    -PmainClass=io.brane.examples.CanonicalRawExample \\
                    -Dbrane.examples.rpc=<RPC_URL> \\
                    -Dbrane.examples.pk=<PRIVATE_KEY>
                """);
    }
}
