package io.brane.examples;

import io.brane.core.BraneDebug;
import io.brane.core.types.Address;
import io.brane.core.types.Wei;
import io.brane.core.model.TransactionRequest;
import io.brane.core.builder.TxBuilder;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.DefaultWalletClient;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.PrivateKeyTransactionSigner;
import io.brane.rpc.PublicClient;
import io.brane.rpc.TransactionSigner;
import io.brane.rpc.WalletClient;
import java.math.BigInteger;

/**
 * Demonstrates Debug Mode logging.
 * 
 * Usage:
 * ./gradlew :brane-examples:run -PmainClass=io.brane.examples.DebugExample \
 *   -Dbrane.examples.rpc=http://127.0.0.1:8545
 */
public final class DebugExample {

    private DebugExample() {}

    public static void main(String[] args) {
        // 1. Enable Debug Mode
        BraneDebug.setEnabled(true);
        System.out.println("=== Debug Mode Enabled ===");

        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        // Use a random key for demo (don't use real funds!)
        final String privateKey = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
        
        final BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
        final PublicClient publicClient = PublicClient.from(provider);
        final PrivateKeyTransactionSigner signer = new PrivateKeyTransactionSigner(privateKey);
        final TransactionSigner txSigner = signer::sign;
        final WalletClient wallet =
                DefaultWalletClient.create(provider, publicClient, txSigner, signer.address());

        try {
            // 2. Trigger RPC logs (eth_blockNumber)
            System.out.println("\n--- Triggering RPC Logs (getLatestBlock) ---");
            publicClient.getLatestBlock();

            // 3. Trigger Tx Lifecycle logs (sendTransaction)
            // Note: This will likely fail on a real node due to insufficient funds/nonce,
            // but it will generate the [TX-SEND] and [RPC] logs we want to verify.
            System.out.println("\n--- Triggering Tx Logs (sendTransaction) ---");
            TransactionRequest tx = TxBuilder.eip1559()
                    .to(new Address("0x0000000000000000000000000000000000000000"))
                    .value(Wei.of(100))
                    .build();
            
            wallet.sendTransaction(tx);

        } catch (Exception e) {
            // Expected failure (e.g. insufficient funds), but logs should still appear
            System.out.println("Caught expected exception: " + e.getMessage());
        }
    }
}
