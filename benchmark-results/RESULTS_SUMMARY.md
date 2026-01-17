# Benchmark Results Summary

Comprehensive benchmark results for the Brane SDK Disruptor-Netty performance optimizations.

**Date:** 2026-01-17
**Branch:** `Atlantropaz/disruptor-netty-perf`

---

## Test Environment

| Component | Value |
|-----------|-------|
| CPU | Apple Silicon (arm64) |
| OS | macOS (Darwin 24.6.0) |
| Java | JDK 21.0.9, OpenJDK 64-Bit Server VM |
| JVM Args | -Xms512m -Xmx512m -XX:+UseG1GC (standard) |
| Target | Local Anvil node (127.0.0.1:8545) |

---

## Executive Summary

| Optimization | Metric | Baseline | After | Improvement |
|--------------|--------|----------|-------|-------------|
| **Native Transport** | Throughput | 25,235 ops/s | 27,918 ops/s | **+10.6%** |
| **Native Transport** | Variance | ±6.6% | ±2.6% | **-57% stdev** |
| **BUSY_SPIN Wait** | p50 Latency | 36.10 μs | 34.18 μs | **-5.3%** |
| **BUSY_SPIN Wait** | p99 Latency | 74.88 μs | 70.14 μs | **-6.3%** |
| **Quick Baseline** | Throughput | - | 111,859 ops/s | (async batch) |

---

## 1. Native Transport (kqueue/epoll)

**Benchmark:** `brane_throughput_chainId`
**Details:** [NATIVE_TRANSPORT.md](NATIVE_TRANSPORT.md)

### Results

| Metric | NIO (Baseline) | Native (kqueue) | Change |
|--------|----------------|-----------------|--------|
| **Throughput** | 25,235 ops/s | **27,918 ops/s** | **+10.6%** |
| Score Error | ±1,665 (6.6%) | ±714 (2.6%) | Lower variance |
| Standard Deviation | 432 | 185 | **-57%** |

### Key Findings

- Native transport provides **10.6% throughput improvement**
- **57% reduction in variance** - more consistent performance
- Auto-detected via `TransportType.AUTO` (kqueue on macOS, epoll on Linux)
- Zero configuration needed - works out of the box

---

## 2. Wait Strategy Comparison

**Benchmark:** `brane_latencyDist_chainId`
**Details:** [WAIT_STRATEGY.md](WAIT_STRATEGY.md)

### Latency Distribution

| Percentile | YIELDING (default) | BUSY_SPIN | Improvement |
|------------|---------------------|-----------|-------------|
| **p50** | 36.10 μs | **34.18 μs** | **-5.3%** |
| p90 | 47.62 μs | 41.15 μs | -13.6% |
| p95 | 55.23 μs | 48.32 μs | -12.5% |
| **p99** | 74.88 μs | **70.14 μs** | **-6.3%** |
| **p99.9** | 172.03 μs | **165.99 μs** | **-3.5%** |
| p99.99 | 619.63 μs | 769.67 μs | +24.2% |
| Mean | 38.92 μs | 36.25 μs | -6.9% |
| Sample Count | 193,258 | 207,604 | +7.4% |

### Key Findings

- BUSY_SPIN provides **5-7% latency reduction at p50-p99.9**
- Higher throughput (7.4% more samples)
- Trade-off: **+24% at p99.99** (extreme tail)
- Recommendation: YIELDING for general use, BUSY_SPIN for HFT/MEV with dedicated cores

---

## 3. Large Response Parsing

**Benchmark:** `LargeResponseBenchmark`
**Details:** [LARGE_RESPONSE.md](LARGE_RESPONSE.md)

### Parsing Performance (eth_getLogs style)

| Log Count | JSON Size | Brane Parse Time | Jackson Raw | Type Safety Overhead |
|-----------|-----------|------------------|-------------|---------------------|
| 100 | ~58 KB | 0.258 ms | 0.050 ms | 5.2x |
| 1,000 | ~585 KB | 2.454 ms | 0.431 ms | 5.7x |
| 5,000 | ~2.9 MB | 12.301 ms | 2.142 ms | 5.7x |

### Throughput

| Log Count | Full Parse (ops/s) | LogParser Only (ops/s) |
|-----------|--------------------|-----------------------|
| 100 | 3,960 | 5,038 |
| 1,000 | 404 | 506 |
| 5,000 | 81 | 99 |

### Key Findings

- Linear scaling: ~2.5 μs per log entry
- Type-safe `LogEntry` creation is ~80% of total parse time
- Current 64KB `HttpObjectAggregator` limit insufficient for production use
- Recommendation: Increase to 4MB for real-world `eth_getLogs` queries

---

## 4. WriteBufferWaterMark Tuning

**Benchmark:** `SustainedLoadBenchmark`
**Details:** [WRITE_BUFFER.md](WRITE_BUFFER.md)

### Sequential Operations (10,000 requests/batch)

| Buffer Config | chainId (ops/s) | blockNumber (ops/s) | Effective req/s |
|---------------|-----------------|---------------------|-----------------|
| 4_16 | 1.998 | 2.005 | ~20K |
| **8_32 (default)** | **2.008** | 1.419 | ~20K |
| 16_64 | 1.973 | 1.719 | ~20K |
| 32_128 | 2.049 | 2.058 | ~20K |

