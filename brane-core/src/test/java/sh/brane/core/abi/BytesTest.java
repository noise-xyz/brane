// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.abi;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

/**
 * Tests for the Bytes ABI type, focusing on the ofStatic subarray factory.
 */
class BytesTest {

    @Test
    void testOfStaticSubarrayMatchesFullArray() {
        byte[] data = new byte[] {0x00, 0x11, 0x22, 0x33, 0x44};

        // ofStatic(subarray) should match ofStatic(extracted copy)
        Bytes fromSubarray = Bytes.ofStatic(data, 1, 3);
        Bytes fromCopy = Bytes.ofStatic(new byte[] {0x11, 0x22, 0x33});

        assertEquals(fromCopy.value(), fromSubarray.value());
        assertFalse(fromSubarray.isDynamic());
    }

    @Test
    void testOfStaticSubarraySingleByte() {
        byte[] data = new byte[] {(byte) 0xFF, 0x42, 0x00};

        Bytes result = Bytes.ofStatic(data, 1, 1);

        assertEquals("0x42", result.value().value());
        assertFalse(result.isDynamic());
        assertEquals("bytes1", result.typeName());
    }

    @Test
    void testOfStaticSubarray32Bytes() {
        byte[] data = new byte[34];
        // Fill bytes 1..32 with a pattern
        for (int i = 0; i < 32; i++) {
            data[i + 1] = (byte) i;
        }

        Bytes result = Bytes.ofStatic(data, 1, 32);

        assertFalse(result.isDynamic());
        assertEquals("bytes32", result.typeName());
        assertEquals(32, result.value().byteLength());
    }

    @Test
    void testOfStaticSubarrayAtEnd() {
        byte[] data = new byte[] {0x00, 0x00, (byte) 0xAB, (byte) 0xCD};

        Bytes result = Bytes.ofStatic(data, 2, 2);

        assertEquals("0xabcd", result.value().value());
        assertFalse(result.isDynamic());
    }

    @Test
    void testOfStaticSubarrayRejectsZeroLength() {
        byte[] data = new byte[] {0x01, 0x02};

        // Static bytes must be 1-32 bytes; zero length is invalid
        assertThrows(IllegalArgumentException.class, () -> Bytes.ofStatic(data, 0, 0));
    }

    @Test
    void testOfStaticSubarrayRejectsOver32Bytes() {
        byte[] data = new byte[64];

        // Static bytes max is 32
        assertThrows(IllegalArgumentException.class, () -> Bytes.ofStatic(data, 0, 33));
    }
}
