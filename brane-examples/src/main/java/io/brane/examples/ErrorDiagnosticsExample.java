package io.brane.examples;

import io.brane.core.chain.ChainProfiles;
import io.brane.core.error.RpcException;
import io.brane.core.error.TxnException;
import io.brane.rpc.BranePublicClient;

/**
 * Demonstrates Brane's error handling &amp; diagnostics.
 *
 * Usage:
 *
 * 1) Real RPC error (bad URL):
 *
 * ./gradlew :brane-examples:run --no-daemon \
 * -PmainClass=io.brane.examples.ErrorDiagnosticsExample \
 * -Pbrane.examples.mode=rpc-error
 *
 * 2) Diagnostics helpers demo (no network needed):
 *
 * ./gradlew :brane-examples:run --no-daemon \
 * -PmainClass=io.brane.examples.ErrorDiagnosticsExample \
 * -Pbrane.examples.mode=helpers
 */
public final class ErrorDiagnosticsExample {

        private ErrorDiagnosticsExample() {
        }

        public static void main(String[] args) {
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

        private static void runRpcErrorDemo() {
                System.out.println("=== RPC error demo ===");
                final String badUrl = "http://127.0.0.1:9999"; // assume no node here

                try {
                        var client = BranePublicClient
                                        .forChain(ChainProfiles.ANVIL_LOCAL)
                                        .withRpcUrl(badUrl)
                                        .build();

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
