// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

class BlobTest {

    @Test
    void constantsAreCorrect() {
        assertEquals(131072, Blob.SIZE);
        assertEquals(4096, Blob.FIELD_ELEMENTS);
        assertEquals(32, Blob.BYTES_PER_FIELD_ELEMENT);
        assertEquals(Blob.SIZE, Blob.FIELD_ELEMENTS * Blob.BYTES_PER_FIELD_ELEMENT);
    }

    @Test
    void acceptsCorrectSize() {
        byte[] data = new byte[Blob.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        Blob blob = new Blob(data);
        assertArrayEquals(data, blob.toBytes());
    }

    @Test
    void rejectsNullData() {
        assertThrows(NullPointerException.class, () -> new Blob(null));
    }

    @Test
    void rejectsTooSmall() {
        byte[] data = new byte[Blob.SIZE - 1];
        assertThrows(IllegalArgumentException.class, () -> new Blob(data));
    }

    @Test
    void rejectsTooLarge() {
        byte[] data = new byte[Blob.SIZE + 1];
        assertThrows(IllegalArgumentException.class, () -> new Blob(data));
    }

    @Test
    void rejectsEmpty() {
        byte[] data = new byte[0];
        assertThrows(IllegalArgumentException.class, () -> new Blob(data));
    }

    @Test
    void defensiveCopyOnConstruction() {
        byte[] data = new byte[Blob.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        Blob blob = new Blob(data);

        // Modify original array
        data[0] = (byte) 0xFF;

        // Blob should not be affected
        assertEquals((byte) 0xAB, blob.toBytes()[0]);
    }

    @Test
    void toBytesReturnsDefensiveCopy() {
        byte[] data = new byte[Blob.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        Blob blob = new Blob(data);

        byte[] copy1 = blob.toBytes();
        byte[] copy2 = blob.toBytes();

        // Different array instances
        assertNotSame(copy1, copy2);

        // Modifying copy should not affect blob
        copy1[0] = (byte) 0xFF;
        assertEquals((byte) 0xAB, blob.toBytes()[0]);
    }

    @Test
    void equalsAndHashCode() {
        byte[] data1 = new byte[Blob.SIZE];
        Arrays.fill(data1, (byte) 0xAB);

        byte[] data2 = new byte[Blob.SIZE];
        Arrays.fill(data2, (byte) 0xAB);

        byte[] data3 = new byte[Blob.SIZE];
        Arrays.fill(data3, (byte) 0xCD);

        Blob blob1 = new Blob(data1);
        Blob blob2 = new Blob(data2);
        Blob blob3 = new Blob(data3);

        // Reflexive
        assertEquals(blob1, blob1);

        // Symmetric
        assertEquals(blob1, blob2);
        assertEquals(blob2, blob1);

        // Different content
        assertNotEquals(blob1, blob3);

        // HashCode consistency
        assertEquals(blob1.hashCode(), blob2.hashCode());

        // Null and other type
        assertNotEquals(blob1, null);
        assertNotEquals(blob1, "not a blob");
    }

    @Test
    void toStringContainsInfo() {
        byte[] data = new byte[Blob.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        Blob blob = new Blob(data);

        String str = blob.toString();
        assertTrue(str.contains("Blob"));
        assertTrue(str.contains(String.valueOf(Blob.SIZE)));
    }
}
