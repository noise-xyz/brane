package io.brane.examples;

import io.brane.core.BraneDebug;
import io.brane.core.error.RpcException;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.PublicClient;
import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Sanity check for Request ID Correlation (Phase 2.9).
 * Demonstrates that request IDs appear in logs and exceptions.
 * Requires a local Anvil node running at http://127.0.0.1:8545
 */
public class RequestIdSanityCheck {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Request ID Correlation Sanity Check ===");

        // 1. Enable debug mode to see logs
        BraneDebug.setEnabled(true);

        // 2. Capture stdout to analyze logs
        final ByteArrayOutputStream outputCapture = new ByteArrayOutputStream();
        final PrintStream originalOut = System.out;
        final PrintStream originalErr = System.err;
        final PrintStream tee = new PrintStream(new TeeOutputStream(outputCapture, originalOut));
        System.setOut(tee);
        System.setErr(tee);

        try {
            // 3. Setup clients
            final BraneProvider provider = HttpBraneProvider.builder("http://127.0.0.1:8545").build();
            final PublicClient publicClient = PublicClient.from(provider);

            originalOut.println("Making multiple RPC calls to demonstrate request ID correlation...\n");

            // 4. Make several RPC calls
            try {
                publicClient.getLatestBlock();
                publicClient.getBlockByNumber(1);
                publicClient.getLatestBlock();
            } catch (Exception e) {
                originalErr.println("Unexpected error during RPC calls: " + e.getMessage());
                System.exit(1);
            }

            // 5. Intentionally trigger an error to see request ID in exception
            originalOut.println("\nTriggering an error to demonstrate request ID in exceptions...\n");
            try {
                // Use an invalid URL to trigger network error
                final BraneProvider badProvider =
                        HttpBraneProvider.builder("http://localhost:1").build();
                final PublicClient badClient = PublicClient.from(badProvider);
                badClient.getLatestBlock();
                originalErr.println("ERROR: Expected RpcException was not thrown!");
                System.exit(1);
            } catch (RpcException e) {
                originalOut.println("✓ Caught expected RpcException:");
                originalOut.println("  Message: " + e.getMessage());
                originalOut.println("  Request ID: " + e.requestId());

                if (e.requestId() == null) {
                    originalErr.println("ERROR: Request ID is null!");
                    System.exit(1);
                }

                if (!e.getMessage().contains("[requestId=" + e.requestId() + "]")) {
                    originalErr.println("ERROR: Exception message doesn't contain request ID!");
                    System.exit(1);
                }
            }

            // Restore original streams
            System.setOut(originalOut);
            System.setErr(originalErr);

            // 6. Analyze captured logs
            final String logOutput = outputCapture.toString();
            final String[] lines = logOutput.split("\n");
            final List<String> rpcLogs = new ArrayList<>();

            System.out.println("\n=== Sample RPC Logs with Request IDs ===");
            int count = 0;
            for (String line : lines) {
                if ((line.contains("[RPC]") || line.contains("[RPC-ERROR]")) && count < 5) {
                    System.out.println(line);
                    count++;
                }
                if (line.contains("[RPC]") || line.contains("[RPC-ERROR]")) {
                    rpcLogs.add(line);
                }
            }

            if (rpcLogs.isEmpty()) {
                System.err.println("ERROR: No RPC logs found!");
                System.exit(1);
            }

            // 7. Verify request IDs are present and monotonically increasing
            System.out.println("\n=== Verifying Request ID Sequence ===");
            final Pattern idPattern = Pattern.compile("id=(\\d+)");
            final List<Long> requestIds = new ArrayList<>();

            for (String log : rpcLogs) {
                final Matcher matcher = idPattern.matcher(log);
                if (matcher.find()) {
                    requestIds.add(Long.parseLong(matcher.group(1)));
                }
            }

            if (requestIds.isEmpty()) {
                System.err.println("ERROR: No request IDs found in logs!");
                System.exit(1);
            }

            System.out.println("Found " + requestIds.size() + " request IDs: " + requestIds);

            // Verify they're monotonically increasing
            for (int i = 1; i < requestIds.size(); i++) {
                if (requestIds.get(i) <= requestIds.get(i - 1)) {
                    System.err.println("ERROR: Request IDs are not monotonically increasing!");
                    System.err.println("  " + requestIds.get(i - 1) + " -> " + requestIds.get(i));
                    System.exit(1);
                }
            }

            System.out.println("✓ Request IDs are monotonically increasing");

            // 8. Verify all RPC logs have request IDs
            long rpcLogsWithId =
                    rpcLogs.stream().filter(log -> log.contains(" id=")).count();

            if (rpcLogs.size() != rpcLogsWithId) {
                System.err.println("ERROR: Not all RPC logs contain request IDs!");
                System.err.println("  Total RPC logs: " + rpcLogs.size());
                System.err.println("  RPC logs with ID: " + rpcLogsWithId);
                System.exit(1);
            }

            System.out.println("✓ All " + rpcLogs.size() + " RPC logs contain request IDs");

            System.out.println("\n=== Sanity Check Passed ===");
            System.out.println("Request ID correlation is working correctly!");

        } finally {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    /**
     * Simple output stream that writes to two destinations
     */
    private static class TeeOutputStream extends java.io.OutputStream {
        private final java.io.OutputStream out1;
        private final java.io.OutputStream out2;

        TeeOutputStream(java.io.OutputStream out1, java.io.OutputStream out2) {
            this.out1 = out1;
            this.out2 = out2;
        }

        @Override
        public void write(int b) throws java.io.IOException {
            out1.write(b);
            out2.write(b);
        }

        @Override
        public void flush() throws java.io.IOException {
            out1.flush();
            out2.flush();
        }
    }
}
