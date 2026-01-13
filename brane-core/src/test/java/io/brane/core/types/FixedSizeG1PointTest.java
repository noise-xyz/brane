// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FixedSizeG1Point} sealed class hierarchy.
 * <p>
 * Tests verify the base class behavior through the permitted subclasses
 * ({@link KzgCommitment} and {@link KzgProof}) and that subclasses with
 * the same data are not equal to each other.
 */
class FixedSizeG1PointTest {

    @Test
    void sizeConstantIsCorrect() {
        assertEquals(48, FixedSizeG1Point.SIZE);
    }

    @Test
    void kzgCommitmentExtendsFixedSizeG1Point() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        FixedSizeG1Point point = new KzgCommitment(data);
        assertInstanceOf(FixedSizeG1Point.class, point);
    }

    @Test
    void kzgProofExtendsFixedSizeG1Point() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        FixedSizeG1Point point = new KzgProof(data);
        assertInstanceOf(FixedSizeG1Point.class, point);
    }

    @Test
    void differentSubclassesWithSameDataAreNotEqual() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);

        KzgCommitment commitment = new KzgCommitment(data.clone());
        KzgProof proof = new KzgProof(data.clone());

        // Same underlying data but different types should NOT be equal
        assertNotEquals(commitment, proof);
        assertNotEquals(proof, commitment);
    }

    @Test
    void subclassesHaveDifferentToStringPrefixes() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);

        KzgCommitment commitment = new KzgCommitment(data.clone());
        KzgProof proof = new KzgProof(data.clone());

        // Verify different type names in toString
        assertTrue(commitment.toString().startsWith("KzgCommitment["));
        assertTrue(proof.toString().startsWith("KzgProof["));
    }

    @Test
    void polymorphicAccessToToBytes() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xCD);

        // Access through base class reference
        FixedSizeG1Point point = new KzgCommitment(data.clone());
        assertArrayEquals(data, point.toBytes());

        point = new KzgProof(data.clone());
        assertArrayEquals(data, point.toBytes());
    }
}
