// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.benchmark;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import sh.brane.core.abi.Abi;
import sh.brane.core.abi.AbiDecoder;
import sh.brane.core.abi.AbiType;
import sh.brane.core.abi.FastAbiEncoder;
import sh.brane.core.abi.TypeSchema;
import sh.brane.core.types.Address;
import sh.brane.core.types.HexData;
import sh.brane.primitives.Hex;

/**
 * JMH benchmark for measuring allocation patterns in ABI encoding/decoding operations.
 *
 * <p>This benchmark provides a foundation for tracking object allocations
 * in ABI operations like encoding function calls and decoding return values.
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
public class AbiAllocationBenchmark {

    /** Standard ERC20 ABI with transfer and balanceOf functions. */
    private static final String ERC20_ABI_JSON = """
            [
              {
                "inputs": [
                  { "internalType": "address", "name": "to", "type": "address" },
                  { "internalType": "uint256", "name": "amount", "type": "uint256" }
                ],
                "name": "transfer",
                "outputs": [{ "internalType": "bool", "name": "", "type": "bool" }],
                "stateMutability": "nonpayable",
                "type": "function"
              },
              {
                "inputs": [{ "internalType": "address", "name": "account", "type": "address" }],
                "name": "balanceOf",
                "outputs": [{ "internalType": "uint256", "name": "", "type": "uint256" }],
                "stateMutability": "view",
                "type": "function"
              }
            ]
            """;

    /** ABI with a function that takes only a uint256 as input. */
    private static final String UINT256_ABI_JSON = """
            [
              {
                "inputs": [{ "internalType": "uint256", "name": "value", "type": "uint256" }],
                "name": "setValue",
                "outputs": [],
                "stateMutability": "nonpayable",
                "type": "function"
              }
            ]
            """;

    /** ABI with a function that takes dynamic bytes as input. */
    private static final String BYTES_ABI_JSON = """
            [
              {
                "inputs": [{ "internalType": "bytes", "name": "data", "type": "bytes" }],
                "name": "setData",
                "outputs": [],
                "stateMutability": "nonpayable",
                "type": "function"
              }
            ]
            """;

    /** ABI with a function that takes a tuple (struct) with multiple fields. */
    private static final String TUPLE_ABI_JSON = """
            [
              {
                "inputs": [
                  {
                    "components": [
                      { "internalType": "address", "name": "addr", "type": "address" },
                      { "internalType": "uint256", "name": "amount", "type": "uint256" },
                      { "internalType": "bytes", "name": "data", "type": "bytes" }
                    ],
                    "internalType": "struct MyStruct",
                    "name": "input",
                    "type": "tuple"
                  }
                ],
                "name": "processStruct",
                "outputs": [],
                "stateMutability": "nonpayable",
                "type": "function"
              }
            ]
            """;

    /**
     * ABI with a complex nested struct containing arrays and nested tuples.
     * Structure:
     * - addresses: address[] (dynamic array of addresses)
     * - amounts: uint256[] (dynamic array of uint256 - using dynamic since fixed-size
     *   arrays like uint256[3] are not yet supported by InternalAbi parser)
     * - nestedData: tuple (nested struct with address, uint256, bytes)
     * - metadata: bytes (dynamic bytes)
     */
    private static final String COMPLEX_NESTED_ABI_JSON = """
            [
              {
                "inputs": [
                  {
                    "components": [
                      { "internalType": "address[]", "name": "addresses", "type": "address[]" },
                      { "internalType": "uint256[]", "name": "amounts", "type": "uint256[]" },
                      {
                        "components": [
                          { "internalType": "address", "name": "addr", "type": "address" },
                          { "internalType": "uint256", "name": "amount", "type": "uint256" },
                          { "internalType": "bytes", "name": "data", "type": "bytes" }
                        ],
                        "internalType": "struct InnerStruct",
                        "name": "nestedData",
                        "type": "tuple"
                      },
                      { "internalType": "bytes", "name": "metadata", "type": "bytes" }
                    ],
                    "internalType": "struct ComplexStruct",
                    "name": "input",
                    "type": "tuple"
                  }
                ],
                "name": "processComplex",
                "outputs": [],
                "stateMutability": "nonpayable",
                "type": "function"
              }
            ]
            """;

    /** ABI instance for ERC20 encoding/decoding. */
    public Abi abi;

    /** Test address for encoding benchmarks. */
    public Address testAddress;

    /** Test amount for encoding benchmarks. */
    public BigInteger testAmount;

    /** Pre-encoded bytes for decoding tests (transfer function calldata). */
    public byte[] preEncodedBytes;

    /** Pre-allocated ByteBuffer for encoding tests (68 bytes for transfer calldata). */
    public ByteBuffer encodingBuffer;

    /** Pre-allocated ByteBuffer for uint256 encoding tests (32 bytes). */
    public ByteBuffer uint256Buffer;

    /** Test value for uint256 encoding benchmarks. */
    public BigInteger uint256TestValue;

    /** Test value for uint256 long encoding benchmarks. */
    public long uint256LongTestValue;

    /** ABI instance for bytes encoding. */
    public Abi bytesAbi;

    /** Test bytes data for encoding benchmarks (64 bytes of data). */
    public HexData testBytesData;

    /** Pre-allocated ByteBuffer for bytes encoding tests. */
    public ByteBuffer bytesBuffer;

    /** ABI instance for tuple encoding. */
    public Abi tupleAbi;

    /** ABI instance for uint256 encoding via InternalAbi path. */
    public Abi uint256Abi;

    /** Tuple input array containing (address, uint256, bytes) for encoding. */
    public Object[] tupleInput;

    /** Pre-encoded uint256 bytes for decoding benchmarks (32 bytes). */
    public byte[] preEncodedUint256;

    /** Schema for decoding a single uint256. */
    public List<TypeSchema> uint256Schema;

    /** ABI instance for complex nested struct encoding. */
    public Abi complexNestedAbi;

    /** Complex nested input containing (address[], uint256[3], nested tuple, bytes). */
    public Object[] complexNestedInput;

    @Setup(Level.Trial)
    public void setup() {
        // Initialize ABI from ERC20 JSON
        abi = Abi.fromJson(ERC20_ABI_JSON);

        // Test address
        testAddress = new Address("0x1234567890123456789012345678901234567890");

        // Test amount: 1 token with 18 decimals (1e18)
        testAmount = new BigInteger("1000000000000000000");

        // Pre-encode transfer(address,uint256) calldata for decoding benchmarks
        // transfer calldata = 4 byte selector + 32 byte address + 32 byte amount = 68 bytes
        String encodedHex = abi.encodeFunction("transfer", testAddress, testAmount).data();
        preEncodedBytes = Hex.decode(encodedHex);

        // Pre-allocate ByteBuffer for encoding tests
        // Size: 4 (selector) + 32 (address) + 32 (amount) = 68 bytes
        encodingBuffer = ByteBuffer.allocate(68);

        // Pre-allocate ByteBuffer for uint256 encoding (32 bytes)
        uint256Buffer = ByteBuffer.allocate(32);

        // Test value for uint256 encoding: 1,000,000
        uint256TestValue = BigInteger.valueOf(1000000);

        // Test value for uint256 long encoding: 1,000,000
        uint256LongTestValue = 1000000L;

        // Initialize bytes ABI
        bytesAbi = Abi.fromJson(BYTES_ABI_JSON);

        // Test bytes data: 64 bytes of data (a typical payload size)
        testBytesData = new HexData("0x" + "ab".repeat(64));

        // Pre-allocate ByteBuffer for bytes encoding tests
        // Size: 4 (selector) + 32 (offset) + 32 (length) + 64 (data padded to 64) = 132 bytes
        bytesBuffer = ByteBuffer.allocate(132);

        // Initialize tuple ABI
        tupleAbi = Abi.fromJson(TUPLE_ABI_JSON);

        // Initialize uint256 ABI for InternalAbi path benchmarking
        uint256Abi = Abi.fromJson(UINT256_ABI_JSON);

        // Tuple input: (address, uint256, bytes)
        // Reuse testAddress, testAmount, and testBytesData for consistency
        tupleInput = new Object[] {testAddress, testAmount, testBytesData};

        // Pre-encode a uint256 value for decoding benchmarks
        // ABI encoding of uint256 is simply the value left-padded to 32 bytes
        ByteBuffer encodeBuffer = ByteBuffer.allocate(32);
        FastAbiEncoder.encodeUInt256(uint256TestValue, encodeBuffer);
        preEncodedUint256 = encodeBuffer.array();

        // Schema for decoding a single uint256
        uint256Schema = List.of(new TypeSchema.UIntSchema(256));

        // Initialize complex nested ABI
        complexNestedAbi = Abi.fromJson(COMPLEX_NESTED_ABI_JSON);

        // Build the complex nested input:
        // - addresses: address[] (3 addresses)
        // - amounts: uint256[3] (3 amounts)
        // - nestedData: tuple (address, uint256, bytes)
        // - metadata: bytes
        Address[] addresses = new Address[] {
            testAddress,
            new Address("0xabcdefabcdefabcdefabcdefabcdefabcdefabcd"),
            new Address("0x9876543210987654321098765432109876543210")
        };
        BigInteger[] amounts = new BigInteger[] {
            testAmount,
            BigInteger.valueOf(2000000),
            BigInteger.valueOf(3000000)
        };
        Object[] nestedTuple = new Object[] {testAddress, testAmount, testBytesData};
        HexData metadata = new HexData("0x" + "ff".repeat(32));
        complexNestedInput = new Object[] {addresses, amounts, nestedTuple, metadata};
    }

    /**
     * Benchmarks encoding a uint256 value (BigInteger) into a pre-allocated ByteBuffer.
     * This is the BASELINE before optimization - measures current allocation patterns.
     *
     * <p><b>Baseline:</b> ~0 B/op (JDK 21.0.9, G1GC) - pre-allocated buffer avoids allocations
     *
     * @return the ByteBuffer after encoding (for blackhole consumption)
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public ByteBuffer encodeUint256BigInteger() {
        uint256Buffer.clear();
        FastAbiEncoder.encodeUInt256(uint256TestValue, uint256Buffer);
        return uint256Buffer;
    }

    /**
     * Benchmarks encoding a uint256 value (long) into a pre-allocated ByteBuffer.
     * Uses the optimized primitive long overload for zero-allocation encoding.
     *
     * <p><b>Baseline:</b> ~0 B/op (JDK 21.0.9, G1GC) - primitive path avoids BigInteger boxing
     *
     * @return the ByteBuffer after encoding (for blackhole consumption)
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public ByteBuffer encodeUint256Long() {
        uint256Buffer.clear();
        FastAbiEncoder.encodeUint256(uint256LongTestValue, uint256Buffer);
        return uint256Buffer;
    }

    /**
     * Benchmarks encoding a uint256 value via the InternalAbi path (Abi.encodeFunction).
     * This provides a comparison against the direct FastAbiEncoder.encodeUInt256 benchmark
     * to measure the overhead of the full ABI encoding pipeline for uint256 values.
     *
     * <p><b>Baseline:</b> 728 B/op (JDK 21.0.9, G1GC)
     *
     * @return the encoded function calldata as HexData (for blackhole consumption)
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object encodeUint256Internal() {
        return uint256Abi.encodeFunction("setValue", uint256TestValue);
    }

    /**
     * Benchmarks ABI encoding of a transfer function call with address and uint256 arguments.
     * This is the BASELINE before optimization - measures allocation patterns in Abi.encodeFunction.
     *
     * <p><b>Baseline:</b> 936 B/op (JDK 21.0.9, G1GC)
     *
     * @return the encoded function calldata as HexData (for blackhole consumption)
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object encodeAddressAbi() {
        return abi.encodeFunction("transfer", testAddress, testAmount);
    }

    /**
     * Benchmarks ABI encoding of a function call with dynamic bytes argument.
     * This is the BASELINE before optimization - measures allocation patterns for dynamic types.
     *
     * <p><b>Baseline:</b> 1,216 B/op (JDK 21.0.9, G1GC)
     *
     * @return the encoded function calldata as HexData (for blackhole consumption)
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object encodeBytes() {
        return bytesAbi.encodeFunction("setData", testBytesData);
    }

    /**
     * Benchmarks ABI encoding of a function call with a tuple (struct) argument.
     * The tuple contains multiple fields: address, uint256, and bytes.
     * This is the BASELINE before optimization - measures allocation patterns for tuple types.
     *
     * <p><b>Baseline:</b> 2,176 B/op (JDK 21.0.9, G1GC)
     *
     * @return the encoded function calldata as HexData (for blackhole consumption)
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object encodeTuple() {
        // Cast to Object to pass the array as a single tuple argument, not varargs
        return tupleAbi.encodeFunction("processStruct", (Object) tupleInput);
    }

    /**
     * Benchmarks ABI decoding of a pre-encoded uint256 value.
     * This is the BASELINE before optimization - measures allocation patterns in AbiDecoder.
     *
     * <p><b>Baseline:</b> 184 B/op (JDK 21.0.9, G1GC)
     *
     * @return the decoded ABI types list (for blackhole consumption)
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public List<AbiType> decodeUint256() {
        return AbiDecoder.decode(preEncodedUint256, uint256Schema);
    }

    /**
     * Benchmarks ABI encoding of a complex nested struct containing arrays and nested tuples.
     * The struct contains:
     * - address[] (dynamic array of 3 addresses)
     * - uint256[] (dynamic array of 3 uint256)
     * - tuple (nested struct with address, uint256, bytes)
     * - bytes (dynamic bytes metadata)
     *
     * <p>This is the BASELINE before optimization - measures allocation patterns
     * for complex nested types combining dynamic arrays and nested tuples.
     *
     * <p><b>Baseline:</b> 5,280 B/op (JDK 21.0.9, G1GC)
     *
     * @return the encoded function calldata as HexData (for blackhole consumption)
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object encodeComplexNested() {
        // Cast to Object to pass the array as a single tuple argument, not varargs
        return complexNestedAbi.encodeFunction("processComplex", (Object) complexNestedInput);
    }
}
