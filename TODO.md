# TODO: brane-core Module Issues

This document captures issues identified in the brane-core module code review, with full context, acceptance criteria, and proposed fixes.

---

## CRITICAL PRIORITY

### 1. Thread-Safety Bug in HexData.value() - Race Condition

**Files:**
- `brane-core/src/main/java/io/brane/core/types/HexData.java:118-123`

**Problem:**
The `value()` method has a race condition. Multiple threads can call `value()` simultaneously, and since `value` is not volatile, one thread's write may not be visible to another. This is a classic double-checked locking bug without proper synchronization.

**Evidence:**
```java
@com.fasterxml.jackson.annotation.JsonValue
public String value() {
    if (value == null) {  // Thread A reads null
        value = Hex.encode(raw);  // Thread B also enters here
    }
    return value;  // Could return partially constructed String
}
```

**Impact:** Data corruption or NullPointerException under concurrent access. HexData is used throughout the codebase for transaction data, addresses, and hashes.

**Acceptance Criteria:**
- [ ] Use AtomicReference or volatile with double-checked locking
- [ ] Ensure thread-safe lazy initialization
- [ ] Add concurrent access test

**Proposed Fix:**
```java
private volatile String value;

public String value() {
    String v = value;
    if (v == null) {
        synchronized (this) {
            v = value;
            if (v == null) {
                value = v = Hex.encode(raw);
            }
        }
    }
    return v;
}
```

---

### 2. Private Key Material Not Cleared from Memory

**Files:**
- `brane-core/src/main/java/io/brane/core/crypto/PrivateKey.java`

**Problem:**
Private key bytes are converted to BigInteger but the original byte array is never zeroed. Also, PrivateKey lacks a `destroy()` or `close()` method to clear sensitive material. This is a security vulnerability - private keys can remain in memory longer than necessary, making them vulnerable to memory dumps or cold boot attacks.

**Impact:** Private keys may be extractable from memory dumps, crash logs, or via memory scanning attacks.

**Acceptance Criteria:**
- [ ] Zero byte arrays after conversion to BigInteger
- [ ] Implement `javax.security.auth.Destroyable` interface
- [ ] Document security considerations
- [ ] Add test verifying key material can be destroyed

**Proposed Fix:**
```java
public final class PrivateKey implements Destroyable {
    private volatile boolean destroyed = false;

    @Override
    public void destroy() {
        // Zero the internal BigInteger if possible
        destroyed = true;
    }

    @Override
    public boolean isDestroyed() {
        return destroyed;
    }
}
```

---

## HIGH PRIORITY

### 3. Static Mutable FixedPointCombMultiplier in FastSigner

**Files:**
- `brane-core/src/main/java/io/brane/core/crypto/FastSigner.java:45`

**Problem:**
`FixedPointCombMultiplier` is stored in a static field and reused. Bouncy Castle's `FixedPointCombMultiplier` caches precomputed tables internally. While likely thread-safe, this should be verified or documented explicitly.

**Evidence:**
```java
private static final FixedPointCombMultiplier MULTIPLIER = new FixedPointCombMultiplier();
```

**Impact:** Potential thread-safety issue in cryptographic signing code. Could result in incorrect signatures under concurrent load.

**Acceptance Criteria:**
- [ ] Verify BouncyCastle's thread-safety guarantees for FixedPointCombMultiplier
- [ ] Either document thread-safety or create new instance per call
- [ ] Add concurrent signing test

---

### 4. Duplicate ChainProfile Records in Different Packages

**Files:**
- `brane-core/src/main/java/io/brane/core/model/ChainProfile.java`
- `brane-core/src/main/java/io/brane/core/chain/ChainProfile.java`

**Problem:**
Two different `ChainProfile` records exist with different fields:
- `io.brane.core.model.ChainProfile(chainId, name, nativeCurrencySymbol)`
- `io.brane.core.chain.ChainProfile(chainId, defaultRpcUrl, supportsEip1559, defaultPriorityFeePerGas)`

This is confusing, violates single responsibility, and creates naming conflicts for imports.

