// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.model;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.brane.core.tx.Eip4844Transaction;
import io.brane.core.types.Address;
import io.brane.core.types.Blob;
import io.brane.core.types.BlobSidecar;
import io.brane.core.types.FixedSizeG1Point;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.KzgCommitment;
import io.brane.core.types.KzgProof;
import io.brane.core.types.Wei;

class BlobTransactionRequestTest {

    private Address from;
    private Address to;
    private BlobSidecar sidecar;
    private KzgCommitment commitment;

    @BeforeEach
    void setUp() {
        from = new Address("0x" + "a".repeat(40));
        to = new Address("0x" + "b".repeat(40));

        byte[] blobData = new byte[Blob.SIZE];
        Arrays.fill(blobData, (byte) 0xAA);
        Blob blob = new Blob(blobData);

        byte[] commitmentData = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(commitmentData, (byte) 0x11);
        commitment = new KzgCommitment(commitmentData);

        byte[] proofData = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(proofData, (byte) 0x22);
        KzgProof proof = new KzgProof(proofData);

        sidecar = new BlobSidecar(List.of(blob), List.of(commitment), List.of(proof));
    }

    @Test
    void rejectsNullTo() {
        assertThrows(NullPointerException.class, () ->
                new BlobTransactionRequest(
                        from, null, Wei.ZERO, 21000L,
                        Wei.of(1), Wei.of(2), Wei.of(3),
                        0L, HexData.EMPTY, null, sidecar));
    }

    @Test
    void rejectsNullSidecar() {
        assertThrows(NullPointerException.class, () ->
                new BlobTransactionRequest(
                        from, to, Wei.ZERO, 21000L,
                        Wei.of(1), Wei.of(2), Wei.of(3),
                        0L, HexData.EMPTY, null, null));
    }

    @Test
    void rejectsNegativeGasLimit() {
        assertThrows(IllegalArgumentException.class, () ->
                new BlobTransactionRequest(
                        from, to, Wei.ZERO, -1L,
                        Wei.of(1), Wei.of(2), Wei.of(3),
                        0L, HexData.EMPTY, null, sidecar));
    }

    @Test
    void rejectsNegativeNonce() {
        assertThrows(IllegalArgumentException.class, () ->
                new BlobTransactionRequest(
                        from, to, Wei.ZERO, 21000L,
                        Wei.of(1), Wei.of(2), Wei.of(3),
                        -1L, HexData.EMPTY, null, sidecar));
    }

    @Test
    void blobVersionedHashesDelegatesToSidecar() {
        BlobTransactionRequest request = new BlobTransactionRequest(
                from, to, Wei.ZERO, 21000L,
                Wei.of(1), Wei.of(2), Wei.of(3),
                0L, HexData.EMPTY, null, sidecar);

        List<Hash> expectedHashes = sidecar.versionedHashes();
        assertEquals(expectedHashes, request.blobVersionedHashes());
        assertEquals(commitment.toVersionedHash(), request.blobVersionedHashes().get(0));
    }

    @Test
    void optionalMethods() {
        BlobTransactionRequest request = new BlobTransactionRequest(
                from, to, Wei.of(100), 21000L,
                Wei.of(1), Wei.of(2), Wei.of(3),
                5L, HexData.EMPTY, null, sidecar);

        assertTrue(request.valueOpt().isPresent());
        assertEquals(Wei.of(100), request.valueOpt().get());

        assertTrue(request.gasLimitOpt().isPresent());
        assertEquals(21000L, request.gasLimitOpt().get());

        assertTrue(request.maxPriorityFeePerGasOpt().isPresent());
        assertEquals(Wei.of(1), request.maxPriorityFeePerGasOpt().get());

        assertTrue(request.maxFeePerGasOpt().isPresent());
        assertEquals(Wei.of(2), request.maxFeePerGasOpt().get());

        assertTrue(request.maxFeePerBlobGasOpt().isPresent());
        assertEquals(Wei.of(3), request.maxFeePerBlobGasOpt().get());

        assertTrue(request.nonceOpt().isPresent());
        assertEquals(5L, request.nonceOpt().get());
    }

    @Test
    void optionalMethodsReturnEmptyForNull() {
        BlobTransactionRequest request = new BlobTransactionRequest(
                null, to, null, null,
                null, null, null,
                null, null, null, sidecar);

        assertFalse(request.valueOpt().isPresent());
        assertFalse(request.gasLimitOpt().isPresent());
        assertFalse(request.maxPriorityFeePerGasOpt().isPresent());
        assertFalse(request.maxFeePerGasOpt().isPresent());
        assertFalse(request.maxFeePerBlobGasOpt().isPresent());
        assertFalse(request.nonceOpt().isPresent());
    }

    @Test
    void accessListOrEmptyReturnsEmptyForNull() {
        BlobTransactionRequest request = new BlobTransactionRequest(
                from, to, Wei.ZERO, 21000L,
                Wei.of(1), Wei.of(2), Wei.of(3),
                0L, HexData.EMPTY, null, sidecar);

        assertTrue(request.accessListOrEmpty().isEmpty());
    }

