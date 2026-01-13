// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.examples;

import sh.brane.core.error.RpcException;
import sh.brane.core.error.TxnException;
import sh.brane.rpc.Brane;

/**
 * Demonstrates Brane's error handling &amp; diagnostics.
 *
 * <p>Usage:
 *
 * <p>1) Real RPC error (bad URL):
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *   -PmainClass=sh.brane.examples.ErrorDiagnosticsExample \
 *   -Pbrane.examples.mode=rpc-error
 * </pre>
 *
 * <p>2) Diagnostics helpers demo (no network needed):
 * <pre>
 * ./gradlew :brane-examples:run --no-daemon \
 *   -PmainClass=sh.brane.examples.ErrorDiagnosticsExample \
 *   -Pbrane.examples.mode=helpers
 * </pre>
 */
public final class ErrorDiagnosticsExample {

        private ErrorDiagnosticsExample() {
        }

        public static void main(String[] args) throws Exception {
                final String mode = System.getProperty("brane.examples.mode", "helpers");
                switch (mode) {
                        case "rpc-error" -> runRpcErrorDemo();
                        case "helpers" -> runHelpersDemo();
                        default -> {
                                System.out.println("Unknown mode: " + mode);
                                System.out.println("Use -Pbrane.examples.mode=rpc-error or helpers");
                        }
                }
        }

        private static void runRpcErrorDemo() throws Exception {
                System.out.println("=== RPC error demo ===");
                final String badUrl = "http://127.0.0.1:9999"; // assume no node here

                Brane client = Brane.connect(badUrl);
                try {
                        // This should fail with a network/connection error wrapped in RpcException
                        client.getLatestBlock();
                        System.out.println("Unexpected success: getLatestBlock() returned without error");
                } catch (RpcException e) {
                        System.out.println("Caught RpcException as expected");
                        System.out.println("message = " + e.getMessage());
                        System.out.println("code    = " + e.code());
                        System.out.println("data    = " + e.data());
                        System.out.println("isBlockRangeTooLarge = " + e.isBlockRangeTooLarge());
                        System.out.println("isFilterNotFound      = " + e.isFilterNotFound());
                } catch (Exception e) {
                        System.out.println("Caught unexpected exception type: " + e.getClass().getName());
                        e.printStackTrace(System.out);
                } finally {
                        client.close();
                }
        }

        private static void runHelpersDemo() {
                System.out.println("=== Helper diagnostics demo ===");

                // 1) RpcException: block range too large
                RpcException blockRangeError = new RpcException(
                                -32000,
                                "block range is too large: from 0x0 to 0xfffff",
                                null,
                                null,
                                null);
                System.out.println("[RpcException/blockRange] isBlockRangeTooLarge = "
                                + blockRangeError.isBlockRangeTooLarge());
                System.out.println("[RpcException/blockRange] isFilterNotFound      = "
                                + blockRangeError.isFilterNotFound());

                // 2) RpcException: filter not found
                RpcException filterNotFoundError = new RpcException(
                                -32000,
                                "filter not found",
                                null,
                                null,
                                null);
                System.out.println("[RpcException/filterNotFound] isBlockRangeTooLarge = "
                                + filterNotFoundError.isBlockRangeTooLarge());
                System.out.println("[RpcException/filterNotFound] isFilterNotFound      = "
                                + filterNotFoundError.isFilterNotFound());

                // 3) TxnException: invalid sender
                TxnException invalidSender = new TxnException("invalid sender: from address does not match signature");
                System.out.println("[TxnException/invalidSender] isInvalidSender    = "
                                + invalidSender.isInvalidSender());
                System.out.println("[TxnException/invalidSender] isChainIdMismatch = "
                                + invalidSender.isChainIdMismatch());

                // 4) TxnException: chain id mismatch
                TxnException chainMismatch = new TxnException("chain id mismatch: expected 31337, got 1");
                System.out.println("[TxnException/chainMismatch] isInvalidSender    = "
                                + chainMismatch.isInvalidSender());
                System.out.println("[TxnException/chainMismatch] isChainIdMismatch = "
                                + chainMismatch.isChainIdMismatch());
        }
}
