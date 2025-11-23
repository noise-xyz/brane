package io.brane.examples;

import io.brane.core.builder.TxBuilder;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.DefaultWalletClient;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.PrivateKeyTransactionSigner;
import io.brane.rpc.PublicClient;
import io.brane.rpc.TransactionSigner;
import io.brane.rpc.WalletClient;
import java.math.BigInteger;

/**
 * Integration test for TxBuilder verifying:
 * 1. EIP-1559 transaction sending (with auto-fill and explicit fees).
 * 2. Contract deployment via builder.
 *
 * Usage:
 * ./gradlew :brane-examples:run -PmainClass=io.brane.examples.TxBuilderIntegrationTest \
 *   -Dbrane.examples.rpc=http://127.0.0.1:8545 \
 *   -Dbrane.examples.pk=0x...
 */
public final class TxBuilderIntegrationTest {

    // Simple contract bytecode that returns 42
    // contract Answer { function value() public pure returns (uint) { return 42; } }
    private static final String BYTECODE =
            "0x608060405234801561001057600080fd5b50610126806100206000396000f3fe6080604052348015600f57600080fd5b506004361060285760003560e01c80633fa4f24514602d575b600080fd5b60336049565b6040518082815260200191505060405180910390f35b6000602a90509056fea2646970667358221220d9b42d7083d5cc53d025915f18596637a7b87d605d4859859108c3f4c650830b64736f6c63430008140033";

    private TxBuilderIntegrationTest() {}

    public static void main(final String[] args) {
        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        final String privateKey = System.getProperty("brane.examples.pk");

        if (privateKey == null || privateKey.isBlank()) {
            System.err.println("Error: -Dbrane.examples.pk must be set");
            System.exit(1);
        }

        final BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
        final PublicClient publicClient = PublicClient.from(provider);
        final PrivateKeyTransactionSigner signer = new PrivateKeyTransactionSigner(privateKey);
        final TransactionSigner txSigner = signer::sign;
        final WalletClient wallet =
                DefaultWalletClient.create(provider, publicClient, txSigner, signer.address());

        System.out.println("Running TxBuilder Integration Tests...");

        testEip1559Transfer(wallet, signer.address());
        testContractDeployment(wallet);

        System.out.println("TxBuilder Integration Tests Passed!");
    }

    private static void testEip1559Transfer(final WalletClient wallet, final Address self) {
        System.out.println("[Test] EIP-1559 Transfer (Self-send)...");
        
        // 1. Auto-filled fees
        TransactionRequest autoRequest = TxBuilder.eip1559()
                .to(self)
                .value(Wei.of(100))
                .build();
        
        TransactionReceipt autoReceipt = wallet.sendTransactionAndWait(autoRequest, 10000, 500);
        System.out.println("  Auto-fill tx: " + autoReceipt.transactionHash().value() + " (Block: " + autoReceipt.blockNumber() + ")");

        // 2. Explicit fees
        TransactionRequest explicitRequest = TxBuilder.eip1559()
                .to(self)
                .value(Wei.of(100))
                .maxFeePerGas(Wei.of(50_000_000_000L))
                .maxPriorityFeePerGas(Wei.of(2_000_000_000L))
                .build();

        TransactionReceipt explicitReceipt = wallet.sendTransactionAndWait(explicitRequest, 10000, 500);
        System.out.println("  Explicit fees tx: " + explicitReceipt.transactionHash().value() + " (Block: " + explicitReceipt.blockNumber() + ")");
    }

    private static void testContractDeployment(final WalletClient wallet) {
        System.out.println("[Test] Contract Deployment via Builder...");

        TransactionRequest deployRequest = TxBuilder.legacy()
                .data(new HexData(BYTECODE))
                .build();

        TransactionReceipt receipt = wallet.sendTransactionAndWait(deployRequest, 10000, 500);
        
        if (receipt.contractAddress().value().isEmpty()) {
            throw new RuntimeException("Contract deployment failed: No contract address in receipt");
        }
        
        System.out.println("  Contract deployed at: " + receipt.contractAddress().value());
    }
}
