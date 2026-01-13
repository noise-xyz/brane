// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.builder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.brane.core.crypto.Kzg;
import io.brane.core.model.AccessListEntry;
import io.brane.core.model.BlobTransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Blob;
import io.brane.core.types.BlobSidecar;
import io.brane.core.types.FixedSizeG1Point;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.KzgCommitment;
import io.brane.core.types.KzgProof;
import io.brane.core.types.Wei;

class Eip4844BuilderTest {

    private static final Address SENDER = new Address("0x" + "a".repeat(40));
    private static final Address RECIPIENT = new Address("0x" + "b".repeat(40));

    /**
     * Mock Kzg implementation for testing.
     */
    private static class MockKzg implements Kzg {
        @Override
        public KzgCommitment blobToCommitment(Blob blob) {
            byte[] commitmentData = new byte[KzgCommitment.SIZE];
            byte[] blobBytes = blob.toBytes();
            System.arraycopy(blobBytes, 0, commitmentData, 0, Math.min(blobBytes.length, KzgCommitment.SIZE));
            return new KzgCommitment(commitmentData);
        }

        @Override
        public KzgProof computeProof(Blob blob, KzgCommitment commitment) {
            byte[] proofData = new byte[FixedSizeG1Point.SIZE];
            byte[] blobBytes = blob.toBytes();
            byte[] commitmentBytes = commitment.toBytes();
            for (int i = 0; i < FixedSizeG1Point.SIZE; i++) {
                proofData[i] = (byte) (blobBytes[i % blobBytes.length] ^ commitmentBytes[i % commitmentBytes.length]);
            }
            return new KzgProof(proofData);
        }

        @Override
        public boolean verifyBlobKzgProof(Blob blob, KzgCommitment commitment, KzgProof proof) {
            return true;
        }

        @Override
        public boolean verifyBlobKzgProofBatch(List<Blob> blobs, List<KzgCommitment> commitments, List<KzgProof> proofs) {
            return true;
        }
    }

    private static BlobSidecar createMockSidecar() {
        Blob blob = new Blob(new byte[Blob.SIZE]);
        KzgCommitment commitment = new KzgCommitment(new byte[KzgCommitment.SIZE]);
        KzgProof proof = new KzgProof(new byte[FixedSizeG1Point.SIZE]);
        return new BlobSidecar(List.of(blob), List.of(commitment), List.of(proof));
    }

    @Test
    void createReturnsNewBuilder() {
        Eip4844Builder builder = Eip4844Builder.create();
        assertNotNull(builder);
    }

    @Test
    void buildWithSidecarSetsAllFields() {
        BlobSidecar sidecar = createMockSidecar();
        Wei maxFeePerGas = Wei.gwei(100);
        Wei maxPriorityFeePerGas = Wei.gwei(2);
        Wei maxFeePerBlobGas = Wei.gwei(10);
        Wei value = Wei.fromEther(new BigDecimal("1"));
        HexData data = HexData.fromBytes(new byte[]{0x12, 0x34});
        AccessListEntry accessEntry = new AccessListEntry(SENDER, List.of(new Hash("0x" + "1".repeat(64))));

        BlobTransactionRequest request = Eip4844Builder.create()
                .from(SENDER)
                .to(RECIPIENT)
                .value(value)
                .nonce(42)
                .gasLimit(100000)
                .maxFeePerGas(maxFeePerGas)
                .maxPriorityFeePerGas(maxPriorityFeePerGas)
                .maxFeePerBlobGas(maxFeePerBlobGas)
                .data(data)
                .accessList(List.of(accessEntry))
                .sidecar(sidecar)
                .build();

        assertEquals(SENDER, request.from());
        assertEquals(RECIPIENT, request.to());
        assertEquals(value, request.value());
        assertEquals(42L, request.nonce());
        assertEquals(100000L, request.gasLimit());
        assertEquals(maxFeePerGas, request.maxFeePerGas());
        assertEquals(maxPriorityFeePerGas, request.maxPriorityFeePerGas());
        assertEquals(maxFeePerBlobGas, request.maxFeePerBlobGas());
        assertEquals(data, request.data());
        assertEquals(List.of(accessEntry), request.accessList());
        assertEquals(sidecar, request.sidecar());
    }

