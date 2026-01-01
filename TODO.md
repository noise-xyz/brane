# TODO: brane-rpc Principal Engineer Code Review (Round 2)

**Review Date:** 2025-12-31 (Fresh Review Post-Fix)
**Reviewer:** Principal Engineer (Opus 4.5)
**Module:** `brane-rpc/src/main/java/io/brane/rpc/`
**Previous Round:** 31 issues fixed (100% complete)
**This Round:** 14 issues fixed (100% complete)

---

## CRITICAL Issues (1) - COMPLETE

### CRIT-1: WebSocketProvider connect() Retry Loop Loses InterruptedException Context

**File:** `WebSocketProvider.java:398-405`
**Status:** Fixed in commit `TBD` - Throw immediately after restoring interrupt flag

**Acceptance Criteria:**
- [x] Throw immediately after restoring interrupt flag: `throw new RuntimeException("Connection attempt interrupted", ie);`
- [x] Test that interrupted threads receive proper exception type

---

## HIGH Issues (4) - COMPLETE

### HIGH-1: BranePublicClient Does Not Implement AutoCloseable - Provider Leak

**File:** `BranePublicClient.java:55-190`
**Status:** Fixed - Implemented `AutoCloseable` with proper resource cleanup

**Acceptance Criteria:**
- [x] Implement `AutoCloseable` on `BranePublicClient`
- [x] Store provider reference for cleanup
- [x] Add `close()` method that closes the underlying provider
- [x] Document that `BranePublicClient` must be closed when done

---

### HIGH-2: MulticallBatch.CallContext.complete() Silently Ignores Null Handle

**File:** `MulticallBatch.java:336-369`
**Status:** Fixed - Added explicit null check with informative error

**Acceptance Criteria:**
- [x] Replace silent return with `Objects.requireNonNull(handle(), "handle should never be null in executed context")`

---

### HIGH-3: DefaultPublicClient.subscribeToLogs Has Misleading @SuppressWarnings

**File:** `DefaultPublicClient.java:344-347`
**Status:** Fixed - Removed misleading annotation with explanatory comment

**Acceptance Criteria:**
- [x] Remove the `@SuppressWarnings` since the code IS type-safe
- [x] Added comment explaining TypeReference provides full type information

---

### HIGH-4: WebSocketProvider.handleEvent Does Not Schedule Timeout for Batched Requests

**File:** `WebSocketProvider.java:1037-1084`
**Status:** Fixed - Added timeout scheduling for batched requests

**Acceptance Criteria:**
- [x] Add timeout scheduling in `handleEvent()` matching `sendAsync()` behavior

---

## MEDIUM Issues (5) - COMPLETE

### MED-1: LogFilter.byContract Deprecated but No Non-Deprecated Single-Address Factory

**File:** `LogFilter.java:82-86`
**Status:** Fixed in commit `352c334`

**Acceptance Criteria:**
- [x] Un-deprecate `byContract(Address, List<Hash>)` - it's a valid convenience method

---

### MED-2: RpcRetry.isRetryableRpcError Missing Common Retryable Errors

**File:** `RpcRetry.java:164-182`
**Status:** Fixed in commit `df93754`

**Acceptance Criteria:**
- [x] Add common transient error patterns (rate limit, internal error, server busy)

---

### MED-3: DefaultWalletClient.throwRevertException Swallows Non-Revert Exceptions

**File:** `DefaultWalletClient.java:346-351`
**Status:** Fixed in commit `6c9dcf9`

**Acceptance Criteria:**
- [x] Log the original exception with stack trace
- [x] Include original exception as cause in the thrown `RevertException`

---

### MED-4: WebSocketConfig.nextPowerOf2 Can Overflow for Large Inputs

**File:** `WebSocketConfig.java:86-90`
**Status:** Fixed in commit `82ac516`

**Acceptance Criteria:**
- [x] Add bounds check: `if (value > (1 << 30)) throw new IllegalArgumentException(...)`
- [x] Test with boundary values near 2^30

---

### MED-5: BraneAsyncClient Static DEFAULT_EXECUTOR Never Closed

**File:** `BraneAsyncClient.java:46-47`
**Status:** Fixed in commit `946901f`

**Acceptance Criteria:**
- [x] Document that `DEFAULT_EXECUTOR` is intentionally static/long-lived

---

## LOW Issues (4) - COMPLETE

### LOW-1: JsonRpcResponse.resultAsMap() Has Inconsistent Exception Documentation

**File:** `JsonRpcResponse.java:67-76`
**Status:** Fixed in commit `ca79c80`

**Acceptance Criteria:**
- [x] Catch and wrap all exceptions in `IllegalArgumentException`

---

### LOW-2: MulticallInvocationHandler.handleObjectMethod Has Confusing equals() Logic

**File:** `MulticallInvocationHandler.java:78`
**Status:** Fixed in commit `caa929d`

**Acceptance Criteria:**
- [x] Rewrite for clarity: `case "equals" -> args != null && args.length > 0 && proxy == args[0];`

---

### LOW-3: BraneExecutors.newIoBoundExecutor() Returns Unnamed Threads

**File:** `BraneExecutors.java:72-74`
**Status:** Fixed in commit `f8eac17`

**Acceptance Criteria:**
- [x] Use `Thread.ofVirtual().name("brane-io-", 0).factory()` for consistency

---

### LOW-4: DefaultPublicClient Uses Deprecated call() Method Internally

**File:** `DefaultPublicClient.java:114-117`
**Status:** Fixed in commit `ef5df86`

**Acceptance Criteria:**
- [x] Move implementation to the new method
- [x] Have deprecated method delegate to the new one

---

## Summary

| Severity | Count | Fixed | Pending |
|----------|-------|-------|---------|
| Critical | 1 | 1 | 0 |
| High | 4 | 4 | 0 |
| Medium | 5 | 5 | 0 |
| Low | 4 | 4 | 0 |
| **Total** | **14** | **14** | **0** |

---

## What's Done Well

1. **Thread Safety Patterns**: Excellent use of `AtomicReference.compareAndExchange()` for chain ID caching, `ConcurrentHashMap` for pending requests, and `AtomicBoolean` for state flags.
2. **Resource Management**: The WebSocketProvider correctly tracks ownership of EventLoopGroup and subscriptionExecutor, only closing resources it created.
3. **Comprehensive Javadoc**: Most public APIs have thorough documentation including usage examples, thread safety notes, and exception documentation.
4. **Record Usage**: Proper use of Java 21 records for immutable data (CallRequest, LogFilter, BatchResult, etc.) with validation in compact constructors.
5. **Metrics Hooks**: The `BraneMetrics` interface provides good extensibility for observability without coupling to specific metrics libraries.
6. **Backpressure Handling**: WebSocketProvider properly handles backpressure with configurable limits and metrics callbacks.
7. **Error Hierarchy**: Well-designed exception hierarchy with specific exception types for different failure modes.
8. **Sealed Types**: Good use of sealed interfaces for `BlockTag` providing compile-time exhaustiveness checking.
9. **Exponential Backoff**: Reconnection logic properly implements exponential backoff with configurable maximum attempts.
10. **UTF-8 Handling**: The `writeUtf8Char` method correctly handles surrogate pairs for supplementary Unicode characters.
