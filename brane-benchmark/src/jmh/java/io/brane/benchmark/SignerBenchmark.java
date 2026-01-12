// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.benchmark;

import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import io.brane.core.crypto.PrivateKeySigner;
import io.brane.core.tx.LegacyTransaction;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

/**
 * JMH benchmark for Brane SDK's transaction signing performance.
 *
 * <p>Measures throughput (ops/sec) for ECDSA signing:
 * <ul>
 *   <li>{@code signLegacy} - legacy (type 0) transaction signing</li>
 *   <li>{@code signEip1559} - EIP-1559 (type 2) transaction signing</li>
 *   <li>{@code signLargePayload} - legacy tx with 10KB data payload</li>
 * </ul>
 *
 * <p>Compare with {@link Web3jSignerBenchmark} for web3j baseline.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class SignerBenchmark {

    private PrivateKeySigner signer;
    private LegacyTransaction legacyTx;
    private io.brane.core.tx.Eip1559Transaction eip1559Tx;
    private LegacyTransaction largeTx;

    @Setup
    public void setup() {
        signer = new PrivateKeySigner("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");

        Address to = new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");

        legacyTx = new LegacyTransaction(
                0,
                Wei.of(1000000000),
                21000,
                to,
                Wei.of(1),
                HexData.EMPTY);

        eip1559Tx = new io.brane.core.tx.Eip1559Transaction(
                11155111,
                0,
                Wei.of(1000000000), // maxPriorityFeePerGas
                Wei.of(2000000000), // maxFeePerGas
                21000,
                to,
                Wei.of(1),
                HexData.EMPTY,
                java.util.List.of());

        // Large payload (10KB)
        byte[] largeData = new byte[10240];
        java.util.Arrays.fill(largeData, (byte) 0xAA);
        largeTx = new LegacyTransaction(
                1,
                Wei.of(1000000000),
                100000,
                to,
                Wei.of(0),
                HexData.fromBytes(largeData));
    }

    @Benchmark
    public io.brane.core.crypto.Signature signLegacy() {
        return signer.signTransaction(legacyTx, 31337);
    }

    @Benchmark
    public io.brane.core.crypto.Signature signEip1559() {
        return signer.signTransaction(eip1559Tx, 11155111);
    }

    @Benchmark
    public io.brane.core.crypto.Signature signLargePayload() {
        return signer.signTransaction(largeTx, 31337);
    }
}