**Impact:** Developers must use fully qualified names or carefully manage imports. High cognitive overhead and potential for bugs.

**Acceptance Criteria:**
- [ ] Consolidate into a single ChainProfile with all fields
- [ ] Deprecate one or merge fields
- [ ] Update all usages across codebase

---

### 5. BraneTxBuilderException Not Part of BraneException Hierarchy

**Files:**
- `brane-core/src/main/java/io/brane/core/builder/BraneTxBuilderException.java`

**Problem:**
`BraneTxBuilderException` extends `RuntimeException` directly, not `BraneException`. This breaks the sealed exception hierarchy pattern established for consistent error handling.

**Evidence:**
```java
public final class BraneTxBuilderException extends RuntimeException {
```

**Impact:** Users catching `BraneException` will miss transaction builder errors. Inconsistent error handling across the SDK.

**Acceptance Criteria:**
- [ ] Either add BraneTxBuilderException to BraneException permits list
- [ ] Or make it extend TxnException
- [ ] Update tests and documentation

---

### 6. Missing Null Check in TransactionRequest.toUnsignedTransaction()

**Files:**
- `brane-core/src/main/java/io/brane/core/model/TransactionRequest.java:122`

**Problem:**
The method throws NullPointerException for `from` but doesn't validate it upfront. If `from` is null but other fields are set, the transaction can be created but will fail silently or with cryptic errors later.

**Impact:** Poor developer experience with unhelpful error messages when creating unsigned transactions.

**Acceptance Criteria:**
- [ ] Add explicit null check for `from` at start of method
- [ ] Throw IllegalStateException with clear message: "from address is required for unsigned transaction"
- [ ] Add test for null from field

---

### 7. Exception Swallowing in RevertDecoder and InternalAbi

**Files:**
- `brane-core/src/main/java/io/brane/core/RevertDecoder.java:145-147`
- `brane-core/src/main/java/io/brane/core/abi/InternalAbi.java:1012`

**Problem:**
Exceptions are caught and silently swallowed with `// fallthrough` comments. This makes debugging extremely difficult when decoding fails.

**Evidence:**
```java
} catch (Exception e) {
    // fallthrough
}
```

**Impact:** Lost diagnostic information when ABI decoding fails. Hours wasted debugging silent failures.

**Acceptance Criteria:**
- [ ] Log at DEBUG level before falling through
- [ ] Or preserve the cause for better diagnostics
- [ ] Document why fallthrough is acceptable in each case

---

### 8. Missing Validation in LegacyTransaction.encodeAsEnvelope()

**Files:**
- `brane-core/src/main/java/io/brane/core/tx/LegacyTransaction.java:100`

**Problem:**
No null check for signature parameter in `encodeAsEnvelope()` unlike `Eip1559Transaction` which has `Objects.requireNonNull(signature, "signature is required")`. Inconsistent validation across transaction types.

**Acceptance Criteria:**
- [ ] Add `Objects.requireNonNull(signature, "signature")` at method start
- [ ] Ensure consistent validation across all transaction types

---

## MEDIUM PRIORITY

### 9. Inconsistent Package Naming: util vs utils

**Files:**
- `brane-core/src/main/java/io/brane/core/util/MethodUtils.java`
- `brane-core/src/main/java/io/brane/core/utils/Topics.java`

**Problem:**
Two utility packages with inconsistent naming: `io.brane.core.util` vs `io.brane.core.utils`. This creates confusion and makes imports harder to remember.

**Acceptance Criteria:**
- [ ] Standardize on one naming convention (prefer `util` - singular)
- [ ] Move `Topics` to `io.brane.core.util`
- [ ] Update all imports

---

### 10. Empty Validation Block in Bytes Record

**Files:**
- `brane-core/src/main/java/io/brane/core/abi/Bytes.java:14-19`

**Problem:**
The compact constructor has an empty if block with only a comment. Dead code that should either be implemented or removed.

**Evidence:**
```java
public Bytes {
    Objects.requireNonNull(value, "value cannot be null");
    if (!isDynamic && value.value().length() > 66) {
        // Actually bytesN can be up to 32 bytes.
        // We don't strictly enforce N here, but we could.
    }
}
```

