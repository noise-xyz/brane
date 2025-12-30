package io.brane.examples;

import io.brane.contract.ReadOnlyContract;
import io.brane.core.RevertDecoder;
import io.brane.core.abi.Abi;
import io.brane.core.error.RevertException;
import io.brane.core.types.Address;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.PublicClient;

public final class RevertIntegrationTest {

    // ABI for RevertExample
    private static final String ABI_JSON = """
            [
                {"type":"function","name":"alwaysRevert","inputs":[],"outputs":[],"stateMutability":"pure"},
                {"type":"function","name":"triggerPanic","inputs":[],"outputs":[],"stateMutability":"pure"},
                {"type":"function","name":"echo","inputs":[{"name":"x","type":"uint256"}],"outputs":[{"name":"","type":"uint256"}],"stateMutability":"pure"}
            ]
            """;

    private RevertIntegrationTest() {
    }

    public static void main(String[] args) {
        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        final String contractAddr = System.getProperty("brane.anvil.revertExample.address");

        if (contractAddr == null || contractAddr.isBlank()) {
            System.err.println("Error: -Dbrane.anvil.revertExample.address must be set");
            System.exit(1);
        }

        System.out.println("Running Revert Integration Tests...");
        System.out.println("RPC: " + rpcUrl);
        System.out.println("Contract: " + contractAddr);

        final PublicClient publicClient =
                PublicClient.from(HttpBraneProvider.builder(rpcUrl).build());
        final Abi abi = Abi.fromJson(ABI_JSON);
        final ReadOnlyContract contract =
                ReadOnlyContract.from(new Address(contractAddr), abi, publicClient);

        testErrorString(contract);
        testPanic(contract);

        System.out.println("Revert Integration Tests Passed!");
    }

    private static void testErrorString(final ReadOnlyContract contract) {
        System.out.println("[Test] Error(string)...");
        try {
            contract.call("alwaysRevert", Void.class);
            throw new RuntimeException("Expected RevertException but call succeeded");
        } catch (RevertException e) {
            if (e.kind() != RevertDecoder.RevertKind.ERROR_STRING) {
                throw new RuntimeException("Expected ERROR_STRING kind, got: " + e.kind());
            }
            if (!"simple reason".equals(e.revertReason())) {
                throw new RuntimeException("Expected 'simple reason', got: " + e.revertReason());
            }
            System.out.println("  Caught expected revert: " + e.getMessage());
        }
    }

    private static void testPanic(final ReadOnlyContract contract) {
        System.out.println("[Test] Panic(uint256)...");
        try {
            contract.call("triggerPanic", Void.class);
            throw new RuntimeException("Expected RevertException but call succeeded");
        } catch (RevertException e) {
            if (e.kind() != RevertDecoder.RevertKind.PANIC) {
                throw new RuntimeException("Expected PANIC kind, got: " + e.kind());
            }
            // 0x12 = division or modulo by zero
            if (!"division or modulo by zero".equals(e.revertReason())) {
                throw new RuntimeException("Expected 'division or modulo by zero', got: " + e.revertReason());
            }
            System.out.println("  Caught expected panic: " + e.getMessage());
        }
    }
}
