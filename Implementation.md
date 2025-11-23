
# Phase 2.7: Debug Mode + RPC Logging (P0.11)

## Goal

Introduce a unified **debug mode** for Brane that logs RPC requests, responses, latencies, gas estimates, transaction lifecycle steps, and decoded revert information. Debug mode dramatically improves developer experience, supports enterprise observability, and simplifies troubleshooting.

## Motivation

* **Developer Productivity**: Debug logs make RPC interactions transparent (similar to “viem debug mode” and “ethers logger”).
* **Enterprise Readiness**: Visibility into RPC flow, latency, revert reasons, and gas dynamics is essential for diagnosing production issues.
* **Consistency**: A centralized logging layer avoids scattered `System.out` calls and ensures Brane emits structured, safe, and uniform logs.

---

## Implementation Details

### Module Placement

* `BraneDebug` → **brane-core**
* `LogSanitizer` → **brane-core**
* `DebugLogger` → **brane-core**
* RPC instrumentation → **brane-rpc**
* PublicClient instrumentation → **brane-rpc**
* WalletClient instrumentation → **brane-rpc**
* Revert decoding integration → **brane-core** + **brane-rpc**

---

### 1. Global Debug Toggle (`BraneDebug`)

A lightweight global flag for enabling/disabling debug logging.

```java
package io.brane.core;

public final class BraneDebug {

    private static volatile boolean enabled = false;

    private BraneDebug() {}

    public static boolean isEnabled() {
        return enabled;
    }

    public static void setEnabled(boolean enabled) {
        BraneDebug.enabled = enabled;
    }
}
```

**Usage:**

```java
BraneDebug.setEnabled(true); // Enable verbose RPC + TX debug logging
```

> Note: Simple global toggle for P0.11; may become per-client configuration later.

---

### 2. Log Sanitization (`LogSanitizer`)

A utility ensuring **no sensitive data** appears in debug logs.

```java
package io.brane.core;

public final class LogSanitizer {

    private LogSanitizer() {}

    public static String sanitize(String input) {
        if (input == null) return "null";

        // Redact private key fields explicitly
        if (input.contains("\"privateKey\"")) {
            return input.replaceAll("\"privateKey\"\\s*:\\s*\"0x[^\"]+\"", 
                                    "\"privateKey\":\"0x***[REDACTED]***\"");
        }

        // Redact signed raw transactions
        if (input.contains("\"raw\"")) {
            return input.replaceAll("\"raw\"\\s*:\\s*\"0x[^\"]+\"", 
                                    "\"raw\":\"0x***[REDACTED]***\"");
        }

        // Truncate very large payloads (bytecode, calldata, logs)
        if (input.length() > 2000) {
            return input.substring(0, 200) + "...(truncated)";
        }

        return input;
    }
}
```

Sanitization is applied **before** everything is sent to `DebugLogger`.

---

### 3. Central Debug Logger (`DebugLogger`)

A uniform logging entry point with built-in sanitization.

```java
package io.brane.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DebugLogger {

    private static final Logger LOG = LoggerFactory.getLogger("io.brane.debug");

    private DebugLogger() {}

    public static void log(String message) {
        if (BraneDebug.isEnabled()) {
            LOG.debug(message);
        }
    }

    public static void log(String message, Throwable t) {
        if (BraneDebug.isEnabled()) {
            LOG.debug(message, t);
        }
    }

    public static String safe(Object o) {
        return LogSanitizer.sanitize(String.valueOf(o));
    }
}
```

#### Log Format Guidelines

All debug logs must follow:

```
[PREFIX] key=value key=value ...
```

Valid prefixes:

* `[RPC]`, `[RPC-ERROR]`
* `[CALL]`, `[CALL-RESULT]`
* `[ESTIMATE-GAS]`, `[ESTIMATE-GAS-RESULT]`
* `[TX-SEND]`, `[TX-HASH]`, `[TX-WAIT]`, `[TX-REVERT]`

---

### 4. RPC-Level Logging (`HttpBraneProvider`)

Instrument RPC request/response + latency.

```java
long start = System.nanoTime();
try {
    var result = doHttpCall(requestJson);
    long durMicros = (System.nanoTime() - start) / 1_000;

    DebugLogger.log("""
        [RPC] method=%s id=%s durationMicros=%d
          request=%s
          response=%s
    """.formatted(
        method,
        id,
        durMicros,
        DebugLogger.safe(requestJson),
        DebugLogger.safe(result)
    ));

    return result;

} catch (Exception e) {
    long durMicros = (System.nanoTime() - start) / 1_000;

    DebugLogger.log("""
        [RPC-ERROR] method=%s durationMicros=%d error=%s
    """.formatted(method, durMicros, e.getMessage()), e);

    throw e;
}
```

