# Sustained Load Benchmark Results - 2026-01-17

## Overview

This benchmark measures throughput under sustained load (10,000 requests per batch) with varying `WriteBufferWaterMark` configurations. The WriteBufferWaterMark controls Netty's flow control for write operations:

- **Low water mark:** When the write buffer drops below this threshold, the channel becomes writable again
- **High water mark:** When the write buffer exceeds this threshold, the channel becomes not-writable (backpressure)

Smaller buffers detect backpressure earlier but may cause more write suspensions. Larger buffers allow more data in flight but delay backpressure detection.

## Hardware

- **CPU:** Apple Silicon (arm64)
- **OS:** macOS (Darwin 24.6.0)
- **Java:** JDK 21.0.9, OpenJDK 64-Bit Server VM
- **JVM Args:** -Xms1g -Xmx1g -XX:+UseG1GC

## Benchmark Configuration

- **Warmup:** 1 iteration, 5s each
- **Measurement:** 3 iterations, 10s each
- **Forks:** 1
- **Requests per batch:** 10,000 `eth_chainId` calls
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

## Benchmark Results

### Throughput (batches/sec where each batch = 10,000 requests)

| WriteBufferWaterMark | Throughput (ops/s) | Error (99.9% CI) | Effective req/s |
|---------------------|-------------------|------------------|-----------------|
| 4_16 | 1.911 | ±4.366 | ~19,110 |
| 8_32 (default) | 0.956 | ±0.986 | ~9,560 |
| 16_64 | 0.961 | ±0.500 | ~9,610 |
| 32_128 | 1.621 | ±8.058 | ~16,210 |

**Note:** Each "op" represents completing 10,000 sequential `eth_chainId` requests.

---

## Analysis

### Observations

1. **High variance:** All configurations show large error margins, indicating significant run-to-run variance. More iterations would be needed for statistically significant conclusions.

2. **Smaller buffers performed well:** The 4_16 configuration showed the highest average throughput (1.911 ops/s). This may indicate that faster backpressure detection helps maintain steady throughput by preventing buffer bloat.

3. **Default configuration is conservative:** The current default (8_32) and the larger 16_64 configuration showed similar, lower throughput (~0.96 ops/s).

4. **32_128 showed high variance:** The largest buffer configuration had the highest error margin (±8.058), suggesting inconsistent behavior - possibly due to delayed backpressure detection causing bursty performance.

### Interpretation

The counter-intuitive result (smaller buffers = higher throughput) may be explained by:

- **Faster feedback loop:** Smaller water marks trigger backpressure sooner, allowing the sender to adapt more quickly
- **Less buffer bloat:** Smaller buffers prevent excessive queuing that can cause latency spikes
- **Local testing bias:** Against a local Anvil node with minimal network latency, the benefits of larger buffers (absorbing network jitter) may not manifest

### Recommendations

1. **Keep default 8_32 for now:** Until more rigorous benchmarking is done, the conservative default is reasonable
2. **Consider 4_16 for low-latency scenarios:** If predictable latency is more important than peak throughput
3. **Run extended benchmarks:** Use more forks and iterations to reduce error margins before making configuration changes

---

## Raw JMH Output

```
Benchmark                                     (waterMarkConfig)   Mode  Cnt  Score   Error  Units
SustainedLoadBenchmark.sustainedLoad_chainId               8_32  thrpt    3  0.956 ± 0.986  ops/s
SustainedLoadBenchmark.sustainedLoad_chainId              16_64  thrpt    3  0.961 ± 0.500  ops/s
SustainedLoadBenchmark.sustainedLoad_chainId             32_128  thrpt    3  1.621 ± 8.058  ops/s
SustainedLoadBenchmark.sustainedLoad_chainId               4_16  thrpt    3  1.911 ± 4.366  ops/s
```

## Command Used

```bash
./gradlew :brane-benchmark:jmh -Pjmh.includes="SustainedLoadBenchmark.sustainedLoad_chainId" --no-configuration-cache
```

## Notes

- Benchmark ran against local Anvil node - results may differ with remote nodes or real networks
- The `sustainedLoad_blockNumber` and `sustainedLoad_asyncBurst` methods were not run in this baseline
- Results saved to `brane-benchmark/build/results/jmh/results.json`
- For production tuning, run with `-Pjmh.f=3 -Pjmh.i=5` for more statistical confidence
