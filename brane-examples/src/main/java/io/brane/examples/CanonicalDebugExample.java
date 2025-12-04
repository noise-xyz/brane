package io.brane.examples;

import io.brane.core.AnsiColors;
import io.brane.core.BraneDebug;
import io.brane.core.builder.TxBuilder;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Wei;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.HttpBraneProvider;
import io.brane.core.crypto.PrivateKeySigner;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;
import io.brane.rpc.DefaultWalletClient;

/**
 * Canonical "Debug and Error Handling" Example for Brane 0.1.0-alpha.
 * <p>
 * Demonstrates:
 * 1. Enabling Debug Mode via {@link BraneDebug#setEnabled(boolean)}.
 * 2. Observing RPC logs (requests, responses, duration).
 * 3. Observing Transaction Lifecycle logs ([TX-SEND], [TX-HASH], [TX-WAIT]).
 * 4. Handling {@link RevertException} and {@link RpcException}.
 * <p>
 * Usage:
 * ./gradlew :brane-examples:run --no-daemon \
 * -PmainClass=io.brane.examples.CanonicalDebugExample \
 * -Dbrane.examples.rpc=http://127.0.0.1:8545
 */
public final class CanonicalDebugExample {

    private CanonicalDebugExample() {
        // Prevent instantiation
    }

    public static void main(final String[] args) {
        // 1. Enable Debug Mode
        // This enables verbose logging for RPC calls and transaction lifecycle events.
        BraneDebug.setEnabled(true);
        System.out.println("=== Canonical Debug Example ===");
        System.out.println(AnsiColors.success("Debug Mode Enabled"));

        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        // Use a random key for demo (don't use real funds!)
        // This key corresponds to address: 0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266
        // (Anvil default #0)
        // We use a random one here to intentionally trigger errors if needed, or use a
        // known one for success.
        // Let's use a random one to be safe and show error handling if funds are
        // missing.
        final String privateKey = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";

        final BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
        final PublicClient publicClient = PublicClient.from(provider);
        final PrivateKeySigner signer = new PrivateKeySigner(privateKey);
        final WalletClient wallet = DefaultWalletClient.create(provider, publicClient, signer, signer.address());

        try {
            // 2. Trigger RPC logs (eth_blockNumber)
            System.out.println("\n[1] Triggering RPC Logs (getLatestBlock)...");
            publicClient.getLatestBlock();
            // Check console for: [RPC] id=... method=eth_getBlockByNumber ...

            // 3. Trigger Tx Lifecycle logs (sendTransaction)
            System.out.println("\n[2] Triggering Tx Logs (sendTransaction)...");

            // Sending to zero address
            final TransactionRequest tx = TxBuilder.eip1559()
                    .to(new Address("0x0000000000000000000000000000000000000000"))
                    .value(Wei.of(100)) // 100 wei
                    .build();

            System.out.println("   Sending 100 wei to 0x00...00");

            // This might fail if the random account has no funds, which is perfect for
            // demonstrating RpcException
            wallet.sendTransactionAndWait(tx, 30_000, 1_000);

        } catch (RevertException e) {
            System.err.println("\n❌ Caught RevertException:");
            System.err.println("   Kind:   " + e.kind());
            System.err.println("   Reason: " + e.revertReason());
            System.err.println("   Raw:    " + e.rawDataHex());
        } catch (RpcException e) {
            System.err.println("\n❌ Caught RpcException:");
            System.err.println("   Code:    " + e.code());
            System.err.println("   Message: " + e.getMessage());
            System.err.println("   Data:    " + e.data());
            System.err.println("   ReqId:   " + e.requestId());
        } catch (Exception e) {
            System.err.println("\n❌ Caught Unexpected Exception: " + e.getClass().getSimpleName());
            System.err.println("   Message: " + e.getMessage());
        }
    }
}