**Acceptance Criteria:**
- [ ] Either enforce bytesN validation (throw exception for oversized data)
- [ ] Or remove the empty block entirely
- [ ] Document validation behavior

---

### 11. Magic Number in LogSanitizer Truncation

**Files:**
- `brane-core/src/main/java/io/brane/core/LogSanitizer.java:31-32`

**Problem:**
Magic numbers 2000 and 200 for truncation limits without explanation. Also inconsistent: checks `> 2000` but truncates to 200 characters.

**Evidence:**
```java
if (sanitized.length() > 2000) {
    sanitized = sanitized.substring(0, 200) + "...(truncated)";
}
```

**Impact:** Confusing behavior - if string is 2001 chars, it gets truncated to 200 chars (losing 1800 chars unnecessarily).

**Acceptance Criteria:**
- [ ] Extract to named constants with documentation
- [ ] Fix inconsistency (probably should truncate to ~2000, not 200)
- [ ] Add tests for truncation behavior

---

### 12. Array.typeName() Returns "unknown" for Empty Arrays

**Files:**
- `brane-core/src/main/java/io/brane/core/abi/Array.java:46`

**Problem:**
Cannot determine element type for empty arrays, returns "unknown" which breaks ABI encoding.

**Evidence:**
```java
String elementTypeName = values.isEmpty() ? "unknown" : values.get(0).typeName();
```

**Acceptance Criteria:**
- [ ] Store element type name in constructor
- [ ] Or derive from Class<T> type parameter
- [ ] Add test for empty array type name

---

### 13. Eip1559Transaction.encodeForSigning() Ignores chainId Parameter

**Files:**
- `brane-core/src/main/java/io/brane/core/tx/Eip1559Transaction.java:112-113`

**Problem:**
The method signature accepts a chainId parameter but ignores it, using the record's chainId instead. This is confusing and potentially error-prone.

**Evidence:**
```java
@Override
public byte[] encodeForSigning(final long chainId) {
    // Note: chainId parameter is ignored; we use the chainId from the record
```

**Acceptance Criteria:**
- [ ] Either validate that parameter matches record's chainId (throw if mismatch)
- [ ] Or change interface to not require chainId for EIP-1559
- [ ] Or document this behavior prominently

---

### 14. Missing equals/hashCode for AccessListEntry

**Files:**
- `brane-core/src/main/java/io/brane/core/model/AccessListEntry.java`

**Problem:**
`AccessListEntry` is a regular class, not a record, but doesn't override `equals()` and `hashCode()`. This could cause issues when comparing transactions or using in collections.

