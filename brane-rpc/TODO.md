# brane-rpc Code Review TODO

Principal Engineer Review - 2026-01-01

---

## CRITICAL

*No critical issues found in this review.*

---

## HIGH

### HIGH-1: Missing null validation for `hash` parameter in `getTransactionByHash`
**Location:** `DefaultPublicClient.java:86-87`

**Problem:** The `getTransactionByHash(Hash hash)` method does not validate that `hash` is non-null before calling `hash.value()`, causing NPE on null input.

**Evidence:**
```
CODE PATH:
1. User calls: client.getTransactionByHash(null)
2. → DefaultPublicClient.getTransactionByHash(Hash hash) at line 86
3. → hash.value() called at line 87
4. → NullPointerException thrown
```

**Inconsistency:** The `DefaultPublicClient` constructor validates `provider` with `Objects.requireNonNull`, but public API methods don't validate inputs.

**Acceptance Criteria:**
- [ ] Add `Objects.requireNonNull(hash, "hash")` at method start
- [ ] Apply same pattern to all public methods accepting domain types

---

### HIGH-2: Missing null validation for `address` parameter in `getBalance`
**Location:** `DefaultPublicClient.java:185-186`

**Problem:** The `getBalance(Address address)` method does not validate that `address` is non-null.

**Evidence:**
```
CODE PATH:
1. User calls: client.getBalance(null)
2. → address.value() called
3. → NullPointerException thrown
```

**Acceptance Criteria:**
- [ ] Add `Objects.requireNonNull(address, "address")` at method start

---

### HIGH-3: Missing null validation for `CallRequest` and `BlockTag` in typed `call` method
**Location:** `DefaultPublicClient.java:114`

**Problem:** The typed `call(CallRequest request, BlockTag blockTag)` method does not validate its parameters, causing NPE when null is passed.

**Acceptance Criteria:**
- [ ] Add `Objects.requireNonNull(request, "request")`
- [ ] Add `Objects.requireNonNull(blockTag, "blockTag")`

---

### HIGH-4: `DefaultPublicClient` constructor does not validate provider
**Location:** `DefaultPublicClient.java:70-72`

**Problem:** Unlike `DefaultWalletClient`, the `DefaultPublicClient` constructor does not validate that `provider` is non-null.

**Inconsistency:** `DefaultWalletClient` constructor at line 78 uses: `this.provider = Objects.requireNonNull(provider, "provider");`

**Acceptance Criteria:**
- [ ] Add `Objects.requireNonNull(provider, "provider")` in constructor

---

### HIGH-5: `MulticallBatch` uses deprecated `call(Map, String)` method internally
**Location:** `MulticallBatch.java:336`

**Problem:** `MulticallBatch.executeChunk()` calls the deprecated `publicClient.call(callObject, "latest")` method instead of the type-safe `call(CallRequest, BlockTag)`.

**Risk:** When the deprecated method is removed, this will break.

**Acceptance Criteria:**
- [ ] Refactor to use type-safe `call(CallRequest, BlockTag)` API
- [ ] Example:
  ```java
  final CallRequest callRequest = CallRequest.builder()
      .to(MULTICALL_ADDRESS)
      .data(new HexData(aggregate3Call.data()))
      .build();
  final HexData resultHex = publicClient.call(callRequest, BlockTag.LATEST);
  ```

---

## MEDIUM

### MED-1: API inconsistency - `RpcClient.call()` returns null vs other methods throw
**Location:** `RpcClient.java:96-98`

**Problem:** `RpcClient.call()` returns null when RPC result is null, but `DefaultPublicClient.getChainId()` and `getBalance()` throw `RpcException` on null results. Inconsistent error handling.

**Why It Matters:**
- Callers must know which methods return null vs throw
- Inconsistent behavior makes API harder to use correctly

**Acceptance Criteria:**
- [ ] Document null-return behavior clearly in all affected methods
- [ ] OR make behavior consistent (always throw on unexpected null)

---

### MED-2: `RpcRetry` backoff constants not configurable
**Location:** `RpcRetry.java:204-216`

**Problem:** Backoff constants (200ms base, 5000ms max, 10-25% jitter) are hardcoded. Some users may need different retry characteristics for their use cases.

**Note:** Jitter IS present (10-25%), but the configuration is not exposed.

**Acceptance Criteria:**
- [ ] Extract retry constants to `RpcRetryConfig` record
- [ ] Allow custom config injection via builder pattern
- [ ] Provide sensible defaults matching current behavior
- [ ] Document retry behavior in class Javadoc

