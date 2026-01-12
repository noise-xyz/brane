// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import io.brane.core.crypto.Kzg;
import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.model.BlobTransactionRequest;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.tx.SidecarBuilder;
import io.brane.core.types.Address;
import io.brane.core.types.BlobSidecar;
import io.brane.core.types.Hash;
import io.brane.core.types.Wei;
import io.brane.kzg.CKzg;

/**
 * Integration tests for EIP-4844 blob transactions against Anvil with Cancun fork.
 *
 * <p>Requires Anvil running with Cancun fork enabled:
 * <pre>
 * anvil --hardfork cancun
 * </pre>
 *
 * <p>Run with:
 * <pre>
 * ./gradlew :brane-rpc:test -Dbrane.integration.tests=true --tests "*BlobTransactionIntegrationTest"
 * </pre>
 */
@EnabledIfSystemProperty(named = "brane.integration.tests", matches = "true")
class BlobTransactionIntegrationTest {

    private static final String TEST_PRIVATE_KEY =
            "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    private static final Address RECIPIENT =
            new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");

    private static Brane.Tester tester;
    private static Kzg kzg;
    private SnapshotId snapshot;

    @BeforeAll
    static void setupClient() {
        String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
        BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
        tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();

        // Load KZG trusted setup for blob operations
        kzg = CKzg.loadFromClasspath();
    }

    @BeforeEach
    void createSnapshot() {
        snapshot = tester.snapshot();
    }

    @org.junit.jupiter.api.AfterEach
    void revertSnapshot() {
        if (snapshot != null) {
            tester.revert(snapshot);
        }
    }

    // ==================== getBlobBaseFee() Integration Tests ====================

    @Nested
    @DisplayName("getBlobBaseFee integration tests")
    class GetBlobBaseFeeTests {

        @Test
        @DisplayName("getBlobBaseFee returns non-negative value")
        void getBlobBaseFeeReturnsNonNegativeValue() {
            Wei blobBaseFee = tester.getBlobBaseFee();

            assertNotNull(blobBaseFee);
            assertTrue(blobBaseFee.value().compareTo(BigInteger.ZERO) >= 0,
                    "Blob base fee should be non-negative");
        }

        @Test
        @DisplayName("getBlobBaseFee returns reasonable value")
        void getBlobBaseFeeReturnsReasonableValue() {
            Wei blobBaseFee = tester.getBlobBaseFee();

            // Blob base fee should be > 0 on Cancun fork
            // Minimum blob base fee is 1 wei per EIP-4844
            assertTrue(blobBaseFee.value().compareTo(BigInteger.ZERO) > 0,
                    "Blob base fee should be greater than zero on Cancun fork");
        }

        @Test
        @DisplayName("getBlobBaseFee is consistent across calls")
        void getBlobBaseFeeIsConsistentAcrossCalls() {
            Wei fee1 = tester.getBlobBaseFee();
            Wei fee2 = tester.getBlobBaseFee();

            // Fees should be the same within the same block
            assertEquals(fee1.value(), fee2.value(),
                    "Blob base fee should be consistent within the same block");
        }

        @Test
        @DisplayName("getBlobBaseFee can change after blocks are mined")
        void getBlobBaseFeeCanChangeAfterMining() {
            Wei initialFee = tester.getBlobBaseFee();
            assertNotNull(initialFee);

            // Mine several blocks
            tester.mine(10);

            // Fee should still be valid (may or may not change)
            Wei newFee = tester.getBlobBaseFee();
            assertNotNull(newFee);
            assertTrue(newFee.value().compareTo(BigInteger.ZERO) > 0,
                    "Blob base fee should remain positive after mining");
        }
    }

    // ==================== sendBlobTransaction() Integration Tests ====================

    @Nested
    @DisplayName("sendBlobTransaction integration tests")
    class SendBlobTransactionTests {

