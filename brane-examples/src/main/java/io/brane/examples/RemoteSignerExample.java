package io.brane.examples;

import io.brane.core.builder.TxBuilder;
import io.brane.core.crypto.Keccak256;
import io.brane.core.crypto.PrivateKey;
import io.brane.core.crypto.Signature;
import io.brane.core.crypto.Signer;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.tx.UnsignedTransaction;
import io.brane.core.types.Address;
import io.brane.core.types.Wei;
import io.brane.rpc.BraneProvider;
import io.brane.rpc.DefaultWalletClient;
import io.brane.rpc.HttpBraneProvider;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;

/**
 * Demonstrates how to implement a custom Signer that could delegate to a remote
 * service (e.g., KMS, HSM).
 * For this example, we simulate the "remote" service with a local private key,
 * but the structure
 * mimics a real remote signer integration.
 */
public final class RemoteSignerExample {

    // 1. Define a "Remote Service" interface
    interface RemoteKeyManagementService {
        Signature sign(String keyId, byte[] hash);

        Address getPublicKey(String keyId);
    }

    // 2. Mock implementation of the remote service (in real life, this calls
    // AWS/GCP/Vault)
    static class MockKmsService implements RemoteKeyManagementService {
        private final PrivateKey key;

        MockKmsService(String hexKey) {
            this.key = PrivateKey.fromHex(hexKey);
        }

        @Override
        public Signature sign(String keyId, byte[] hash) {
            System.out.println("[KMS] Signing hash for key: " + keyId);
            return key.signFast(hash);
        }

        @Override
        public Address getPublicKey(String keyId) {
            return key.toAddress();
        }
    }

    // 3. Implement the Brane Signer interface
    static class KmsSigner implements Signer {
        private final RemoteKeyManagementService kms;
        private final String keyId;
        private final Address address;

        KmsSigner(RemoteKeyManagementService kms, String keyId) {
            this.kms = kms;
            this.keyId = keyId;
            this.address = kms.getPublicKey(keyId);
        }

        @Override
        public Address address() {
            return address;
        }

        @Override
        public Signature signMessage(byte[] message) {
            throw new UnsupportedOperationException("Remote signing of messages not implemented yet");
        }

        @Override
        public Signature signTransaction(UnsignedTransaction tx, long chainId) {
            // A. Encode the transaction for signing (preimage)
            byte[] preimage = tx.encodeForSigning(chainId);
            byte[] hash = Keccak256.hash(preimage);

            // B. Request signature from "remote" service
            return kms.sign(keyId, hash);
        }
    }

    public static void main(String[] args) {
        String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        String remoteKey = System.getProperty("brane.examples.pk"); // Using this as our "KMS" key source

        if (remoteKey == null) {
            System.err.println("Please provide -Dbrane.examples.pk");
            System.exit(1);
        }

        System.out.println("=== Custom Remote Signer Example ===");

        // Setup
        BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
        PublicClient publicClient = PublicClient.from(provider);

        // Initialize our "Remote" Signer
        RemoteKeyManagementService mockKms = new MockKmsService(remoteKey);
        Signer customSigner = new KmsSigner(mockKms, "alias/my-eth-key");

        System.out.println("Signer Address (from KMS): " + customSigner.address().value());

        // Create WalletClient with custom signer
        WalletClient wallet = DefaultWalletClient.create(
                provider,
                publicClient,
                customSigner);

        // Send a transaction
        try {
            var tx = TxBuilder.eip1559()
                    .to(customSigner.address()) // Self-transfer
                    .value(Wei.of(1000))
                    .build();

            System.out.println("Sending transaction via KmsSigner...");
            TransactionReceipt receipt = wallet.sendTransactionAndWait(tx, 10_000, 1_000);

            System.out.println("✓ Transaction confirmed: " + receipt.transactionHash().value());
            System.out.println("✓ Block: " + receipt.blockNumber());
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(1);
        }
    }
}
