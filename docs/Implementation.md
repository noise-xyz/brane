# Brane Concurrency & Async Upgrades – Implementation Plan

> **Status:** Draft v2 (Revised based on codebase review)  
> **Last Updated:** 2025-12-09  
> **Branch:** `feature/async-upgrades-plan`

---

## 0. North Star

**Goals**

- Keep `BraneProvider.send()` as the canonical, synchronous abstraction.
- Make Brane **Loom-native** (virtual threads first) for blocking flows.
- Make WebSocket + Disruptor **safe and predictable** under load (threading, backpressure, timeouts).
- Be **async-friendly** without forcing any particular async framework (Reactor/Rx/etc.) on users.

**Non-Goals**

- Do **not** convert the SDK into a reactive `Mono`/`Flux`–only API.
- Do **not** hide complex concurrency that surprises HFT/MM/enterprise/RPC users.

---

## Current State Analysis

### What Already Exists

| Component | Location | Status |
|-----------|----------|--------|
| `BraneProvider.send()` sync interface | `brane-rpc/.../BraneProvider.java` | ✅ Stable |
| `HttpBraneProvider` with virtual threads | `HttpBraneProvider.java:29` | ✅ Uses `newVirtualThreadPerTaskExecutor()` |
| `WebSocketProvider` with Netty + Disruptor | `WebSocketProvider.java` | ⚠️ Needs callback safety |
| `RpcRetry` for transient failures | `RpcRetry.java` | ✅ Linear backoff implemented |
| `RpcConfig` for HTTP settings | `RpcConfig.java` | ⚠️ HTTP-only, no WebSocket config |
| `DEFAULT_TIMEOUT_MS` constant | `WebSocketProvider.java:64` | ❌ Declared but **never used** |

### Key Observations from Code Review

1. **Subscription callbacks run on Netty I/O thread** (`handleNotificationNode` lines 347-358) — blocking callbacks will stall all WebSocket I/O.

2. **No slot collision detection** — `slots[slot] = future` overwrites without checking if slot is occupied (line 447).

3. **`DEFAULT_TIMEOUT_MS = 60_000`** is declared but never wired into request lifecycle — no per-request timeout implemented.

4. **Hard-coded Disruptor config**:
   - Ring buffer size: 4096 (line 131)
   - Wait strategy: `YieldingWaitStrategy` (line 134)
   - Max pending requests: 65536 (line 62)

5. **`WebSocketProvider.send()`** correctly delegates to `sendAsync(...).join()` (line 421) — runs on caller thread ✅

6. **No `WebSocketConfig`** record exists — all configuration is hard-coded.

---

## 1. Canonical Provider Contract & Public Story

### 1.1 Stabilize `BraneProvider` as Sync Canonical Interface

- [x] Confirm `BraneProvider` remains (**VERIFIED** — interface is stable):

  ```java
  public interface BraneProvider {
      JsonRpcResponse send(String method, List<?> params) throws RpcException;

      default String subscribe(String method, List<?> params, Consumer<Object> callback)
              throws RpcException {
          throw new UnsupportedOperationException("This provider does not support subscriptions");
      }

      default boolean unsubscribe(String subscriptionId) throws RpcException {
          throw new UnsupportedOperationException("This provider does not support subscriptions");
      }
  }
  ```

- [x] Verify all providers implement `send()` as the **primary** entrypoint (**VERIFIED**).
- [x] Ensure that sync `send()` in `WebSocketProvider` **never runs on the Netty I/O thread** (**VERIFIED** — line 421 uses `sendAsync(...).join()` on caller thread).

  - [ ] Add a unit/integration test that asserts `send()` is not executed on `brane-netty-io`.

### 1.2 Document the Concurrency Philosophy

- [ ] Update `README.md` / `AGENT.md` with a short section:

  - [ ] "`BraneProvider.send()` is the canonical API."
  - [ ] "Brane is synchronous by default and **Loom-native**: use `Executors.newVirtualThreadPerTaskExecutor()` for scalable concurrency."
  - [ ] "Async helpers (`sendAsync`, `sendAsyncBatch`, and future adapters) are **optional**, not required."

---

## 2. WebSocketProvider: Threading & Callback Safety

### 2.1 Move Subscription Callbacks Off Netty I/O Thread

