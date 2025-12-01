package io.brane.benchmark;

import io.brane.core.tx.LegacyTransaction;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.PrivateKeyTransactionSigner;
import org.openjdk.jmh.annotations.*;

import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class SignerBenchmark {

    private PrivateKeyTransactionSigner signer;
    private LegacyTransaction legacyTx;
    private io.brane.core.tx.Eip1559Transaction eip1559Tx;
    private LegacyTransaction largeTx;

    @Setup
    public void setup() {
        signer = new PrivateKeyTransactionSigner("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
        
        Address to = new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8");
        
        legacyTx = new LegacyTransaction(
                0,
                Wei.of(1000000000),
                21000,
                to,
                Wei.of(1),
                HexData.EMPTY
        );

        eip1559Tx = new io.brane.core.tx.Eip1559Transaction(
                11155111,
                0,
                Wei.of(1000000000), // maxPriorityFeePerGas
                Wei.of(2000000000), // maxFeePerGas
                21000,
                to,
                Wei.of(1),
                HexData.EMPTY,
                java.util.List.of()
        );
        
        // Large payload (10KB)
        byte[] largeData = new byte[10240];
        java.util.Arrays.fill(largeData, (byte) 0xAA);
        largeTx = new LegacyTransaction(
                1,
                Wei.of(1000000000),
                100000,
                to,
                Wei.of(0),
                new HexData(io.brane.primitives.Hex.encode(largeData))
        );
    }

    @Benchmark
    public String signLegacy() {
        return signer.sign(legacyTx, 31337);
    }

    @Benchmark
    public String signEip1559() {
        return signer.sign(eip1559Tx, 11155111);
    }

    @Benchmark
    public String signLargePayload() {
        return signer.sign(largeTx, 31337);
    }
}
