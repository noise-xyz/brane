
# Phase 2.9: Request ID Correlation (P0.13)

## Goal

Add **correlated request IDs** to all JSON-RPC calls so that:

* Every RPC request has a **monotonic `id`**.
* That `id` appears in:
  * The JSON-RPC request/response.
  * `RpcException` instances.
  * Debug logs (`[RPC] ...`, `[RPC-ERROR] ...`).

This makes it trivial to trace a single request across logs and exceptions.

## Motivation

* **Debuggability**: Being able to say "requestId=123" and grep through logs is a massive time saver when diagnosing production issues.
* **Leverages existing work**: Integrates cleanly with `DebugLogger` + debug mode from Phase 2.7.
* **Low risk**: Does not change the public ergonomic surface, only internal wiring and logs.
* **Complements retry logic**: Makes it easy to trace which requests were retried (from Phase 2.8).

---

## Current State

From the codebase analysis:

### `HttpBraneProvider` (already has ID generation)
```java
// Existing field in HttpBraneProvider.java
private final AtomicLong ids = new AtomicLong(1L);

// Current request construction
final JsonRpcRequest request =
    new JsonRpcRequest("2.0", method, safeParams, String.valueOf(ids.getAndIncrement()));
```

**Status**: ✅ ID generation is already implemented but currently as a String. `AtomicLong ids` is already used, so request ID generation is thread-safe out of the box.
**Action needed**: Extract the numeric ID before conversion for local use in logging and exceptions.

### `RpcException` (missing requestId field)
```java
// Current structure in RpcException.java
public final class RpcException extends BraneException {
    private final int code;
    private final String data;
    
    public RpcException(final int code, final String message, 
                       final String data, final Throwable cause) { ... }
}
```

**Status**: ❌ No `requestId` field yet.
**Action needed**: Add `requestId` field and update constructors.

### Debug Logging (partial implementation)
The current `[RPC]` and `[RPC-ERROR]` logs exist but don't include `request id`.

**Status**: ⚠️ Logging infrastructure exists from Phase 2.7.
**Action needed**: Update log format strings to include `id=%d`.

---

## Implementation Details

### 1. Update ID Generation in `HttpBraneProvider`

**Current approach**:
```java
String.valueOf(ids.getAndIncrement())
```

**New approach**:
```java
final long requestId = ids.getAndIncrement();
final JsonRpcRequest request = 
    new JsonRpcRequest("2.0", method, safeParams, String.valueOf(requestId));
```

This allows the numeric `requestId` to be used locally for:
* Logging
* Exception construction
* Optional response correlation

**Files to modify**:
* `brane-rpc/src/main/java/io/brane/rpc/HttpBraneProvider.java` (around existing JSON-RPC request construction and logging)

---

### 2. Extend `RpcException` with `requestId`

Update `io.brane.core.error.RpcException`:

```java
package io.brane.core.error;

public final class RpcException extends BraneException {

    private final int code;
    private final String data;
    private final Long requestId; // [NEW] - nullable for backward compat

    public RpcException(
            final int code, 
            final String message, 
            final String data, 
            final Long requestId,  // [NEW] parameter
            final Throwable cause) {
        super(augmentMessage(message, requestId), cause);
        this.code = code;
        this.data = data;
        this.requestId = requestId;
    }

    // Convenience constructor without cause
    public RpcException(final int code, final String message, final String data, final Long requestId) {
        this(code, message, data, requestId, null);
    }

    public Long requestId() {
        return requestId;
    }

    private static String augmentMessage(String message, Long requestId) {
        if (requestId == null) {
            return message;
        }
        return "[requestId=" + requestId + "] " + message;
    }

    // Keep existing methods: code(), data(), isBlockRangeTooLarge(), isFilterNotFound()
    // ...
}
```

**Migration strategy**:
* Add new constructor with `requestId` parameter
* Keep old constructor temporarily for backward compatibility (deprecate it)
* Update all call sites in `HttpBraneProvider` to use new constructor
* After adding `augmentMessage`, update any tests that assert exact `RpcException.getMessage()` contents to account for the new `[requestId=…]` prefix when `requestId` is non-null

**Files to modify**:
* `brane-core/src/main/java/io/brane/core/error/RpcException.java`
* `brane-rpc/src/main/java/io/brane/rpc/HttpBraneProvider.java` (all throw sites)

---

### 3. Update Debug Logging Format

**Current format** in HttpBraneProvider:
```java
DebugLogger.log(
    "[RPC] method=%s durationMicros=%s request=%s response=%s"
        .formatted(method, durationMicros, payload, responseBody)
);
```

**New format**:
```java
DebugLogger.log(
    "[RPC] id=%d method=%s durationMicros=%d request=%s response=%s"
        .formatted(requestId, method, durationMicros, payload, responseBody)
);
```

**For error logs**:
```java
DebugLogger.log(
    "[RPC-ERROR] id=%d method=%s code=%s message=%s data=%s durationMicros=%d"
        .formatted(requestId, method, err.code(), err.message(), err.data(), durationMicros)
);
```

**Files to modify**:
* `brane-rpc/src/main/java/io/brane/rpc/HttpBraneProvider.java` (update existing `DebugLogger.log` calls)

---

### 4. Update All RpcException Throw Sites

**Locations in `HttpBraneProvider`**:

1. **HTTP error response** (after response received):
   ```java
   throw new RpcException(-32001, 
       "HTTP error for method " + method + ": " + response.statusCode(),
       response.body(), requestId, null);
   ```

