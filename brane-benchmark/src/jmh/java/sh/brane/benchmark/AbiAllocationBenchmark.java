// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.benchmark;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import sh.brane.core.abi.Abi;
import sh.brane.core.types.Address;
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
    }
}
