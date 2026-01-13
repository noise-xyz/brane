// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.core.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Arrays;

import org.junit.jupiter.api.Test;

/**
 * Tests for {@link FixedSizeG1Point} abstract class functionality.
 * <p>
 * Tests are performed through a test-only concrete implementation since the base class is abstract.
 */
class FixedSizeG1PointTest {

    /**
     * Test implementation of FixedSizeG1Point for testing the abstract class behavior.
     */
    private static final class TestG1Point extends FixedSizeG1Point {
        TestG1Point(byte[] data) {
            super(data, "TestG1Point");
        }

        @Override
        protected String typeName() {
            return "TestG1Point";
        }
    }

    @Test
    void sizeConstantIsCorrect() {
        assertEquals(48, FixedSizeG1Point.SIZE);
    }

    @Test
    void acceptsCorrectSize() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        var point = new TestG1Point(data);
        assertArrayEquals(data, point.toBytes());
    }

    @Test
    void rejectsNullData() {
        assertThrows(NullPointerException.class, () -> new TestG1Point(null));
    }

    @Test
    void rejectsTooSmall() {
        byte[] data = new byte[FixedSizeG1Point.SIZE - 1];
        assertThrows(IllegalArgumentException.class, () -> new TestG1Point(data));
    }

    @Test
    void rejectsTooLarge() {
        byte[] data = new byte[FixedSizeG1Point.SIZE + 1];
        assertThrows(IllegalArgumentException.class, () -> new TestG1Point(data));
    }

    @Test
    void rejectsEmpty() {
        byte[] data = new byte[0];
        assertThrows(IllegalArgumentException.class, () -> new TestG1Point(data));
    }

    @Test
    void defensiveCopyOnConstruction() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        var point = new TestG1Point(data);

        // Modify original array
        data[0] = (byte) 0xFF;

        // Point should not be affected
        assertEquals((byte) 0xAB, point.toBytes()[0]);
    }

    @Test
    void toBytesReturnsDefensiveCopy() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        var point = new TestG1Point(data);

        byte[] copy1 = point.toBytes();
        byte[] copy2 = point.toBytes();

        // Different array instances
        assertNotSame(copy1, copy2);

        // Modifying copy should not affect point
        copy1[0] = (byte) 0xFF;
        assertEquals((byte) 0xAB, point.toBytes()[0]);
    }

    @Test
    void toBytesUnsafeReturnsInternalArray() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        var point = new TestG1Point(data);

        byte[] unsafe1 = point.toBytesUnsafe();
        byte[] unsafe2 = point.toBytesUnsafe();

        // Same instance should be returned
        assertSame(unsafe1, unsafe2);
    }

    @Test
    void equalsAndHashCode() {
        byte[] data1 = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data1, (byte) 0xAB);

        byte[] data2 = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data2, (byte) 0xAB);

        byte[] data3 = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data3, (byte) 0xCD);

        var point1 = new TestG1Point(data1);
        var point2 = new TestG1Point(data2);
        var point3 = new TestG1Point(data3);

        // Reflexive
        assertEquals(point1, point1);

        // Symmetric
        assertEquals(point1, point2);
        assertEquals(point2, point1);

        // Different content
        assertNotEquals(point1, point3);

        // HashCode consistency
        assertEquals(point1.hashCode(), point2.hashCode());

        // Null and other type
        assertNotEquals(point1, null);
        assertNotEquals(point1, "not a point");
    }

    @Test
    void errorMessageContainsTypeName() {
        byte[] data = new byte[10];
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class, () -> new TestG1Point(data));
        assertTrue(ex.getMessage().contains("TestG1Point"));
        assertTrue(ex.getMessage().contains("48"));
        assertTrue(ex.getMessage().contains("10"));
    }

    @Test
    void toStringContainsTypeNameAndHex() {
        byte[] data = new byte[FixedSizeG1Point.SIZE];
        Arrays.fill(data, (byte) 0xAB);
        var point = new TestG1Point(data);

        String str = point.toString();
        assertTrue(str.contains("TestG1Point"));
        assertTrue(str.contains("0x"));
        // Should contain the hex encoding of 0xAB repeated
        assertTrue(str.contains("ab"));
    }
}