Ensures:

* No raw private keys logged
* No full bytecode/signed tx
* Latency is visible

---

### 5. PublicClient Logging (eth_call, estimateGas)

Instrument:

* `eth_call(...)`
* `eth_estimateGas(...)`
* Other read-only operations

Examples:

```java
DebugLogger.log("[CALL] method=eth_call to=" + to);
DebugLogger.log("[CALL-RESULT] len=" + result.length());
```

```java
DebugLogger.log("[ESTIMATE-GAS] tx=" + DebugLogger.safe(tx));
DebugLogger.log("[ESTIMATE-GAS-RESULT] gas=" + gas);
```

All logged values must be sanitized.

---

### 6. WalletClient Logging (Send, Hash, Polling, Revert)

Instrument:

* **Before send:**

  ```
  [TX-SEND] type=EIP-1559 to=0xabc nonce=auto value=100 wei
  ```

* **After obtaining tx hash:**

  ```
  [TX-HASH] 0x123...
  ```

* **Polling for receipt:**

  ```
  [TX-WAIT] hash=0x123 attempt=3
  ```

* **On revert:**

  ```
  [TX-REVERT] hash=0x123 kind=PANIC reason="division or modulo by zero"
  ```

Never log:

* private keys
* full raw signed transactions

---

### 7. Revert Decoding Integration (From P0.10)

When revert data is present:

1. Call `RevertDecoder.decode(rawData)`
2. If `kind != UNKNOWN` → throw `RevertException(kind, reason, raw, cause)`
3. Emit:

```java
DebugLogger.log("[TX-REVERT] hash=%s kind=%s reason=%s"
    .formatted(hash, decoded.kind(), decoded.reason()));
```

Ensures revert diagnostics appear clearly during debugging.

---

## Steps

1. [ ] Add `BraneDebug` global toggle
2. [ ] Add `LogSanitizer`
3. [ ] Add `DebugLogger` (SLF4J-backed, applies sanitization)
4. [ ] Instrument `HttpBraneProvider` with detailed RPC logs
5. [ ] Instrument `PublicClient` (`eth_call`, `estimateGas`)
6. [ ] Instrument `DefaultWalletClient` (send/hash/wait/revert)
7. [ ] Ensure **all logs** use sanitization utilities
8. [ ] Add documentation + small README example

---

## Testing

### 1. Debug Toggle Tests

* Default: `BraneDebug.isEnabled() == false`
* Debug off → no logs emitted
* Debug on → logs emitted to SLF4J `ListAppender`

---

### 2. Provider Logging Tests

Using mock/fake HTTP transport:

* Debug off → **no `[RPC]` logs**
* Debug on → verify:

  * `[RPC] method=eth_blockNumber`
  * `durationMicros=...`
  * Sanitized request/response
* Error case → `[RPC-ERROR]`

---

### 3. PublicClient Logging Tests

Mock RPC provider:

* Debug on → verify:

  * `[CALL]`
  * `[CALL-RESULT]`
  * `[ESTIMATE-GAS]`
  * `[ESTIMATE-GAS-RESULT]`

---

### 4. WalletClient Logging Tests

Fake provider:

* Success path:

  * `[TX-SEND]`
  * `[TX-HASH]`
  * `[TX-WAIT]`
* Revert path:

  * `[TX-REVERT] kind=ERROR_STRING`
  * `[TX-REVERT] kind=PANIC`

---

### 5. Sensitive Data Tests (`LogSanitizerTest`)

Unit tests:

* Private key fields → redacted
* `"raw": "0x..."` → redacted
* Large data → truncated
* Normal strings → unchanged

Integration:

* Send a tx with known private key
* Capture logs
* Assert:

  * Logs do **not** contain the private key
  * Logs do **not** contain raw signed transaction hex

---

### 6. Optional Integration Example

Add `DebugExample` in `brane-examples`:

```java
BraneDebug.setEnabled(true);
var provider = HttpBraneProvider.builder(rpc).build();
...
```

Run:

```
./gradlew :brane-examples:run \
  -PmainClass=io.brane.examples.DebugExample \
  -Dbrane.examples.rpc=http://127.0.0.1:8545
```

Check logs manually for readability & safety.