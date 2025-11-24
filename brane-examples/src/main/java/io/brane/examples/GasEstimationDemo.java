package io.brane.examples;

import io.brane.core.BraneDebug;
import io.brane.core.builder.TxBuilder;
import io.brane.core.chain.ChainProfile;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.Wei;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.DefaultWalletClient;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;
import java.math.BigInteger;

/**
 * Demonstrates Brane's configurable gas estimation.
 * Shows default 20% buffer vs custom 50% buffer.
 * 
 * <p>Requires a local Anvil node running at http://127.0.0.1:8545
 * 
 * <p>Run with:
 * <pre>
 * anvil --accounts 1 --balance 10000
 * ./gradlew :brane-examples:run -PmainClass=io.brane.examples.GasEstimationDemo
 * </pre>
 */
public class GasEstimationDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Gas Estimation Configuration Demo ===\n");

        // Enable debug mode to see gas estimation logs
        BraneDebug.setEnabled(true);

        final String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        // Default Anvil Account 0
        final String privateKey = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

        final BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
        final PublicClient publicClient = PublicClient.from(provider);
        
        // Get chain ID
        final long chainId = Long.parseLong(provider.send("eth_chainId", null).result().toString().substring(2), 16);
        
        // Setup signer
        final var credentials = io.brane.internal.web3j.crypto.Credentials.create(privateKey);
        final Address signerAddress = new Address(credentials.getAddress());
        final io.brane.rpc.TransactionSigner signer =
                tx -> {
                    io.brane.internal.web3j.crypto.RawTransaction raw = tx;
                    byte[] signedMessage =
                            io.brane.internal.web3j.crypto.TransactionEncoder.signMessage(
                                    raw, chainId, credentials);
                    return io.brane.primitives.Hex.encode(signedMessage);
                };

        System.out.println("Sender: " + signerAddress.value());
        System.out.println("RPC: " + rpcUrl + "\n");

        //  Chain profile
        final ChainProfile profile = ChainProfile.of(chainId, rpcUrl, true, Wei.of(1_000_000_000L));

        // Test 1: Default 20% buffer
        System.out.println("--- Test 1: Default Buffer (20%) ---");
        final WalletClient defaultWallet = DefaultWalletClient.create(
                provider, publicClient, signer, signerAddress, profile);

        final TransactionRequest tx1 = TxBuilder.eip1559()
                .to(new Address("0x0000000000000000000000000000000000000001"))
                .value(Wei.of(100))
                .build();

        System.out.println("Sending transaction with default buffer...");
        final Hash txHash1 = defaultWallet.sendTransaction(tx1);
        System.out.println("✓ Transaction sent: " + txHash1.value() + "\n");

        // Test 2: Custom 50% buffer
        System.out.println("--- Test 2: Custom Buffer (50%) ---");
        final WalletClient customWallet = DefaultWalletClient.create(
                provider,
                publicClient,
                signer,
                signerAddress,
                profile,
                BigInteger.valueOf(150), // 150/100 = 50% buffer
                BigInteger.valueOf(100));

        final TransactionRequest tx2 = TxBuilder.eip1559()
                .to(new Address("0x0000000000000000000000000000000000000002"))
                .value(Wei.of(200))
                .build();

        System.out.println("Sending transaction with 50% buffer...");
        final Hash txHash2 = customWallet.sendTransaction(tx2);
        System.out.println("✓ Transaction sent: " + txHash2.value() + "\n");

        System.out.println("=== Demo Complete ===");
        System.out.println("\nNote: With debug mode enabled, you can see the [RPC] logs showing:");
        System.out.println("  - eth_estimateGas calls");
        System.out.println("  - Gas limit calculations with buffers");
        System.out.println("  - Final transaction parameters");
        System.out.println("\nLook for logs like:");
        System.out.println("  [RPC] method=eth_estimateGas ... (shows base estimate)");
        System.out.println("  [TX-SEND] gasLimit=<value> ... (shows buffered gas limit)");
    }
}