---

### MED-3: `WebSocketProvider` subscription callback exceptions not surfaced to metrics
**Location:** `WebSocketProvider.java:540-546`

**Problem:** When a subscription callback throws an exception, it's logged but not reported to `BraneMetrics`. Callback errors invisible to monitoring.

**Acceptance Criteria:**
- [ ] Add `metrics.onSubscriptionCallbackError(subId, e)` method to `BraneMetrics`
- [ ] Call it when callback throws

---

### MED-4: BraneAsyncClient Default Executor Shutdown Race Condition
**Location:** `BraneAsyncClient.java:50-74, 111-123`

**Problem:** The `shutdownDefaultExecutor` method does not atomically replace the executor reference when shutting down. If called while another thread is in `getOrCreateDefaultExecutor`, there could be a race where:
1. Thread A calls `shutdownDefaultExecutor`, gets executor reference
2. Thread A calls `executor.shutdown()`
3. Thread B in `getOrCreateDefaultExecutor` sees the shutdown executor, creates new one
4. Thread A's `awaitTermination` could return while Thread B's new executor starts

**Why It Matters:** Confusing behavior for test cleanup; caller thinks executor is shut down but new one exists.

**Acceptance Criteria:**
- [ ] Add test demonstrating the race condition
- [ ] Implement atomic shutdown-and-nullify using `compareAndSet(executor, null)` pattern
- [ ] Ensure `shutdownDefaultExecutor` returns accurate state
- [ ] Document thread safety guarantees in Javadoc
- [ ] Consider daemon executor OR shutdown hook for default executor

---

### MED-5: `WebSocketProvider.sendAsyncBatch()` lacks timeout support
**Location:** `WebSocketProvider.java:792-817`

**Problem:** `sendAsyncBatch()` explicitly does NOT support timeouts (documented at line 782-786). Batch requests can hang indefinitely.

**Acceptance Criteria:**
- [ ] Add timeout parameter to `sendAsyncBatch()`
- [ ] OR apply default timeout internally

---

### MED-6: HttpBraneProvider error logging uses same mechanism as success
**Location:** `HttpBraneProvider.java:114-124`

**Problem:** HTTP errors (non-2xx) logged via `DebugLogger.logRpc()` same as successful requests. Harder to filter/alert on errors.

**Acceptance Criteria:**
- [ ] Use `log.warn()` or `log.error()` for HTTP errors in addition to DebugLogger
- [ ] Consider metrics hook for HTTP errors

---

### MED-7: MulticallBatch ThreadLocal Leak Risk
**Location:** `MulticallBatch.java:99, 208-217`

**Problem:** The `pendingCall` ThreadLocal stores call context between proxy invocation and `add()` call. If user code throws an exception between the proxy call and `add()`, the ThreadLocal is leaked until the thread is reused or destroyed.

```java
// User code pattern that leaks:
var batch = client.createBatch();
var proxy = batch.bind(MyContract.class, addr, abi);
try {
    var unused = proxy.myMethod(arg); // Records to ThreadLocal
    throw new RuntimeException("oops"); // Exception before add()
} catch (Exception e) {
    // pendingCall ThreadLocal still holds reference
}
// If thread is pooled (e.g., virtual thread), leak persists
```

**Why It Matters:** Virtual threads are pooled; leaked ThreadLocals accumulate. Memory leak can grow over time in long-running applications.

**Acceptance Criteria:**
- [ ] Document the cleanup requirement prominently in class Javadoc
- [ ] Add `IllegalStateException` detection when `recordCall` finds stale pending call
- [ ] Consider alternative API that doesn't require ThreadLocal (e.g., return builder from `bind()`)
- [ ] Add unit test verifying leak detection/cleanup behavior
- [ ] Update examples to show proper try-finally cleanup pattern

---

### MED-8: WebSocketProvider Reconnect Could Lose Pending Requests
**Location:** `WebSocketProvider.java:1018-1049`

**Problem:** When reconnecting, `failAllPending` is called from `channelInactive`. However:
1. Requests submitted during `failAllPending` iteration may be missed
2. If reconnect fails after such requests are submitted, their futures may never complete

**Why It Matters:** Request futures could hang in edge cases during network instability.

**Acceptance Criteria:**
- [ ] Add explicit state machine for connection state (CONNECTED, RECONNECTING, CLOSED)
- [ ] Reject new requests submitted during RECONNECTING state OR queue them for retry
- [ ] Add integration test simulating network instability during request submission
- [ ] Ensure all pending futures complete (success or failure) within timeout
- [ ] Document connection state behavior in Javadoc

