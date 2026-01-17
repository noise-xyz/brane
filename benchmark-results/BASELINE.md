# Baseline Measurements - 2026-01-17 (v2 - More Iterations)

## Hardware
- CPU: Apple Silicon (arm64)
- RAM: (system default)
- OS: macOS (Darwin 24.6.0)
- Java: JDK 21.0.9, OpenJDK 64-Bit Server VM
- Anvil: local (127.0.0.1:8545)

## Benchmark Configuration
- Warmup: 3 iterations, 3s each
- Measurement: 5 iterations, 3s each
- Forks: 1

---

## Quick Baseline (runWebSocketBenchmark)

**Command:** `./gradlew :brane-benchmark:runWebSocketBenchmark`

- **Throughput: 111,859 ops/s**
- Total Time: 89.40 ms for 10,000 requests

---

## Native Transport Baseline (brane_throughput_chainId)

**Command:** `./gradlew :brane-benchmark:jmh -Pjmh.includes="brane_throughput_chainId" -Pjmh.wi=3 -Pjmh.i=5 -Pjmh.f=1`

| Metric | Value |
|--------|-------|
| **Throughput** | **25,235 ops/s** |
| Score Error | ± 1,665 (6.6%) |
| Min | 24,742 ops/s |
| Max | 25,714 ops/s |
| Stdev | 432 |

*Note: This is synchronous single-threaded throughput (blocking per request), not async batch throughput.*

---

## BusySpinWaitStrategy Baseline (brane_latencyDist_chainId)

**Command:** `./gradlew :brane-benchmark:jmh -Pjmh.includes="brane_latencyDist_chainId" -Pjmh.wi=3 -Pjmh.i=5 -Pjmh.f=1`

**Wait Strategy:** YIELDING (default)
**Sample Count:** 193,258 operations

| Percentile | Latency (μs) |
|------------|--------------|
| p0 (min) | 24.10 |
| **p50** | **36.10** |
| p90 | 47.62 |
| p95 | 55.23 |
| **p99** | **74.88** |
| **p99.9** | **172.03** |
| p99.99 | 619.63 |
| p100 (max) | 12,206.08 |

**Mean:** 38.917 μs (± 0.343)

---

## After Implementing Quick Wins

### After Native Transport (Quick Win #1)

**Date:**
**Transport:** epoll / kqueue / NIO

| Metric | Before | After | Improvement |
|--------|--------|-------|-------------|
| Throughput (ops/s) | 25,235 | | % |
| Quick baseline (ops/s) | 111,859 | | % |

### After BusySpinWaitStrategy (Quick Win #2)

**Date:**
**Wait Strategy:** BUSY_SPIN

| Percentile | Before (μs) | After (μs) | Improvement |
|------------|-------------|------------|-------------|
| p50 | 36.10 | | |
| p99 | 74.88 | | |
| p99.9 | 172.03 | | |
| p99.99 | 619.63 | | |
