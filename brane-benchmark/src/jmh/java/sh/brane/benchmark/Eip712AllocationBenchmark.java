// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.benchmark;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import sh.brane.core.crypto.eip712.Eip712Domain;
import sh.brane.core.crypto.eip712.TypeDefinition;
import sh.brane.core.crypto.eip712.TypedData;
import sh.brane.core.crypto.eip712.TypedDataField;
import sh.brane.core.types.Address;
import sh.brane.core.types.Hash;

/**
 * JMH benchmark for measuring allocation patterns in EIP-712 typed data hashing.
 *
 * <p>Measures the allocation cost of computing EIP-712 struct hashes,
 * including domain separator computation, type encoding, and data encoding.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 2, time = 1)
@Measurement(iterations = 3, time = 1)
@Fork(1)
public class Eip712AllocationBenchmark {

    // ═══════════════════════════════════════════════════════════════
    // Simple struct: Permit (5 fields, no nesting)
    // ═══════════════════════════════════════════════════════════════

    record Permit(Address owner, Address spender, BigInteger value, BigInteger nonce, BigInteger deadline) {}

    private static final Map<String, List<TypedDataField>> PERMIT_TYPES = Map.of(
        "Permit", List.of(
            TypedDataField.of("owner", "address"),
            TypedDataField.of("spender", "address"),
            TypedDataField.of("value", "uint256"),
            TypedDataField.of("nonce", "uint256"),
            TypedDataField.of("deadline", "uint256")
        )
    );

    private static final TypeDefinition<Permit> PERMIT_DEFINITION =
        TypeDefinition.forRecord(Permit.class, "Permit", PERMIT_TYPES);

    // ═══════════════════════════════════════════════════════════════
    // Nested struct: Mail with Person sub-struct
    // ═══════════════════════════════════════════════════════════════

    record Person(String name, Address wallet) {}
    record Mail(Person from, Person to, String contents) {}

    private static final Map<String, List<TypedDataField>> MAIL_TYPES = Map.of(
        "Mail", List.of(
            TypedDataField.of("from", "Person"),
            TypedDataField.of("to", "Person"),
            TypedDataField.of("contents", "string")
        ),
        "Person", List.of(
            TypedDataField.of("name", "string"),
            TypedDataField.of("wallet", "address")
        )
    );

    @SuppressWarnings("unchecked")
    private static final TypeDefinition<Mail> MAIL_DEFINITION =
        new TypeDefinition<>(
            "Mail",
            MAIL_TYPES,
            mail -> Map.of(
                "from", (Object) Map.of(
                    "name", mail.from().name(),
                    "wallet", mail.from().wallet()
                ),
                "to", (Object) Map.of(
                    "name", mail.to().name(),
                    "wallet", mail.to().wallet()
                ),
                "contents", mail.contents()
            )
        );

    // ═══════════════════════════════════════════════════════════════
    // Test data
    // ═══════════════════════════════════════════════════════════════

    private TypedData<Permit> simpleTypedData;
    private TypedData<Mail> nestedTypedData;

    @Setup(Level.Trial)
    public void setup() {
        var domain = Eip712Domain.builder()
            .name("TestDapp")
            .version("1")
            .chainId(1)
            .verifyingContract(new Address("0x1234567890123456789012345678901234567890"))
            .build();

        var permit = new Permit(
            new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"),
            new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"),
            BigInteger.valueOf(1000000000000000000L),
            BigInteger.ZERO,
            BigInteger.valueOf(1234567890)
        );
        simpleTypedData = TypedData.create(domain, PERMIT_DEFINITION, permit);

        var mail = new Mail(
            new Person("Alice", new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa")),
            new Person("Bob", new Address("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb")),
            "Hello, Bob!"
        );
        nestedTypedData = TypedData.create(domain, MAIL_DEFINITION, mail);
    }

    /**
     * Benchmarks hashing a simple struct (Permit — 5 flat fields, no nesting).
     * Measures allocation from TypedDataEncoder.encodeData + typeHash + hashStruct.
     *
     * @return the EIP-712 hash (for blackhole consumption)
     */
    @Benchmark
    public Hash hashSimpleStruct() {
        return simpleTypedData.hash();
    }

    /**
     * Benchmarks hashing a nested struct (Mail with Person sub-structs).
     * Measures additional allocation from recursive struct encoding.
     *
     * @return the EIP-712 hash (for blackhole consumption)
     */
    @Benchmark
    public Hash hashNestedStruct() {
        return nestedTypedData.hash();
    }
}
