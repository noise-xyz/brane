package io.brane.benchmark;

import io.brane.contract.Abi;
import io.brane.core.types.HexData;
import org.openjdk.jmh.annotations.*;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class AbiBenchmark {

    private Abi abi;
    private BigInteger supply;

    @Setup
    public void setup() {
        String json = """
            [
              {
                "inputs": [{"internalType": "uint256", "name": "initialSupply", "type": "uint256"}],
                "stateMutability": "nonpayable",
                "type": "constructor"
              }
            ]
            """;
        abi = Abi.fromJson(json);
        supply = new BigInteger("1000000000000000000");
    }

    @Benchmark
    public HexData encodeConstructor() {
        return abi.encodeConstructor(supply);
    }
}
