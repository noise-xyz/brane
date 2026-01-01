# TODO: brane-rpc Principal Engineer Code Review

**Review Date:** 2025-12-31 (Fresh Review)
**Reviewer:** Principal Engineer (Opus 4.5)
**Module:** `brane-rpc/src/main/java/io/brane/rpc/`

---

## CRITICAL Issues (5) - ✅ ALL FIXED

### CRIT-1: Race Condition in WebSocketProvider Slot Allocation (TOCTOU) ✅

**File:** `WebSocketProvider.java:590-611, 651-704`
**Fixed in:** `f26e878`

**Problem:** The slot allocation in `allocateSlot()` has a TOCTOU race condition. Between checking `existing.isDone()` and the CAS operation, another thread could:
1. Complete the existing future
2. Allocate and use that slot
3. Send a request

Now TWO requests share the same slot, and the first response will complete both futures with potentially wrong data.

**Acceptance Criteria:**
- [x] Use `ConcurrentHashMap<Long, CompletableFuture>` keyed by request ID instead of slot-based array
- [x] Or use monotonically increasing slot index with modular wrapping and timeout-based cleanup
- [x] Add integration tests simulating high-contention slot allocation

---

### CRIT-2: Unbounded Reconnect Loop Can Cause Resource Exhaustion ✅

**File:** `WebSocketProvider.java:896-910`
**Fixed in:** `b03e123`

**Problem:** If server is permanently down, creates infinite scheduled reconnect tasks with no backoff.

**Acceptance Criteria:**
- [x] Add maximum reconnect attempts (e.g., 10)
- [x] Implement exponential backoff (1s, 2s, 4s, 8s...)
- [x] After max attempts, set `closed=true` and fail all pending requests
- [x] Expose `reconnectAttempts` counter in metrics

---

### CRIT-3: Thread Safety Issue in Chain ID Caching (DefaultWalletClient) ✅

**File:** `DefaultWalletClient.java:462-485`
**Fixed in:** `3d8739f`

**Problem:** CAS race condition can return wrong chain ID when two threads call concurrently.

**Acceptance Criteria:**
- [x] Use `compareAndExchange` to detect if another thread won, validate winner's value
- [x] Or use lock for the slow path
- [x] Return value from `compareAndExchange` result, not second `get()`

---

### CRIT-4: Resource Leak - subscriptionExecutor Never Closed ✅

**File:** `WebSocketProvider.java:147, 854-894`
**Fixed in:** `7cd31cd`

**Problem:** `close()` method never shuts down `subscriptionExecutor`.

**Acceptance Criteria:**
- [x] Track if `subscriptionExecutor` is default (owned) or user-provided
- [x] In `close()`, shut down owned executor
- [x] Document that user-provided executors are NOT closed

---

### CRIT-5: NPE Risk in handleNotificationNode ✅

**File:** `WebSocketProvider.java:496-509`
**Fixed in:** `03994ee`

**Problem:** Malformed notification without `subscription` field throws NPE in Netty event loop.

**Acceptance Criteria:**
- [x] Null-check `params.get("subscription")` before `asText()`
- [x] Handle malformed notifications gracefully with logging
- [x] Never let exceptions escape to Netty's exceptionCaught

---

## HIGH Issues (9) - 4 Fixed, 5 Pending

### HIGH-1: Missing Null Check in HttpBraneProvider.send() ✅

**File:** `HttpBraneProvider.java:53-88`
**Fixed in:** `6cec2f6`

**Acceptance Criteria:**
- [x] Add `Objects.requireNonNull(method, "method")` at entry
- [x] Test with null method parameter

---

### HIGH-2: RpcRetry Swallows InterruptedException Context ✅

**File:** `RpcRetry.java:120-131`
**Fixed in:** `27affc4`

When `lastException != null`, the InterruptedException cause is lost.

**Acceptance Criteria:**
- [x] Include InterruptedException as suppressed exception
- [x] Test interrupt status is preserved

---

### HIGH-3: SmartGasStrategy Silently Downgrades EIP-1559 to Legacy ✅

