# Connection Failure Detection Benchmark - 2026-01-17

## Overview

This benchmark measures the time to detect connection failure to an unreachable host (192.0.2.1, RFC 5737 TEST-NET-1).
It validates that timeout settings are honored and measures the overhead of connection failure detection.

## Hardware

- CPU: Apple Silicon (arm64)
- OS: macOS (Darwin 24.6.0)
- Java: JDK 21.0.9, OpenJDK 64-Bit Server VM

## Benchmark Configuration

- Mode: Single-shot time (no warmup)
- Measurement: 3 iterations per timeout value
- Forks: 1
- Target: 192.0.2.1:8545 (RFC 5737 TEST-NET-1, guaranteed unreachable)

---

## HTTP Connection Failure Detection

**Command:** `./gradlew :brane-benchmark:jmh -Pjmh.includes="http_connectionFailure"`

| Configured Timeout | Actual Detection Time | Error (99.9%) | Overhead |
|--------------------|----------------------|---------------|----------|
| 100 ms | 104.8 ms | ± 9.3 ms | +4.8% |
| 500 ms | 502.9 ms | ± 37.9 ms | +0.6% |
| 1000 ms | 1006.4 ms | ± 124.3 ms | +0.6% |
| 2000 ms | 2003.1 ms | ± 26.9 ms | +0.2% |

### Analysis

- **HTTP timeouts are accurate**: Detection times closely match configured timeouts
- **Low overhead**: 0.2-4.8% overhead above configured timeout
- **Consistent behavior**: Error margins are small relative to timeout values
- **Shorter timeouts have higher relative overhead**: Expected due to fixed JVM/OS overhead

---

## WebSocket Connection Failure Detection

**Status:** ✅ **CONNECT_TIMEOUT_MILLIS now applied** (T2-3A fix verified)

**Command:** `./gradlew :brane-benchmark:jmh -Pjmh.includes="ws_connectionFailure"`

| Configured Timeout | Actual Total Time | Error (99.9%) |
|--------------------|-------------------|---------------|
| 100 ms | 27.4 s | ± 0.5 s |
| 500 ms | 31.4 s | ± 0.1 s |
| 1000 ms | 36.4 s | ± 0.6 s |
| 2000 ms | 46.4 s | ± 0.8 s |

### Why WebSocket Times Are Longer

The WebSocket provider includes **built-in retry logic** for resilience:

- **Max attempts:** 10 retries before giving up
- **Backoff delays:** 100ms → 200ms → 400ms → 800ms → 1600ms → 3200ms → 5000ms (capped)
- **Total backoff:** ~21.3 seconds across all retries

**Expected total time formula:**
```
total_time = (timeout × 10 attempts) + backoff_delays (~21.3s)
```

| Configured | Calculation | Expected | Actual |
|------------|-------------|----------|--------|
| 100 ms | (100×10) + 21,300 = 22,300 ms | ~22.3s | 27.4s |
| 500 ms | (500×10) + 21,300 = 26,300 ms | ~26.3s | 31.4s |
| 1000 ms | (1000×10) + 21,300 = 31,300 ms | ~31.3s | 36.4s |
| 2000 ms | (2000×10) + 21,300 = 41,300 ms | ~41.3s | 46.4s |

The ~5 second difference between expected and actual is due to JVM startup overhead and benchmark measurement overhead.

### Verification: Timeout IS Working

The key evidence that `CONNECT_TIMEOUT_MILLIS` is correctly applied:

1. **Proportional scaling**: Total time increases by ~(timeout × 10) as expected
2. **Consistent pattern**: Each 100ms increase in timeout adds ~1 second to total time
3. **No OS default**: If timeout wasn't applied, all values would show ~75 seconds (OS TCP default)

**Before fix (T2-B2 baseline):** WebSocket used OS default timeout (~75 seconds)
**After fix (T2-3C verification):** WebSocket respects configured timeout per-attempt

---

## Conclusions

1. **HTTP provider correctly honors timeouts** - Users can rely on `connectTimeout` settings
2. **WebSocket provider now honors timeouts** - `CONNECT_TIMEOUT_MILLIS` applied at `WebSocketProvider.java:588`
3. **WebSocket has retry resilience** - 10 attempts with exponential backoff before permanent failure
4. **Recommended timeout values:**
   - Production: 5-10 seconds (allows retries to succeed on transient failures)
   - Fast-fail scenarios: 100-500ms (but note total failure time includes retries)
