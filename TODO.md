# TODO: brane-contract Module Issues

This document captures issues identified in the brane-contract module code review (Round 2), with full context, acceptance criteria, and proposed fixes.

**Previous Review:** Round 1 identified 11 issues, of which 10 were fixed and 1 deferred. This round found 8 new issues.

---

## MEDIUM PRIORITY

### 1. AbiBinding Uses Non-Thread-Safe HashMap

**Files:**
- `brane-core/src/main/java/io/brane/core/abi/AbiBinding.java:11-24`

**Problem:**
The `AbiBinding` class uses a plain `HashMap` for caching method-to-metadata mappings. While this cache is populated entirely during construction, the class is used by `ContractInvocationHandler` which may be invoked from multiple threads (the proxy instance could be shared). Although the cache is read-only after construction, `HashMap` does not guarantee safe publication without explicit synchronization or using a thread-safe collection.

**Evidence:**
```java
private final Map<Method, Abi.FunctionMetadata> cache;

public AbiBinding(final Abi abi, final Class<?> contractInterface) {
    // ...
    this.cache = new HashMap<>();
    // ... populates cache
}
```

**Impact:** While unlikely to cause issues in practice (since the cache is fully populated before any reads), the JMM does not guarantee safe publication of HashMap contents without synchronization. A reader thread could theoretically see a partially constructed HashMap.

**Acceptance Criteria:**
- [x] Cache MUST be safely published for concurrent read access
- [x] Use `Map.copyOf()` after population to create an immutable snapshot
- [ ] ~~Use `Collections.unmodifiableMap()` wrapper~~ (not needed)
- [ ] ~~Initialize with `ConcurrentHashMap`~~ (not needed)
- [x] Add test demonstrating thread-safe access pattern

---

### 2. ReadWriteContract Ignores ContractOptions

**Files:**
- `brane-contract/src/main/java/io/brane/contract/ReadWriteContract.java:116-126, 166-181`

**Problem:**
`ReadWriteContract.send()` and `sendAndWait()` always use EIP-1559 transactions (`TxBuilder.eip1559()`) and do not apply any `ContractOptions` like gas limit or max priority fee. In contrast, `ContractInvocationHandler.buildTransactionRequest()` properly respects `ContractOptions` for transaction type, gas limit, and max priority fee.

This creates an API inconsistency: users who use `BraneContract.bind()` get configurable transaction options, but users who use `ReadWriteContract.from()` get hardcoded defaults.

**Evidence:**
```java
// ReadWriteContract.send() - no options support
public Hash send(final String functionName, final Wei value, final Object... args) {
    // ...
    final TransactionRequest request =
            TxBuilder.eip1559()  // Always EIP-1559, no legacy option
                    .to(address())
                    .value(value)
                    .data(new HexData(fnCall.data()))
                    .build();  // No gasLimit, no maxPriorityFee
    return walletClient.sendTransaction(request);
}
```

**Impact:** Users of `ReadWriteContract` cannot configure gas limits, priority fees, or use legacy transactions, leading to potential gas estimation issues or transaction failures on non-EIP-1559 chains.

**Acceptance Criteria:**
- [x] Add `ContractOptions` parameter to `ReadWriteContract.from()` factory method
- [x] Apply `transactionType` option (LEGACY vs EIP-1559) in `send()`/`sendAndWait()`
- [x] Apply `gasLimit` option to transaction requests
- [x] Apply `maxPriorityFee` option for EIP-1559 transactions
- [x] Add tests verifying options are applied correctly
- [x] Update Javadoc to document options support

**Proposed Fix:**
```java
public static ReadWriteContract from(
        final Address address,
        final String abiJson,
        final PublicClient publicClient,
        final WalletClient walletClient,
        final ContractOptions options) {  // New parameter
    // ... store options and use in send/sendAndWait
}
```

---

### 3. deployRequest() Missing Constructor Validation

**Files:**
- `brane-contract/src/main/java/io/brane/contract/BraneContract.java:268-292`

**Problem:**
The `deployRequest()` method does not validate that the provided arguments match the constructor parameters in the ABI. It simply passes arguments to `abi.encodeConstructor(args)` which may throw a cryptic error or produce incorrect encoding if types don't match.

**Evidence:**
```java
public static io.brane.core.model.TransactionRequest deployRequest(
        final String abiJson,
        final String bytecode,
        final Object... args) {
    // ... no validation of args against ABI constructor
    final HexData encodedArgs = abi.encodeConstructor(args);
    // ...
}
```

**Impact:** Users passing wrong argument types or counts get unhelpful error messages from the encoding layer rather than clear validation errors.