**File:** `SmartGasStrategy.java:169-206`
**Fixed in:** `f9f6c49`

If user explicitly requested EIP-1559 but node returned null baseFee, silently falls back to legacy without warning.

**Acceptance Criteria:**
- [x] Log WARNING when falling back from EIP-1559 to legacy
- [ ] Consider throwing if user explicitly set `isEip1559=true`
- [x] Document behavior in Javadoc

---

### HIGH-4: MulticallBatch ThreadLocal Not Cleared on Exception ✅

**File:** `MulticallBatch.java:82, 164-174`
**Fixed in:** `833b53b`

**Acceptance Criteria:**
- [x] Clear ThreadLocal BEFORE throwing
- [x] Add `clearPending()` call in `recordCall` when detecting orphaned call

---

### HIGH-5: DefaultPublicClient.getBlockByTag Missing @Nullable

**File:** `DefaultPublicClient.java:284-307`

Interface declares `@Nullable BlockHeader`, but implementation lacks annotation.

**Acceptance Criteria:**
- [ ] Add `@Nullable` annotation to implementation
- [ ] Or use `Optional<BlockHeader>` (Java 21 best practice)

---

### HIGH-6: WebSocketProvider processResponseNode Silently Drops Responses

**File:** `WebSocketProvider.java:511-543`

If response ID cannot be parsed, response is silently dropped and caller's future never completes:

```java
if (idNode.isTextual()) {
    try {
        id = Long.parseLong(idNode.asText());
    } catch (Exception e) {
        log.warn(...);  // Falls through with id=-1
    }
}
// id=-1: response silently dropped, caller hangs forever
```

**Acceptance Criteria:**
- [ ] Log at ERROR level when response cannot be matched
- [ ] Track "orphaned responses" in metrics
- [ ] Consider timing out futures that never receive responses

---

### HIGH-7: sendTransactionAndWait Uses Wall Clock (Vulnerable to Clock Skew)

**File:** `DefaultWalletClient.java:277-308`

```java
final Instant deadline = Instant.now().plus(Duration.ofMillis(timeoutMillis));
while (Instant.now().isBefore(deadline)) { ... }
```

**Impact:** NTP sync or VM pause makes deadline comparison unreliable.

**Acceptance Criteria:**
- [ ] Use `System.nanoTime()` for elapsed time tracking
- [ ] `long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis)`

---

### HIGH-8: BatchHandle Double-Completion Throws Instead of Idempotent

**File:** `BatchHandle.java:75-80`

Throwing on double-completion is problematic if timeout handler and normal completion race.

**Acceptance Criteria:**
- [ ] Make completion idempotent (return false instead of throwing)
- [ ] Or document that callers MUST ensure single completion

---

### HIGH-9: RpcUtils.decodeHexBigInteger Returns ZERO for Empty String

**File:** `RpcUtils.java:139-148`

```java
public static BigInteger decodeHexBigInteger(final String hex) {
    if (hex == null || hex.isEmpty()) {
        return BigInteger.ZERO;  // "" != "0x0", could mask parsing bugs
    }
}
```

**Acceptance Criteria:**
- [ ] Throw `IllegalArgumentException` for empty string, OR
- [ ] Document explicitly that empty string = ZERO
- [ ] Add test cases clarifying expected behavior

---

## MEDIUM Issues (10) - Recommend

### MED-1: Code Duplication - toTxObject in SmartGasStrategy and DefaultWalletClient

**Files:** `SmartGasStrategy.java:274-286`, `DefaultWalletClient.java:426-436`

Nearly identical `buildTxObject` / `toTxObject` methods.

**Acceptance Criteria:**
- [ ] Extract to `RpcUtils.buildTxObject(TransactionRequest)`
- [ ] Reuse in both classes

---

### MED-2: Magic Number DEFAULT_CHUNK_SIZE = 500 Unexplained

**File:** `MulticallBatch.java:72`

```java
private static final int DEFAULT_CHUNK_SIZE = 500;  // Why 500?
```

