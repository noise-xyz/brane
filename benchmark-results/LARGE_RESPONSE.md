# Large Response Benchmark Results - 2026-01-17

## Overview

This benchmark measures the performance of parsing large `eth_getLogs` style JSON-RPC responses with varying numbers of log entries. It establishes a baseline for the current 64KB `HttpObjectAggregator` limit and measures the parsing pipeline performance.

## Hardware

- **CPU:** Apple Silicon (arm64)
- **OS:** macOS (Darwin 24.6.0)
- **Java:** JDK 21.0.9, OpenJDK 64-Bit Server VM
- **JVM Args:** -Xms512m -Xmx512m -XX:+UseG1GC

## Benchmark Configuration

- **Warmup:** 3 iterations, 1s each
- **Measurement:** 5 iterations, 2s each
- **Forks:** 2
- **Log Counts:** 100, 1000, 5000 entries
- **Data:** Synthetic ERC-20 Transfer event logs (~600 bytes each)

---

## Response Size Analysis

| Log Count | Approx JSON Size | Exceeds 64KB Limit? |
|-----------|------------------|---------------------|
| 100 | ~58 KB | No |
| 1000 | ~585 KB | **Yes (9x limit)** |
| 5000 | ~2.9 MB | **Yes (46x limit)** |

**Current `HttpObjectAggregator` limit:** 65,536 bytes (64 KB)

This limit would cause failures for responses with ~110+ log entries. Real-world `eth_getLogs` queries for active contracts can easily return 1000+ entries.

---

## Benchmark Results

### Latency (Average Time per Operation)

**Command:** `./gradlew :brane-benchmark:jmh -Pjmh.includes="LargeResponseBenchmark" -Pjmh.wi=3 -Pjmh.i=5 -Pjmh.f=2`

#### Full Parse Pipeline (JSON + LogEntry creation)

| Benchmark | 100 logs | 1000 logs | 5000 logs |
|-----------|----------|-----------|-----------|
| **brane_fullParse_logs** | 0.258 ± 0.011 ms | 2.454 ± 0.040 ms | 12.301 ± 0.130 ms |
| jackson_fullParse_logs | 0.050 ± 0.001 ms | 0.431 ± 0.010 ms | 2.142 ± 0.021 ms |

#### LogParser Only (pre-parsed JSON input)

| Benchmark | 100 logs | 1000 logs | 5000 logs |
|-----------|----------|-----------|-----------|
| **brane_logParserOnly** | 0.201 ± 0.004 ms | 1.999 ± 0.026 ms | 10.349 ± 0.204 ms |
| jackson_rawMapAccess | ~0.001 ms | 0.005 ± 0.001 ms | 0.042 ± 0.001 ms |

### Throughput (ops/sec)

| Benchmark | 100 logs | 1000 logs | 5000 logs |
|-----------|----------|-----------|-----------|
| **brane_throughput_fullParse** | 3,960 ± 114 | 404 ± 5 | 80.6 ± 3.8 |
| **brane_throughput_logParserOnly** | 5,038 ± 43 | 506 ± 16 | 98.8 ± 2.5 |

---

## Analysis

### Performance Characteristics

1. **Linear scaling:** Parse time scales linearly with log count (~2.5 μs per log entry)
2. **LogParser overhead:** ~80% of total parse time is in `LogParser.parseLogs()` creating type-safe `LogEntry` records
3. **Jackson baseline:** Raw Jackson parsing is 5-6x faster but returns untyped `Map<String, Object>`

### LogEntry Creation Cost

The benchmark reveals that creating type-safe `LogEntry` records from raw JSON has significant overhead:

| Log Count | Jackson (raw maps) | Brane (LogEntry records) | Overhead |
|-----------|--------------------|--------------------------| ---------|
| 100 | 0.050 ms | 0.258 ms | **5.2x** |
| 1000 | 0.431 ms | 2.454 ms | **5.7x** |
| 5000 | 2.142 ms | 12.301 ms | **5.7x** |

This overhead is the cost of type safety: converting hex strings to `Address`, `Hash`, `HexData`, parsing topics arrays, etc.

### Implications for HttpObjectAggregator Limit

The current 64KB limit is insufficient for real-world `eth_getLogs` usage:

- **100 logs (~58KB):** Just under the limit - fragile
- **1000 logs (~585KB):** Would fail with current limit
- **5000 logs (~2.9MB):** Would fail with current limit

**Recommendation:** Increase `HttpObjectAggregator` limit to at least 4MB (4,194,304 bytes) to support production use cases, or implement streaming JSON parsing.

---

## Raw JMH Output

```
Benchmark                                              (logCount)   Mode  Cnt     Score     Error  Units
LargeResponseBenchmark.brane_throughput_fullParse             100  thrpt   10  3960.004 ± 114.013  ops/s
LargeResponseBenchmark.brane_throughput_fullParse            1000  thrpt   10   403.992 ±   5.247  ops/s
LargeResponseBenchmark.brane_throughput_fullParse            5000  thrpt   10    80.641 ±   3.783  ops/s
LargeResponseBenchmark.brane_throughput_logParserOnly         100  thrpt   10  5038.123 ±  43.333  ops/s
LargeResponseBenchmark.brane_throughput_logParserOnly        1000  thrpt   10   505.643 ±  15.843  ops/s
LargeResponseBenchmark.brane_throughput_logParserOnly        5000  thrpt   10    98.754 ±   2.450  ops/s
LargeResponseBenchmark.brane_fullParse_logs                   100   avgt   10     0.258 ±   0.011  ms/op
LargeResponseBenchmark.brane_fullParse_logs                  1000   avgt   10     2.454 ±   0.040  ms/op
LargeResponseBenchmark.brane_fullParse_logs                  5000   avgt   10    12.301 ±   0.130  ms/op
LargeResponseBenchmark.brane_logParserOnly                    100   avgt   10     0.201 ±   0.004  ms/op
LargeResponseBenchmark.brane_logParserOnly                   1000   avgt   10     1.999 ±   0.026  ms/op
LargeResponseBenchmark.brane_logParserOnly                   5000   avgt   10    10.349 ±   0.204  ms/op
LargeResponseBenchmark.jackson_fullParse_logs                 100   avgt   10     0.050 ±   0.001  ms/op
LargeResponseBenchmark.jackson_fullParse_logs                1000   avgt   10     0.431 ±   0.010  ms/op
LargeResponseBenchmark.jackson_fullParse_logs                5000   avgt   10     2.142 ±   0.021  ms/op
LargeResponseBenchmark.jackson_rawMapAccess                   100   avgt   10    ≈ 10⁻³            ms/op
LargeResponseBenchmark.jackson_rawMapAccess                  1000   avgt   10     0.005 ±   0.001  ms/op
LargeResponseBenchmark.jackson_rawMapAccess                  5000   avgt   10     0.042 ±   0.001  ms/op
```

## Command Used

```bash
./gradlew :brane-benchmark:jmh -Pjmh.includes="LargeResponseBenchmark" -Pjmh.wi=3 -Pjmh.i=5 -Pjmh.f=2
```

## Notes

- Benchmark uses synthetic data generated in-memory, not actual RPC round-trips
- This isolates parsing performance from network latency
- Real-world performance would include network I/O overhead
- The benchmark validates that the code compiles and runs correctly against the current codebase
