package io.brane.benchmark;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;
import org.web3j.abi.TypeEncoder;
import org.web3j.abi.datatypes.DynamicArray;
import org.web3j.abi.datatypes.DynamicStruct;
import org.web3j.abi.datatypes.Utf8String;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;

@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class Web3jAbiBenchmark {

    private Uint256 supply;
    private Outer complexData;

    @Setup
    public void setup() {
        supply = new Uint256(new BigInteger("1000000000000000000"));

        // Complex Data Construction
        // Struct Inner { uint256 a; string b; }
        Inner inner1 = new Inner(new Uint256(BigInteger.ONE), new Utf8String("inner1"));
        Inner inner2 = new Inner(new Uint256(BigInteger.TWO), new Utf8String("inner2"));

        // Struct Outer { Inner[] inners; bytes32 id; }
        byte[] idBytes = new byte[32];
        idBytes[0] = (byte) 0xAB;
        Bytes32 id = new Bytes32(idBytes);

        complexData = new Outer(new DynamicArray<>(Inner.class, Arrays.asList(inner1, inner2)), id);

    }

    @Benchmark
    public String encodeConstructor() {
        return TypeEncoder.encode(supply);
    }

    @Benchmark
    public String encodeComplex() {
        return TypeEncoder.encode(complexData);
    }

    // Web3j doesn't have a direct "decode struct" method without using the
    // generated wrapper's logic
    // or FunctionReturnDecoder. We'll simulate what FunctionReturnDecoder does for
    // a list of types.
    // However, decoding a single struct directly isn't standard in Web3j's public
    // API as easily as encoding.
    // We will skip decodeComplex for Web3j if it's too convoluted to match exactly,
    // or we can use TypeDecoder for individual fields if we treated it as a
    // function return.
    // For now, let's stick to encoding for direct comparison, or try to use
    // TypeDecoder.

    // Implementing struct classes manually
    public static class Inner extends DynamicStruct {
        public Inner(Uint256 a, Utf8String b) {
            super(a, b);
        }
    }

    public static class Outer extends DynamicStruct {
        public Outer(DynamicArray<Inner> inners, Bytes32 id) {
            super(inners, id);
        }
    }
}
