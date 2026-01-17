# Wait Strategy Benchmark Results

## Overview

This document records the benchmark results comparing different Disruptor wait strategies for the Brane SDK WebSocket provider.

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
- **Benchmark:** `brane_latencyDist_chainId` (SampleTime mode)

## Results

### Latency Distribution: `brane_latencyDist_chainId`

| Percentile | YIELDING (baseline) | BUSY_SPIN | Improvement |
|------------|---------------------|-----------|-------------|
| **p50** | 36.10 μs | **34.18 μs** | **-5.3%** |
| p90 | 47.62 μs | 41.15 μs | -13.6% |
| p95 | 55.23 μs | 48.32 μs | -12.5% |
| **p99** | 74.88 μs | **70.14 μs** | **-6.3%** |
| **p99.9** | 172.03 μs | **165.99 μs** | **-3.5%** |
| p99.99 | 619.63 μs | 769.67 μs | +24.2% |
| Mean | 38.92 μs | 36.25 μs | -6.9% |
| Sample Count | 193,258 | 207,604 | +7.4% |

### Summary

BUSY_SPIN wait strategy provides:

1. **-5.3% p50 latency** - From 36.10 to 34.18 μs
2. **-6.3% p99 latency** - From 74.88 to 70.14 μs
3. **-3.5% p99.9 latency** - From 172.03 to 165.99 μs
4. **Higher throughput** - 7.4% more samples in the same measurement window
5. **Trade-off at p99.99** - Slightly higher tail latency (+24.2%)

### Interpretation

The BUSY_SPIN strategy shows consistent latency improvements at common percentiles (p50-p99.9) but with increased variability at extreme tails (p99.99+). This is expected behavior because:

- BUSY_SPIN consumes 100% of one CPU core, reducing response latency when work is available
- Without dedicated CPU core pinning (taskset/isolcpus), OS scheduler interruptions cause occasional long tails
- For most use cases, the p50-p99.9 improvements are more relevant than p99.99 outliers

**Recommendation:** Use BUSY_SPIN only when:
1. Sub-microsecond latency consistency matters (HFT, MEV)
2. Dedicated CPU cores are available and pinned
3. Higher CPU usage is acceptable

For most applications, YIELDING (default) provides a better trade-off between latency and resource usage.

### Raw Data

**Baseline (YIELDING):**
```
Benchmark                                    Mode     Cnt      Score    Error  Units
WebSocketBenchmark.brane_latencyDist_chainId sample  193258   38.917 ± 0.343  us/op
  ·p0.00                                     sample           24.10          us/op
  ·p0.50                                     sample           36.10          us/op
  ·p0.90                                     sample           47.62          us/op
  ·p0.95                                     sample           55.23          us/op
  ·p0.99                                     sample           74.88          us/op
  ·p0.999                                    sample          172.03          us/op
  ·p0.9999                                   sample          619.63          us/op
  ·p1.00                                     sample        12206.08          us/op
```

**After BUSY_SPIN:**
```
Benchmark                                                Mode     Cnt      Score   Error  Units
WaitStrategyBenchmark.brane_latencyDist_chainId_busySpin sample  207604   36.253 ± 0.311  us/op
  ·p0.00                                                 sample           20.896          us/op
  ·p0.50                                                 sample           34.176          us/op
  ·p0.90                                                 sample           41.152          us/op
  ·p0.95                                                 sample           48.320          us/op
  ·p0.99                                                 sample           70.144          us/op
  ·p0.999                                                sample          165.989          us/op
  ·p0.9999                                               sample          769.674          us/op
  ·p1.00                                                 sample        10027.008          us/op
```

## Commands Used

**Baseline (YIELDING):**
```bash
./gradlew :brane-benchmark:jmh -Pjmh.includes="brane_latencyDist_chainId" -Pjmh.wi=3 -Pjmh.i=5 -Pjmh.f=1
```

**BUSY_SPIN:**
```bash
./gradlew :brane-benchmark:jmh -Pjmh.includes="brane_latencyDist_chainId_busySpin" -Pjmh.wi=3 -Pjmh.i=5 -Pjmh.f=1
```

## Notes

- Baseline YIELDING results are from BASELINE.md (recorded earlier)
- BUSY_SPIN test uses `WaitStrategyBenchmark.java` with explicit BUSY_SPIN configuration
- Both tests run against local Anvil on macOS with native kqueue transport
- CPU was not pinned (no taskset/isolcpus), which limits BUSY_SPIN effectiveness
- With proper CPU core isolation, BUSY_SPIN improvements would be more pronounced

## Conclusion

The BUSY_SPIN wait strategy delivers **5-7% latency improvements at p50-p99.9** compared to YIELDING, at the cost of 100% CPU utilization on one core. For latency-critical applications with dedicated infrastructure, this trade-off is worthwhile. For general-purpose use, the default YIELDING strategy remains the recommended choice.
