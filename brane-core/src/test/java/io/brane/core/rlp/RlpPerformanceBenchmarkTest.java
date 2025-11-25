package io.brane.core.rlp;

import io.brane.primitives.rlp.RlpItem;
import io.brane.primitives.rlp.RlpList;
import io.brane.primitives.rlp.RlpNumeric;
import io.brane.primitives.rlp.RlpString;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Performance benchmark comparing our RLP implementation with web3j's.
 * This test helps verify that our implementation has acceptable performance.
 */
class RlpPerformanceBenchmarkTest {

    private static final int ITERATIONS = 100_000;
    private static final int WARMUP_ITERATIONS = 10_000;

    @Test
    void benchmarkStringEncoding() {
        System.out.println("\n=== String Encoding Benchmark ===");

        // Test data: various string lengths
        String[] testStrings = {
                "a", // 1 byte
                "Hello", // 5 bytes
                "Hello, Ethereum!", // 16 bytes
                "a".repeat(55), // 55 bytes (max short)
                "a".repeat(100), // 100 bytes (long)
                "a".repeat(1000) // 1000 bytes (very long)
        };

        for (String test : testStrings) {
            byte[] testBytes = test.getBytes();

            // 1) Cached mode: one RlpString instance, many encode() calls
            benchmarkStringEncodingCached(testBytes, testBytes.length);

            // 2) Fair mode: new RlpString each iteration (no cache benefit)
            benchmarkStringEncodingFresh(testBytes, testBytes.length);
        }
    }

    @Test
    void benchmarkIntegerEncoding() {
        System.out.println("\n=== Integer Encoding Benchmark ===");

        // Test various integer values
        long[] testValues = {
                0,
                127,
                256,
                65535,
                1_000_000,
                1_000_000_000L
        };

        for (long value : testValues) {
            // 1) Cached mode: one RlpItem instance, many encode() calls
            benchmarkIntegerEncodingCached(value);

            // 2) Fair mode: new RlpString each iteration
            benchmarkIntegerEncodingFresh(value);
        }
    }

    @Test
    void benchmarkListEncoding() {
        System.out.println("\n=== List Encoding Benchmark ===");

        // Test lists of various sizes
        int[] listSizes = { 0, 2, 10, 50, 100 };

        for (int size : listSizes) {
            benchmarkListEncodingImpl(size);
        }
    }

