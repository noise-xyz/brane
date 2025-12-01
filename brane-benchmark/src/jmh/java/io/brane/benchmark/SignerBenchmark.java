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
    private LegacyTransaction tx;

    @Setup
    public void setup() {
        signer = new PrivateKeyTransactionSigner("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
        tx = new LegacyTransaction(
                0,
                Wei.of(1000000000),
                21000,
                new Address("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"),
                Wei.of(1),
                HexData.EMPTY
        );
    }

    @Benchmark
    public String signLegacy() {
        return signer.sign(tx, 31337);
    }
}