---

### MED-9: SmartGasStrategy Silent Fallback to Legacy
**Location:** `SmartGasStrategy.java:269-284`

**Problem:** When EIP-1559 is requested but `baseFeePerGas` is unavailable, the default behavior is `FALLBACK_WARN` which logs a warning and silently switches to legacy transaction. This could surprise users who explicitly requested EIP-1559.

```java
case FALLBACK_WARN -> log.warn(
    "EIP-1559 transaction requested but baseFeePerGas unavailable from node; " +
    "falling back to legacy gas pricing...");
// Then proceeds to build LEGACY transaction
```

**Why It Matters:** User requests `TxBuilder.eip1559()` but silently receives legacy transaction with potentially worse gas pricing.

**Acceptance Criteria:**
- [ ] Consider changing default behavior to `THROW` (breaking change, needs migration guide)
- [ ] OR add prominent warning in builder Javadoc about fallback behavior
- [ ] Add unit test verifying fallback behavior is explicit
- [ ] Return metadata indicating actual transaction type used vs requested
- [ ] Document behavior differences from viem (which throws by default)

---

### MED-10: Deprecated API Unsafe Cast in DefaultPublicClient
**Location:** `DefaultPublicClient.java:138-150`

**Problem:** The deprecated `call(Map<String, Object>, String)` method unsafely casts map values to String without validation.

```java
final Address to = new Address((String) callObject.get("to"));  // ClassCastException if not String
final HexData data = callObject.containsKey("data")
        ? new HexData((String) callObject.get("data"))  // ClassCastException if not String
        : null;
```

**Why It Matters:** `ClassCastException` if caller passes non-String values, NPE if "to" key missing.

**Acceptance Criteria:**
- [ ] Add null check for "to" key with descriptive error message
- [ ] Add type validation before casting with descriptive error message
- [ ] Add unit test for invalid input handling
- [ ] Set removal version in `@Deprecated` annotation

---

## LOW

### LOW-1: Redundant instanceof pattern without Java 21 pattern matching
**Location:** `WebSocketProvider.java:488-497`

**Problem:** Uses old-style instanceof:
```java
if (msg instanceof FullHttpResponse) {
    FullHttpResponse response = (FullHttpResponse) msg;
```

**Acceptance Criteria:**
- [ ] Use Java 21 pattern matching:
  ```java
  if (msg instanceof FullHttpResponse response) {
  ```

---

### LOW-2: Magic string "latest" used in multiple places
**Location:**
- `DefaultPublicClient.java:186`
- `SmartGasStrategy.java:198`

**Problem:** String "latest" hardcoded instead of `BlockTag.LATEST.toRpcValue()`.

**Acceptance Criteria:**
- [ ] Replace with `BlockTag.LATEST.toRpcValue()` for consistency

---

### LOW-3: `RpcUtils` methods could be package-private
**Location:** `internal/RpcUtils.java`

**Problem:** Methods like `extractFromIterable`, `extractFromArray`, `stringValue` are public but in internal package. Should be package-private.

**Acceptance Criteria:**
- [ ] Change public methods to package-private
- [ ] OR add `@InternalApi` annotation

---

### LOW-4: `JsonRpcRequest` lacks Javadoc
**Location:** `JsonRpcRequest.java`

**Problem:** Public record has no documentation, unlike well-documented `JsonRpcResponse`.

**Acceptance Criteria:**
- [ ] Add class-level Javadoc matching style of `JsonRpcResponse`
- [ ] Add `@param` tags for each record component

---

### LOW-5: `LogParser` uses manual iteration instead of streams
**Location:** `internal/LogParser.java:79-84`

**Problem:** Topic parsing uses manual loop:
```java
final var topics = new ArrayList<Hash>();
if (topicsHex != null) {
    for (String t : topicsHex) {
        topics.add(new Hash(t));
    }
}
```

**Acceptance Criteria:**
- [ ] Refactor to:
  ```java
  final List<Hash> topics = topicsHex == null
      ? List.of()
      : topicsHex.stream().map(Hash::new).toList();
  ```

---

### LOW-6: `DefaultWalletClient.ValueParts` record undocumented
**Location:** `DefaultWalletClient.java:522-529`

**Problem:** Private `ValueParts` record has no documentation. Field meanings unclear to maintainers.

