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
| 100 ms | 103.3 ms | ± 26.0 ms | +3.3% |
| 500 ms | 504.0 ms | ± 10.5 ms | +0.8% |
| 1000 ms | 1005.3 ms | ± 61.6 ms | +0.5% |
| 2000 ms | 2003.3 ms | ± 6.9 ms | +0.2% |

### Analysis

- **HTTP timeouts are accurate**: Detection times closely match configured timeouts
- **Low overhead**: 0.2-3.3% overhead above configured timeout
- **Consistent behavior**: Error margins are small relative to timeout values
- **Shorter timeouts have higher relative overhead**: Expected due to fixed JVM/OS overhead

---

## WebSocket Connection Failure Detection

**Status:** ⚠️ **NOT TESTED - Bug Found**

The WebSocket benchmark was skipped because `WebSocketProvider` does not apply `connectTimeout`
from `WebSocketConfig` to the Netty Bootstrap channel options. The connection attempt uses the
OS default TCP connection timeout (~75 seconds) instead of the configured timeout.

**Root Cause:** `WebSocketProvider.connect()` does not set `ChannelOption.CONNECT_TIMEOUT_MILLIS`
when creating the Netty Bootstrap.

**Recommendation:** Fix `WebSocketProvider` to honor `connectTimeout` from `WebSocketConfig`:
```java
b.group(group)
    .channel(channelClass)
    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, (int) config.connectTimeout().toMillis())  // ADD THIS
    .option(ChannelOption.TCP_NODELAY, true)
    // ...
```

---

## Conclusions

1. **HTTP provider correctly honors timeouts** - Users can rely on `connectTimeout` settings
2. **WebSocket provider needs timeout fix** - Connection timeouts are ignored (see recommendation above)
3. **Recommended default timeout**: 5-10 seconds for production, 100-500ms for fast-fail scenarios