**Acceptance Criteria:**
- [ ] Add Javadoc explaining derivation (e.g., payload size calculation)
- [ ] Consider making configurable per-provider

---

### MED-3: HttpBraneProvider.close() May Block Forever

**File:** `HttpBraneProvider.java:42-46`

```java
public void close() {
    httpClient.close();
    executor.close();  // May block waiting for tasks - no timeout
}
```

**Acceptance Criteria:**
- [ ] Use `executor.shutdownNow()` or `awaitTermination()` with timeout
- [ ] Document blocking behavior in Javadoc

---

### MED-4: Missing Javadoc on Subscription Interface

**File:** `Subscription.java:1-18`

```java
public interface Subscription {
    String id();        // No @return
    void unsubscribe(); // No @throws - idempotent?
}
```

**Acceptance Criteria:**
- [ ] Add full Javadoc with `@return`, `@throws`
- [ ] Document idempotency of `unsubscribe()`

---

### MED-5: BranePublicClient.Builder Leaks Provider on Build Failure

**File:** `BranePublicClient.java:159-172`

```java
public BranePublicClient build() {
    final BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
    final PublicClient publicClient = PublicClient.from(provider);  // If throws, provider leaks
    return new BranePublicClient(publicClient, profile);
}
```

**Acceptance Criteria:**
- [ ] Wrap in try-catch, close provider on exception
- [ ] Use try-with-resources pattern

---

### MED-6: LogParser Uses Raw List Type

**File:** `LogParser.java:50-51`

```java
@SuppressWarnings("unchecked")
final List<Map<String, Object>> rawLogs = MAPPER.convertValue(value, List.class);
```

**Acceptance Criteria:**
- [ ] Use `new TypeReference<List<Map<String, Object>>>() {}`
- [ ] Remove @SuppressWarnings

---

### MED-7: WebSocketProvider writeEscapedString Corrupts Supplementary Unicode

**File:** `WebSocketProvider.java:982-1020`

```java
for (int i = 0; i < s.length(); i++) {
    char c = s.charAt(i);  // Only handles BMP characters
    buf.writeByte(c);      // Corrupts surrogate pairs (emoji)
}
```

**Acceptance Criteria:**
- [ ] Use proper UTF-8 encoding for non-ASCII characters
- [ ] Test with emoji in method names

---

### MED-8: Inconsistent Factory Method Naming

- `WebSocketProvider.create()`
- `HttpBraneProvider.builder().build()`
- `PublicClient.from()`
- `DefaultWalletClient.from()` / `DefaultWalletClient.create()`

**Acceptance Criteria:**
- [ ] Standardize: `of()` for simple, `builder()` for complex, `from()` for conversion
- [ ] Document pattern in style guide

---

### MED-9: RetryExhaustedException serialVersionUID Policy Unclear

**File:** `RetryExhaustedException.java:39`

```java
private static final long serialVersionUID = 1L;
```

**Acceptance Criteria:**
- [ ] Add comment explaining serialVersionUID policy
- [ ] Consider generated UID based on class structure

---

### MED-10: CallRequest Allows Both Legacy and EIP-1559 Gas Fields

**File:** `CallRequest.java:49-58`

```java
public record CallRequest(
    BigInteger gasPrice,           // Legacy
    BigInteger maxFeePerGas,       // EIP-1559
    BigInteger maxPriorityFeePerGas
) { /* No validation prevents both being set */ }
```

**Acceptance Criteria:**
- [ ] Add validation in compact constructor
- [ ] Throw if both legacy and EIP-1559 fields are set

---

## LOW Issues (7) - Suggestions

### LOW-1: BraneAsyncClient DEFAULT_EXECUTOR Unnamed Threads

**File:** `BraneAsyncClient.java:46`

```java
private static final Executor DEFAULT_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
```

**Acceptance Criteria:**
- [ ] Use `Thread.ofVirtual().name("brane-async-", 0).factory()`

---

### LOW-2: JsonRpcError.code Should Be long Not int

**File:** `JsonRpcError.java:6`

JSON-RPC spec allows large error codes that could overflow `int`.

