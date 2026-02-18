// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.benchmark;

import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import sh.brane.core.crypto.Signature;
import sh.brane.core.tx.Eip1559Transaction;
import sh.brane.core.tx.LegacyTransaction;
import sh.brane.core.types.Address;
import sh.brane.core.types.HexData;
import sh.brane.core.types.Wei;

/**
 * JMH benchmark for measuring allocation patterns in transaction RLP encoding.
 *
 * <p>Isolates the RLP encoding cost from signing by using a pre-computed signature.
 * This allows measuring the allocation impact of stream operations, array copies,
 * and BigInteger conversions in the encoding path alone.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class TxEncodeAllocationBenchmark {

    private LegacyTransaction legacyTx;
    private Eip1559Transaction eip1559Tx;
    private Signature signature;

    @Setup(Level.Trial)
    public void setup() {
        Address to = new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");

        legacyTx = new LegacyTransaction(
                0,
                Wei.of(1000000000),
                21000,
                to,
                Wei.of(1),
                HexData.EMPTY);

        eip1559Tx = new Eip1559Transaction(
                11155111,
                0,
                Wei.of(1000000000),
                Wei.of(2000000000),
                21000,
                to,
                Wei.of(1),
                HexData.EMPTY,
                List.of());

        // Pre-computed signature (valid structure, used only for encoding measurement)
        byte[] r = new byte[32];
        byte[] s = new byte[32];
        r[0] = 0x7f; // Ensure non-zero for realistic encoding
        r[31] = 0x01;
        s[0] = 0x3f;
        s[31] = 0x02;
        signature = new Signature(r, s, 1);
    }

    /**
     * Benchmarks RLP encoding of a legacy transaction for signing.
     * Measures allocation from RLP list construction and BigInteger encoding.
     *
     * @return the RLP-encoded bytes (for blackhole consumption)
     */
    @Benchmark
    public byte[] encodeLegacyForSigning() {
        return legacyTx.encodeForSigning(31337);
    }

    /**
     * Benchmarks RLP encoding of an EIP-1559 transaction for signing.
     * Measures allocation from RLP list construction and BigInteger encoding.
     *
     * @return the RLP-encoded bytes (for blackhole consumption)
     */
    @Benchmark
    public byte[] encodeEip1559ForSigning() {
        return eip1559Tx.encodeForSigning(11155111);
    }

    /**
     * Benchmarks RLP encoding of a signed legacy transaction envelope.
     * Measures allocation from Signature.r()/s() defensive copies + BigInteger wrapping.
     *
     * @return the RLP-encoded envelope bytes (for blackhole consumption)
     */
    @Benchmark
    public byte[] encodeLegacyEnvelope() {
        return legacyTx.encodeAsEnvelope(signature);
    }

    /**
     * Benchmarks RLP encoding of a signed EIP-1559 transaction envelope.
     * Measures allocation from Signature.r()/s() defensive copies + BigInteger wrapping.
     *
     * @return the RLP-encoded envelope bytes (for blackhole consumption)
     */
    @Benchmark
    public byte[] encodeEip1559Envelope() {
        return eip1559Tx.encodeAsEnvelope(signature);
    }
}