**Goal:** No user callback runs on the `brane-netty-io` event loop by default.

- [ ] Add a configurable subscription executor to `WebSocketProvider`:

  ```java
  private volatile Executor subscriptionExecutor =
      Executors.newVirtualThreadPerTaskExecutor(); // default

  public void setSubscriptionExecutor(Executor executor) {
      this.subscriptionExecutor = Objects.requireNonNull(executor);
  }
  ```

- [ ] Update `handleNotificationNode`:

  ```java
  Consumer<JsonRpcResponse> listener = subscriptions.get(subId);
  if (listener != null) {
      JsonRpcResponse response = new JsonRpcResponse("2.0", result, null, null);
      subscriptionExecutor.execute(() -> listener.accept(response));
  }
  ```

- [ ] Add tests:

  - [ ] Callback thread name is **not** `brane-netty-io`.
  - [ ] Custom executor via `setSubscriptionExecutor(...)` is respected.

- [ ] Update docs:

  - [ ] Document default behavior: callbacks on virtual threads.
  - [ ] Warn: "Do not block the Netty I/O thread; use the subscription executor for heavy work."

---

## 3. Backpressure & In-Flight Request Limits

### 3.1 Configurable Pending Request Limit

- [ ] Make `MAX_PENDING_REQUESTS` configurable via `RpcConfig` / `WebSocketConfig`:

  ```java
  public final class WebSocketConfig {
      int maxPendingRequests = 65536; // default
      int ringBufferSize = 4096;      // default
      // ...
  }
  ```

- [ ] Replace hard-coded `MAX_PENDING_REQUESTS` with config.

- [ ] When choosing a slot:

  ```java
  int slot = (int) (id & SLOT_MASK);
  if (slots[slot] != null) {
      // Too many in-flight requests; fail fast
      CompletableFuture<JsonRpcResponse> failed = new CompletableFuture<>();
      failed.completeExceptionally(new RpcException(
          TOO_MANY_IN_FLIGHT,
          "Pending requests limit reached",
          null
      ));
      return failed;
  }
  ```

- [ ] Optionally: consider a `Semaphore` to enforce a strict in-flight cap.

- [ ] Add tests:

  - [ ] When limit is reached, `sendAsync` returns a failed `CompletableFuture`.
  - [ ] No slot overwrites occur under overload conditions.

### 3.2 Configurable Ring Buffer Size

- [ ] Surface ring buffer size (currently 4096) via config.
- [ ] Ensure Disruptor is initialized using the configurable size.
- [ ] Add tests for:

  - [ ] Small ring buffer (sanity).
  - [ ] Large ring buffer (no regression).

---

## 4. Per-Request Timeouts

**Goal:** No caller is stuck with a permanently pending future.

### 4.1 Async API with Timeout

- [ ] Add overload to `WebSocketProvider`:

  ```java
  public CompletableFuture<JsonRpcResponse> sendAsync(
      String method,
      List<?> params,
      Duration timeout
  )
  ```

- [ ] Implementation outline:

  - [ ] Create `CompletableFuture<JsonRpcResponse> future`.
  - [ ] Store in `slots[slot]`.
  - [ ] Schedule a timeout using either:

    - Netty `EventLoop.schedule(...)`, or
    - A dedicated `ScheduledExecutorService`.
  - [ ] On timeout:

    - [ ] If future not completed, `completeExceptionally`.
    - [ ] Clear slot.
    - [ ] Optionally record metrics.

- [ ] Add a default timeout in config (e.g. `Optional<Duration> defaultRequestTimeout`).

- [ ] Ensure `send()` uses the default timeout when appropriate, or leave as "no timeout" and document that the user should decide.

- [ ] Add tests:

  - [ ] Request times out and future completes exceptionally.
  - [ ] Late responses after timeout don't blow up (slot already cleared).

---

## 5. Loom-Friendliness & CPU vs I/O Guidelines

### 5.1 Audit HTTP Provider for Loom

- [ ] Review `HttpBraneProvider`:

  - [ ] Confirm no long-lived `synchronized` blocks in hot paths.
  - [ ] Confirm no heavy CPU work in `send()`; it should be mostly HTTP + light parsing.

