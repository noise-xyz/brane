// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.benchmark;

import java.math.BigInteger;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import io.brane.core.abi.Abi;
import io.brane.core.types.HexData;

/**
 * JMH benchmark for Brane SDK's ABI encoding and decoding performance.
 *
 * <p>Measures throughput (ops/sec) for:
 * <ul>
 *   <li>{@code encodeConstructor} - simple uint256 constructor encoding</li>
 *   <li>{@code encodeComplex} - nested tuple/array encoding (struct with tuple[])</li>
 *   <li>{@code decodeComplex} - decoding complex nested structures</li>
 * </ul>
 *
 * <p>Compare with {@link Web3jAbiBenchmark} for web3j baseline.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class AbiBenchmark {

    private Abi complexAbi;
    private java.util.List<Object> complexData;
    private HexData encodedComplex;
    private Abi abi;
    private BigInteger supply;
    private Object[] complexArgs;

    private java.util.List<io.brane.core.abi.TypeSchema> schemas;

    @Setup
    public void setup() {
        // ... existing setup ...
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
        HexData id = HexData.fromBytes(idBytes);

        complexData = java.util.List.of(java.util.List.of(inners, id));
        complexArgs = complexData.toArray();

        // Pre-encode for decoding benchmark
        // Strip selector to get raw argument data
        String hex = complexAbi.encodeFunction("processNested", complexArgs).data();
        String argsHex = "0x" + hex.substring(10);
        encodedComplex = new HexData(argsHex);

        // Prepare schemas for decoding
        // (Outer) -> tuple(Inner[] inners, bytes32 id)
        // Inner -> tuple(uint256 a, string b)
        schemas = java.util.List.of(
            new io.brane.core.abi.TypeSchema.TupleSchema(java.util.List.of(
                new io.brane.core.abi.TypeSchema.ArraySchema(
                    new io.brane.core.abi.TypeSchema.TupleSchema(java.util.List.of(
                        new io.brane.core.abi.TypeSchema.UIntSchema(256),
                        new io.brane.core.abi.TypeSchema.StringSchema()
                    )),
                    -1 // dynamic array
                ),
                new io.brane.core.abi.TypeSchema.BytesSchema(32) // bytes32
            ))
        );
    }

    @Benchmark
    public HexData encodeConstructor() {
        return abi.encodeConstructor(supply);
    }

    @Benchmark
    public String encodeComplex() {
        return complexAbi.encodeFunction("processNested", complexArgs).data();
    }

    @Benchmark
    public java.util.List<io.brane.core.abi.AbiType> decodeComplex() {
        return io.brane.core.abi.AbiDecoder.decode(encodedComplex.toBytes(), schemas);
    }
}