        @Test
        @DisplayName("sendBlobTransaction submits transaction and returns hash")
        void sendBlobTransactionReturnsHash() {
            byte[] testData = "Hello, EIP-4844 blobs!".getBytes();
            BlobSidecar sidecar = SidecarBuilder.from(testData).build(kzg);

            BlobTransactionRequest request = new BlobTransactionRequest(
                    null, // from (auto-filled from signer)
                    RECIPIENT,
                    null, // value
                    null, // gasLimit (auto-estimated)
                    null, // maxPriorityFeePerGas (auto-filled)
                    null, // maxFeePerGas (auto-filled)
                    null, // maxFeePerBlobGas (auto-filled)
                    null, // nonce (auto-fetched)
                    null, // data
                    null, // accessList
                    sidecar);

            Hash txHash = tester.sendBlobTransaction(request);

            assertNotNull(txHash);
            assertTrue(txHash.value().startsWith("0x"));
            assertEquals(66, txHash.value().length(), "Transaction hash should be 32 bytes (66 hex chars with 0x)");
        }

        @Test
        @DisplayName("sendBlobTransactionAndWait confirms transaction")
        void sendBlobTransactionAndWaitConfirmsTransaction() {
            byte[] testData = "Test blob data for confirmation".getBytes();
            BlobSidecar sidecar = SidecarBuilder.from(testData).build(kzg);

            BlobTransactionRequest request = new BlobTransactionRequest(
                    null, RECIPIENT, null, null, null, null, null, null, null, null, sidecar);

            TransactionReceipt receipt = tester.asSigner().sendBlobTransactionAndWait(request);

            assertNotNull(receipt);
            assertTrue(receipt.status(), "Blob transaction should succeed");
            assertNotNull(receipt.transactionHash());
            assertNotNull(receipt.blockNumber());
            assertTrue(receipt.blockNumber() > 0, "Block number should be positive");
        }

        @Test
        @DisplayName("sendBlobTransaction with value transfers ETH")
        void sendBlobTransactionWithValueTransfersEth() {
            BigInteger recipientBalanceBefore = tester.getBalance(RECIPIENT);
            Wei transferAmount = Wei.fromEther(new BigDecimal("0.01"));

            byte[] testData = "Blob with ETH transfer".getBytes();
            BlobSidecar sidecar = SidecarBuilder.from(testData).build(kzg);

            BlobTransactionRequest request = new BlobTransactionRequest(
                    null, RECIPIENT, transferAmount, null, null, null, null, null, null, null, sidecar);

            TransactionReceipt receipt = tester.asSigner().sendBlobTransactionAndWait(request);

            assertTrue(receipt.status());

            BigInteger recipientBalanceAfter = tester.getBalance(RECIPIENT);
            assertEquals(recipientBalanceBefore.add(transferAmount.value()), recipientBalanceAfter,
                    "Recipient should receive the transferred ETH");
        }

        @Test
        @DisplayName("sendBlobTransaction with multiple blobs")
        void sendBlobTransactionWithMultipleBlobs() {
            // Create enough data to span multiple blobs (each blob holds ~127KB of user data)
            byte[] largeData = new byte[150_000]; // ~150KB will require 2 blobs
            for (int i = 0; i < largeData.length; i++) {
                largeData[i] = (byte) (i % 256);
            }
            BlobSidecar sidecar = SidecarBuilder.from(largeData).build(kzg);

            assertTrue(sidecar.size() >= 2, "Should have at least 2 blobs for this data size");

            BlobTransactionRequest request = new BlobTransactionRequest(
                    null, RECIPIENT, null, null, null, null, null, null, null, null, sidecar);

            TransactionReceipt receipt = tester.asSigner().sendBlobTransactionAndWait(request);

            assertTrue(receipt.status(), "Multi-blob transaction should succeed");
        }

