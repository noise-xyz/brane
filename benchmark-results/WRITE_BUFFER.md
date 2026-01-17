# WriteBufferWaterMark Benchmark Results - 2026-01-17

## Overview

This document records benchmark results for different `WriteBufferWaterMark` configurations in Netty's WebSocket channel. The WriteBufferWaterMark controls flow control for write operations:

- **Low water mark:** When the write buffer drops below this threshold, the channel becomes writable again
- **High water mark:** When the write buffer exceeds this threshold, the channel becomes not-writable (backpressure)

## Hardware

- **CPU:** Apple Silicon (arm64)
- **OS:** macOS (Darwin 24.6.0)
- **Java:** JDK 21.0.9, OpenJDK 64-Bit Server VM
- **JVM Args:** -Xms1g -Xmx1g -XX:+UseG1GC

## Benchmark Configuration

- **Warmup:** 1 iteration, 5s each (changed from 2 in benchmark source)
- **Measurement:** 3 iterations, 10s each
- **Forks:** 1
- **Requests per batch:** 10,000
- **Target:** Local Anvil node (ws://127.0.0.1:8545)

---

## WriteBufferWaterMark Configurations Tested

| Config | Low Water Mark | High Water Mark |
|--------|----------------|-----------------|
| 4_16 | 4 KB | 16 KB |
| 8_32 (default) | 8 KB | 32 KB |
| 16_64 | 16 KB | 64 KB |
| 32_128 | 32 KB | 128 KB |

---

## Results Summary

### sustainedLoad_chainId (Sequential sync calls)

| WriteBufferWaterMark | Throughput (ops/s) | Error (99.9% CI) | Effective req/s |
|---------------------|-------------------|------------------|-----------------|
| 4_16 | 1.998 | ±2.886 | ~19,980 |
| 8_32 (default) | 2.008 | ±2.267 | ~20,080 |
| 16_64 | 1.973 | ±4.927 | ~19,730 |
| 32_128 | 2.049 | ±2.075 | ~20,490 |

### sustainedLoad_blockNumber (Sequential sync calls)

| WriteBufferWaterMark | Throughput (ops/s) | Error (99.9% CI) | Effective req/s |
|---------------------|-------------------|------------------|-----------------|
| 4_16 | 2.005 | ±3.748 | ~20,050 |
| 8_32 (default) | 1.419 | ±2.970 | ~14,190 |
| 16_64 | 1.719 | ±5.161 | ~17,190 |
| 32_128 | 2.058 | ±1.726 | ~20,580 |

### sustainedLoad_asyncBurst (Batched async calls, 1000 concurrent)

| WriteBufferWaterMark | Throughput (ops/s) | Error (99.9% CI) | Effective req/s |
|---------------------|-------------------|------------------|-----------------|
| 4_16 | 9.737 | ±21.808 | ~97,370 |
| 8_32 (default) | 11.044 | ±32.488 | ~110,440 |
| 16_64 | 10.741 | ±20.888 | ~107,410 |
| 32_128 | 9.074 | ±4.370 | ~90,740 |

**Note:** Each "op" represents completing 10,000 requests.

---

## Analysis

### Sequential Operations (chainId, blockNumber)

1. **All configurations perform similarly** for sequential sync calls (~1.9-2.1 ops/s)
2. **32_128 showed marginally better performance** with 2.049 ops/s for chainId and 2.058 ops/s for blockNumber
3. **Error margins are large** relative to the differences, meaning the configurations are statistically equivalent for this workload

### Async Burst Operations

1. **Default 8_32 performed best** at 11.044 ops/s (~110K req/s effective)
2. **Larger buffers (32_128) performed worst** at 9.074 ops/s with lowest variance (±4.370)
3. **High variance across all configs** indicates the async workload is sensitive to system conditions

### Key Observations

- For **sequential workloads**, buffer size has minimal impact - the bottleneck is request/response latency
- For **concurrent async workloads**, smaller-to-medium buffers (8_32, 16_64) appear to perform better
- The **32_128 config showed lowest variance** but also lowest throughput for async, suggesting more predictable but slower backpressure behavior
- **4_16 (smallest buffers)** showed middle-of-the-pack performance with high variance

---

## Recommendations

1. **Keep default 8_32** - It provides good async performance and is reasonable for sequential workloads
2. **Consider 32_128 for predictability** - Lower variance may be preferable in production where consistency matters
3. **Avoid changing based on these results alone** - The high error margins suggest more iterations are needed for statistical significance

---

## Raw JMH Output

```
Benchmark                                         (waterMarkConfig)   Mode  Cnt   Score    Error  Units
SustainedLoadBenchmark.sustainedLoad_asyncBurst                8_32  thrpt    3  11.044 ± 32.488  ops/s
SustainedLoadBenchmark.sustainedLoad_asyncBurst               16_64  thrpt    3  10.741 ± 20.888  ops/s
SustainedLoadBenchmark.sustainedLoad_asyncBurst              32_128  thrpt    3   9.074 ±  4.370  ops/s
SustainedLoadBenchmark.sustainedLoad_asyncBurst                4_16  thrpt    3   9.737 ± 21.808  ops/s
SustainedLoadBenchmark.sustainedLoad_blockNumber               8_32  thrpt    3   1.419 ±  2.970  ops/s
SustainedLoadBenchmark.sustainedLoad_blockNumber              16_64  thrpt    3   1.719 ±  5.161  ops/s
SustainedLoadBenchmark.sustainedLoad_blockNumber             32_128  thrpt    3   2.058 ±  1.726  ops/s
SustainedLoadBenchmark.sustainedLoad_blockNumber               4_16  thrpt    3   2.005 ±  3.748  ops/s
SustainedLoadBenchmark.sustainedLoad_chainId                   8_32  thrpt    3   2.008 ±  2.267  ops/s
SustainedLoadBenchmark.sustainedLoad_chainId                  16_64  thrpt    3   1.973 ±  4.927  ops/s
SustainedLoadBenchmark.sustainedLoad_chainId                 32_128  thrpt    3   2.049 ±  2.075  ops/s
SustainedLoadBenchmark.sustainedLoad_chainId                   4_16  thrpt    3   1.998 ±  2.886  ops/s
```

## Command Used

```bash
./gradlew :brane-benchmark:jmh -Pjmh.includes="SustainedLoadBenchmark" --no-configuration-cache
```

## Notes

- Benchmark ran against local Anvil node - results may differ with remote nodes or real networks
- Results saved to `brane-benchmark/build/results/jmh/results.json`
- For production tuning, run with `-Pjmh.f=3 -Pjmh.i=5 -Pjmh.wi=3` for more statistical confidence
