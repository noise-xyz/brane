package io.brane.examples;

import java.util.List;

import io.brane.core.AnsiColors;
import io.brane.core.BraneDebug;
import io.brane.core.builder.TxBuilder;
import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.model.AccessListEntry;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.Wei;
import io.brane.rpc.Brane;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;

/**
 * Canonical "Modern Transactions" Example for Brane 0.1.0-alpha.
 * <p>
 * Demonstrates "Modern by Default" features:
 * 1. **EIP-1559**: The default transaction type.
 * 2. **Smart Gas Defaults**: Automatic gas limit (w/ 20% buffer) and fee
 * calculation (2x baseFee).
 * 3. **Access Lists**: Including access lists for gas optimization (EIP-2930).
 * <p>
 * Usage:
 * ./gradlew :brane-examples:run --no-daemon \
 * -PmainClass=io.brane.examples.CanonicalTxExample \
 * -Dbrane.examples.rpc=http://127.0.0.1:8545
 */
public final class CanonicalTxExample {

    private CanonicalTxExample() {
        // Prevent instantiation
    }

    public static void main(final String[] args) {
        // Enable Debug Mode to see the "Smart Gas" logic in action (logs [ESTIMATE-GAS]
        // etc.)
        // We enable TX logging but disable RPC logging to keep the output clean.
        BraneDebug.setTxLogging(true);
        BraneDebug.setRpcLogging(false);
        System.out.println("=== Canonical Modern Tx Example ===");

        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        // Anvil Default Account #0
        final String privateKey = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
        final Address recipient = new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");

        final PrivateKeySigner signer = new PrivateKeySigner(privateKey);
        final Brane.Signer client = Brane.connect(rpcUrl, signer);

        try {
            // ---------------------------------------------------------
            // 1. Smart Gas Defaults (EIP-1559)
            // ---------------------------------------------------------
            System.out.println("\n[1] Smart Gas Defaults (EIP-1559)...");
            System.out.println("    - No gas limit specified -> Auto-estimated + 20% buffer");
            System.out.println("    - No fees specified      -> Auto-calculated (2x baseFee + priority)");

            final TransactionRequest defaultTx = TxBuilder.eip1559()
                    .to(recipient)
                    .value(Wei.of(100))
                    .build();

            final TransactionReceipt receipt1 = client.sendTransactionAndWait(defaultTx, 30_000, 1_000);
            System.out.println(AnsiColors.success("Tx 1 Success: " + receipt1.transactionHash().value()));
            System.out.println("  Gas Used: " + receipt1.cumulativeGasUsed().value());

            // ---------------------------------------------------------
            // 2. Access Lists (EIP-2930)
            // ---------------------------------------------------------
            System.out.println("\n[2] Access List Transaction...");
            System.out.println("    - Pre-warming storage slots/addresses for gas reduction");

            // Example: Accessing the recipient's address and a random storage slot
            final List<AccessListEntry> accessList = List.of(
                    new AccessListEntry(recipient,
                            List.of(new Hash("0x0000000000000000000000000000000000000000000000000000000000000001"))));

            final TransactionRequest accessListTx = TxBuilder.eip1559()
                    .to(recipient)
                    .value(Wei.of(100))
                    .accessList(accessList)
                    .build();

            final TransactionReceipt receipt2 = client.sendTransactionAndWait(accessListTx, 30_000, 1_000);
            System.out.println(AnsiColors.success("Tx 2 Success: " + receipt2.transactionHash().value()));
            System.out.println("  Gas Used: " + receipt2.cumulativeGasUsed().value());

            // ---------------------------------------------------------
            // 3. Explicit Overrides
            // ---------------------------------------------------------
            System.out.println("\n[3] Explicit Gas Overrides...");
            System.out.println("    - Manually setting gas limit and fees");

            final TransactionRequest explicitTx = TxBuilder.eip1559()
                    .to(recipient)
                    .value(Wei.of(100))
                    .gasLimit(50_000) // Explicit limit
                    .maxFeePerGas(Wei.gwei(50)) // Explicit max fee
                    .maxPriorityFeePerGas(Wei.gwei(2)) // Explicit priority fee
                    .build();

            final TransactionReceipt receipt3 = client.sendTransactionAndWait(explicitTx, 30_000, 1_000);
            System.out.println(AnsiColors.success("Tx 3 Success: " + receipt3.transactionHash().value()));

        } catch (final RpcException e) {
            System.err.println("❌ RPC error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        } catch (final RevertException e) {
            System.err.println("❌ Revert error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }
}
