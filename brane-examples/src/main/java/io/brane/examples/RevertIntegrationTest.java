package io.brane.examples;

import io.brane.contract.Abi;
import io.brane.contract.Contract;
import io.brane.core.RevertDecoder;
import io.brane.core.error.RevertException;
import io.brane.core.types.Address;
import io.brane.rpc.Client;
import io.brane.rpc.HttpClient;

import java.net.URI;

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
        final String contractAddr = System.getProperty("brane.examples.contract");

        if (contractAddr == null || contractAddr.isBlank()) {
            System.err.println("Error: -Dbrane.examples.contract must be set");
            System.exit(1);
        }

        System.out.println("Running Revert Integration Tests...");
        System.out.println("RPC: " + rpcUrl);
        System.out.println("Contract: " + contractAddr);

        final Client client = new HttpClient(URI.create(rpcUrl));
        final Abi abi = Abi.fromJson(ABI_JSON);
        final Contract contract = new Contract(new Address(contractAddr), abi, client);

        testErrorString(contract);
        testPanic(contract);

        System.out.println("Revert Integration Tests Passed!");
    }

    private static void testErrorString(Contract contract) {
        System.out.println("[Test] Error(string)...");
        try {
            contract.read("alwaysRevert", Void.class);
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

    private static void testPanic(Contract contract) {
        System.out.println("[Test] Panic(uint256)...");
        try {
            contract.read("triggerPanic", Void.class);
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
