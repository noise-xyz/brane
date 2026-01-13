// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import sh.brane.core.builder.TxBuilder;
import sh.brane.core.chain.ChainProfile;
import sh.brane.core.crypto.Signature;
import sh.brane.core.crypto.Signer;
import sh.brane.core.error.InvalidSenderException;
import sh.brane.core.error.RevertException;
import sh.brane.core.error.RpcException;
import sh.brane.core.model.BlockHeader;
import sh.brane.core.model.TransactionReceipt;
import sh.brane.core.model.TransactionRequest;
import sh.brane.core.tx.UnsignedTransaction;
import sh.brane.core.types.Address;
import sh.brane.core.types.Hash;
import sh.brane.core.types.HexData;
import sh.brane.core.types.Wei;

/**
 * Unit tests for {@link DefaultSigner} with mock BraneProvider.
 */
@ExtendWith(MockitoExtension.class)
class DefaultSignerTest {

    private static final Address SENDER = new Address("0x" + "1".repeat(40));
    private static final Address RECIPIENT = new Address("0x" + "2".repeat(40));
    private static final Hash TX_HASH = new Hash("0x" + "a".repeat(64));
    private static final Hash BLOCK_HASH = new Hash("0x" + "b".repeat(64));
    private static final Hash PARENT_HASH = new Hash("0x" + "c".repeat(64));

    @Mock
    private BraneProvider provider;

    private FakeSigner fakeSigner;
    private DefaultSigner signer;

    @BeforeEach
    void setUp() {
        fakeSigner = new FakeSigner(SENDER);
        // Create with EIP-1559 supporting chain profile
        ChainProfile chain = ChainProfile.of(1L, null, true, Wei.of(1_000_000_000L));
        signer = new DefaultSigner(provider, fakeSigner, chain, 0, RpcRetryConfig.defaults());
    }

    // ==================== sendTransaction() Tests ====================

    @Test
    void sendTransactionReturnsHash() {
        // Given: provider returns successful responses for all required calls
        stubChainId("0x1");
        stubLatestBlock(20_000_000_000L); // baseFee = 20 gwei
        stubEstimateGas("0x5208"); // 21000
        stubNonce("0x5");
        stubSendRawTransaction(TX_HASH.value());

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.of(1_000_000_000_000_000_000L)) // 1 ETH
                .build();

        // When
        Hash hash = signer.sendTransaction(request);

