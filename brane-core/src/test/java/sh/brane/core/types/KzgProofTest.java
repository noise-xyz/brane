// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class KzgProofTest {

    @Test
    void constantsAreCorrect() {
        assertEquals(48, FixedSizeG1Point.SIZE);
    }

    @Test
    void acceptsCorrectSize() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        KzgProof proof = new KzgProof(data);
        assertArrayEquals(data, proof.toBytes());
    }

    @Test
    void rejectsNullData() {
        assertThrows(NullPointerException.class, () -> new KzgProof(null));
    }

    @Test
    void rejectsTooSmall() {
        byte[] data = new byte[FixedSizeG1Point.SIZE - 1];
        assertThrows(IllegalArgumentException.class, () -> new KzgProof(data));
    }

    @Test
    void rejectsTooLarge() {
        byte[] data = new byte[FixedSizeG1Point.SIZE + 1];
        assertThrows(IllegalArgumentException.class, () -> new KzgProof(data));
    }

    @Test
    void rejectsEmpty() {
        byte[] data = new byte[0];
        assertThrows(IllegalArgumentException.class, () -> new KzgProof(data));
    }

    @Test
    void defensiveCopyOnConstruction() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        KzgProof proof = new KzgProof(data);

        // Modify original array
        data[0] = (byte) 0xFF;

        // Proof should not be affected
        assertEquals((byte) 0xAB, proof.toBytes()[0]);
    }

    @Test
    void toBytesReturnsDefensiveCopy() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        KzgProof proof = new KzgProof(data);

        byte[] copy1 = proof.toBytes();
        byte[] copy2 = proof.toBytes();

        // Different array instances
        assertNotSame(copy1, copy2);

        // Modifying copy should not affect proof
        copy1[0] = (byte) 0xFF;
        assertEquals((byte) 0xAB, proof.toBytes()[0]);
    }

    @Test
    void equalsAndHashCode() {
        byte[] data1 = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data1, (byte) 0xAB);

        byte[] data2 = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data2, (byte) 0xAB);

        byte[] data3 = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data3, (byte) 0xCD);

        KzgProof proof1 = new KzgProof(data1);
        KzgProof proof2 = new KzgProof(data2);
        KzgProof proof3 = new KzgProof(data3);

        // Reflexive
        assertEquals(proof1, proof1);

        // Symmetric
        assertEquals(proof1, proof2);
        assertEquals(proof2, proof1);

        // Different content
        assertNotEquals(proof1, proof3);

        // HashCode consistency
        assertEquals(proof1.hashCode(), proof2.hashCode());

        // Null and other type
        assertNotEquals(proof1, null);
        assertNotEquals(proof1, "not a proof");
    }

    @Test
    void toStringContainsHex() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        KzgProof proof = new KzgProof(data);

        String str = proof.toString();
        assertTrue(str.contains("KzgProof"));
        assertTrue(str.contains("0x"));
        // Should contain the hex encoding of 0xAB repeated
        assertTrue(str.contains("ab"));
    }
}