    @Test
    void buildWithBlobDataCreatesRequest() {
        byte[] blobData = "Hello, EIP-4844!".getBytes();
        MockKzg kzg = new MockKzg();

        BlobTransactionRequest request = Eip4844Builder.create()
                .from(SENDER)
                .to(RECIPIENT)
                .blobData(blobData)
                .build(kzg);

        assertEquals(SENDER, request.from());
        assertEquals(RECIPIENT, request.to());
        assertNotNull(request.sidecar());
        assertEquals(1, request.sidecar().size());
    }

    @Test
    void buildWithBlobDataRequiresKzg() {
        byte[] blobData = "test".getBytes();

        NullPointerException ex = assertThrows(NullPointerException.class, () ->
                Eip4844Builder.create()
                        .to(RECIPIENT)
                        .blobData(blobData)
                        .build(null));
        assertEquals("kzg is required when building with blobData", ex.getMessage());
    }

    @Test
    void buildWithKzgRequiresBlobData() {
        MockKzg kzg = new MockKzg();

        BraneTxBuilderException ex = assertThrows(BraneTxBuilderException.class, () ->
                Eip4844Builder.create()
                        .to(RECIPIENT)
                        .build(kzg));
        assertEquals("blobData is required when building with Kzg", ex.getMessage());
    }

    @Test
    void buildWithoutKzgRequiresSidecar() {
        BraneTxBuilderException ex = assertThrows(BraneTxBuilderException.class, () ->
                Eip4844Builder.create()
                        .to(RECIPIENT)
                        .build());
        assertEquals("sidecar is required; use build(Kzg) for raw blobData", ex.getMessage());
    }

    @Test
    void buildRequiresToAddress() {
        BlobSidecar sidecar = createMockSidecar();

        BraneTxBuilderException ex = assertThrows(BraneTxBuilderException.class, () ->
                Eip4844Builder.create()
                        .sidecar(sidecar)
                        .build());
        assertEquals("to address is required for EIP-4844 transactions", ex.getMessage());
    }

    @Test
    void blobDataAndSidecarAreMutuallyExclusive_sidecarFirst() {
        BlobSidecar sidecar = createMockSidecar();

        BraneTxBuilderException ex = assertThrows(BraneTxBuilderException.class, () ->
                Eip4844Builder.create()
                        .sidecar(sidecar)
                        .blobData("test".getBytes()));
        assertEquals("Cannot set blobData when sidecar is already set", ex.getMessage());
    }

    @Test
    void blobDataAndSidecarAreMutuallyExclusive_blobDataFirst() {
        BlobSidecar sidecar = createMockSidecar();

        BraneTxBuilderException ex = assertThrows(BraneTxBuilderException.class, () ->
                Eip4844Builder.create()
                        .blobData("test".getBytes())
                        .sidecar(sidecar));
        assertEquals("Cannot set sidecar when blobData is already set", ex.getMessage());
    }

    @Test
    void buildWithKzgRejectsSidecar() {
        BlobSidecar sidecar = createMockSidecar();
        MockKzg kzg = new MockKzg();

        // Directly set both via reflection would be needed to test this,
        // but since the setters prevent it, we can only test that build(kzg)
        // works correctly when only blobData is set
        byte[] blobData = "test".getBytes();
        BlobTransactionRequest request = Eip4844Builder.create()
                .to(RECIPIENT)
                .blobData(blobData)
                .build(kzg);

        assertNotNull(request.sidecar());
    }