        // Then
        assertEquals(TX_HASH.value(), hash.value());
        verify(provider).send(eq("eth_sendRawTransaction"), any());
    }

    @Test
    void sendTransactionUsesSignerAddress() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubSendRawTransaction(TX_HASH.value());

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.ZERO)
                .build();

        // When
        signer.sendTransaction(request);

        // Then: verify nonce was fetched for signer address
        verify(provider).send(eq("eth_getTransactionCount"), eq(List.of(SENDER.value(), "pending")));
    }

    @Test
    void sendTransactionBuildsEip1559Transaction() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x3");
        stubSendRawTransaction(TX_HASH.value());

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.of(1_000_000_000L))
                .build();

        // When
        signer.sendTransaction(request);

        // Then
        assertEquals("EIP1559", fakeSigner.lastTransactionType());
        assertEquals(3L, fakeSigner.lastNonce());
    }

    @Test
    void sendTransactionUsesProvidedNonce() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        // Don't stub nonce - it shouldn't be called
        stubSendRawTransaction(TX_HASH.value());

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.ZERO)
                .nonce(42L)
                .build();

        // When
        signer.sendTransaction(request);

        // Then: should use provided nonce
        assertEquals(42L, fakeSigner.lastNonce());
    }

    @Test
    void sendTransactionUsesProvidedGasLimit() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        // Don't stub estimateGas - SmartGasStrategy fills it
        stubNonce("0x0");
        stubSendRawTransaction(TX_HASH.value());

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.ZERO)
                .gasLimit(100_000L)
                .build();

        // When
        signer.sendTransaction(request);

        // Then: should use provided gas limit (with buffer applied by SmartGasStrategy)
        assertTrue(fakeSigner.lastGasLimit() >= 100_000L);
    }

    @Test
    void sendTransactionUsesProvidedFees() {
        // Given
        stubChainId("0x1");
        // No block needed - fees are provided
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubSendRawTransaction(TX_HASH.value());

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.ZERO)
                .maxFeePerGas(Wei.of(50_000_000_000L))
                .maxPriorityFeePerGas(Wei.of(2_000_000_000L))
                .build();

        // When
        signer.sendTransaction(request);

        // Then
        assertEquals(BigInteger.valueOf(50_000_000_000L), fakeSigner.lastMaxFeePerGas());
        assertEquals(BigInteger.valueOf(2_000_000_000L), fakeSigner.lastMaxPriorityFeePerGas());
    }

    @Test
    void sendTransactionFallsBackToLegacyWhenNoBaseFee() {
        // Given: block has no base fee (pre-London)
        stubChainId("0x1");
        stubLatestBlockWithoutBaseFee();
        stubEstimateGas("0x5208");
        stubGasPrice("0x3b9aca00"); // 1 gwei
        stubNonce("0x0");
        stubSendRawTransaction(TX_HASH.value());

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.ZERO)
                .build();

        // When
        signer.sendTransaction(request);

        // Then: should fall back to legacy
        assertEquals("LEGACY", fakeSigner.lastTransactionType());
    }

    @Test
    void sendLegacyTransaction() {
        // Given
        stubChainId("0x1");
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubSendRawTransaction(TX_HASH.value());

        TransactionRequest request = TxBuilder.legacy()
                .to(RECIPIENT)
                .value(Wei.of(1_000_000_000L))
                .gasPrice(Wei.of(20_000_000_000L))
                .build();

        // When
        signer.sendTransaction(request);

        // Then
        assertEquals("LEGACY", fakeSigner.lastTransactionType());
        assertEquals(BigInteger.valueOf(20_000_000_000L), fakeSigner.lastGasPrice());
    }

    @Test
    void sendTransactionThrowsOnRpcError() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubSendRawTransactionError(-32000, "insufficient funds");

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.of(1_000_000_000_000_000_000L))
                .build();

        // When/Then
        RpcException ex = assertThrows(RpcException.class, () -> signer.sendTransaction(request));
        assertTrue(ex.getMessage().contains("insufficient funds"));
    }

    @Test
    void sendTransactionThrowsInvalidSenderException() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubSendRawTransactionError(-32000, "invalid sender");

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.ZERO)
                .build();

        // When/Then
        assertThrows(InvalidSenderException.class, () -> signer.sendTransaction(request));
    }

    @Test
    void sendTransactionThrowsAfterClose() {
        // Given
        signer.close();

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.ZERO)
                .build();

        // When/Then
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> signer.sendTransaction(request));
        assertTrue(ex.getMessage().contains("closed"));
    }

    @Test
    void sendTransactionCachesChainId() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubSendRawTransaction(TX_HASH.value());

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.ZERO)
                .build();

        // When: send two transactions
        signer.sendTransaction(request);

        stubNonce("0x1");
        stubSendRawTransaction(TX_HASH.value());
        signer.sendTransaction(request);

        // Then: chainId should only be fetched once
        verify(provider, times(1)).send(eq("eth_chainId"), any());
    }

    // ==================== sendTransactionAndWait() Tests ====================

    @Test
    void sendTransactionAndWaitReturnsReceipt() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubSendRawTransaction(TX_HASH.value());
        stubReceiptSuccess(TX_HASH.value());

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.ZERO)
                .build();

        // When
        TransactionReceipt receipt = signer.sendTransactionAndWait(request, 5000, 10);

        // Then
        assertEquals(TX_HASH.value(), receipt.transactionHash().value());
        assertTrue(receipt.status());
    }

    @Test
    void sendTransactionAndWaitPollsUntilReceipt() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubSendRawTransaction(TX_HASH.value());
        // First poll returns null, second returns receipt
        stubReceiptNullThenSuccess(TX_HASH.value());

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.ZERO)
                .build();

        // When
        TransactionReceipt receipt = signer.sendTransactionAndWait(request, 5000, 10);

        // Then
        assertEquals(TX_HASH.value(), receipt.transactionHash().value());
        verify(provider, times(2)).send(eq("eth_getTransactionReceipt"), any());
    }

    @Test
    void sendTransactionAndWaitTimesOut() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubSendRawTransaction(TX_HASH.value());
        // Always return null - never mined
        stubReceiptAlwaysNull();

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.ZERO)
                .build();

        // When/Then
        RpcException ex = assertThrows(RpcException.class,
                () -> signer.sendTransactionAndWait(request, 100, 10));
        assertTrue(ex.getMessage().contains("Timed out"));
    }

    @Test
    void sendTransactionAndWaitThrowsOnRevert() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubSendRawTransaction(TX_HASH.value());
        stubReceiptReverted(TX_HASH.value());
        // When replaying via eth_call, return revert data
        stubCallRevert("0x08c379a00000000000000000000000000000000000000000000000000000000000000020"
                + "000000000000000000000000000000000000000000000000000000000000000e"
                + "5472616e73666572206661696c6564000000000000000000000000000000000000");

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.ZERO)
                .data(new HexData("0xdeadbeef"))
                .build();

        // When/Then
        assertThrows(RevertException.class,
                () -> signer.sendTransactionAndWait(request, 5000, 10));
    }

    @Test
    void sendTransactionAndWaitMultiplePolls() {
        // Given: receipt not available for first 3 polls
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubSendRawTransaction(TX_HASH.value());
        stubReceiptAfterNPolls(TX_HASH.value(), 3);

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.ZERO)
                .build();

        // When
        TransactionReceipt receipt = signer.sendTransactionAndWait(request, 10000, 5);

        // Then: should succeed after 4 polls (3 nulls + 1 receipt)
        assertEquals(TX_HASH.value(), receipt.transactionHash().value());
        verify(provider, times(4)).send(eq("eth_getTransactionReceipt"), any());
    }

    @Test
    void sendTransactionAndWaitWithDefaultTimeout() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubSendRawTransaction(TX_HASH.value());
        stubReceiptSuccess(TX_HASH.value());

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.ZERO)
                .build();

        // When: use two-argument version with custom timeout and poll interval
        TransactionReceipt receipt = signer.sendTransactionAndWait(request, 30000, 100);

        // Then
        assertEquals(TX_HASH.value(), receipt.transactionHash().value());
        assertTrue(receipt.status());
    }

    @Test
    void sendTransactionAndWaitRevertWithUnknownReason() {
        // Given: receipt shows reverted but eth_call doesn't return revert data
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubSendRawTransaction(TX_HASH.value());
        stubReceiptReverted(TX_HASH.value());
        // eth_call returns error without revert data
        stubCallError(-32000, "execution reverted");

        TransactionRequest request = TxBuilder.eip1559()
                .to(RECIPIENT)
                .value(Wei.ZERO)
                .build();

        // When/Then
        RevertException ex = assertThrows(RevertException.class,
                () -> signer.sendTransactionAndWait(request, 5000, 10));
        assertTrue(ex.getMessage().contains("reverted"));
    }

    // ==================== sendBlobTransaction() Tests ====================

    @Test
    void sendBlobTransactionReturnsHash() {
        // Given: provider returns successful responses for all required calls
        stubChainId("0x1");
        stubLatestBlock(20_000_000_000L); // baseFee = 20 gwei
        stubEstimateGas("0x5208"); // 21000
        stubNonce("0x5");
        stubBlobBaseFee("0x1"); // 1 wei blob base fee
        stubSendRawTransaction(TX_HASH.value());

        sh.brane.core.model.BlobTransactionRequest request = new sh.brane.core.model.BlobTransactionRequest(
                null, // from (defaults to signer.address())
                RECIPIENT,
                Wei.of(1_000_000_000_000_000_000L), // 1 ETH
                null, // gasLimit (auto)
                null, // maxPriorityFeePerGas (auto)
                null, // maxFeePerGas (auto)
                null, // maxFeePerBlobGas (auto)
                null, // nonce (auto)
                null, // data
                null, // accessList
                createTestSidecar());

        // When
        Hash hash = signer.sendBlobTransaction(request);

        // Then
        assertEquals(TX_HASH.value(), hash.value());
        verify(provider).send(eq("eth_sendRawTransaction"), any());
    }

    @Test
    void sendBlobTransactionUsesSignerAddress() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubBlobBaseFee("0x1");
        stubSendRawTransaction(TX_HASH.value());

        sh.brane.core.model.BlobTransactionRequest request = new sh.brane.core.model.BlobTransactionRequest(
                null, // from defaults to signer.address()
                RECIPIENT,
                Wei.ZERO,
                null, null, null, null, null, null, null,
                createTestSidecar());

        // When
        signer.sendBlobTransaction(request);

        // Then: verify nonce was fetched for signer address
        verify(provider).send(eq("eth_getTransactionCount"), eq(List.of(SENDER.value(), "pending")));
    }

    @Test
    void sendBlobTransactionBuildsEip4844Transaction() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x3");
        stubBlobBaseFee("0x1");
        stubSendRawTransaction(TX_HASH.value());

        sh.brane.core.model.BlobTransactionRequest request = new sh.brane.core.model.BlobTransactionRequest(
                null,
                RECIPIENT,
                Wei.of(1_000_000_000L),
                null, null, null, null, null, null, null,
                createTestSidecar());

        // When
        signer.sendBlobTransaction(request);

        // Then
        assertEquals("EIP4844", fakeSigner.lastTransactionType());
        assertEquals(3L, fakeSigner.lastNonce());
    }

    @Test
    void sendBlobTransactionUsesProvidedNonce() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        // Don't stub nonce - it shouldn't be called
        stubBlobBaseFee("0x1");
        stubSendRawTransaction(TX_HASH.value());

        sh.brane.core.model.BlobTransactionRequest request = new sh.brane.core.model.BlobTransactionRequest(
                null,
                RECIPIENT,
                Wei.ZERO,
                null, // gasLimit
                null, // maxPriorityFeePerGas
                null, // maxFeePerGas
                null, // maxFeePerBlobGas
                42L, // nonce
                null, null,
                createTestSidecar());

        // When
        signer.sendBlobTransaction(request);

        // Then: should use provided nonce
        assertEquals(42L, fakeSigner.lastNonce());
    }

    @Test
    void sendBlobTransactionUsesProvidedGasLimit() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        // Don't stub estimateGas - it shouldn't be called when gasLimit is provided
        stubNonce("0x0");
        stubBlobBaseFee("0x1");
        stubSendRawTransaction(TX_HASH.value());

        sh.brane.core.model.BlobTransactionRequest request = new sh.brane.core.model.BlobTransactionRequest(
                null,
                RECIPIENT,
                Wei.ZERO,
                100_000L, // gasLimit
                null, null, null, null, null, null,
                createTestSidecar());

        // When
        signer.sendBlobTransaction(request);

        // Then: should use provided gas limit
        assertEquals(100_000L, fakeSigner.lastGasLimit());
    }

    @Test
    void sendBlobTransactionUsesProvidedFees() {
        // Given
        stubChainId("0x1");
        // No block needed - fees are provided
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        // No blobBaseFee needed - maxFeePerBlobGas is provided
        stubSendRawTransaction(TX_HASH.value());

        sh.brane.core.model.BlobTransactionRequest request = new sh.brane.core.model.BlobTransactionRequest(
                null,
                RECIPIENT,
                Wei.ZERO,
                null, // gasLimit
                Wei.of(2_000_000_000L), // maxPriorityFeePerGas
                Wei.of(50_000_000_000L), // maxFeePerGas
                Wei.of(100_000_000_000L), // maxFeePerBlobGas
                null, null, null,
                createTestSidecar());

        // When
        signer.sendBlobTransaction(request);

        // Then
        assertEquals(BigInteger.valueOf(50_000_000_000L), fakeSigner.lastMaxFeePerGas());
        assertEquals(BigInteger.valueOf(2_000_000_000L), fakeSigner.lastMaxPriorityFeePerGas());
        assertEquals(BigInteger.valueOf(100_000_000_000L), fakeSigner.lastMaxFeePerBlobGas());
    }

    @Test
    void sendBlobTransactionDefaultsMaxFeePerBlobGasTo2xBlobBaseFee() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubBlobBaseFee("0x3b9aca00"); // 1 gwei blob base fee
        stubSendRawTransaction(TX_HASH.value());

        sh.brane.core.model.BlobTransactionRequest request = new sh.brane.core.model.BlobTransactionRequest(
                null,
                RECIPIENT,
                Wei.ZERO,
                null, null, null, null, null, null, null,
                createTestSidecar());

        // When
        signer.sendBlobTransaction(request);

        // Then: maxFeePerBlobGas should be 2x blob base fee = 2 gwei
        assertEquals(BigInteger.valueOf(2_000_000_000L), fakeSigner.lastMaxFeePerBlobGas());
    }

    @Test
    void sendBlobTransactionThrowsOnRpcError() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubBlobBaseFee("0x1");
        stubSendRawTransactionError(-32000, "insufficient funds");

        sh.brane.core.model.BlobTransactionRequest request = new sh.brane.core.model.BlobTransactionRequest(
                null,
                RECIPIENT,
                Wei.of(1_000_000_000_000_000_000L),
                null, null, null, null, null, null, null,
                createTestSidecar());

        // When/Then
        RpcException ex = assertThrows(RpcException.class, () -> signer.sendBlobTransaction(request));
        assertTrue(ex.getMessage().contains("insufficient funds"));
    }

    @Test
    void sendBlobTransactionThrowsInvalidSenderException() {
        // Given
        stubChainId("0x1");
        stubLatestBlock(10_000_000_000L);
        stubEstimateGas("0x5208");
        stubNonce("0x0");
        stubBlobBaseFee("0x1");
        stubSendRawTransactionError(-32000, "invalid sender");

        sh.brane.core.model.BlobTransactionRequest request = new sh.brane.core.model.BlobTransactionRequest(
                null,
                RECIPIENT,
                Wei.ZERO,
                null, null, null, null, null, null, null,
                createTestSidecar());

        // When/Then
        assertThrows(InvalidSenderException.class, () -> signer.sendBlobTransaction(request));
    }

    // ==================== signer() Tests ====================

    @Test
    void signerReturnsConfiguredSigner() {
        // When
        Signer s = signer.signer();

        // Then
        assertSame(fakeSigner, s);
        assertEquals(SENDER, s.address());
    }

    // ==================== Delegation Tests ====================

    @Test
    void chainIdDelegatesToReader() {
        // Given
        stubChainId("0x89"); // Polygon

        // When
        BigInteger chainId = signer.chainId();

        // Then
        assertEquals(BigInteger.valueOf(137), chainId);
    }

    @Test
    void getBalanceDelegatesToReader() {
        // Given
        JsonRpcResponse balanceResponse = new JsonRpcResponse("2.0", "0xde0b6b3a7640000", null, "1");
        when(provider.send(eq("eth_getBalance"), any())).thenReturn(balanceResponse);

        // When
        BigInteger balance = signer.getBalance(SENDER);

        // Then
        assertEquals(new BigInteger("de0b6b3a7640000", 16), balance);
    }

    @Test
    void getLatestBlockDelegatesToReader() {
        // Given
        stubLatestBlock(10_000_000_000L);

        // When
        BlockHeader block = signer.getLatestBlock();

        // Then
        assertNotNull(block);
    }

    @Test
    void closeDelegatesToReader() {
        // When
        signer.close();

        // Then
        verify(provider).close();
    }

    // ==================== Helper Methods ====================

    private void stubChainId(String hexValue) {
        JsonRpcResponse response = new JsonRpcResponse("2.0", hexValue, null, "1");
        when(provider.send(eq("eth_chainId"), any())).thenReturn(response);
    }

    private void stubLatestBlock(long baseFeePerGas) {
        Map<String, Object> blockMap = new LinkedHashMap<>();
        blockMap.put("hash", BLOCK_HASH.value());
        blockMap.put("parentHash", PARENT_HASH.value());
        blockMap.put("number", "0x100");
        blockMap.put("timestamp", "0x64a7b8c0");
        blockMap.put("baseFeePerGas", "0x" + Long.toHexString(baseFeePerGas));
        JsonRpcResponse response = new JsonRpcResponse("2.0", blockMap, null, "1");
        when(provider.send(eq("eth_getBlockByNumber"), eq(List.of("latest", Boolean.FALSE)))).thenReturn(response);
    }

    private void stubLatestBlockWithoutBaseFee() {
        Map<String, Object> blockMap = new LinkedHashMap<>();
        blockMap.put("hash", BLOCK_HASH.value());
        blockMap.put("parentHash", PARENT_HASH.value());
        blockMap.put("number", "0x100");
        blockMap.put("timestamp", "0x64a7b8c0");
        // No baseFeePerGas
        JsonRpcResponse response = new JsonRpcResponse("2.0", blockMap, null, "1");
        when(provider.send(eq("eth_getBlockByNumber"), eq(List.of("latest", Boolean.FALSE)))).thenReturn(response);
    }

    private void stubEstimateGas(String hexValue) {
        JsonRpcResponse response = new JsonRpcResponse("2.0", hexValue, null, "1");
        when(provider.send(eq("eth_estimateGas"), any())).thenReturn(response);
    }

    private void stubGasPrice(String hexValue) {
        JsonRpcResponse response = new JsonRpcResponse("2.0", hexValue, null, "1");
        when(provider.send(eq("eth_gasPrice"), any())).thenReturn(response);
    }

    private void stubBlobBaseFee(String hexValue) {
        JsonRpcResponse response = new JsonRpcResponse("2.0", hexValue, null, "1");
        when(provider.send(eq("eth_blobBaseFee"), any())).thenReturn(response);
    }

    private void stubNonce(String hexValue) {
        JsonRpcResponse response = new JsonRpcResponse("2.0", hexValue, null, "1");
        when(provider.send(eq("eth_getTransactionCount"), any())).thenReturn(response);
    }

    private void stubSendRawTransaction(String txHash) {
        JsonRpcResponse response = new JsonRpcResponse("2.0", txHash, null, "1");
        when(provider.send(eq("eth_sendRawTransaction"), any())).thenReturn(response);
    }

    private void stubSendRawTransactionError(int code, String message) {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, new JsonRpcError(code, message, null), "1");
        when(provider.send(eq("eth_sendRawTransaction"), any())).thenReturn(response);
    }

    private void stubReceiptSuccess(String txHash) {
        Map<String, Object> receiptMap = new LinkedHashMap<>();
        receiptMap.put("transactionHash", txHash);
        receiptMap.put("blockNumber", "0x1");
        receiptMap.put("blockHash", BLOCK_HASH.value());
        receiptMap.put("from", SENDER.value());
        receiptMap.put("to", RECIPIENT.value());
        receiptMap.put("status", "0x1");
        receiptMap.put("cumulativeGasUsed", "0x5208");
        receiptMap.put("logs", List.of());
        JsonRpcResponse response = new JsonRpcResponse("2.0", receiptMap, null, "1");
        when(provider.send(eq("eth_getTransactionReceipt"), any())).thenReturn(response);
    }

    private void stubReceiptNullThenSuccess(String txHash) {
        JsonRpcResponse nullResponse = new JsonRpcResponse("2.0", null, null, "1");
        Map<String, Object> receiptMap = new LinkedHashMap<>();
        receiptMap.put("transactionHash", txHash);
        receiptMap.put("blockNumber", "0x1");
        receiptMap.put("blockHash", BLOCK_HASH.value());
        receiptMap.put("from", SENDER.value());
        receiptMap.put("to", RECIPIENT.value());
        receiptMap.put("status", "0x1");
        receiptMap.put("cumulativeGasUsed", "0x5208");
        receiptMap.put("logs", List.of());
        JsonRpcResponse successResponse = new JsonRpcResponse("2.0", receiptMap, null, "1");
        when(provider.send(eq("eth_getTransactionReceipt"), any()))
                .thenReturn(nullResponse)
                .thenReturn(successResponse);
    }

    private void stubReceiptAlwaysNull() {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null, null, "1");
        when(provider.send(eq("eth_getTransactionReceipt"), any())).thenReturn(response);
    }

    private void stubReceiptReverted(String txHash) {
        Map<String, Object> receiptMap = new LinkedHashMap<>();
        receiptMap.put("transactionHash", txHash);
        receiptMap.put("blockNumber", "0x1");
        receiptMap.put("blockHash", BLOCK_HASH.value());
        receiptMap.put("from", SENDER.value());
        receiptMap.put("to", RECIPIENT.value());
        receiptMap.put("status", "0x0"); // Reverted
        receiptMap.put("cumulativeGasUsed", "0x5208");
        receiptMap.put("logs", List.of());
        JsonRpcResponse response = new JsonRpcResponse("2.0", receiptMap, null, "1");
        when(provider.send(eq("eth_getTransactionReceipt"), any())).thenReturn(response);
    }

    private void stubCallRevert(String revertData) {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null,
                new JsonRpcError(-32000, "execution reverted", revertData), "1");
        when(provider.send(eq("eth_call"), any())).thenReturn(response);
    }

    private void stubCallError(int code, String message) {
        JsonRpcResponse response = new JsonRpcResponse("2.0", null,
                new JsonRpcError(code, message, null), "1");
        when(provider.send(eq("eth_call"), any())).thenReturn(response);
    }

    private void stubReceiptAfterNPolls(String txHash, int nullPolls) {
        JsonRpcResponse nullResponse = new JsonRpcResponse("2.0", null, null, "1");
        Map<String, Object> receiptMap = new LinkedHashMap<>();
        receiptMap.put("transactionHash", txHash);
        receiptMap.put("blockNumber", "0x1");
        receiptMap.put("blockHash", BLOCK_HASH.value());
        receiptMap.put("from", SENDER.value());
        receiptMap.put("to", RECIPIENT.value());
        receiptMap.put("status", "0x1");
        receiptMap.put("cumulativeGasUsed", "0x5208");
        receiptMap.put("logs", List.of());
        JsonRpcResponse successResponse = new JsonRpcResponse("2.0", receiptMap, null, "1");

        // Build stubbing with the right number of null returns followed by success
        var stubbing = when(provider.send(eq("eth_getTransactionReceipt"), any()))
                .thenReturn(nullResponse);
        for (int i = 1; i < nullPolls; i++) {
            stubbing = stubbing.thenReturn(nullResponse);
        }
        stubbing.thenReturn(successResponse);
    }

    /**
     * Creates a test blob sidecar with dummy data.
     * The commitment produces a versioned hash that starts with 0x01 (KZG version).
     */
    private static sh.brane.core.types.BlobSidecar createTestSidecar() {
        // Create a blob with all zeros (128 KiB)
        byte[] blobData = new byte[sh.brane.core.types.Blob.SIZE];
        sh.brane.core.types.Blob blob = new sh.brane.core.types.Blob(blobData);

        // Create a commitment (48 bytes) - just use zeros for testing
        byte[] commitmentData = new byte[sh.brane.core.types.FixedSizeG1Point.SIZE];
        sh.brane.core.types.KzgCommitment commitment = new sh.brane.core.types.KzgCommitment(commitmentData);

        // Create a proof (48 bytes) - just use zeros for testing
        byte[] proofData = new byte[sh.brane.core.types.FixedSizeG1Point.SIZE];
        sh.brane.core.types.KzgProof proof = new sh.brane.core.types.KzgProof(proofData);

        return new sh.brane.core.types.BlobSidecar(
                List.of(blob),
                List.of(commitment),
                List.of(proof));
    }

    /**
     * Fake signer for testing that captures signed transaction details.
     */
    private static final class FakeSigner implements Signer {
        private final Address address;
        private UnsignedTransaction lastTx;

        FakeSigner(Address address) {
            this.address = address;
        }

        @Override
        public Address address() {
            return address;
        }

        @Override
        public Signature signMessage(byte[] message) {
            return new Signature(new byte[32], new byte[32], 27);
        }

        @Override
        public Signature signTransaction(UnsignedTransaction tx, long chainId) {
            this.lastTx = tx;
            return new Signature(new byte[32], new byte[32], 0);
        }

        long lastNonce() {
            return switch (lastTx) {
                case sh.brane.core.tx.LegacyTransaction tx -> tx.nonce();
                case sh.brane.core.tx.Eip1559Transaction tx -> tx.nonce();
                case sh.brane.core.tx.Eip4844Transaction tx -> tx.nonce();
            };
        }

        long lastGasLimit() {
            return switch (lastTx) {
                case sh.brane.core.tx.LegacyTransaction tx -> tx.gasLimit();
                case sh.brane.core.tx.Eip1559Transaction tx -> tx.gasLimit();
                case sh.brane.core.tx.Eip4844Transaction tx -> tx.gasLimit();
            };
        }

        BigInteger lastMaxPriorityFeePerGas() {
            return switch (lastTx) {
                case sh.brane.core.tx.Eip1559Transaction tx -> tx.maxPriorityFeePerGas().value();
                case sh.brane.core.tx.Eip4844Transaction tx -> tx.maxPriorityFeePerGas().value();
                default -> null;
            };
        }

        BigInteger lastMaxFeePerGas() {
            return switch (lastTx) {
                case sh.brane.core.tx.Eip1559Transaction tx -> tx.maxFeePerGas().value();
                case sh.brane.core.tx.Eip4844Transaction tx -> tx.maxFeePerGas().value();
                default -> null;
            };
        }

        BigInteger lastMaxFeePerBlobGas() {
            if (lastTx instanceof sh.brane.core.tx.Eip4844Transaction tx) {
                return tx.maxFeePerBlobGas().value();
            }
            return null;
        }

        BigInteger lastGasPrice() {
            if (lastTx instanceof sh.brane.core.tx.LegacyTransaction tx) {
                return tx.gasPrice().value();
            }
            return null;
        }

        String lastTransactionType() {
            return switch (lastTx) {
                case sh.brane.core.tx.LegacyTransaction tx -> "LEGACY";
                case sh.brane.core.tx.Eip1559Transaction tx -> "EIP1559";
                case sh.brane.core.tx.Eip4844Transaction tx -> "EIP4844";
            };
        }
    }
}