    @Test
    void accessListOrEmptyReturnsProvidedList() {
        List<AccessListEntry> entries = List.of(
                new AccessListEntry(to, List.of(new Hash("0x" + "1".repeat(64)))));

        BlobTransactionRequest request = new BlobTransactionRequest(
                from, to, Wei.ZERO, 21000L,
                Wei.of(1), Wei.of(2), Wei.of(3),
                0L, HexData.EMPTY, entries, sidecar);

        assertEquals(entries, request.accessListOrEmpty());
    }

    @Test
    void toUnsignedTransactionThrowsWhenFromIsNull() {
        BlobTransactionRequest request = new BlobTransactionRequest(
                null, to, Wei.ZERO, 21000L,
                Wei.of(1), Wei.of(2), Wei.of(3),
                0L, HexData.EMPTY, null, sidecar);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> request.toUnsignedTransaction(1L));
        assertEquals("from address is required for unsigned transaction", ex.getMessage());
    }

    @Test
    void toUnsignedTransactionThrowsWhenNonceIsNull() {
        BlobTransactionRequest request = new BlobTransactionRequest(
                from, to, Wei.ZERO, 21000L,
                Wei.of(1), Wei.of(2), Wei.of(3),
                null, HexData.EMPTY, null, sidecar);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> request.toUnsignedTransaction(1L));
        assertEquals("nonce must be set", ex.getMessage());
    }

    @Test
    void toUnsignedTransactionThrowsWhenGasLimitIsNull() {
        BlobTransactionRequest request = new BlobTransactionRequest(
                from, to, Wei.ZERO, null,
                Wei.of(1), Wei.of(2), Wei.of(3),
                0L, HexData.EMPTY, null, sidecar);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> request.toUnsignedTransaction(1L));
        assertEquals("gasLimit must be set", ex.getMessage());
    }

    @Test
    void toUnsignedTransactionThrowsWhenMaxPriorityFeePerGasIsNull() {
        BlobTransactionRequest request = new BlobTransactionRequest(
                from, to, Wei.ZERO, 21000L,
                null, Wei.of(2), Wei.of(3),
                0L, HexData.EMPTY, null, sidecar);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> request.toUnsignedTransaction(1L));
        assertEquals("maxPriorityFeePerGas must be set for EIP-4844 transactions", ex.getMessage());
    }

    @Test
    void toUnsignedTransactionThrowsWhenMaxFeePerGasIsNull() {
        BlobTransactionRequest request = new BlobTransactionRequest(
                from, to, Wei.ZERO, 21000L,
                Wei.of(1), null, Wei.of(3),
                0L, HexData.EMPTY, null, sidecar);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> request.toUnsignedTransaction(1L));
        assertEquals("maxFeePerGas must be set for EIP-4844 transactions", ex.getMessage());
    }

    @Test
    void toUnsignedTransactionThrowsWhenMaxFeePerBlobGasIsNull() {
        BlobTransactionRequest request = new BlobTransactionRequest(
                from, to, Wei.ZERO, 21000L,
                Wei.of(1), Wei.of(2), null,
                0L, HexData.EMPTY, null, sidecar);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> request.toUnsignedTransaction(1L));
        assertEquals("maxFeePerBlobGas must be set for EIP-4844 transactions", ex.getMessage());
    }

    @Test
    void toUnsignedTransactionCreatesEip4844Transaction() {
        Wei value = Wei.of(1000);
        Wei maxPriorityFee = Wei.of(1);
        Wei maxFee = Wei.of(2);
        Wei maxBlobFee = Wei.of(3);
        long gasLimit = 21000L;
        long nonce = 5L;
        long chainId = 1L;

        BlobTransactionRequest request = new BlobTransactionRequest(
                from, to, value, gasLimit,
                maxPriorityFee, maxFee, maxBlobFee,
                nonce, HexData.EMPTY, null, sidecar);

        Eip4844Transaction tx = request.toUnsignedTransaction(chainId);

        assertEquals(chainId, tx.chainId());
        assertEquals(nonce, tx.nonce());
        assertEquals(maxPriorityFee, tx.maxPriorityFeePerGas());
        assertEquals(maxFee, tx.maxFeePerGas());
        assertEquals(gasLimit, tx.gasLimit());
        assertEquals(to, tx.to());
        assertEquals(value, tx.value());
        assertEquals(HexData.EMPTY, tx.data());
        assertTrue(tx.accessList().isEmpty());
        assertEquals(maxBlobFee, tx.maxFeePerBlobGas());
        assertEquals(sidecar.versionedHashes(), tx.blobVersionedHashes());
    }

    @Test
    void toUnsignedTransactionDefaultsNullValueToZero() {
        BlobTransactionRequest request = new BlobTransactionRequest(
                from, to, null, 21000L,
                Wei.of(1), Wei.of(2), Wei.of(3),
                0L, HexData.EMPTY, null, sidecar);

        Eip4844Transaction tx = request.toUnsignedTransaction(1L);
        assertEquals(Wei.ZERO, tx.value());
    }

    @Test
    void toUnsignedTransactionDefaultsNullDataToEmpty() {
        BlobTransactionRequest request = new BlobTransactionRequest(
                from, to, Wei.ZERO, 21000L,
                Wei.of(1), Wei.of(2), Wei.of(3),
                0L, null, null, sidecar);

        Eip4844Transaction tx = request.toUnsignedTransaction(1L);
        assertEquals(HexData.EMPTY, tx.data());
    }
}