**Acceptance Criteria:**
- [ ] Add brief Javadoc explaining field purposes

---

### LOW-7: Inconsistent use of `var` vs explicit types
**Location:** Various files

**Problem:** Some places use `var` where type is unclear:
- `DefaultPublicClient.java:93`: `final var map = MAPPER.convertValue(...)` - type not obvious

**Acceptance Criteria:**
- [ ] Use `var` only when RHS makes type obvious
- [ ] Keep explicit types for domain types (Address, Wei, Hash)

---

### LOW-8: Inconsistent Null Handling in LogParser
**Location:** `LogParser.java:67-90`

**Problem:** Different null handling patterns for nullable fields:
```java
address != null ? new Address(address) : null,  // Can be null
data != null ? new HexData(data) : HexData.EMPTY,  // Defaults to EMPTY
txHash != null ? new Hash(txHash) : null,  // Can be null
```

**Why It Matters:** Callers must null-check selectively based on undocumented behavior.

**Acceptance Criteria:**
- [ ] Add `@Nullable` annotations to `LogEntry` record fields
- [ ] Document which fields can be null and under what circumstances in Javadoc
- [ ] Document the semantic difference between `HexData.EMPTY` and `null`

---

### LOW-9: RpcConfig/WebSocketConfig Duplicate URL Validation
**Location:**
- `RpcConfig.java:28-47`
- `WebSocketConfig.java:107-126`
- `HttpBraneProvider.java:205-224`

**Problem:** URL validation logic is duplicated across three classes with slight variations.

**Acceptance Criteria:**
- [ ] Extract URL validation to shared utility method in `internal` package
- [ ] Standardize error messages across HTTP and WebSocket validation
- [ ] Add unit test for URL validation utility

---

### LOW-10: BranePublicClient No Closed State Check
**Location:** `BranePublicClient.java:114-175`

**Problem:** After `close()` is called, methods still forward to the closed provider, causing implementation-specific errors.

**Acceptance Criteria:**
- [ ] Add closed state check in delegating methods OR document expected behavior
- [ ] Throw `IllegalStateException("Client is closed")` for clear error message
- [ ] Add unit test verifying behavior after close

---

### LOW-11: Missing @Nullable Annotations
**Location:** `JsonRpcError.java` and others

**Problem:** Some record components like `JsonRpcError.data` can be null but lack `@Nullable` annotation.

**Acceptance Criteria:**
- [ ] Audit all record components for nullable fields
- [ ] Add `@Nullable` annotations where appropriate
- [ ] Ensure IDE null analysis provides accurate warnings

---

### LOW-12: ObjectMapper Not Configurable
**Location:** `RpcUtils.java:49`

**Problem:** The shared `ObjectMapper` uses default configuration. Users may need custom serialization settings.

**Acceptance Criteria:**
- [ ] Consider allowing ObjectMapper injection
- [ ] OR document which Jackson modules/settings are used
- [ ] Ensure type serializers for Brane types are registered

---

### LOW-13: WebSocketProvider Test Coverage Gap
**Location:** `src/test/java/io/brane/rpc/`

**Problem:** WebSocketProvider's complex reconnection, batching, and timeout logic would benefit from more unit tests with mocked Netty components.

**Acceptance Criteria:**
- [ ] Add unit tests for `failAllPending` method
- [ ] Add unit tests for reconnect scheduling logic
- [ ] Add unit tests for timeout cancellation
- [ ] Add integration test for subscription recovery after reconnect

---

## What's Good

1. **Java 21 patterns**: Records, sealed interfaces, switch expressions used effectively
2. **Thread safety**: Proper `AtomicReference`, `AtomicBoolean`, `ConcurrentHashMap` usage
3. **Virtual threads**: Appropriate use for I/O-bound work
4. **Comprehensive Javadoc**: Most public classes well-documented
5. **Error handling**: `RpcRetry` has sophisticated retry logic with proper exception classification
6. **Type safety**: Heavy use of type-safe wrappers (`Address`, `Hash`, `HexData`, `Wei`)
7. **Resource management**: `AutoCloseable` implemented appropriately
8. **Defensive coding**: Input validation in constructors and builders
9. **Immutability**: Records and `List.copyOf()` prevent mutation
10. **WebSocket Performance**: Zero-allocation serialization, Disruptor batching
11. **Metrics Integration**: `BraneMetrics` interface allows custom monitoring

---

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 0 |
| HIGH | 5 |
| MEDIUM | 10 |
| LOW | 13 |
| **Total** | **28** |
