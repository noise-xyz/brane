# TODO: brane-contract Module Issues

This document captures all issues identified in the brane-contract module code review, with full context, acceptance criteria, and proposed fixes.

---

## HIGH PRIORITY

### 1. ContractOptions Fields Silently Ignored - COMPLETED

**Status:** Fixed in commit `fix: apply ContractOptions fields to transactions`

**Problem:**
`ContractOptions` allows users to configure `gasLimit`, `transactionType`, and `maxPriorityFee`, but these values are **never applied** to transactions. Users configure these expecting them to work, but they have zero effect.

**Resolution:**
Added `buildTransactionRequest()` method in `ContractInvocationHandler` that:
- Uses `TxBuilder.legacy()` when `transactionType` is LEGACY
- Uses `TxBuilder.eip1559()` otherwise
- Applies `gasLimit` from options
- Applies `maxPriorityFee` for EIP-1559 transactions

**Acceptance Criteria:**
- [x] When `gasLimit` is set in `ContractOptions`, it MUST be applied to the `TransactionRequest`
- [x] When `transactionType` is `LEGACY`, MUST use `TxBuilder.legacy()` instead of `TxBuilder.eip1559()`
- [x] When `maxPriorityFee` is set, MUST call `.maxPriorityFeePerGas()` on the builder
- [x] Add tests verifying each option is applied to the transaction

---

### 2. ReadWriteContract Cannot Send ETH with Transactions - COMPLETED

**Status:** Fixed in commit `feat: add payable support to ReadWriteContract`

**Problem:**
`ReadWriteContract.send()` and `sendAndWait()` hardcode `Wei.of(0)`, making it impossible to call payable functions that require ETH value.

**Resolution:**
Added overloaded methods that accept `Wei value` parameter:
- `send(String functionName, Wei value, Object... args)`
- `sendAndWait(String functionName, Wei value, long timeoutMillis, long pollIntervalMillis, Object... args)`

**Acceptance Criteria:**
- [x] `ReadWriteContract` MUST support sending ETH value with transactions
- [x] Either add `sendWithValue(String functionName, Wei value, Object... args)` methods
- [x] Or accept `Wei` as first argument in varargs (consistent with `@Payable` pattern)
- [x] Add tests for payable function calls through `ReadWriteContract`

---

### 3. Missing Revert Handling in ContractInvocationHandler - COMPLETED

**Status:** Fixed in commit `fix: add revert handling to ContractInvocationHandler.invokeView()`

**Problem:**
`ContractInvocationHandler.invokeView()` does not catch `RpcException` or use `RevertDecoder`, while `ReadOnlyContract.call()` does. This creates inconsistent error handling where proxy-based calls get raw RPC exceptions instead of decoded `RevertException`.

**Resolution:**
Updated `invokeView()` to:
- Wrap RPC call in try-catch
- Check for null/blank response before decoding
- Call `RevertDecoder.throwIfRevert(e)` on `RpcException`

**Acceptance Criteria:**
- [x] `ContractInvocationHandler.invokeView()` MUST wrap call in try-catch
- [x] MUST call `RevertDecoder.throwIfRevert(e)` on `RpcException`
- [x] MUST check for null/blank response before decoding
- [x] Add tests verifying revert reasons are decoded through proxy binding

---

### 4. Missing String Return Type Support in BraneContract - COMPLETED

**Status:** Fixed in commit `feat: add String return type support in BraneContract`

**Problem:**
`BraneContract.validateReturnType()` does not support `String.class`, even though the ABI encoding/decoding layer fully supports strings. This blocks common ERC-20/ERC-721 view functions like `name()` and `symbol()`.

**Resolution:**
Added `String.class` support in `validateReturnType()` with `isStringType()` helper method.

**Acceptance Criteria:**
- [x] `validateReturnType()` MUST accept `String.class` for view functions
- [x] MUST validate that ABI output type is "string"
- [x] Add tests for `String` return type binding (e.g., ERC-20 `name()`)

---

## MEDIUM PRIORITY

### 5. Missing ABI Payable Validation for @Payable Annotation - COMPLETED

**Status:** Fixed in commit `fix: validate @Payable against ABI stateMutability`

**Problem:**
`@Payable` annotation validates that the method is not a view function, but does NOT verify that the ABI declares `stateMutability: "payable"`. Users can mark a non-payable function with `@Payable`, causing runtime failures.

**Resolution:**
- Added `isPayable()` method to `Abi.FunctionMetadata` record
- Added validation in `BraneContract.validateMethod()` to check ABI stateMutability
- Moved parameter count validation from `AbiBinding` to `BraneContract` for @Payable awareness

**Acceptance Criteria:**
- [x] When `@Payable` is present, MUST verify ABI function has `stateMutability: "payable"`
- [x] Throw `IllegalArgumentException` if ABI function is `nonpayable`
- [x] Add test for mismatched @Payable annotation vs ABI stateMutability

---

### 6. Null Check Missing in ContractInvocationHandler.invokeView() - COMPLETED

**Status:** Fixed as part of Issue #3

**Problem:**
If `publicClient.call()` returns null, the result is passed directly to `call.decode()`, causing NPE or confusing errors.

**Resolution:**
The null/blank check was added as part of the revert handling fix in Issue #3.

