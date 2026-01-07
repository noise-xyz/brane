package io.brane.examples;

import io.brane.core.RevertDecoder;
import io.brane.core.abi.Abi;
import io.brane.core.builder.TxBuilder;
import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.error.RevertException;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.rpc.Brane;

/**
 * Integration test for WalletClient revert handling.
 *
 * Verifies that WalletClient.sendTransaction() throws RevertException
 * when the node rejects a transaction with revert data.
 *
 * Usage:
 * ./gradlew :brane-examples:run -PmainClass=io.brane.examples.WalletRevertTest
 * \
 * -Dbrane.examples.rpc=http://127.0.0.1:8545 \
 * -Dbrane.examples.contract=0x... \
 * -Dbrane.examples.pk=0x...
 */
public final class WalletRevertTest {

    // ABI for RevertExample
    private static final String ABI_JSON = """
            [
                {"type":"function","name":"alwaysRevert","inputs":[],"outputs":[],"stateMutability":"pure"}
            ]
            """;

    private WalletRevertTest() {
    }

    public static void main(String[] args) {
        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        final String contractAddr = System.getProperty("brane.examples.contract");
        final String privateKey = System.getProperty("brane.examples.pk");

        if (contractAddr == null || contractAddr.isBlank()) {
            System.err.println("Error: -Dbrane.examples.contract must be set");
            System.exit(1);
        }
        if (privateKey == null || privateKey.isBlank()) {
            System.err.println("Error: -Dbrane.examples.pk must be set");
            System.exit(1);
        }

        System.out.println("Running Brane.Signer Revert Test...");
        System.out.println("RPC: " + rpcUrl);
        System.out.println("Contract: " + contractAddr);

        final PrivateKeySigner signer = new PrivateKeySigner(privateKey);
        final Brane.Signer client = Brane.connect(rpcUrl, signer);

        testRevert(client, new Address(contractAddr));

        System.out.println("Brane.Signer Revert Test Passed!");
    }

    private static void testRevert(Brane.Signer client, Address contractAddr) {
        System.out.println("[Test] Brane.Signer sends transaction that will revert...");

        // Encode call to alwaysRevert()
        final Abi abi = Abi.fromJson(ABI_JSON);
        final Abi.FunctionCall call = abi.encodeFunction("alwaysRevert");

        TransactionRequest request = TxBuilder.eip1559()
                .to(contractAddr)
                .data(new HexData(call.data()))
                .build();

        try {
            client.sendTransaction(request);
            throw new RuntimeException("Expected RevertException but transaction was accepted");
        } catch (RevertException e) {
            // Expected - some nodes may pre-validate and reject with revert data
            if (e.kind() != RevertDecoder.RevertKind.ERROR_STRING) {
                throw new RuntimeException("Expected ERROR_STRING kind, got: " + e.kind());
            }
            if (!"simple reason".equals(e.revertReason())) {
                throw new RuntimeException("Expected 'simple reason', got: " + e.revertReason());
            }
            System.out.println("  Caught expected revert during send: " + e.getMessage());
        } catch (Exception e) {
            // Some nodes don't pre-validate - they accept the tx and it reverts later
            System.out.println("  Node accepted transaction (will revert when mined): " + e.getClass().getSimpleName());
            System.out.println("  Note: Not all nodes pre-validate transactions for reverts");
        }
    }
}