- [ ] Add examples to docs:

  ```java
  try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
      var provider = HttpBraneProvider.create(config);

      for (int i = 0; i < 10_000; i++) {
          exec.submit(() -> {
              JsonRpcResponse res =
                  provider.send("eth_getBlockByNumber", List.of("latest", false));
              // process res
          });
      }
  }
  ```

### 5.2 Separate CPU-Bound Work

- [ ] Identify CPU-heavy components (e.g. crypto, RLP, heavy JSON parsing).

- [ ] Create a `CpuExecutor` helper:

  ```java
  public final class BraneExecutors {
      public static ExecutorService newCpuBoundExecutor() {
          int cores = Runtime.getRuntime().availableProcessors();
          return Executors.newFixedThreadPool(cores);
      }
  }
  ```

- [ ] Document guidance:

  - I/O-bound work → virtual threads.
  - CPU-bound work → bounded platform-thread executors.

---

## 6. Async Adapters (Optional Facade)

### 6.1 `BraneAsyncClient` Facade

- [ ] Create a small async wrapper around `BraneProvider`:

  ```java
  public final class BraneAsyncClient {
      private final BraneProvider provider;
      private final Executor executor;

      public BraneAsyncClient(BraneProvider provider, Executor executor) {
          this.provider = Objects.requireNonNull(provider);
          this.executor = Objects.requireNonNull(executor);
      }

      public CompletableFuture<JsonRpcResponse> sendAsync(String method, List<?> params) {
          return CompletableFuture.supplyAsync(
              () -> provider.send(method, params),
              executor
          );
      }
  }
  ```

- [ ] If `provider` is an instance of `WebSocketProvider`, consider delegating to its native `sendAsync` for efficiency:

  - [ ] But keep this an implementation detail to avoid tight coupling.

- [ ] Add docs:

  - [ ] "If you want futures, use `BraneAsyncClient` and supply your own executor (e.g. virtual threads)."
  - [ ] "The core SDK remains sync; async is opt-in."

---

## 7. Disruptor Tuning & Netty Integration

### 7.1 Configurable Disruptor Wait Strategy

- [ ] Introduce a configurable wait strategy field:

  ```java
  public enum WaitStrategyType {
      YIELDING,
      BLOCKING
      // potential room for more later
  }
  ```

- [ ] In `WebSocketConfig`, add:

  ```java
  WaitStrategyType waitStrategyType = WaitStrategyType.YIELDING;
  ```

- [ ] Switch Disruptor initialization:

  ```java
  WaitStrategy waitStrategy =
      config.waitStrategyType() == WaitStrategyType.BLOCKING
          ? new BlockingWaitStrategy()
          : new YieldingWaitStrategy();

  this.disruptor = new Disruptor<>(
      RequestEvent::new,
      config.ringBufferSize(),
      threadFactory,
      ProducerType.MULTI,
      waitStrategy
  );
  ```

- [ ] Document recommended choices:

  - Yielding: ultra-low latency, HFT / MEV.
  - Blocking: CPU-conscious, enterprise batch.

### 7.2 EventLoopGroup Ownership

- [ ] Add optional `EventLoopGroup` parameter to `WebSocketProvider` factory:

  - [ ] Default: single-threaded `NioEventLoopGroup(1, ...)`.
  - [ ] Advanced users: can plug in their own group.

- [ ] Document:

  - [ ] "Default is a single I/O thread for minimal context switching."
  - [ ] "You may supply your own EventLoopGroup if integrating with an existing Netty stack."

---

## 8. Observability & Metrics

### 8.1 Metrics Hooks

- [ ] Define a simple metrics interface:

  ```java
  public interface BraneMetrics {
      void onRequestStarted();
      void onRequestCompleted(Duration latency);
      void onRequestTimeout();
      void onTooManyInFlight();
      void onRingBufferFull();
  }
  ```

- [ ] Allow injecting a `BraneMetrics` instance into `WebSocketProvider`.

- [ ] Instrument:

  - [ ] Request start/end.
  - [ ] Timeouts.
  - [ ] Backpressure rejections.
  - [ ] Ring buffer saturation (if detectable).

- [ ] Add basic in-memory implementation for tests / examples.

---

## 9. Benchmarks & Regression Tests

### 9.1 Fix a Baseline Benchmark Suite

