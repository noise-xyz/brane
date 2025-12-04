package io.brane.examples;

import io.brane.core.BraneDebug;
import io.brane.core.types.Address;
import io.brane.core.types.Wei;
import io.brane.core.model.TransactionRequest;
import io.brane.core.builder.TxBuilder;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.DefaultWalletClient;
import io.brane.rpc.HttpBraneProvider;
import io.brane.core.crypto.PrivateKeySigner;
import io.brane.rpc.PublicClient;
import io.brane.core.crypto.Signer;
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
 * ./gradlew :brane-examples:run
 * -PmainClass=io.brane.examples.DebugIntegrationTest \
 * -Dbrane.examples.rpc=http://127.0.0.1:8545 \
 * -Dorg.slf4j.simpleLogger.defaultLogLevel=debug
 */
public final class DebugIntegrationTest {

    private DebugIntegrationTest() {
    }

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
        final PrivateKeySigner signer = new PrivateKeySigner(privateKey);
        final WalletClient wallet = DefaultWalletClient.create(provider, publicClient, signer, signer.address());

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

    private static void verifyLogs(final String logs) {
        // 1. Verify RPC Logging - new minimalist format without request IDs
        if (!logs.contains("[RPC]")) {
            throw new RuntimeException("Missing [RPC] log");
        }
        if (!logs.contains("method=eth_getBlockByNumber")) {
            throw new RuntimeException("Missing eth_getBlockByNumber in logs");
        }
        // New format uses "duration=" not "durationMicros="
        if (!logs.contains("duration=")) {
            throw new RuntimeException("Missing duration in logs");
        }

        // 2. Verify Tx Lifecycle Logging
        if (!logs.contains("[TX-SEND]")) {
            throw new RuntimeException("Missing [TX-SEND] log");
        }
        // With Smart Gas, estimation is done via RPC
        if (!logs.contains("method=eth_estimateGas")) {
            throw new RuntimeException("Missing eth_estimateGas in logs");
        }

        // 3. Verify Redaction
        if (logs.contains("0x1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef")) {
            throw new RuntimeException("Private key leaked in logs!");
        }
        // Note: New format doesn't log full request/response payloads,
        // so credential redaction verification is not applicable
    }
}
