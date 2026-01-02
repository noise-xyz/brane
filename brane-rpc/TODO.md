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

### MED-2: `RpcRetry` backoff missing jitter
**Location:** `RpcRetry.java:206-209`

**Problem:** Exponential backoff uses fixed delays without jitter, causing thundering herd problems when multiple clients retry simultaneously.

**Why It Matters:**
- Multiple clients hitting rate limits retry at exactly the same times
- Can overwhelm RPC providers during recovery

**Acceptance Criteria:**
- [ ] Add 10-25% random jitter to backoff delays:
  ```java
  private static long backoff(final int attempt) {
      final long delay = BACKOFF_BASE_MS * (1L << (attempt - 1));
      final long capped = Math.min(delay, BACKOFF_MAX_MS);
      return capped + ThreadLocalRandom.current().nextLong(capped / 4);
  }
  ```

---

### MED-3: `WebSocketProvider` subscription callback exceptions not surfaced to metrics
**Location:** `WebSocketProvider.java:540-546`

**Problem:** When a subscription callback throws an exception, it's logged but not reported to `BraneMetrics`. Callback errors invisible to monitoring.

**Acceptance Criteria:**
- [ ] Add `metrics.onSubscriptionCallbackError(subId, e)` method to `BraneMetrics`
- [ ] Call it when callback throws

---

### MED-4: Potential resource leak in `BraneAsyncClient` default executor lifecycle
**Location:** `BraneAsyncClient.java:50-74`

**Problem:** Static `DEFAULT_EXECUTOR_REF` is lazily initialized but may never be shut down. `shutdownDefaultExecutor()` exists but users must remember to call it.

**Why It Matters:**
- In web containers or apps with class reloading, can leak threads
- No automatic cleanup

**Acceptance Criteria:**
- [ ] Consider daemon executor OR shutdown hook for default executor
- [ ] Document cleanup requirements prominently

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

---

## Summary

| Severity | Count |
|----------|-------|
| CRITICAL | 0 |
| HIGH | 5 |
| MEDIUM | 6 |
| LOW | 7 |
| **Total** | **18** |