### Async Burst (1,000 concurrent)

| Buffer Config | Throughput (ops/s) | Effective req/s |
|---------------|-------------------|-----------------|
| 4_16 | 9.737 | ~97K |
| **8_32 (default)** | **11.044** | **~110K** |
| 16_64 | 10.741 | ~107K |
| 32_128 | 9.074 | ~91K |

### Key Findings

- Default 8_32 performs best for async workloads (~110K req/s)
- Buffer size has minimal impact on sequential operations
- High variance across all configs - more iterations needed
- Recommendation: Keep default 8_32 configuration

---

## 5. Connection Failure Detection

**Benchmark:** `ConnectionFailureBenchmark`
**Details:** [CONNECTION_FAILURE.md](CONNECTION_FAILURE.md)

### HTTP Connection Timeout

| Configured | Actual | Overhead |
|------------|--------|----------|
| 100 ms | 104.8 ms | +4.8% |
| 500 ms | 502.9 ms | +0.6% |
| 1,000 ms | 1,006.4 ms | +0.6% |
| 2,000 ms | 2,003.1 ms | +0.2% |

### WebSocket Connection Timeout (with retry)

| Configured | Total Time | Notes |
|------------|------------|-------|
| 100 ms | 27.4 s | 10 retries + backoff |
| 500 ms | 31.4 s | 10 retries + backoff |
| 1,000 ms | 36.4 s | 10 retries + backoff |
| 2,000 ms | 46.4 s | 10 retries + backoff |

### Key Findings

- HTTP timeouts are accurate (0.2-4.8% overhead)
- WebSocket includes built-in retry resilience (10 attempts + exponential backoff)
- `CONNECT_TIMEOUT_MILLIS` now correctly applied (T2-3A fix verified)
- Recommendation: 5-10 seconds for production, 100-500ms for fast-fail

---

## 6. Quick Baseline

**Benchmark:** `runWebSocketBenchmark` (async batch)

| Metric | Value |
|--------|-------|
| **Throughput** | **111,859 ops/s** |
| Total Time | 89.40 ms for 10,000 requests |
| Mode | Async batch (not single-threaded blocking) |

This represents the maximum achievable throughput with async batching against a local Anvil node.

---

## Benchmark Commands Reference

```bash
# Quick baseline (async batch)
./gradlew :brane-benchmark:runWebSocketBenchmark

# Native transport throughput
./gradlew :brane-benchmark:jmh -Pjmh.includes="brane_throughput_chainId" -Pjmh.wi=3 -Pjmh.i=5 -Pjmh.f=1

# Wait strategy latency
./gradlew :brane-benchmark:jmh -Pjmh.includes="brane_latencyDist_chainId" -Pjmh.wi=3 -Pjmh.i=5 -Pjmh.f=1

# BUSY_SPIN wait strategy
./gradlew :brane-benchmark:jmh -Pjmh.includes="brane_latencyDist_chainId_busySpin" -Pjmh.wi=3 -Pjmh.i=5 -Pjmh.f=1

# Large response parsing
./gradlew :brane-benchmark:jmh -Pjmh.includes="LargeResponseBenchmark" -Pjmh.wi=3 -Pjmh.i=5 -Pjmh.f=2

# Sustained load / WriteBuffer
./gradlew :brane-benchmark:jmh -Pjmh.includes="SustainedLoadBenchmark" --no-configuration-cache

# Connection failure detection
./gradlew :brane-benchmark:jmh -Pjmh.includes="http_connectionFailure"
./gradlew :brane-benchmark:jmh -Pjmh.includes="ws_connectionFailure"
```

---

## Conclusions

### Performance Wins

1. **Native Transport**: +10.6% throughput, 57% lower variance - enabled by default
2. **BUSY_SPIN Wait Strategy**: 5-7% latency reduction for HFT use cases
3. **Async Batching**: 111K ops/s achievable with proper batching

### Configuration Recommendations

| Use Case | Transport | Wait Strategy | WriteBuffer |
|----------|-----------|---------------|-------------|
| General purpose | AUTO (default) | YIELDING (default) | 8_32 (default) |
| Low-latency trading | AUTO | BUSY_SPIN + CPU pinning | 8_32 |
| High-throughput batch | AUTO | YIELDING | 8_32 |

### Known Limitations

1. **64KB HttpObjectAggregator limit** - May fail on large `eth_getLogs` responses
2. **p99.99 tail latency** - BUSY_SPIN shows 24% higher extreme tail
3. **WriteBuffer tuning** - High variance suggests more benchmarking needed

---

## Related Documents

- [BASELINE.md](BASELINE.md) - Initial baseline measurements
- [NATIVE_TRANSPORT.md](NATIVE_TRANSPORT.md) - kqueue/epoll comparison
- [WAIT_STRATEGY.md](WAIT_STRATEGY.md) - Disruptor wait strategy analysis
- [LARGE_RESPONSE.md](LARGE_RESPONSE.md) - Large response parsing benchmarks
- [WRITE_BUFFER.md](WRITE_BUFFER.md) - WriteBufferWaterMark tuning
- [SUSTAINED_LOAD.md](SUSTAINED_LOAD.md) - Sustained load benchmarks
- [CONNECTION_FAILURE.md](CONNECTION_FAILURE.md) - Connection timeout verification
