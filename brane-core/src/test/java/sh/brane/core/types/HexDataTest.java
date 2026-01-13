// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.core.types;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;

class HexDataTest {

    @Test
    void acceptsEvenLength() {
        HexData data = new HexData("0x1234abcd");
        assertEquals("0x1234abcd", data.value());
    }

    @Test
    void rejectsOddLength() {
        assertThrows(IllegalArgumentException.class, () -> new HexData("0x123"));
    }

    @Test
    void emptyConstant() {
        assertEquals("0x", HexData.EMPTY.value());
        assertArrayEquals(new byte[0], HexData.EMPTY.toBytes());
    }

    @Test
    void roundTripBytes() {
        byte[] bytes = new byte[] {(byte) 0x01, (byte) 0xFF};
        HexData data = HexData.fromBytes(bytes);
        assertArrayEquals(bytes, data.toBytes());
    }

    @Test
    void fromBytesDefensiveCopy() {
        // CRIT-1: Verify that mutation of original array does not affect HexData
        byte[] original = new byte[] {(byte) 0xAB, (byte) 0xCD};
        HexData data = HexData.fromBytes(original);

        // Mutate the original array
        original[0] = (byte) 0x00;
        original[1] = (byte) 0x00;

        // HexData should still have the original values
        assertEquals("0xabcd", data.value());
        assertArrayEquals(new byte[] {(byte) 0xAB, (byte) 0xCD}, data.toBytes());
    }

    @Test
    void concurrentAccessToValueIsThreadSafe() throws Exception {
        // Create HexData from bytes (lazy string initialization)
        byte[] bytes = new byte[] {(byte) 0xDE, (byte) 0xAD, (byte) 0xBE, (byte) 0xEF};
        HexData data = HexData.fromBytes(bytes);

        int threadCount = 100;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        List<Future<String>> futures = new ArrayList<>();

        // Launch multiple threads that all call value() simultaneously
        for (int i = 0; i < threadCount; i++) {
            futures.add(executor.submit(() -> {
                startLatch.await(); // Wait for all threads to be ready
                return data.value();
            }));
        }

        // Start all threads at once
        startLatch.countDown();

        // All threads should get the same value
        String expected = "0xdeadbeef";
        for (Future<String> future : futures) {
            assertEquals(expected, future.get());
        }

        executor.shutdown();
    }

    @Test
    void equalsBytesToBytesComparison() {
        // CRIT-3: Test that two bytes-based HexData compare correctly
        byte[] bytes = new byte[] {(byte) 0x12, (byte) 0x34};
        HexData a = HexData.fromBytes(bytes);
        HexData b = HexData.fromBytes(bytes);

        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equalsStringToStringComparison() {
        // CRIT-3: Test that two string-based HexData compare correctly
        HexData a = new HexData("0x1234");
        HexData b = new HexData("0x1234");

        assertTrue(a.equals(b));
        assertTrue(b.equals(a));
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void equalsMixedComparison() {
        // CRIT-3: Test that bytes-based and string-based HexData with same content are equal
        byte[] bytes = new byte[] {(byte) 0x12, (byte) 0x34};
        HexData fromBytes = HexData.fromBytes(bytes);
        HexData fromString = new HexData("0x1234");

        assertTrue(fromBytes.equals(fromString));
        assertTrue(fromString.equals(fromBytes));
        assertEquals(fromBytes.hashCode(), fromString.hashCode());
    }

    @Test
    void equalsReturnsFalseForDifferentContent() {
        // CRIT-3: Test that different content returns false
        HexData a = HexData.fromBytes(new byte[] {(byte) 0x12, (byte) 0x34});
        HexData b = HexData.fromBytes(new byte[] {(byte) 0xAB, (byte) 0xCD});

        assertFalse(a.equals(b));
        assertFalse(b.equals(a));
    }

    @Test
    void equalsHandlesEdgeCases() {
        // CRIT-3: Test edge cases
        HexData data = HexData.fromBytes(new byte[] {0x01});

        assertFalse(data.equals(null));
        assertFalse(data.equals("not a HexData"));
        assertTrue(data.equals(data)); // same instance
    }

    @Test
    void equalsEmptyData() {
        // CRIT-3: Test equality for empty data
        HexData empty1 = HexData.EMPTY;
        HexData empty2 = HexData.fromBytes(new byte[0]);
        HexData empty3 = HexData.EMPTY;

        assertTrue(empty1.equals(empty2));
        assertTrue(empty2.equals(empty3));
        assertTrue(empty1.equals(empty3));
    }
}