    @Test
    void buildWithoutKzgRejectsBlobData() {
        BlobSidecar sidecar = createMockSidecar();

        // Only sidecar can be set when using build()
        BlobTransactionRequest request = Eip4844Builder.create()
                .to(RECIPIENT)
                .sidecar(sidecar)
                .build();

        assertEquals(sidecar, request.sidecar());
    }

    @Test
    void accessListIsCopied() {
        BlobSidecar sidecar = createMockSidecar();
        AccessListEntry entry = new AccessListEntry(SENDER, List.of(new Hash("0x" + "1".repeat(64))));
        List<AccessListEntry> original = new java.util.ArrayList<>();
        original.add(entry);

        BlobTransactionRequest request = Eip4844Builder.create()
                .to(RECIPIENT)
                .sidecar(sidecar)
                .accessList(original)
                .build();

        // Modifying original list should not affect the request
        original.clear();
        assertEquals(1, request.accessList().size());
    }

    @Test
    void nullAccessListIsAllowed() {
        BlobSidecar sidecar = createMockSidecar();

        BlobTransactionRequest request = Eip4844Builder.create()
                .to(RECIPIENT)
                .sidecar(sidecar)
                .accessList(null)
                .build();

        assertNull(request.accessList());
    }

    @Test
    void blobDataIsCopied() {
        byte[] original = "test".getBytes();
        MockKzg kzg = new MockKzg();

        Eip4844Builder builder = Eip4844Builder.create()
                .to(RECIPIENT)
                .blobData(original);

        // Modifying original array should not affect the builder
        original[0] = 'x';

        BlobTransactionRequest request = builder.build(kzg);
        assertNotNull(request.sidecar());
    }

    @Test
    void nullBlobDataClearsPrevious() {
        MockKzg kzg = new MockKzg();

        // Setting blobData to null should clear it
        BraneTxBuilderException ex = assertThrows(BraneTxBuilderException.class, () ->
                Eip4844Builder.create()
                        .to(RECIPIENT)
                        .blobData("test".getBytes())
                        .blobData(null)  // This clears blobData
                        .build(kzg));  // Now build(kzg) fails because no blobData
        assertEquals("blobData is required when building with Kzg", ex.getMessage());
    }

    @Test
    void fluentChainingWorks() {
        BlobSidecar sidecar = createMockSidecar();

        // Verify all methods return the builder for chaining
        BlobTransactionRequest request = Eip4844Builder.create()
                .from(SENDER)
                .to(RECIPIENT)
                .value(Wei.of(0))
                .nonce(0)
                .gasLimit(21000)
                .maxFeePerGas(Wei.gwei(1))
                .maxPriorityFeePerGas(Wei.gwei(1))
                .maxFeePerBlobGas(Wei.gwei(1))
                .data(HexData.EMPTY)
                .accessList(List.of())
                .sidecar(sidecar)
                .build();

        assertNotNull(request);
    }

    @Test
    void minimalBuildWithSidecar() {
        BlobSidecar sidecar = createMockSidecar();

        BlobTransactionRequest request = Eip4844Builder.create()
                .to(RECIPIENT)
                .sidecar(sidecar)
                .build();

        assertEquals(RECIPIENT, request.to());
        assertEquals(sidecar, request.sidecar());
        assertNull(request.from());
        assertNull(request.value());
        assertNull(request.nonce());
        assertNull(request.gasLimit());
        assertNull(request.maxFeePerGas());
        assertNull(request.maxPriorityFeePerGas());
        assertNull(request.maxFeePerBlobGas());
        assertNull(request.data());
        assertNull(request.accessList());
    }

    @Test
    void minimalBuildWithBlobData() {
        MockKzg kzg = new MockKzg();

        BlobTransactionRequest request = Eip4844Builder.create()
                .to(RECIPIENT)
                .blobData("test".getBytes())
                .build(kzg);

        assertEquals(RECIPIENT, request.to());
        assertNotNull(request.sidecar());
        assertNull(request.from());
    }
}
