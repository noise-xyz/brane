// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.benchmark;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import sh.brane.core.abi.Abi;
import sh.brane.core.abi.FastAbiEncoder;
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

    /** Tuple input array containing (address, uint256, bytes) for encoding. */
    public Object[] tupleInput;

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

        // Tuple input: (address, uint256, bytes)
        // Reuse testAddress, testAmount, and testBytesData for consistency
        tupleInput = new Object[] {testAddress, testAmount, testBytesData};
    }

    /**
     * Benchmarks encoding a uint256 value (BigInteger) into a pre-allocated ByteBuffer.
     * This is the BASELINE before optimization - measures current allocation patterns.
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
     * This benchmark will use the optimized long overload once Task 2.3 adds
     * FastAbiEncoder.encodeUInt256(long, ByteBuffer).
     *
     * <p>TODO: Replace with FastAbiEncoder.encodeUInt256(uint256LongTestValue, uint256Buffer)
     * once the long overload is added in Task 2.3.
     *
     * @return the ByteBuffer after encoding (for blackhole consumption)
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public ByteBuffer encodeUint256Long() {
        uint256Buffer.clear();
        // TODO: Replace with FastAbiEncoder.encodeUInt256(uint256LongTestValue, uint256Buffer)
        // once Task 2.3 adds the long overload. Currently using BigInteger.valueOf() as placeholder.
        FastAbiEncoder.encodeUInt256(BigInteger.valueOf(uint256LongTestValue), uint256Buffer);
        return uint256Buffer;
    }

    /**
     * Benchmarks ABI encoding of a transfer function call with address and uint256 arguments.
     * This is the BASELINE before optimization - measures allocation patterns in Abi.encodeFunction.
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
     * @return the encoded function calldata as HexData (for blackhole consumption)
     */
    @Benchmark
    @BenchmarkMode(Mode.AverageTime)
    public Object encodeTuple() {
        return tupleAbi.encodeFunction("processStruct", tupleInput);
    }
}
