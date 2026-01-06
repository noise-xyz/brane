# Code Review TODO - feat/sim-txns

Principal engineer review findings for eth_simulateV1 implementation.

---

## T2 - High Priority (Fix Before Merge)

### T2-1: [DONE] Fix null message handling in CallResult.fromMap()
**File:** `brane-rpc/src/main/java/io/brane/rpc/CallResult.java:57-58`
**Issue:** When `error.get("message")` is null, `String.valueOf()` returns literal `"null"` string - confusing UX.
**Fix:** Add null check and provide default message:
```java
String message = error != null && error.get("message") != null
    ? String.valueOf(error.get("message"))
    : "execution failed";
```

### T2-2: [DONE] Add null check for rawValue in SimulateResult.parseAssetChange()
**File:** `brane-rpc/src/main/java/io/brane/rpc/SimulateResult.java:120`
**Issue:** `rawValue` cast from `map.get("value")` without null check - NPE if malformed response.
**Fix:** Add null check before accessing rawValue:
```java
Map<String, Object> rawValue = (Map<String, Object>) map.get("value");
if (rawValue == null) {
    throw new IllegalArgumentException("Malformed asset change: missing 'value' field");
}
```

---

## T3 - Design Issues (Should Address)

### T3-1: [DONE] Remove confusing deprecated methods from RpcException
**File:** `brane-core/src/main/java/io/brane/core/error/RpcException.java:74-100`
**Issue:** Added deprecated `getCode()`/`getData()` on NEW code - suggests false backward compat. These methods don't serve any real backward compatibility purpose.
**Fix:** Remove the deprecated `getCode()` and `getData()` methods entirely since this is new code and no backward compatibility is needed.

### T3-2: [DONE] Hide or remove unimplemented fetchTokenMetadata from public API
**File:** `brane-rpc/src/main/java/io/brane/rpc/SimulateRequest.java:42,248-254`
**Issue:** `fetchTokenMetadata` documented as "NOT YET IMPLEMENTED" but exposed in public API. Users may set this flag expecting functionality that doesn't exist.
**Fix:** Either remove from public API until implemented, or throw `UnsupportedOperationException` if set to `true` (fail-fast).

### T3-3: [DONE] Document multi-block simulation limitation
**File:** `brane-rpc/src/main/java/io/brane/rpc/SimulateResult.java:83-111`
**Issue:** `fromList()` only processes first block - subsequent blocks silently ignored. eth_simulateV1 spec supports multi-block simulation.
**Fix:** Add clear Javadoc warning that only single-block simulation is currently supported.

---

## T4 - Minor (Nice to Have)

### T4-1: [DONE] Add missing @since tags to new public classes
**Files:** `SimulateCall.java`, `SimulateRequest.java`, `SimulateResult.java`, `CallResult.java`, `AccountOverride.java`
**Issue:** New public API classes lack `@since` version tags, inconsistent with existing classes like `CallRequest`.
**Fix:** Add `@since 0.2.0` to all new public classes.

### T4-2: [DONE] Add test for status=0 failure detection in CallResult
**File:** `brane-rpc/src/test/java/io/brane/rpc/SimulateCallsTest.java`
**Issue:** `CallResult.fromMap()` has dual failure detection (error field OR status=0), but only error field path is tested.
**Fix:** Add test case that verifies `status=0` correctly produces a `Failure` even without error field.

### T4-3: [DONE] Improve builder error message in SimulateRequest.Builder.build()
**File:** `brane-rpc/src/main/java/io/brane/rpc/SimulateRequest.java:265-267`
**Issue:** Builder silently converts null calls to empty list, then fails in record constructor. Error comes from wrong place.
**Fix:** Validate in `build()` and throw helpful exception:
```java
if (calls == null || calls.isEmpty()) {
    throw new IllegalStateException("At least one call must be added via call() or calls()");
}
```

### T4-4: [DONE] Add SimulateCall.of() convenience factory method
**File:** `brane-rpc/src/main/java/io/brane/rpc/SimulateCall.java`
**Issue:** `CallRequest` has `CallRequest.of(Address to, HexData data)` convenience factory. `SimulateCall` lacks this, requiring builder for simple cases.
**Fix:** Add `SimulateCall.of(Address to, HexData data)` factory method.

### T4-5: [DONE] Update spec link to final merged spec
**Files:** `brane-rpc/src/main/java/io/brane/rpc/exception/SimulateNotSupportedException.java:35`, `docs/public-client/simulate.mdx:122`
**Issue:** Links reference PR `https://github.com/ethereum/execution-apis/pull/484` instead of merged spec.
**Fix:** Update link to point to merged specification if available.

---

## Deferred (Future Work)

### DEFERRED-1: Consider consolidating SimulateCall and CallRequest
**Issue:** ~150 lines duplicated with nearly identical fields, validation, builders. Maintenance burden.
**Options:**
- `SimulateCall extends CallRequest` (inheritance)
- `SimulateCall` delegates to `CallRequest` internally (composition)
- Accept `CallRequest` in `SimulateRequest` and add simulation-specific metadata separately
**Decision:** Defer to future refactoring effort to avoid scope creep.

### DEFERRED-2: Document RpcException sealed hierarchy change
**Issue:** `RpcException` changed from `final` to `non-sealed` - affects exhaustive pattern matching.
**Action:** Document in release notes for 0.2.0.