**Acceptance Criteria:**
- [x] Validate argument count matches ABI constructor input count (already in encodeConstructor)
- [x] Provide clear error message on argument count mismatch (already in encodeConstructor)
- [x] Add test for constructor argument validation
- [x] Error message MUST include expected vs actual argument count (already in encodeConstructor)

**Proposed Fix:**
```java
public static TransactionRequest deployRequest(
        final String abiJson,
        final String bytecode,
        final Object... args) {
    final Abi abi = Abi.fromJson(abiJson);

    // Get constructor metadata and validate
    final var constructor = abi.getConstructor();
    if (constructor.isPresent()) {
        final int expected = constructor.get().inputs().size();
        if (args.length != expected) {
            throw new IllegalArgumentException(
                    "Constructor expects " + expected + " arguments but got " + args.length);
        }
    } else if (args.length > 0) {
        throw new IllegalArgumentException(
                "Contract has no constructor but " + args.length + " arguments provided");
    }

    final HexData encodedArgs = abi.encodeConstructor(args);
    // ...
}
```

---

## LOW PRIORITY

### 4. Missing Tuple/Struct Return Type Support

**Files:**
- `brane-contract/src/main/java/io/brane/contract/BraneContract.java:358-424`

**Problem:**
The `validateReturnType()` method does not handle tuple/struct return types, which are common in Solidity contracts. If a user creates an interface method that returns a custom record type to match a tuple ABI output, validation will fail with "Unsupported return type" even though the underlying ABI decoder might support it.

**Evidence:**
```java
private static void validateReturnType(final Method method, final Abi.FunctionMetadata metadata) {
    // ... handles BigInteger, Address, Boolean, String, byte[], HexData, List, arrays
    // But no handling for tuple types or custom record classes
    throw new IllegalArgumentException(
            "Unsupported return type for view function "
                    + method.getName()
                    + ": "
                    + returnType.getSimpleName());
}
```

**Impact:** Functions returning structs/tuples cannot be bound through the proxy API, limiting usability for complex contracts.

**Acceptance Criteria:**
- [ ] Either add validation for record types mapping to tuple outputs, OR
- [ ] Document this limitation explicitly in class Javadoc
- [ ] If implementing: validate record component count matches tuple output count
- [ ] Add test for tuple return type (if implemented)

---

### 5. @Payable Silently Falls Back to Zero Value

**Files:**
- `brane-contract/src/main/java/io/brane/contract/ContractInvocationHandler.java:61-67`

**Problem:**
The payable handling logic silently falls back to `Wei.of(0)` if a method is annotated with `@Payable` but invoked without a `Wei` argument. While bind-time validation should catch interface mismatches, runtime invocation through reflection could bypass this.

**Evidence:**
```java
if (isPayable && invocationArgs.length > 0 && invocationArgs[0] instanceof Wei) {
    value = (Wei) invocationArgs[0];
    contractArgs = Arrays.copyOfRange(invocationArgs, 1, invocationArgs.length);
} else {
    value = Wei.of(0);  // Silent fallback - no warning or error
    contractArgs = invocationArgs;
}
```

**Impact:** Minor - validation at bind time should prevent this, but defense-in-depth would be better.

**Acceptance Criteria:**
- [ ] ~~Throw at runtime~~ (not needed - bind-time validation catches this)
- [x] Document explicitly that bind-time validation is relied upon
- [x] Add test verifying behavior when @Payable method called without Wei

---

### 6. ContractOptions.Builder Allows Zero Timeout

**Files:**
- `brane-contract/src/main/java/io/brane/contract/ContractOptions.java:194-200`

**Problem:**
The `Builder.timeout()` method only validates that the duration is not negative, but allows `Duration.ZERO`. A zero timeout would cause `sendTransactionAndWait()` to fail immediately without any polling attempts.

**Evidence:**
```java
public Builder timeout(final Duration timeout) {
    Objects.requireNonNull(timeout, "timeout must not be null");
    if (timeout.isNegative()) {
        throw new IllegalArgumentException("timeout must not be negative");
    }
    this.timeout = timeout;
    return this;
}
```

**Impact:** Users setting `timeout(Duration.ZERO)` would get confusing behavior where transactions are sent but never waited for.

**Acceptance Criteria:**
- [x] Reject zero timeout: `if (timeout.isNegative() || timeout.isZero())`
- [ ] ~~Document that zero timeout means "no waiting"~~ (not needed - rejected)
- [x] Add test for zero timeout behavior

---

### 7. Missing equals/hashCode in ContractOptions