**Acceptance Criteria:**
- [x] MUST check if `output` is null or blank before decoding
- [x] MUST throw `AbiDecodingException` with clear message for empty responses
- [x] Add test for null/empty RPC response handling

---

### 7. Missing byte[]/HexData Return Type Support - COMPLETED

**Status:** Fixed in commit `feat: add byte[]/HexData return type support for bytes outputs`

**Problem:**
`validateReturnType()` does not support `byte[]` or `HexData` return types, even though ABI layer supports them. Functions returning `bytes` or `bytesN` cannot be bound.

**Resolution:**
Added `byte[].class` and `HexData.class` support in `validateReturnType()` with `isBytesType()` helper that matches both dynamic "bytes" and fixed-size "bytesN" patterns.

**Acceptance Criteria:**
- [x] `validateReturnType()` MUST accept `byte[].class` and `HexData.class`
- [x] MUST validate ABI output is "bytes" or "bytesN" pattern
- [x] Add tests for bytes return type binding

---

### 8. Inconsistent API Behaviors Across Contract Facades - COMPLETED

**Status:** Fixed as part of Issue #3

**Problem:**
Three contract APIs have different capabilities, creating confusion:

| Feature | BraneContract.bind() | ReadOnlyContract | ReadWriteContract |
|---------|---------------------|------------------|-------------------|
| Revert decoding | NO | YES | Delegated |
| Null check | NO | YES | Delegated |

**Resolution:**
Issue #3 fix added revert handling and null checks to `ContractInvocationHandler`, making behavior consistent.

**Acceptance Criteria:**
- [x] All contract APIs MUST have consistent error handling (revert decoding)
- [x] Document differences in Javadoc if intentional
- [ ] Consider unifying implementations via shared helper methods (deferred - not strictly necessary)

---

### 9. Missing Array/List Return Type Support - COMPLETED

**Status:** Fixed in commit `feat: add List/array return type support for array outputs`

**Problem:**
`validateReturnType()` does not handle functions returning arrays. The ABI layer supports array decoding to `List<T>`, but validation rejects it.

**Resolution:**
Added `List.class` and array type support in `validateReturnType()` with `isArrayType()` helper using existing `ARRAY_PATTERN`.

**Note:** Fixed-size array support (e.g., `uint256[3]`) is limited by a pre-existing issue in `brane-core`'s ABI parser. Dynamic arrays (e.g., `address[]`) work correctly.

**Acceptance Criteria:**
- [x] `validateReturnType()` MUST accept `List.class` and array types
- [x] MUST validate ABI output is array type
- [x] Add tests for array return type binding

---

## LOW PRIORITY

### 10. ContractOptions Should Be a Record - DEFERRED

**Status:** Not implemented (low priority, optional)

**Problem:**
`ContractOptions` is 263 lines of boilerplate for 5 immutable fields. Java 16+ records provide cleaner implementation.

**Acceptance Criteria:**
- [ ] Consider converting to record with compact constructor for validation
- [ ] Maintain builder pattern for ergonomic construction
- [ ] No functional change required

**Note:** This is a style improvement, not a bug. Deferred as it doesn't affect functionality.

---

### 11. Missing Javadoc for Thrown Exceptions on Proxy Methods - COMPLETED

**Status:** Fixed in commit `docs: document runtime exceptions thrown by proxy methods`

**Problem:**
`BraneContract.bind()` documents its own exceptions, but doesn't mention that invoked proxy methods can throw `RpcException`, `RevertException`, `AbiEncodingException`, or `AbiDecodingException`.

**Resolution:**
Added "Runtime Exceptions from Proxy Methods" section to class Javadoc documenting:
- View/Pure functions: `RpcException`, `RevertException`, `AbiDecodingException`
- State-changing functions: `RpcException`, `AbiEncodingException`

**Acceptance Criteria:**
- [x] Add Javadoc section describing exceptions thrown by bound methods
- [x] Document that view methods may throw `RpcException`, `RevertException`
- [x] Document that write methods may throw `RpcException`

---

## Summary

| # | Issue | Severity | Type | Status |
|---|-------|----------|------|--------|
| 1 | ContractOptions fields ignored | HIGH | Bug | COMPLETED |
| 2 | ReadWriteContract cannot send ETH | HIGH | API Limitation | COMPLETED |
| 3 | Missing revert handling in proxy | HIGH | Bug | COMPLETED |
| 4 | Missing String return type | HIGH | Bug | COMPLETED |
| 5 | No ABI payable validation | MEDIUM | Bug | COMPLETED |
| 6 | Null check missing in proxy | MEDIUM | Bug | COMPLETED |
| 7 | Missing bytes return types | MEDIUM | API Limitation | COMPLETED |
| 8 | Inconsistent API behaviors | MEDIUM | API Design | COMPLETED |
| 9 | Missing array return types | MEDIUM | API Limitation | COMPLETED |
| 10 | ContractOptions should be record | LOW | Style | DEFERRED |
| 11 | Missing Javadoc for exceptions | LOW | Documentation | COMPLETED |

---

## Completion Status

**10 of 11 issues completed** (Issue #10 deferred as optional style improvement)

All HIGH and MEDIUM priority issues have been addressed. The fixes are available on the `fix/brane-contract` branch.