**Acceptance Criteria:**
- [ ] Convert to record (preferred - it's just data)
- [ ] Or implement equals/hashCode manually
- [ ] Add equality tests

---

### 15. Hex.decode Duplication in Eip1559Transaction

**Files:**
- `brane-core/src/main/java/io/brane/core/tx/Eip1559Transaction.java:202-211`

**Problem:**
`hexToBytes()` is reimplemented locally instead of using `io.brane.primitives.Hex.decode()`. Code duplication and potential for divergent behavior.

**Evidence:**
```java
private static byte[] hexToBytes(final String hex) {
    String clean = hex.startsWith("0x") ? hex.substring(2) : hex;
    // ...manual implementation...
}
```

**Acceptance Criteria:**
- [ ] Replace with `io.brane.primitives.Hex.decode(hex)`
- [ ] Remove local implementation

---

### 16. Transaction Record Missing Validation

**Files:**
- `brane-core/src/main/java/io/brane/core/model/Transaction.java`

**Problem:**
The Transaction record has no compact constructor validation. Fields like `hash`, `from`, `input`, `value` should not be null for a valid transaction.

**Acceptance Criteria:**
- [ ] Add compact constructor with null checks for required fields
- [ ] Document which fields are optional vs required
- [ ] Add validation tests

---

### 17. Inconsistent Use of Optional

**Files:**
- `brane-core/src/main/java/io/brane/core/model/Transaction.java` uses `Optional<Address> to`
- `brane-core/src/main/java/io/brane/core/model/TransactionRequest.java` uses nullable `Address to`

**Problem:**
Inconsistent handling of nullable `to` field - one uses Optional, one uses nullable. This creates API confusion.

**Acceptance Criteria:**
- [ ] Standardize on one approach across the codebase
- [ ] Prefer nullable with `*Opt()` accessors (Brane pattern)
- [ ] Update affected classes

---

### 18. BytesSchema Missing N Parameter for bytesN

**Files:**
- `brane-core/src/main/java/io/brane/core/abi/TypeSchema.java:83-88`

**Problem:**
`BytesSchema(boolean isDynamic)` doesn't track the size N for static bytesN types. This makes it impossible to properly validate bytesN decoding.

**Evidence:**
```java
record BytesSchema(boolean isDynamic) implements TypeSchema {
```

**Acceptance Criteria:**
- [ ] Add `int size` parameter for static bytes (1-32)
- [ ] Use -1 or Optional for dynamic bytes
- [ ] Add validation tests

---

## LOW PRIORITY

### 19. Missing Javadoc on Several Public Classes/Methods

**Locations:**
- `TxBuilder` interface methods lack Javadoc
- `Eip1559Builder` and `LegacyBuilder` methods lack Javadoc
- `MethodUtils.isObjectMethod()` documentation could be improved
- `AccessListWithGas` record parameters need `@param` tags
- `MulticallResult` record needs fuller Javadoc
- `Call3` record needs fuller Javadoc

**Acceptance Criteria:**
- [ ] Add Javadoc with @param, @return, @throws tags
- [ ] Follow pattern established in `Address.java`

---

### 20. LogFormatter Has Hardcoded Spacing

**Files:**
- `brane-core/src/main/java/io/brane/core/LogFormatter.java:132-133`

**Problem:**
Uses hardcoded 10-space indentation which may not align well in all terminals or log viewers.

**Evidence:**
```java
"[CALL]%s tag=%s\n          to=%s\n          data=%s"
```

**Acceptance Criteria:**
- [ ] Consider using constant for indentation
- [ ] Or dynamic alignment based on context

---

### 21. Utf8String.contentByteSize() Allocates on Every Call

**Files:**
- `brane-core/src/main/java/io/brane/core/abi/Utf8String.java:37-41`

**Problem:**
`contentByteSize()` calls `value.getBytes(UTF_8)` which allocates a new array each time. Performance concern for frequently called method.

**Evidence:**
```java
public int contentByteSize() {
    int len = value.getBytes(java.nio.charset.StandardCharsets.UTF_8).length;
```

**Acceptance Criteria:**
- [ ] Cache byte length computation (lazy or eager)
- [ ] Or document performance characteristics

---

## TEST COVERAGE CONCERNS

### 22. Missing Tests for Edge Cases

**Areas lacking tests:**
- `HexData` concurrent access test
- `InternalAbi.decodeMulticallResults()` with malformed data
- `RevertDecoder` with various custom error formats
- `TransactionRequest.toUnsignedTransaction()` validation paths
- `Signature.getRecoveryId()` with various v values
- `PrivateKey.recoverAddress()` with edge case signatures
- `LogSanitizer` with various sensitive patterns

**Acceptance Criteria:**
- [ ] Add tests for concurrent HexData access
- [ ] Add tests for ABI decoder edge cases
- [ ] Add tests for error handling paths
- [ ] Add tests for signature recovery edge cases

---

### 23. No Integration Tests for AbiBinding

**Files:**
- No test file for `brane-core/src/main/java/io/brane/core/abi/AbiBinding.java`

**Acceptance Criteria:**
- [ ] Add unit tests for AbiBinding
- [ ] Test method resolution with various interfaces
- [ ] Test caching behavior

---

## Summary

| # | Issue | Severity | Type | Status |
|---|-------|----------|------|--------|
| 1 | Thread-safety bug in HexData.value() | CRITICAL | Thread Safety | TODO |
| 2 | Private key material not cleared | CRITICAL | Security | TODO |
| 3 | Static FixedPointCombMultiplier thread safety | HIGH | Thread Safety | TODO |
| 4 | Duplicate ChainProfile records | HIGH | API Design | TODO |
| 5 | BraneTxBuilderException not in hierarchy | HIGH | API Consistency | TODO |
| 6 | Missing null check in TransactionRequest | HIGH | Validation | TODO |
| 7 | Exception swallowing in decoders | HIGH | Error Handling | TODO |
| 8 | Missing validation in LegacyTransaction | HIGH | Validation | TODO |
| 9 | Inconsistent util vs utils packages | MEDIUM | Code Organization | TODO |
| 10 | Empty validation block in Bytes | MEDIUM | Code Quality | TODO |
| 11 | Magic numbers in LogSanitizer | MEDIUM | Code Quality | TODO |
| 12 | Array.typeName() returns "unknown" | MEDIUM | API Design | TODO |
| 13 | encodeForSigning() ignores chainId | MEDIUM | API Design | TODO |
| 14 | Missing equals/hashCode in AccessListEntry | MEDIUM | API Design | TODO |
| 15 | Hex.decode duplication | MEDIUM | Code Duplication | TODO |
| 16 | Transaction record missing validation | MEDIUM | Validation | TODO |
| 17 | Inconsistent use of Optional | MEDIUM | API Consistency | TODO |
| 18 | BytesSchema missing size parameter | MEDIUM | Incomplete Implementation | TODO |
| 19 | Missing Javadoc | LOW | Documentation | TODO |
| 20 | Hardcoded spacing in LogFormatter | LOW | Code Quality | TODO |
| 21 | Utf8String allocates on every call | LOW | Performance | TODO |
| 22 | Missing edge case tests | MEDIUM | Test Coverage | TODO |
| 23 | No AbiBinding tests | MEDIUM | Test Coverage | TODO |

---

## Recommended Implementation Order

### Phase 1: Critical Security & Thread Safety
1. **Issue #1** - Fix HexData thread safety (data integrity)
2. **Issue #2** - Clear private key material (security)
3. **Issue #3** - Verify FastSigner thread safety (crypto correctness)

### Phase 2: High Priority API Issues
4. **Issue #5** - Fix BraneTxBuilderException hierarchy (API consistency)
5. **Issue #6** - Add TransactionRequest validation (developer experience)
6. **Issue #7** - Fix exception swallowing (debugging)
7. **Issue #8** - Add LegacyTransaction validation (consistency)
8. **Issue #4** - Consolidate ChainProfile (API cleanup)

### Phase 3: Medium Priority Improvements
9. **Issue #9** - Standardize package naming
10. **Issue #14** - Fix AccessListEntry equals/hashCode
11. **Issue #15** - Remove Hex.decode duplication
12. **Issue #16** - Add Transaction validation
13. **Issue #17** - Standardize Optional usage
14. **Issue #18** - Fix BytesSchema
15. **Issue #10-13** - Code quality fixes

### Phase 4: Low Priority & Tests
16. **Issues #19-21** - Documentation and polish
17. **Issues #22-23** - Test coverage improvements

---

## What's Good (Keep Doing)

1. **Excellent use of Java 21 records** - Immutable data types with validation in compact constructors
2. **Sealed interfaces** - `AbiType`, `TypeSchema`, `UnsignedTransaction`, `BraneException` properly use sealed types
3. **Pattern matching** - Good use of switch expressions with pattern matching
4. **Documentation** - Many classes have excellent Javadoc with examples (e.g., `Address`, `RevertDecoder`)
5. **Input validation** - Most record compact constructors validate inputs properly
6. **Defensive copies** - `Signature` makes defensive copies of byte arrays
7. **Security awareness** - `LogSanitizer` exists to prevent credential leakage
8. **Performance optimization** - `FastAbiEncoder` and `FastSigner` show attention to performance
9. **Clear error hierarchy** - `BraneException` sealed hierarchy is well-designed
10. **Builder pattern** - `TxBuilder` interface with `Eip1559Builder`/`LegacyBuilder` is clean
