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

    private Abi complexAbi;
    private java.util.List<Object> complexData;
    private HexData encodedComplex;
    private Abi abi;
    private BigInteger supply;

    @Setup
    public void setup() {
        // Simple ABI
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

        // Complex ABI (Nested Tuples)
        // Struct Inner { uint256 a; string b; }
        // Struct Outer { Inner[] inners; bytes32 id; }
        // function processNested(Outer memory outer)
        String complexJson = """
            [
              {
                "inputs": [
                  {
                    "components": [
                      {
                        "components": [
                          {"internalType": "uint256", "name": "a", "type": "uint256"},
                          {"internalType": "string", "name": "b", "type": "string"}
                        ],
                        "internalType": "struct Inner[]",
                        "name": "inners",
                        "type": "tuple[]"
                      },
                      {"internalType": "bytes32", "name": "id", "type": "bytes32"}
                    ],
                    "internalType": "struct Outer",
                    "name": "outer",
                    "type": "tuple"
                  }
                ],
                "name": "processNested",
                "outputs": [],
                "stateMutability": "pure",
                "type": "function"
              }
            ]
            """;
        complexAbi = Abi.fromJson(complexJson);

        // Prepare Complex Data
        java.util.List<Object> inner1 = java.util.List.of(BigInteger.ONE, "inner1");
        java.util.List<Object> inner2 = java.util.List.of(BigInteger.TWO, "inner2");
        java.util.List<java.util.List<Object>> inners = java.util.List.of(inner1, inner2);
        
        byte[] idBytes = new byte[32];
        idBytes[0] = (byte) 0xAB;
        HexData id = new HexData(io.brane.primitives.Hex.encode(idBytes));
        
        complexData = java.util.List.of(java.util.List.of(inners, id));
        
        // Pre-encode for decoding benchmark
        String hex = complexAbi.encodeFunction("processNested", complexData.toArray()).data();
        encodedComplex = new HexData(hex);
    }

    @Benchmark
    public HexData encodeConstructor() {
        return abi.encodeConstructor(supply);
    }

    @Benchmark
    public String encodeComplex() {
        return complexAbi.encodeFunction("processNested", complexData.toArray()).data();
    }

    @Benchmark
    public java.util.List<Object> decodeComplex() {
        return complexAbi.encodeFunction("processNested", complexData.toArray())
            .decode(encodedComplex.value(), java.util.List.class);
    }
}
