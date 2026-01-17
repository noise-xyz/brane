// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.benchmark;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import sh.brane.primitives.Hex;

/**
 * JMH benchmark for measuring allocation patterns in Brane SDK operations.
 *
 * <p>This benchmark provides a foundation for tracking object allocations
 * across common SDK operations like hex encoding/decoding and byte array
 * manipulation.
 *
 * <p>Uses {@code Scope.Thread} to ensure each benchmark thread has its own
 * state instance, avoiding contention and providing accurate per-thread
 * allocation measurements.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class AllocationBenchmark {

    /** 64-character hex string with 0x prefix (represents 32 bytes). */
    public String hexString;

    /** 32-byte test array for encoding/decoding benchmarks. */
    public byte[] testBytes;

    /** Pre-allocated char array for allocation-free hex encoding (66 chars = "0x" + 64 hex). */
    public char[] destChars;

    /** Pre-allocated byte array for allocation-free hex decoding (32 bytes). */
    public byte[] destBytes;

    @Setup(Level.Trial)
    public void setup() {
        // 64-char hex with 0x prefix = 66 chars total, representing 32 bytes
        hexString = "0x0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef";

        // 32-byte array matching the hex string pattern
        testBytes = new byte[32];
        for (int i = 0; i < 32; i++) {
            testBytes[i] = (byte) i;
        }

        // Pre-allocated char buffer for allocation-free encoding
        destChars = new char[66];

        // Pre-allocated byte buffer for allocation-free decoding
        destBytes = new byte[32];
    }

    /**
     * Benchmarks encoding a 32-byte array to a hex string with 0x prefix.
     * Output: 66-character string (2 prefix + 64 hex chars).
     *
     * <p><b>Baseline:</b> 264 B/op (JDK 21.0.9, G1GC)
     *
     * @return the encoded hex string (for blackhole consumption)
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public String hexEncode() {
        return Hex.encode(testBytes);
    }

    /**
     * Benchmarks allocation-free encoding using pre-allocated char[] buffer.
     * Input: 32-byte array, Output: writes to pre-allocated 66-char buffer.
     *
     * <p><b>Expected:</b> 0 B/op - no allocations, writes directly to pre-allocated buffer.
     *
     * @return the number of characters written (for blackhole consumption)
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int hexEncodeTo() {
        return Hex.encodeTo(testBytes, destChars, 0, true);
    }

    /**
     * Benchmarks decoding a 64-character hex string (with 0x prefix) to bytes.
     * Input: 66-character string, Output: 32-byte array.
     *
     * <p><b>Baseline:</b> 48 B/op (JDK 21.0.9, G1GC) - optimized with index-based parsing (no substring)
     *
     * @return the decoded byte array (for blackhole consumption)
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public byte[] hexDecode() {
        return Hex.decode(hexString);
    }

    /**
     * Benchmarks allocation-free decoding using pre-allocated byte[] buffer.
     * Input: 66-character hex string (with 0x prefix), Output: writes to pre-allocated 32-byte buffer.
     *
     * <p><b>Expected:</b> 0 B/op - no allocations, writes directly to pre-allocated buffer.
     *
     * @return the number of bytes written (for blackhole consumption)
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public int hexDecodeTo() {
        return Hex.decodeTo(hexString, 0, hexString.length(), destBytes, 0);
    }
}