- [ ] Create a small `brane-benchmarks` module (JMH or harness-based) that:

  - [ ] Compares:

    - `HttpBraneProvider` (Loom)
    - `WebSocketProvider` with `sendAsync`
    - `WebSocketProvider` with `sendAsyncBatch`
    - web3j baseline
  - [ ] Runs against a local devnet (Anvil/Hardhat).

- [ ] Track:

  - [ ] Ops/s for simple RPC (e.g., `eth_blockNumber`, `eth_getBalance`).
  - [ ] Tail latency distribution if feasible.

- [ ] Document current numbers in `/docs/perf.md`.

---

## 10. Documentation & Examples

- [ ] Add "Concurrency & Async" section to README:

  - [ ] Examples:

    - [ ] Simple sync HTTP usage with Loom.
    - [ ] WebSocket async usage with `sendAsyncBatch`.
    - [ ] Subscriptions with safe callback executor.
    - [ ] Using `BraneAsyncClient` with custom executor.
- [ ] Cross-link from AGENT.md so agents and contributors follow the same mental model.

---

## Appendix: Open Questions / Future Work

- [ ] Should we add structured concurrency helpers (`StructuredTaskScope`) around common multi-call patterns?
- [ ] Do we want optional integration modules (`brane-reactor`, `brane-rxjava`) later?
- [ ] How far do we go with adaptive wait strategies for Disruptor (beyond simple config)?
- [ ] Request ID Type: Consider using UUID or random ID instead of sequential `AtomicLong` to avoid predictability?

---

## Implementation Order (Suggested)

| Phase | Item | Priority | Effort | Dependencies |
|-------|------|----------|--------|--------------|
| **Phase 1: Safety** |
| 2.1 | Subscription callback safety | 🔴 Critical | S | None |
| 3.1 | Slot collision detection | 🔴 Critical | S | None |
| 4.1 | Per-request timeouts | 🟠 High | M | None |
| **Phase 2: Configuration** |
| 3.1-3.2 | `WebSocketConfig` record | 🟠 High | M | None |
| 7.1 | Configurable wait strategy | 🟡 Medium | S | WebSocketConfig |
| 7.2 | EventLoopGroup injection | 🟡 Medium | S | WebSocketConfig |
| **Phase 3: Observability** |
| 8.1 | Metrics interface | 🟡 Medium | M | Phase 1 |
| 6.1 | `BraneAsyncClient` facade | 🟢 Low | S | None |
| **Phase 4: Documentation** |
| 1.2 + 10 | Documentation updates | 🟡 Medium | S | Phase 1-2 |
| 10 | Canonical examples | 🟡 Medium | S | Phase 4 docs |

**Legend:** S = Small (1-2 hours), M = Medium (half-day), L = Large (1+ day)

---

## Verification Checklist

Before merging any phase:

- [ ] `./verify_all.sh` passes
- [ ] New features have unit tests
- [ ] Integration tests cover new async behavior
- [ ] No regression in benchmark numbers (run `brane-benchmark`)
- [ ] Documentation updated in `docs/` and/or `AGENT.md`
- [ ] Javadoc added for new public APIs

---

## Notes from Codebase Review

### Items Already Implemented ✅

1. **`BraneProvider.send()` is stable** — Interface is clean, no changes needed.
2. **`HttpBraneProvider` uses virtual threads** — Already calls `newVirtualThreadPerTaskExecutor()` at line 29.
3. **`WebSocketProvider.send()` delegates correctly** — Calls `sendAsync(...).join()` on caller thread.
4. **`RpcRetry` exists** — Linear backoff with transient error detection.

### Items That Need Attention ⚠️

1. **`DEFAULT_TIMEOUT_MS = 60_000`** is declared but **never used** — dead code.
2. **Subscription callbacks block Netty I/O** — High-priority fix.
3. **Slot array can overwrite** — No collision check before assignment.
4. **No `WebSocketConfig`** — All Disruptor/Netty settings hard-coded.

### Existing Test Coverage

- `WebSocketProviderTest` — Unit tests for `parseResponseFromByteBuf` only.
- `WebSocketIntegrationTest` — Integration tests for subscriptions and async batch.
- No tests for: timeout behavior, backpressure, thread assertion.

