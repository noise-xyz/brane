package io.brane.core.types;

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
}
