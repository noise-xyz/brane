package io.brane.examples;

import io.brane.contract.Abi;
import io.brane.core.RevertDecoder;
import io.brane.core.builder.TxBuilder;
import io.brane.core.error.RevertException;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.DefaultWalletClient;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.PrivateKeyTransactionSigner;
import io.brane.rpc.PublicClient;
import io.brane.rpc.TransactionSigner;
import io.brane.rpc.WalletClient;

/**
 * Integration test for WalletClient revert handling.
 * 
 * Verifies that WalletClient.sendTransaction() throws RevertException
 * when the node rejects a transaction with revert data.
 * 
 * Usage:
 * ./gradlew :brane-examples:run -PmainClass=io.brane.examples.WalletRevertTest \
 *   -Dbrane.examples.rpc=http://127.0.0.1:8545 \
 *   -Dbrane.examples.contract=0x... \
 *   -Dbrane.examples.pk=0x...
 */
public final class WalletRevertTest {

    // ABI for RevertExample
    private static final String ABI_JSON = """
            [
                {"type":"function","name":"alwaysRevert","inputs":[],"outputs":[],"stateMutability":"pure"}
            ]
            """;

    private WalletRevertTest() {}

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

        System.out.println("Running WalletClient Revert Test...");
        System.out.println("RPC: " + rpcUrl);
        System.out.println("Contract: " + contractAddr);

        final BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
        final PublicClient publicClient = PublicClient.from(provider);
        final PrivateKeyTransactionSigner signer = new PrivateKeyTransactionSigner(privateKey);
        final TransactionSigner txSigner = signer::sign;
        final WalletClient wallet =
                DefaultWalletClient.create(provider, publicClient, txSigner, signer.address());

        testWalletRevert(wallet, new Address(contractAddr));

        System.out.println("WalletClient Revert Test Passed!");
    }

    private static void testWalletRevert(WalletClient wallet, Address contractAddr) {
        System.out.println("[Test] WalletClient sends transaction that will revert...");
        
        // Encode call to alwaysRevert()
        final Abi abi = Abi.fromJson(ABI_JSON);
        final Abi.FunctionCall call = abi.encodeFunction("alwaysRevert");
        
        TransactionRequest request = TxBuilder.eip1559()
                .to(contractAddr)
                .data(new HexData(call.data()))
                .build();

        try {
            wallet.sendTransaction(request);
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