**Acceptance Criteria:**
- [ ] Change to `long` or document `int` limitation

---

### LOW-3: WebSocketConfig Validation Could Suggest Valid Values

**File:** `WebSocketConfig.java:104-111`

```java
throw new IllegalArgumentException(
    "maxPendingRequests must be a power of 2, got: " + maxPendingRequests);
// Could suggest: "got: 1000, try: 1024"
```

**Acceptance Criteria:**
- [ ] Suggest nearest power of 2 in error message

---

### LOW-4: RpcConfig Missing Validation

**File:** `RpcConfig.java:17-22`

No validation that URL is valid or timeouts are positive.

**Acceptance Criteria:**
- [ ] Validate URL format
- [ ] Validate timeouts > 0

---

### LOW-5: Test Coverage - WebSocket Edge Cases

No tests for:
- Reconnect under load
- Slot collision handling
- Timeout vs completion races

**Acceptance Criteria:**
- [ ] Add stress tests for concurrent request submission
- [ ] Test reconnect behavior with mock server

---

### LOW-6: LongAdder Fields Never Exposed via Metrics

**File:** `WebSocketProvider.java:44-45`

`totalRequests`, `totalResponses`, `totalErrors` fields exist but not exposed.

**Acceptance Criteria:**
- [ ] Expose via BraneMetrics
- [ ] Or remove unused fields

---

### LOW-7: DefaultPublicClient.SubscriptionImpl Ignores Unsubscribe Errors

**File:** `DefaultPublicClient.java:348-353`

```java
public void unsubscribe() {
    provider.unsubscribe(id);  // Errors not handled
}
```

**Acceptance Criteria:**
- [ ] Catch and log unsubscribe failures
- [ ] Make unsubscribe idempotent

---

## Summary

| Severity | Count | Fixed | Pending | Status |
|----------|-------|-------|---------|--------|
| Critical | 5 | 5 | 0 | ✅ 100% |
| High | 9 | 4 | 5 | 🟡 44% |
| Medium | 10 | 0 | 10 | ⬜ 0% |
| Low | 7 | 0 | 7 | ⬜ 0% |
| **Total** | **31** | **9** | **22** | **29% Complete** |

### Fixed Issues (by commit)

| Commit | Issue | Description |
|--------|-------|-------------|
| `f26e878` | CRIT-1 | Eliminate TOCTOU race in WebSocketProvider slot allocation |
| `b03e123` | CRIT-2 | Add max attempts and exponential backoff to WebSocket reconnect |
| `3d8739f` | CRIT-3 | Use compareAndExchange for thread-safe chain ID caching |
| `7cd31cd` | CRIT-4 | Close subscriptionExecutor to prevent resource leak |
| `03994ee` | CRIT-5 | Prevent NPE in handleNotificationNode for malformed notifications |
| `6cec2f6` | HIGH-1 | Add null check for method parameter in HttpBraneProvider.send() |
| `27affc4` | HIGH-2 | Preserve InterruptedException context in RpcRetry |
| `f9f6c49` | HIGH-3 | Log warning when SmartGasStrategy downgrades EIP-1559 to legacy |
| `833b53b` | HIGH-4 | Clear ThreadLocal before throwing in MulticallBatch.recordCall() |

---

## What's Done Well

1. **Thread Safety Patterns**: Good use of `AtomicReference`, `ConcurrentHashMap`, `AtomicBoolean`
2. **Comprehensive Javadoc**: Most public APIs have excellent documentation with examples
3. **Record Usage**: Proper use of records for DTOs
4. **Error Hierarchy**: Well-designed `BraneException` sealed hierarchy
5. **Builder Pattern**: Clean builder APIs for configuration
6. **Virtual Thread Support**: Appropriate use for I/O-bound work
7. **Metrics Integration**: Thoughtful `BraneMetrics` interface
8. **Zero-Allocation Serialization**: ByteBuf-based JSON in WebSocketProvider
9. **Sealed Interfaces**: Good use of sealed types for `BlockTag`
10. **Defensive Null Checks**: Consistent `Objects.requireNonNull()` in constructors
