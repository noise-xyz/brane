package io.brane.examples;

import io.brane.core.chain.ChainProfiles;
import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Wei;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.DefaultWalletClient;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.PublicClient;

/**
 * Sanity check for Smart Gas Defaults.
 * Requires a local Anvil node running at http://127.0.0.1:8545
 */
public final class SmartGasSanityCheck {

        private SmartGasSanityCheck() {
                // Prevent instantiation
        }

        // Default Anvil Account 0 Private Key
        private static final String PRIVATE_KEY = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

        public static void main(String[] args) {
                try {
                        System.out.println("=== Smart Gas Sanity Check ===");

                        // 1. Setup Provider and Clients
                        final BraneProvider provider = HttpBraneProvider.builder("http://127.0.0.1:8545").build();
                        final PublicClient publicClient = PublicClient.from(provider);

                        // 2. Setup Signer using brane-crypto
                        final PrivateKeySigner signer = new PrivateKeySigner(PRIVATE_KEY);
                        final Address sender = signer.address();

                        final DefaultWalletClient wallet = DefaultWalletClient.from(
                                        provider,
                                        publicClient,
                                        signer,
                                        sender,
                                        31337, // Anvil Chain ID
                                        ChainProfiles.ANVIL_LOCAL);

                        // 3. Create a simple transfer transaction WITHOUT gas fields
                        System.out.println("Creating transaction with NO gas fields...");
                        final TransactionRequest request = io.brane.core.builder.TxBuilder.eip1559()
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
                } catch (final RpcException e) {
                        System.err.println("RPC error: " + e.getMessage());
                        System.exit(1);
                } catch (final RevertException e) {
                        System.err.println("Transaction reverted: " + e.revertReason());
                        System.exit(1);
                }
        }
}
