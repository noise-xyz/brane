// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.benchmark;

import java.math.BigInteger;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import sh.brane.core.abi.Abi;
import sh.brane.core.abi.AbiDecoder;
import sh.brane.core.abi.AbiType;
import sh.brane.core.abi.TypeSchema;
import sh.brane.core.abi.TypeSchema.ArraySchema;
import sh.brane.core.abi.TypeSchema.BytesSchema;
import sh.brane.core.abi.TypeSchema.StringSchema;
import sh.brane.core.abi.TypeSchema.TupleSchema;
import sh.brane.core.abi.TypeSchema.UIntSchema;
import sh.brane.core.types.HexData;

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
    private List<Object> complexData;
    private HexData encodedComplex;
    private Abi abi;
    private BigInteger supply;
    private Object[] complexArgs;

    private List<TypeSchema> schemas;

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
        List<Object> inner1 = List.of(BigInteger.ONE, "inner1");
        List<Object> inner2 = List.of(BigInteger.TWO, "inner2");
        List<List<Object>> inners = List.of(inner1, inner2);

        byte[] idBytes = new byte[32];
        idBytes[0] = (byte) 0xAB;
        HexData id = HexData.fromBytes(idBytes);

        complexData = List.of(List.of(inners, id));
        complexArgs = complexData.toArray();

        // Pre-encode for decoding benchmark
        // Strip selector to get raw argument data
        String hex = complexAbi.encodeFunction("processNested", complexArgs).data();
        String argsHex = "0x" + hex.substring(10);
        encodedComplex = new HexData(argsHex);

        // Prepare schemas for decoding
        // (Outer) -> tuple(Inner[] inners, bytes32 id)
        // Inner -> tuple(uint256 a, string b)
        schemas = List.of(
            new TupleSchema(List.of(
                new ArraySchema(
                    new TupleSchema(List.of(
                        new UIntSchema(256),
                        new StringSchema()
                    )),
                    -1 // dynamic array
                ),
                new BytesSchema(32) // bytes32
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
    public List<AbiType> decodeComplex() {
        return AbiDecoder.decode(encodedComplex.toBytes(), schemas);
    }
}
