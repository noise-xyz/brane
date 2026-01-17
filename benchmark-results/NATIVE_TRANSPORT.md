# Native Transport Benchmark Results

## Overview

This document records the benchmark results comparing NIO (baseline) vs native transport (kqueue on macOS) for the Brane SDK WebSocket provider.

## Test Environment

- **CPU:** Apple Silicon (arm64)
- **OS:** macOS (Darwin 24.6.0)
- **Java:** JDK 21.0.9, OpenJDK 64-Bit Server VM
- **JVM Args:** -Xms512m -Xmx512m -XX:+UseG1GC
- **Anvil:** local (127.0.0.1:8545)

## Benchmark Configuration

- **Warmup:** 3 iterations, 3s each
- **Measurement:** 5 iterations, 3s each
- **Forks:** 1
- **Threads:** 1 (single-threaded synchronous)

## Results

### Throughput: `brane_throughput_chainId`

| Metric | Baseline (NIO) | Native (kqueue) | Change |
|--------|----------------|-----------------|--------|
| **Throughput** | 25,235 ops/s | **27,918 ops/s** | **+10.6%** |
| Score Error (99.9%) | ± 1,665 (6.6%) | ± 714 (2.6%) | Lower variance |
| Min | 24,742 ops/s | 27,710 ops/s | +12.0% |
| Max | 25,714 ops/s | 28,181 ops/s | +9.6% |
| Stdev | 432 | 185 | -57% |

### Summary

Native transport (kqueue on macOS) provides:

1. **+10.6% throughput improvement** - From 25,235 to 27,918 ops/s
2. **Lower variance** - Score error reduced from ±6.6% to ±2.6%
3. **More consistent performance** - Standard deviation reduced by 57%

### Raw Data

**Baseline (NIO) - from BASELINE.md:**
```
Benchmark                                     Mode  Cnt      Score     Error  Units
WebSocketBenchmark.brane_throughput_chainId  thrpt    5  25235.000 ± 1665.000  ops/s
```

**After Native Transport (kqueue):**
```
Benchmark                                     Mode  Cnt      Score     Error  Units
WebSocketBenchmark.brane_throughput_chainId  thrpt    5  27918.272 ± 713.635  ops/s
```

Individual iteration scores (native):
- Iteration 1: 28,026.890 ops/s
- Iteration 2: 27,710.235 ops/s
- Iteration 3: 28,180.519 ops/s
- Iteration 4: 27,848.683 ops/s
- Iteration 5: 27,825.033 ops/s

## Commands Used

**Baseline:**
```bash
./gradlew :brane-benchmark:jmh -Pjmh.includes="brane_throughput_chainId" -Pjmh.wi=3 -Pjmh.i=5 -Pjmh.f=1
```

**Native Transport (same command, auto-detects kqueue on macOS):**
```bash
./gradlew :brane-benchmark:jmh -Pjmh.includes="brane_throughput_chainId" -Pjmh.wi=3 -Pjmh.i=5 -Pjmh.f=1
```

## Notes

- Baseline was measured before commits T1-1 through T1-5 added native transport support
- Native transport is auto-detected via `TransportType.AUTO` (the default)
- On macOS/BSD: uses kqueue
- On Linux: uses epoll
- Fallback: NIO (when natives unavailable)

## Conclusion

The native transport implementation delivers a meaningful **10.6% throughput improvement** with **significantly lower variance**, making it a clear win for performance-sensitive applications. The improvement comes from reduced syscall overhead and zero-copy I/O operations provided by the platform-specific transport implementations.
