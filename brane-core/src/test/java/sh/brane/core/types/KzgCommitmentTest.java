// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class KzgCommitmentTest {

    @Test
    void constantsAreCorrect() {
        assertEquals(48, FixedSizeG1Point.SIZE);
    }

    @Test
    void acceptsCorrectSize() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        KzgCommitment commitment = new KzgCommitment(data);
        assertArrayEquals(data, commitment.toBytes());
    }

    @Test
    void rejectsNullData() {
        assertThrows(NullPointerException.class, () -> new KzgCommitment(null));
    }

    @Test
    void rejectsTooSmall() {
        byte[] data = new byte[FixedSizeG1Point.SIZE - 1];
        assertThrows(IllegalArgumentException.class, () -> new KzgCommitment(data));
    }

    @Test
    void rejectsTooLarge() {
        byte[] data = new byte[FixedSizeG1Point.SIZE + 1];
        assertThrows(IllegalArgumentException.class, () -> new KzgCommitment(data));
    }

    @Test
    void rejectsEmpty() {
        byte[] data = new byte[0];
        assertThrows(IllegalArgumentException.class, () -> new KzgCommitment(data));
    }

    @Test
    void defensiveCopyOnConstruction() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        KzgCommitment commitment = new KzgCommitment(data);

        // Modify original array
        data[0] = (byte) 0xFF;

        // Commitment should not be affected
        assertEquals((byte) 0xAB, commitment.toBytes()[0]);
    }

    @Test
    void toBytesReturnsDefensiveCopy() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        KzgCommitment commitment = new KzgCommitment(data);

        byte[] copy1 = commitment.toBytes();
        byte[] copy2 = commitment.toBytes();

        // Different array instances
        assertNotSame(copy1, copy2);

        // Modifying copy should not affect commitment
        copy1[0] = (byte) 0xFF;
        assertEquals((byte) 0xAB, commitment.toBytes()[0]);
    }

    @Test
    void toVersionedHashReturnsCorrectFormat() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        KzgCommitment commitment = new KzgCommitment(data);

        Hash versionedHash = commitment.toVersionedHash();

        assertNotNull(versionedHash);
        // Versioned hash should be 32 bytes
        byte[] hashBytes = versionedHash.toBytes();
        assertEquals(32, hashBytes.length);
        // First byte should be 0x01 (KZG version)
        assertEquals((byte) 0x01, hashBytes[0]);
    }

    @Test
    void toVersionedHashIsCached() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        KzgCommitment commitment = new KzgCommitment(data);

        Hash hash1 = commitment.toVersionedHash();
        Hash hash2 = commitment.toVersionedHash();

        // Same instance should be returned
        assertSame(hash1, hash2);
    }

    @Test
    void equalsAndHashCode() {
        byte[] data1 = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data1, (byte) 0xAB);

        byte[] data2 = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data2, (byte) 0xAB);

        byte[] data3 = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data3, (byte) 0xCD);

        KzgCommitment commitment1 = new KzgCommitment(data1);
        KzgCommitment commitment2 = new KzgCommitment(data2);
        KzgCommitment commitment3 = new KzgCommitment(data3);

        // Reflexive
        assertEquals(commitment1, commitment1);

        // Symmetric
        assertEquals(commitment1, commitment2);
        assertEquals(commitment2, commitment1);

        // Different content
        assertNotEquals(commitment1, commitment3);

        // HashCode consistency
        assertEquals(commitment1.hashCode(), commitment2.hashCode());

        // Null and other type
        assertNotEquals(commitment1, null);
        assertNotEquals(commitment1, "not a commitment");
    }

    @Test
    void toStringContainsHex() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        KzgCommitment commitment = new KzgCommitment(data);

        String str = commitment.toString();
        assertTrue(str.contains("KzgCommitment"));
        assertTrue(str.contains("0x"));
        // Should contain the hex encoding of 0xAB repeated
        assertTrue(str.contains("ab"));
    }

    @Test
    void differentCommitmentsProduceDifferentVersionedHashes() {
        byte[] data1 = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data1, (byte) 0xAB);

        byte[] data2 = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data2, (byte) 0xCD);

        KzgCommitment commitment1 = new KzgCommitment(data1);
        KzgCommitment commitment2 = new KzgCommitment(data2);

        assertNotEquals(commitment1.toVersionedHash(), commitment2.toVersionedHash());
    }
}
