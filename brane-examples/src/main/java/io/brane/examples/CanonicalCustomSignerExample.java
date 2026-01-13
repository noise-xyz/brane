// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.examples;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

import io.brane.core.AnsiColors;
import io.brane.core.builder.TxBuilder;
import io.brane.core.crypto.Keccak256;
import io.brane.core.crypto.PrivateKey;
import io.brane.core.crypto.Signature;
import io.brane.core.crypto.Signer;
import io.brane.core.error.RpcException;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.tx.UnsignedTransaction;
import io.brane.core.types.Address;
import io.brane.core.types.Wei;
import io.brane.rpc.Brane;

/**
 * Canonical Custom Signer Example.
 * <p>
 * This example demonstrates how to implement the {@link Signer} interface to
 * abstract
 * away private key management. It covers two advanced scenarios:
 * <ol>
 * <li><b>Cloud KMS:</b> Simulating a Key Management Service (AWS/GCP) where the
 * key never leaves the secure enclave.</li>
 * <li><b>MPC/Threshold:</b> Simulating a Multi-Party Computation wallet where
 * multiple approvals are required before signing.</li>
 * </ol>
 */
public final class CanonicalCustomSignerExample {

    private static final String RPC_URL = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
    private static final Address RECIPIENT = new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");

    public static void main(String[] args) {
        System.out.println("=== Canonical Custom Signer Examples ===");

        try {
            runKmsScenario();
            System.out.println("\n--------------------------------------------------\n");
            runMpcScenario();
        } catch (final RpcException e) {
            System.err.println("❌ RPC error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }

        System.exit(0);
    }

    private static void runKmsScenario() throws RpcException {
        System.out.println("[Scenario 1] Cloud KMS Signer");

        // 1. Initialize the "Cloud KMS" (Simulation)
        final String keyId = "alias/my-secure-key-1";
        MockCloudKms mockKms = new MockCloudKms();
        mockKms.createKey(keyId, "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");

        // 2. Create our Custom Signer
        Signer signer = new KmsSigner(mockKms, keyId);
        System.out.println("  Signer Type: KmsSigner");
        System.out.println("  Address: " + signer.address());

        // 3. Send Transaction
        sendTransaction(signer, "KMS");
    }

    private static void runMpcScenario() throws RpcException {
        System.out.println("[Scenario 2] MPC (Threshold) Signer");

        // 1. Setup the MPC Cluster (Simulation)
        // 3 parties, threshold 2
        String distributedKey = "0x59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d";
        MockMpcCluster mpcCluster = new MockMpcCluster(distributedKey, 3, 2);

        // 2. Create the MPC Signer
        Signer signer = new MpcSigner(mpcCluster);
        System.out.println("  Signer Type: MpcSigner");
        System.out.println("  Address: " + signer.address());

        // 3. Start Transaction in background (it will block for approvals)
        CompletableFuture<Void> txFuture = CompletableFuture.runAsync(() -> {
            try {
                sendTransaction(signer, "MPC");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        // 4. Simulate Approvals
        try {
            Thread.sleep(1000);
            System.out.println("\n  [MPC Coordinator] Requesting approvals...");

            Thread.sleep(500);
            System.out.println("    > Alice approving...");
            mpcCluster.approve("Alice");

            Thread.sleep(500);
            System.out.println("    > Bob approving...");
            mpcCluster.approve("Bob");

            // Wait for completion
            txFuture.get(10, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("MPC operation interrupted", e);
        } catch (java.util.concurrent.ExecutionException | java.util.concurrent.TimeoutException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RpcException rpcEx) {
                throw rpcEx;
            }
            throw new RuntimeException("MPC operation failed", e);
        }
    }

    private static void sendTransaction(Signer signer, String label) throws RpcException {
        Brane.Signer client = Brane.connect(RPC_URL, signer);

        System.out.println("  Sending transaction...");

        TransactionRequest tx = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.fromEther(new BigDecimal("0.01")))
                .build();

        TransactionReceipt receipt = client.sendTransactionAndWait(tx, 60_000, 1_000);

        if (receipt.status()) {
            System.out.println(
                    AnsiColors.success("  ✓ " + label + " Transaction Confirmed: " + receipt.transactionHash()));
        } else {
            throw new RuntimeException(label + " Transaction Failed");
        }
    }

    // --- KMS Implementation ---

    public static class KmsSigner implements Signer {
        private final MockCloudKms kmsClient;
        private final String keyId;
        private final Address address;

        public KmsSigner(MockCloudKms kmsClient, String keyId) {
            this.kmsClient = kmsClient;
            this.keyId = keyId;
            this.address = kmsClient.getPublicKey(keyId).toAddress();
        }

        @Override
        public Address address() {
            return address;
        }

        @Override
        public Signature signMessage(byte[] message) {
            // Simulate signing a message
            byte[] prefix = ("\u0019Ethereum Signed Message:\n" + message.length)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] prefixedMessage = new byte[prefix.length + message.length];
            System.arraycopy(prefix, 0, prefixedMessage, 0, prefix.length);
            System.arraycopy(message, 0, prefixedMessage, prefix.length, message.length);

            Signature sig = kmsClient.sign(keyId, Keccak256.hash(prefixedMessage));
            // Adjust v to be 27 or 28 for EIP-191 compatibility
            return new Signature(sig.r(), sig.s(), sig.v() + 27);
        }

        @Override
        public Signature signTransaction(UnsignedTransaction tx, long chainId) {
            System.out.println("  [KmsSigner] Requesting signature from KMS for Key ID: " + keyId);
            byte[] preimage = tx.encodeForSigning(chainId);
            byte[] messageHash = Keccak256.hash(preimage);
            return kmsClient.sign(keyId, messageHash);
        }
    }

    public static class MockCloudKms {
        private final Map<String, PrivateKey> secureStorage = new HashMap<>();

        public void createKey(String keyId, String privateKeyHex) {
            secureStorage.put(keyId, PrivateKey.fromHex(privateKeyHex));
        }

        public PrivateKey getPublicKey(String keyId) {
            return secureStorage.get(keyId);
        }

        public Signature sign(String keyId, byte[] digest) {
            PrivateKey pk = secureStorage.get(keyId);
            if (pk == null)
                throw new IllegalArgumentException("Key not found: " + keyId);
            return pk.signFast(digest);
        }
    }

    // --- MPC Implementation ---

    public static class MpcSigner implements Signer {
        private final MockMpcCluster cluster;
        private final Address address;

        public MpcSigner(MockMpcCluster cluster) {
            this.cluster = cluster;
            this.address = cluster.getPublicKey().toAddress();
        }

        @Override
        public Address address() {
            return address;
        }

        @Override
        public Signature signMessage(byte[] message) {
            System.out.println("  [MpcSigner] Message received. Waiting for " + cluster.threshold + " approvals...");
            try {
                cluster.waitForThreshold();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("MPC Signing Interrupted", e);
            }
            System.out.println("  [MpcSigner] Threshold met! Generating signature...");

            byte[] prefix = ("\u0019Ethereum Signed Message:\n" + message.length)
                    .getBytes(java.nio.charset.StandardCharsets.UTF_8);
            byte[] prefixedMessage = new byte[prefix.length + message.length];
            System.arraycopy(prefix, 0, prefixedMessage, 0, prefix.length);
            System.arraycopy(message, 0, prefixedMessage, prefix.length, message.length);

            Signature sig = cluster.generateSignature(Keccak256.hash(prefixedMessage));
            // Adjust v to be 27 or 28 for EIP-191 compatibility
            return new Signature(sig.r(), sig.s(), sig.v() + 27);
        }

        @Override
        public Signature signTransaction(UnsignedTransaction tx, long chainId) {
            System.out
                    .println("  [MpcSigner] Transaction received. Waiting for " + cluster.threshold + " approvals...");
            try {
                cluster.waitForThreshold();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("MPC Signing Interrupted", e);
            }
            System.out.println("  [MpcSigner] Threshold met! Generating signature...");

            byte[] preimage = tx.encodeForSigning(chainId);
            byte[] messageHash = Keccak256.hash(preimage);
            return cluster.generateSignature(messageHash);
        }
    }

    public static class MockMpcCluster {
        private final PrivateKey privateKey;
        private final int threshold;
        private final Set<String> approvals = new HashSet<>();
        private final ReentrantLock lock = new ReentrantLock();
        private final Condition thresholdReached = lock.newCondition();

        public MockMpcCluster(String privateKeyHex, int totalParties, int threshold) {
            this.privateKey = PrivateKey.fromHex(privateKeyHex);
            this.threshold = threshold;
        }

        public PrivateKey getPublicKey() {
            return privateKey;
        }

        public void approve(String partyId) {
            lock.lock();
            try {
                approvals.add(partyId);
                System.out.println("    (Cluster: " + approvals.size() + "/" + threshold + " approvals)");
                if (approvals.size() >= threshold) {
                    thresholdReached.signalAll();
                }
            } finally {
                lock.unlock();
            }
        }

        public void waitForThreshold() throws InterruptedException {
            lock.lock();
            try {
                while (approvals.size() < threshold) {
                    thresholdReached.await();
                }
            } finally {
                lock.unlock();
            }
        }

        public Signature generateSignature(byte[] digest) {
            return privateKey.signFast(digest);
        }
    }
}