        @Test
        @DisplayName("sendBlobTransaction consecutive transactions increment nonce")
        void sendBlobTransactionIncrementNonce() {
            byte[] testData1 = "First blob transaction".getBytes();
            byte[] testData2 = "Second blob transaction".getBytes();
            BlobSidecar sidecar1 = SidecarBuilder.from(testData1).build(kzg);
            BlobSidecar sidecar2 = SidecarBuilder.from(testData2).build(kzg);

            BlobTransactionRequest request1 = new BlobTransactionRequest(
                    null, RECIPIENT, null, null, null, null, null, null, null, null, sidecar1);

            BlobTransactionRequest request2 = new BlobTransactionRequest(
                    null, RECIPIENT, null, null, null, null, null, null, null, null, sidecar2);

            TransactionReceipt receipt1 = tester.asSigner().sendBlobTransactionAndWait(request1);
            TransactionReceipt receipt2 = tester.asSigner().sendBlobTransactionAndWait(request2);

            assertTrue(receipt1.status());
            assertTrue(receipt2.status());
            // Both should be in blocks (potentially different blocks)
            assertNotNull(receipt1.blockNumber());
            assertNotNull(receipt2.blockNumber());
        }
    }

    // ==================== Auto-fill Fee Integration Tests ====================

    @Nested
    @DisplayName("Auto-fill fee integration tests")
    class AutoFillFeeTests {

        @Test
        @DisplayName("auto-fills maxFeePerBlobGas when not provided")
        void autoFillsMaxFeePerBlobGas() {
            byte[] testData = "Test auto-fill blob gas".getBytes();
            BlobSidecar sidecar = SidecarBuilder.from(testData).build(kzg);

            // Don't provide maxFeePerBlobGas - should be auto-filled
            BlobTransactionRequest request = new BlobTransactionRequest(
                    null, RECIPIENT, null, null, null, null, null, null, null, null, sidecar);

            // If auto-fill didn't work, this would fail
            TransactionReceipt receipt = tester.asSigner().sendBlobTransactionAndWait(request);

            assertTrue(receipt.status(), "Transaction with auto-filled blob gas fee should succeed");
        }

        @Test
        @DisplayName("auto-fills maxFeePerGas and maxPriorityFeePerGas when not provided")
        void autoFillsEip1559Fees() {
            byte[] testData = "Test auto-fill EIP-1559 fees".getBytes();
            BlobSidecar sidecar = SidecarBuilder.from(testData).build(kzg);

            // Don't provide any fees - all should be auto-filled
            BlobTransactionRequest request = new BlobTransactionRequest(
                    null, RECIPIENT, null, null, null, null, null, null, null, null, sidecar);

            TransactionReceipt receipt = tester.asSigner().sendBlobTransactionAndWait(request);

            assertTrue(receipt.status(), "Transaction with auto-filled EIP-1559 fees should succeed");
        }

        @Test
        @DisplayName("auto-fills gasLimit when not provided")
        void autoFillsGasLimit() {
            byte[] testData = "Test auto-fill gas limit".getBytes();
            BlobSidecar sidecar = SidecarBuilder.from(testData).build(kzg);

            // Don't provide gasLimit - should be auto-estimated
            BlobTransactionRequest request = new BlobTransactionRequest(
                    null, RECIPIENT, null, null, null, null, null, null, null, null, sidecar);

            TransactionReceipt receipt = tester.asSigner().sendBlobTransactionAndWait(request);

            assertTrue(receipt.status(), "Transaction with auto-estimated gas limit should succeed");
        }

        @Test
        @DisplayName("auto-fills nonce when not provided")
        void autoFillsNonce() {
            byte[] testData = "Test auto-fill nonce".getBytes();
            BlobSidecar sidecar = SidecarBuilder.from(testData).build(kzg);

            // Don't provide nonce - should be auto-fetched
            BlobTransactionRequest request = new BlobTransactionRequest(
                    null, RECIPIENT, null, null, null, null, null, null, null, null, sidecar);

            TransactionReceipt receipt = tester.asSigner().sendBlobTransactionAndWait(request);

            assertTrue(receipt.status(), "Transaction with auto-fetched nonce should succeed");
        }

