// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import io.brane.primitives.rlp.Rlp;
import io.brane.primitives.rlp.RlpList;
import io.brane.primitives.rlp.RlpString;

/**
 * JMH benchmark for Brane SDK's RLP encoding performance.
 *
 * <p>Measures throughput (ops/sec) for RLP encoding:
 * <ul>
 *   <li>{@code encodeSimple} - small list with 2 string items</li>
 *   <li>{@code encodeComplex} - nested list with 100+ items</li>
 * </ul>
 *
 * <p>RLP encoding is used for transaction serialization before signing.
 */
@State(Scope.Thread)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
public class RlpBenchmark {

    private RlpList simpleList;
    private RlpList complexList;

    @Setup
    public void setup() {
        // Simple List: ["hello", "world"]
        simpleList = RlpList.of(
            RlpString.of("hello".getBytes()),
            RlpString.of("world".getBytes())
        );

        // Complex List: Nested lists with many items
        List<io.brane.primitives.rlp.RlpItem> items = new ArrayList<>();
        for (int i = 0; i < 100; i++) {
            items.add(RlpString.of(("item" + i).getBytes()));
        }
        complexList = RlpList.of(
            RlpList.of(items),
            RlpString.of("suffix".getBytes())
        );
    }

    @Benchmark
    public byte[] encodeSimple() {
        return Rlp.encode(simpleList);
    }

    @Benchmark
    public byte[] encodeComplex() {
        return Rlp.encode(complexList);
    }
}
