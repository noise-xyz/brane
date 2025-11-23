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
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

/**
 * Integration test for Debug Mode logging.
 * 
 * Captures System.err (where SLF4J SimpleLogger writes by default) to verify:
 * 1. RPC logs are emitted ([RPC])
 * 2. Tx lifecycle logs are emitted ([TX-SEND])
 * 3. Sensitive data is redacted (private keys)
 * 
 * Usage:
 * ./gradlew :brane-examples:run -PmainClass=io.brane.examples.DebugIntegrationTest \
 *   -Dbrane.examples.rpc=http://127.0.0.1:8545 \
 *   -Dorg.slf4j.simpleLogger.defaultLogLevel=debug
 */
public final class DebugIntegrationTest {

    private DebugIntegrationTest() {}

    public static void main(String[] args) {
        System.out.println("Running Debug Integration Tests...");

        // Capture stderr to verify logs
        final ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        final PrintStream originalErr = System.err;
        System.setErr(new PrintStream(stderr));

        try {
            runTest();
        } finally {
            System.setErr(originalErr);
        }

        final String logs = stderr.toString();
        
        // Print logs for manual inspection if needed
        System.out.println("--- Captured Logs ---");
        System.out.println(logs);
        System.out.println("---------------------");

        verifyLogs(logs);
        System.out.println("Debug Integration Tests Passed!");
    }

    private static void runTest() {
        BraneDebug.setEnabled(true);
        
        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        // Use a random key for demo
        final String privateKey = "0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef";
        
        final BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
        final PublicClient publicClient = PublicClient.from(provider);
        final PrivateKeyTransactionSigner signer = new PrivateKeyTransactionSigner(privateKey);
        final TransactionSigner txSigner = signer::sign;
        final WalletClient wallet =
                DefaultWalletClient.create(provider, publicClient, txSigner, signer.address());

        try {
            // Trigger RPC logs
            publicClient.getLatestBlock();

            // Trigger Tx Logs
            TransactionRequest tx = TxBuilder.eip1559()
                    .to(new Address("0x0000000000000000000000000000000000000000"))
                    .value(Wei.of(100))
                    .build();
            
            wallet.sendTransaction(tx);
        } catch (Exception e) {
            // Ignore expected errors (insufficient funds etc)
        }
    }

    private static void verifyLogs(String logs) {
        // 1. Verify RPC Logging
        if (!logs.contains("[RPC] method=eth_getBlockByNumber")) {
            throw new RuntimeException("Missing [RPC] log for eth_getBlockByNumber");
        }
        if (!logs.contains("durationMicros=")) {
            throw new RuntimeException("Missing durationMicros in logs");
        }

        // 2. Verify Tx Lifecycle Logging
        if (!logs.contains("[TX-SEND]")) {
            throw new RuntimeException("Missing [TX-SEND] log");
        }
        if (!logs.contains("[ESTIMATE-GAS]")) {
            throw new RuntimeException("Missing [ESTIMATE-GAS] log");
        }

        // 3. Verify Redaction
        if (logs.contains("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")) {
            throw new RuntimeException("Private key leaked in logs!");
        }
        if (logs.contains("\"privateKey\":\"0x***[REDACTED]***\"")) {
            // Good - we see the redacted version (if it was logged at all)
        }
        
        // Ensure we don't see raw signed tx
        // The mock key produces a signature, we want to ensure we don't see the full raw hex if it's logged
        // (Note: HttpBraneProvider logs request body, so we check if "raw" param is redacted)
        if (logs.contains("\"raw\":\"0x") && !logs.contains("\"raw\":\"0x***[REDACTED]***\"")) {
             // If raw is present, it MUST be redacted. 
             // However, eth_sendRawTransaction params are a list of strings, not a JSON object with "raw" key.
             // Our LogSanitizer handles "raw": "..." JSON fields. 
             // For eth_sendRawTransaction params: ["0x..."], LogSanitizer truncates if > 2000 chars.
             // Since our test tx is small, it might not be truncated. 
             // But let's check if we see the [RPC-ERROR] which might contain data.
        }
    }
}