**Files:**
- `brane-contract/src/main/java/io/brane/contract/ContractOptions.java`

**Problem:**
`ContractOptions` is an immutable value class but does not override `equals()` or `hashCode()`. This means two `ContractOptions` instances with identical field values will not be equal. While this may be intentional (identity-based comparison), it could surprise users who expect value semantics from an immutable configuration object.

**Evidence:**
```java
public final class ContractOptions {
    // ... fields and methods
    // No equals() or hashCode() implementations
}
```

**Impact:** Users cannot reliably compare or use `ContractOptions` in collections that rely on value equality.

**Acceptance Criteria:**
- [ ] ~~Convert to a record~~ (not chosen - would require breaking changes)
- [x] Implement `equals()` and `hashCode()` based on all field values
- [ ] ~~Document explicitly that reference equality is intentional~~ (not needed)
- [x] Add test for equality behavior

**Note:** This is related to the deferred Issue #10 from Round 1 (ContractOptions should be a record).

---

### 8. Duplicate isObjectMethod() Utility

**Files:**
- `brane-contract/src/main/java/io/brane/contract/ContractInvocationHandler.java:129`
- `brane-core/src/main/java/io/brane/core/abi/AbiBinding.java:45`

**Problem:**
The utility method `isObjectMethod(Method)` is duplicated in both `ContractInvocationHandler` and `AbiBinding` with identical implementation.

**Evidence:**
```java
// In both files:
private static boolean isObjectMethod(final Method method) {
    return method.getDeclaringClass() == Object.class;
}
```

**Impact:** Minor code duplication; any fix or enhancement must be applied twice.

**Acceptance Criteria:**
- [ ] Extract to a shared utility class (e.g., `MethodUtils` or `ReflectionUtils`), OR
- [ ] Move validation logic to a single location, OR
- [ ] Document why duplication is acceptable (e.g., to avoid cross-module dependency)

---

## Summary

| # | Issue | Severity | Type | Status |
|---|-------|----------|------|--------|
| 1 | AbiBinding non-thread-safe HashMap | MEDIUM | Thread Safety | FIXED |
| 2 | ReadWriteContract ignores ContractOptions | MEDIUM | API Inconsistency | FIXED |
| 3 | deployRequest() missing constructor validation | MEDIUM | Validation | FIXED |
| 4 | Missing tuple/struct return type support | LOW | Feature Gap | OPEN |
| 5 | @Payable silent fallback to zero | LOW | Defensive Programming | FIXED |
| 6 | Zero timeout allowed | LOW | Validation | FIXED |
| 7 | Missing equals/hashCode in ContractOptions | LOW | API Design | FIXED |
| 8 | Duplicate isObjectMethod() | LOW | Code Duplication | OPEN |

---

## Recommended Implementation Order

1. **Issue #2** - Add ContractOptions support to ReadWriteContract (API consistency)
2. **Issue #3** - Add constructor validation to deployRequest() (better errors)
3. **Issue #1** - Fix AbiBinding thread safety (correctness)
4. **Issue #7** - Fix ContractOptions equals/hashCode (consider converting to record)
5. **Issue #6** - Reject zero timeout (validation)
6. **Issue #5** - Document or enforce @Payable runtime behavior
7. **Issue #4** - Document tuple limitation or implement support
8. **Issue #8** - Extract shared utility (cleanup)

---

## Previously Completed (Round 1)

For reference, the following issues were addressed in the previous review round:

| Issue | Description | Resolution |
|-------|-------------|------------|
| ContractOptions fields ignored | gasLimit, transactionType, maxPriorityFee not applied | Fixed in ContractInvocationHandler |
| ReadWriteContract cannot send ETH | Hardcoded Wei.of(0) | Added overloaded methods with Wei parameter |
| Missing revert handling in proxy | invokeView() didn't decode reverts | Added try-catch with RevertDecoder |
| Missing String return type | validateReturnType() rejected String | Added String.class support |
| No ABI payable validation | @Payable not checked against ABI | Added isPayable() validation |
| Null check missing in proxy | NPE on null RPC response | Added null/blank check |
| Missing bytes return types | byte[]/HexData not supported | Added isBytesType() helper |
| Inconsistent API behaviors | Different error handling across APIs | Unified with revert handling fix |
| Missing array return types | List/array not supported | Added isArrayType() helper |
| Missing Javadoc for exceptions | Proxy exceptions undocumented | Added runtime exceptions section |
| ContractOptions as record | 263 lines of boilerplate | DEFERRED (optional, superseded by Issue #7) |