        @Test
        @DisplayName("uses provided maxFeePerBlobGas when specified")
        void usesProvidedMaxFeePerBlobGas() {
            byte[] testData = "Test provided blob gas fee".getBytes();
            BlobSidecar sidecar = SidecarBuilder.from(testData).build(kzg);

            // Get current blob base fee and set maxFeePerBlobGas to 10x that
            Wei blobBaseFee = tester.getBlobBaseFee();
            Wei highBlobFee = Wei.of(blobBaseFee.value().multiply(BigInteger.TEN));

            BlobTransactionRequest request = new BlobTransactionRequest(
                    null, // from
                    RECIPIENT,
                    null, // value
                    null, // gasLimit
                    null, // maxPriorityFeePerGas
                    null, // maxFeePerGas
                    highBlobFee, // maxFeePerBlobGas - explicitly set
                    null, // nonce
                    null, // data
                    null, // accessList
                    sidecar);

            TransactionReceipt receipt = tester.asSigner().sendBlobTransactionAndWait(request);

            assertTrue(receipt.status(), "Transaction with explicit blob gas fee should succeed");
        }

        @Test
        @DisplayName("uses provided nonce when specified")
        void usesProvidedNonce() {
            byte[] testData = "Test provided nonce".getBytes();
            BlobSidecar sidecar = SidecarBuilder.from(testData).build(kzg);

            // Set a known nonce using tester, then use that exact nonce
            Address senderAddress = tester.signer().address();
            tester.setNonce(senderAddress, 100);

            BlobTransactionRequest request = new BlobTransactionRequest(
                    null, // from
                    RECIPIENT,
                    null, // value
                    null, // gasLimit
                    null, // maxPriorityFeePerGas
                    null, // maxFeePerGas
                    null, // maxFeePerBlobGas
                    100L, // nonce - explicitly set to match what we set above
                    null, // data
                    null, // accessList
                    sidecar);

            TransactionReceipt receipt = tester.asSigner().sendBlobTransactionAndWait(request);

            assertTrue(receipt.status(), "Transaction with explicit nonce should succeed");
        }

        @Test
        @DisplayName("uses provided gasLimit when specified")
        void usesProvidedGasLimit() {
            byte[] testData = "Test provided gas limit".getBytes();
            BlobSidecar sidecar = SidecarBuilder.from(testData).build(kzg);

            // Provide a reasonable gas limit
            long explicitGasLimit = 50_000L;

            BlobTransactionRequest request = new BlobTransactionRequest(
                    null, // from
                    RECIPIENT,
                    null, // value
                    explicitGasLimit, // gasLimit - explicitly set
                    null, // maxPriorityFeePerGas
                    null, // maxFeePerGas
                    null, // maxFeePerBlobGas
                    null, // nonce
                    null, // data
                    null, // accessList
                    sidecar);

            TransactionReceipt receipt = tester.asSigner().sendBlobTransactionAndWait(request);

            assertTrue(receipt.status(), "Transaction with explicit gas limit should succeed");
        }

        @Test
        @DisplayName("all fields auto-filled produces valid transaction")
        void allFieldsAutoFilledProducesValidTransaction() {
            byte[] testData = "Complete auto-fill test".getBytes();
            BlobSidecar sidecar = SidecarBuilder.from(testData).build(kzg);

            // Only provide required fields: to and sidecar
            BlobTransactionRequest request = new BlobTransactionRequest(
                    null, // from (auto-filled from signer)
                    RECIPIENT, // to (required)
                    null, // value
                    null, // gasLimit (auto-estimated)
                    null, // maxPriorityFeePerGas (auto-filled)
                    null, // maxFeePerGas (auto-filled)
                    null, // maxFeePerBlobGas (auto-filled)
                    null, // nonce (auto-fetched)
                    null, // data
                    null, // accessList
                    sidecar); // sidecar (required)

            TransactionReceipt receipt = tester.asSigner().sendBlobTransactionAndWait(request);

            assertNotNull(receipt);
            assertTrue(receipt.status(), "Fully auto-filled blob transaction should succeed");
            assertNotNull(receipt.transactionHash());
            assertNotNull(receipt.blockNumber());
            assertNotNull(receipt.blockHash());
        }
    }
}