    @Test
    void benchmarkComplexStructure() {
        System.out.println("\n=== Complex Structure Benchmark ===");

        // Build a transaction-like structure
        // [nonce, gasPrice, gasLimit, to, value, data, v, r, s]
        RlpList ourTxList = RlpList.of(
                RlpString.of(1L), // nonce
                RlpString.of(new BigInteger("20000000000")), // gasPrice
                RlpString.of(21000L), // gasLimit
                RlpString.of("0x742d35Cc6634C0532925a3b844Bc9e7595f0bEb0"), // to
                RlpString.of(new BigInteger("1000000000000000000")), // value (1 ETH)
                RlpString.of(new byte[0]), // data
                RlpString.of(27L), // v
                RlpString.of(new BigInteger("12345")), // r
                RlpString.of(new BigInteger("67890")) // s
        );

        List<io.brane.internal.web3j.rlp.RlpType> web3jTxItems = List.of(
                io.brane.internal.web3j.rlp.RlpString.create(toMinimalBytes(1L)),
                io.brane.internal.web3j.rlp.RlpString.create(new BigInteger("20000000000").toByteArray()),
                io.brane.internal.web3j.rlp.RlpString.create(toMinimalBytes(21000L)),
                io.brane.internal.web3j.rlp.RlpString.create(hexToBytes("742d35Cc6634C0532925a3b844Bc9e7595f0bEb0")),
                io.brane.internal.web3j.rlp.RlpString.create(new BigInteger("1000000000000000000").toByteArray()),
                io.brane.internal.web3j.rlp.RlpString.create(new byte[0]),
                io.brane.internal.web3j.rlp.RlpString.create(toMinimalBytes(27L)),
                io.brane.internal.web3j.rlp.RlpString.create(new BigInteger("12345").toByteArray()),
                io.brane.internal.web3j.rlp.RlpString.create(new BigInteger("67890").toByteArray()));
        io.brane.internal.web3j.rlp.RlpList web3jTxList = new io.brane.internal.web3j.rlp.RlpList(web3jTxItems);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            ourTxList.encode();
            io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jTxList);
        }

        // Benchmark our implementation
        long ourStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            ourTxList.encode();
        }
        long ourEnd = System.nanoTime();

        // Benchmark web3j implementation
        long web3jStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jTxList);
        }
        long web3jEnd = System.nanoTime();

        printResults("Transaction-like structure", ourEnd - ourStart, web3jEnd - web3jStart);
    }

    // =========================
    // String benchmarks
    // =========================

    // Cached mode: one RlpString, many encode() calls (benefits from cache)
    private void benchmarkStringEncodingCached(byte[] data, int length) {
        RlpString ourString = RlpString.of(data);
        io.brane.internal.web3j.rlp.RlpString web3jString = io.brane.internal.web3j.rlp.RlpString.create(data);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            ourString.encode();
            io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jString);
        }

        // Benchmark our implementation
        long ourStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            ourString.encode();
        }
        long ourEnd = System.nanoTime();

        // Benchmark web3j implementation
        long web3jStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jString);
        }
        long web3jEnd = System.nanoTime();

        printResults("String (" + length + " bytes)", ourEnd - ourStart, web3jEnd - web3jStart);
    }

    // Fair mode: new RlpString each iteration (no cache advantage)
    private void benchmarkStringEncodingFresh(byte[] data, int length) {

        // Warmup (our side)
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            RlpString s = RlpString.of(data);
            s.encode();
        }

        // Warmup (web3j)
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            io.brane.internal.web3j.rlp.RlpString ws = io.brane.internal.web3j.rlp.RlpString.create(data);
            io.brane.internal.web3j.rlp.RlpEncoder.encode(ws);
        }

        // Benchmark our implementation (fresh instance each time)
        long ourStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            RlpString s = RlpString.of(data);
            s.encode();
        }
        long ourEnd = System.nanoTime();

        // Benchmark web3j implementation (fresh instance each time)
        long web3jStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            io.brane.internal.web3j.rlp.RlpString ws = io.brane.internal.web3j.rlp.RlpString.create(data);
            io.brane.internal.web3j.rlp.RlpEncoder.encode(ws);
        }
        long web3jEnd = System.nanoTime();

        printResults("String (" + length + " bytes) [fresh]", ourEnd - ourStart, web3jEnd - web3jStart);
    }

    // =========================
    // Integer benchmarks
    // =========================

    // Cached mode: one RlpItem, many encode() calls
    private void benchmarkIntegerEncodingCached(long value) {
        // Our side: use numeric helper to build an RlpItem once, then only measure
        // encode() cost
        RlpItem ourItem = RlpNumeric.encodeLongUnsignedItem(value);

        byte[] valueBytes = toMinimalBytes(value);
        io.brane.internal.web3j.rlp.RlpString web3jString = io.brane.internal.web3j.rlp.RlpString.create(valueBytes);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            ourItem.encode();
            io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jString);
        }

        // Benchmark our implementation
        long ourStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            ourItem.encode();
        }
        long ourEnd = System.nanoTime();

        // Benchmark web3j implementation
        long web3jStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jString);
        }
        long web3jEnd = System.nanoTime();

        printResults("Integer (" + value + ")", ourEnd - ourStart, web3jEnd - web3jStart);
    }

    // Fair mode: new RlpString each iteration, both sides
    private void benchmarkIntegerEncodingFresh(long value) {
        byte[] valueBytes = toMinimalBytes(value);

        // Warmup (our side)
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            RlpString s = RlpString.of(valueBytes);
            s.encode();
        }

        // Warmup (web3j)
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            io.brane.internal.web3j.rlp.RlpString ws = io.brane.internal.web3j.rlp.RlpString.create(valueBytes);
            io.brane.internal.web3j.rlp.RlpEncoder.encode(ws);
        }

        // Benchmark our implementation (fresh instance each time)
        long ourStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            RlpString s = RlpString.of(valueBytes);
            s.encode();
        }
        long ourEnd = System.nanoTime();

        // Benchmark web3j implementation (fresh instance each time)
        long web3jStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            io.brane.internal.web3j.rlp.RlpString ws = io.brane.internal.web3j.rlp.RlpString.create(valueBytes);
            io.brane.internal.web3j.rlp.RlpEncoder.encode(ws);
        }
        long web3jEnd = System.nanoTime();

        printResults("Integer (" + value + ") [fresh]", ourEnd - ourStart, web3jEnd - web3jStart);
    }

    // =========================
    // List benchmarks (unchanged)
    // =========================

    private void benchmarkListEncodingImpl(int size) {
        Random random = new Random(42); // Fixed seed for reproducibility
        List<RlpItem> ourItems = new ArrayList<>();
        List<io.brane.internal.web3j.rlp.RlpType> web3jItems = new ArrayList<>();

        for (int i = 0; i < size; i++) {
            byte[] data = new byte[random.nextInt(20) + 1];
            random.nextBytes(data);

            ourItems.add(RlpString.of(data));
            web3jItems.add(io.brane.internal.web3j.rlp.RlpString.create(data));
        }

        RlpList ourList = RlpList.of(ourItems);
        io.brane.internal.web3j.rlp.RlpList web3jList = new io.brane.internal.web3j.rlp.RlpList(web3jItems);

        // Warmup
        for (int i = 0; i < WARMUP_ITERATIONS; i++) {
            ourList.encode();
            io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jList);
        }

        // Benchmark our implementation
        long ourStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            ourList.encode();
        }
        long ourEnd = System.nanoTime();

        // Benchmark web3j implementation
        long web3jStart = System.nanoTime();
        for (int i = 0; i < ITERATIONS; i++) {
            io.brane.internal.web3j.rlp.RlpEncoder.encode(web3jList);
        }
        long web3jEnd = System.nanoTime();

        printResults("List (" + size + " items)", ourEnd - ourStart, web3jEnd - web3jStart);
    }

    // =========================
    // Helpers
    // =========================

    private void printResults(String testName, long ourNanos, long web3jNanos) {
        double ourMs = ourNanos / 1_000_000.0;
        double web3jMs = web3jNanos / 1_000_000.0;
        double ourOpsPerSec = (ITERATIONS / (ourNanos / 1_000_000_000.0));
        double web3jOpsPerSec = (ITERATIONS / (web3jNanos / 1_000_000_000.0));
        double speedup = ((double) web3jNanos / ourNanos);

        System.out.printf(
                "%-35s | Our: %8.2f ms (%10.0f ops/s) | Web3j: %8.2f ms (%10.0f ops/s) | Speedup: %.2fx%s%n",
                testName,
                ourMs, ourOpsPerSec,
                web3jMs, web3jOpsPerSec,
                speedup,
                speedup >= 1.0 ? " ✓" : " [slower]");
    }

    private byte[] toMinimalBytes(long value) {
        if (value == 0) {
            return new byte[0];
        }

        byte[] bytes = BigInteger.valueOf(value).toByteArray();

        // Remove leading zero bytes
        int firstNonZero = 0;
        while (firstNonZero < bytes.length && bytes[firstNonZero] == 0) {
            firstNonZero++;
        }

        if (firstNonZero == 0) {
            return bytes;
        }

        return java.util.Arrays.copyOfRange(bytes, firstNonZero, bytes.length);
    }

    private byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}
