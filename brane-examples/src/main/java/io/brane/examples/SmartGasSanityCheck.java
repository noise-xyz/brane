package io.brane.examples;

import io.brane.core.chain.ChainProfiles;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Wei;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.DefaultWalletClient;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.PublicClient;
import io.brane.rpc.TransactionSigner;
import java.math.BigInteger;
import java.net.URI;
import java.time.Duration;

/**
 * Sanity check for Smart Gas Defaults.
 * Requires a local Anvil node running at http://127.0.0.1:8545
 */
public class SmartGasSanityCheck {

    // Default Anvil Account 0 Private Key
    private static final String PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
    private static final Address SENDER = new Address("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266");

    public static void main(String[] args) throws Exception {
        System.out.println("=== Smart Gas Sanity Check ===");

        // 1. Setup Provider and Clients
        final BraneProvider provider = HttpBraneProvider.builder("http://127.0.0.1:8545").build();
        final PublicClient publicClient = PublicClient.from(provider);

        // 2. Setup Signer (using internal web3j for now, as Phase 3 is not yet done)
        final io.brane.internal.web3j.crypto.Credentials credentials =
                io.brane.internal.web3j.crypto.Credentials.create(PRIVATE_KEY);
        
        final TransactionSigner signer =
                tx -> {
                    // Simple signing wrapper using legacy web3j
                    // In Phase 3 this will be replaced by brane-crypto
                    io.brane.internal.web3j.crypto.RawTransaction raw = tx;
                    byte[] signedMessage =
                            io.brane.internal.web3j.crypto.TransactionEncoder.signMessage(
                                    raw, 31337, credentials);
                    return io.brane.primitives.Hex.encode(signedMessage);
                };

        final DefaultWalletClient wallet =
                DefaultWalletClient.from(
                        provider,
                        publicClient,
                        signer,
                        SENDER,
                        31337, // Anvil Chain ID
                        ChainProfiles.ANVIL_LOCAL);

        // 3. Create a simple transfer transaction WITHOUT gas fields
        System.out.println("Creating transaction with NO gas fields...");
        final TransactionRequest request =
                io.brane.core.builder.TxBuilder.eip1559()
                        .to(new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8")) // Account 1
                        .value(Wei.of(1_000_000_000_000_000_000L)) // 1 ETH
                        .build();

        // 4. Send and Wait
        System.out.println("Sending transaction...");
        final TransactionReceipt receipt = wallet.sendTransactionAndWait(request, 1000, 30);

        // 5. Verify and Print
        System.out.println("Transaction Mined!");
        System.out.println("Hash: " + receipt.transactionHash());
        System.out.println("Block Number: " + receipt.blockNumber());
        System.out.println("Cumulative Gas Used: " + receipt.cumulativeGasUsed());
        
        if (receipt.status()) {
            System.out.println("Status: SUCCESS");
        } else {
            System.err.println("Status: FAILED");
            System.exit(1);
        }
        
        System.out.println("=== Sanity Check Passed ===");
    }
}
