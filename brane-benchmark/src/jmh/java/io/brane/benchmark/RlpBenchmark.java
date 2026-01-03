package io.brane.benchmark;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.openjdk.jmh.annotations.*;

import io.brane.primitives.rlp.Rlp;
import io.brane.primitives.rlp.RlpList;
import io.brane.primitives.rlp.RlpString;

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
