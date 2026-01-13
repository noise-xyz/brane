# brane-benchmark

JMH (Java Microbenchmark Harness) benchmarks for performance-critical paths.

## Running Benchmarks

```bash
# Quick benchmark
./gradlew :brane-benchmark:runQuickBenchmark

# Manual benchmark (custom iterations)
./gradlew :brane-benchmark:runManualBenchmark

# WebSocket benchmark
./gradlew :brane-benchmark:runWebSocketBenchmark

# Real-world latency test
./gradlew :brane-benchmark:runRealWorldBenchmark
```

## Benchmark Classes

| Class | Focus |
|-------|-------|
| `ComprehensiveBenchmark` | Full suite (ABI, signing, RPC) |
| `QuickProviderBenchmark` | Provider latency comparison |
| `WebSocketBenchmark` | WebSocket-specific performance |
| `ManualBenchmark` | Manual timing (non-JMH) |
| `RealWorldLatencyTest` | Real-world scenario testing |

## What We Benchmark

- **ABI encoding/decoding**: Throughput for various types
- **Signing**: ECDSA signature generation
- **RPC latency**: HTTP vs WebSocket
- **Gas estimation**: Strategy performance
- **Memory allocation**: GC pressure

## JMH Configuration

```java
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 1)
@Measurement(iterations = 3)
@Fork(1)
```

## Comparing with web3j

Benchmarks include web3j comparison where relevant:
- ABI encoding comparison
- RPC latency comparison
- Memory usage comparison

**Note**: web3j is only used here for benchmarking - it must never leak into main SDK code.

## Adding New Benchmarks

1. Create class in `sh.brane.benchmark`
2. Add JMH annotations
3. Add Gradle task if needed:
   ```groovy
   task runMyBenchmark(type: JavaExec) {
       classpath = sourceSets.main.runtimeClasspath
       mainClass = 'sh.brane.benchmark.MyBenchmark'
   }
   ```

## Dependencies

- JMH 1.37 (with annotation processor)
- web3j 4.10.3 (for comparison only)
- brane-* modules