2. **JSON-RPC error from node** (error in response):
   ```java
   throw new RpcException(err.code(), err.message(), 
       extractErrorData(err.data()), requestId, null);
   ```

3. **Serialization error** (before requestId allocated):
   ```java
   throw new RpcException(-32700, 
       "Unable to serialize JSON-RPC request for " + request.method(), 
       null, null, e);  // requestId not available here (pre-send)
   ```

4. **Network error** (during HTTP call):
   ```java
   throw new RpcException(-32000, "Network error during JSON-RPC call", 
       null, requestId, e);
   ```

5. **Parse error** (after response received):
   ```java
   throw new RpcException(-32700, 
       "Unable to parse JSON-RPC response for method " + method, 
       body, requestId, e);
   ```

**Important**: For error paths that occur before we allocate a `requestId` (e.g., failures building the request payload), pass `null` to `RpcException`. For all errors after `requestId` is allocated, always pass the actual `requestId`.

---

### 5. Optional Enhancements (Out of Scope for P0.13)

These can be added in future phases:

* **Response correlation**: Return `RpcResult<T>` record from `HttpBraneProvider.send()` to include both result and `requestId`.
* **Transaction logs**: Include `requestId` in `[TX-SEND]`, `[TX-REVERT]` logs in `DefaultWalletClient`.
* **Retry correlation**: Log which `requestId` triggered a retry in `RpcRetry`.

---

## Steps

1. [x] ~~Add an `AtomicLong nextRequestId` to `HttpBraneProvider`~~ (already exists as `ids`)
2. [ ] Extract numeric `requestId` in `HttpBraneProvider.send()` before String conversion
3. [ ] Extend `RpcException` to carry optional `requestId` field
4. [ ] Update `RpcException` constructors and add `augmentMessage()` helper
5. [ ] Update all `RpcException` throw sites in `HttpBraneProvider` to pass `requestId`
6. [ ] Update `DebugLogger` calls to include `id=%d` in `[RPC]` and `[RPC-ERROR]` logs
7. [ ] Add unit tests for request ID correlation
8. [ ] Update integration tests to verify logs contain request IDs

---

## Testing

### A. Request ID Assignment (Unit)

**Test file**: `brane-rpc/src/test/java/io/brane/rpc/HttpBraneProviderTest.java` (new file)

1. **Monotonic IDs**
   * Make two consecutive RPC calls
   * Capture the JSON-RPC request bodies (via mock HTTP client)
   * Assert the request JSON contains `"id": "1"` and `"id": "2"` (Brane currently serializes ids as strings)
   * Ensure IDs increment sequentially

2. **Unique per call**
   * Make calls to different methods (`eth_blockNumber`, `eth_chainId`)
   * Assert each gets a unique ID

3. **Thread safety** (optional)
   * Make concurrent RPC calls from multiple threads
   * Assert all IDs are unique (no duplicates)

---

### B. RpcException Request ID (Unit)

**Test file**: `brane-core/src/test/java/io/brane/core/error/RpcExceptionTest.java` (new file)

1. **Exception carries requestId**
   * Create `new RpcException(-32000, "test", null, 42L, null)`
   * Assert:
     * `exception.requestId() == 42`
     * `exception.getMessage()` contains `"[requestId=42]"`
     * `exception.code() == -32000`

2. **Null requestId supported**
   * Create `new RpcException(-32000, "test", null, null, null)`
   * Assert:
     * `exception.requestId() == null`
     * `exception.getMessage()` equals `"test"` (no prefix)

3. **Backward compatibility** (if keeping old constructor)
   * Verify old constructor still works for migration period

---

### C. Debug Log Correlation (Integration)

**Test file**: `brane-rpc/src/test/java/io/brane/rpc/HttpBraneProviderDebugTest.java` (new file)

1. **[RPC] log includes id**
   * Enable debug mode: `BraneDebug.setEnabled(true)`
   * Make a single RPC call via `HttpBraneProvider`
   * Capture SLF4J logs (using `ListAppender` or similar)
   * Assert log line contains:
     * `[RPC]`
     * `id=1`
     * `method=eth_blockNumber` (or the method called)

2. **[RPC-ERROR] log includes id**
   * Configure mock HTTP client to throw `IOException`
   * Make RPC call
   * Capture logs
   * Assert `[RPC-ERROR]` log contains:
     * `id=1`
     * `method=<method>`
     * Error details

3. **Multiple requests have distinct IDs**
   * Make 3 consecutive calls
   * Assert logs show `id=1`, `id=2`, `id=3`

---

### D. Integration with Retry Logic (Verification)

**Test file**: Extend existing `DefaultWalletClientTest.java`

1. **Each retry attempt gets new ID**
   * Configure a fake or mock provider to fail first call, succeed second
   * Enable debug logging
   * Send transaction (triggers retry from Phase 2.8)
   * Capture logs
   * Assert:
     * First attempt: `[RPC] id=N ...`
     * Retry attempt: `[RPC] id=N+1 ...`
   * This ensures retries don't reuse request IDs

---

## Verification Checklist

Before marking Phase 2.9 complete:

- [ ] All `RpcException` instances in logs and error messages show `[requestId=X]`
- [ ] All `[RPC]` debug logs include `id=X` parameter
- [ ] All `[RPC-ERROR]` debug logs include `id=X` parameter
- [ ] Request IDs increment monotonically across multiple calls
- [ ] No compilation errors or deprecation warnings
- [ ] All unit tests pass
- [ ] Full integration test suite passes (including retry scenarios)
- [ ] Example: Run `DebugIntegrationTest` and verify logs contain request IDs