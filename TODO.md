# TODO: brane-core Code Review Issues

Issues identified in principal engineer code review of the `fix/brane-core` branch.

---

## CRITICAL PRIORITY

### 1. PrivateKey.destroy() Has a Race Condition

**File:** `brane-core/src/main/java/io/brane/core/crypto/PrivateKey.java:294-298`

**Problem:**
The `destroy()` method has a race condition. Between setting `destroyed = true` and nulling out `privateKeyValue`, another thread could call `checkNotDestroyed()`, pass the check, then read `privateKeyValue` which becomes null, causing an NPE in `FastSigner.sign()`.

**Current Code:**
```java
@Override
public void destroy() {
    destroyed = true;
    privateKeyValue = null;
    publicKey = null;
}
```

**Acceptance Criteria:**
- [ ] Add synchronization around `destroy()` method
- [ ] Add synchronization around `signFast()` method to capture key reference safely
- [ ] Ensure atomicity: no thread can observe `destroyed = false` but then read null key
- [ ] Add concurrent destroy/sign test to verify fix

**Proposed Fix:**
```java
@Override
public void destroy() {
    synchronized (this) {
        destroyed = true;
        privateKeyValue = null;
        publicKey = null;
    }
}

public Signature signFast(final byte[] messageHash) {
    BigInteger key;
    synchronized (this) {
        checkNotDestroyed();
        key = privateKeyValue;
    }
    Objects.requireNonNull(messageHash, "message hash cannot be null");
    if (messageHash.length != 32) {
        throw new IllegalArgumentException("Message hash must be 32 bytes, got " + messageHash.length);
    }
    return FastSigner.sign(messageHash, key);
}
```

---

### 2. PrivateKey Constructor Zeros Input Array - Breaking Caller Expectations

**File:** `brane-core/src/main/java/io/brane/core/crypto/PrivateKey.java:89-92`

**Problem:**
The constructor modifies the caller's array by zeroing it after use. This is a breaking API change and violates the principle of least surprise. Callers may reuse the array or expect it to be unchanged.

**Current Code:**
```java
} finally {
    // Zero the input byte array to minimize exposure of key material
    Arrays.fill(keyBytes, (byte) 0);
}
```

**Acceptance Criteria:**
- [ ] Choose one of these approaches:
  - Option A: Document this behavior prominently in Javadoc with `@apiNote`
  - Option B: Clone the array first and zero the copy instead
  - Option C: Create factory method `PrivateKey.fromBytesDestructive()` for opt-in zeroing
- [ ] Add test verifying the chosen behavior
- [ ] Update any affected callers

---

## HIGH PRIORITY

### 3. Utf8String.utf8ByteLength() Surrogate Handling is Incomplete

**File:** `brane-core/src/main/java/io/brane/core/abi/Utf8String.java:54-67`

**Problem:**
The surrogate handling is incomplete:
1. If a high surrogate is not followed by a low surrogate (malformed string), the code produces incorrect results
2. If the string ends with a lone high surrogate, `i++` could skip past end without correct length calculation

**Current Code:**
```java
private static int utf8ByteLength(String s) {
    int len = 0;
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c < 0x80) {
            len += 1;
        } else if (c < 0x800) {
            len += 2;
        } else if (Character.isHighSurrogate(c)) {
            len += 4;
            i++; // Skip low surrogate
        } else {
            len += 3;
        }
    }
    return len;
}
```

**Acceptance Criteria:**
- [ ] Check that high surrogate is followed by low surrogate before incrementing `i`
- [ ] Handle lone surrogates gracefully (treat as 3-byte BMP character)
- [ ] Add tests for malformed surrogate strings
- [ ] Add tests for strings ending with lone high surrogate

**Proposed Fix:**
```java
private static int utf8ByteLength(String s) {
    int len = 0;
    for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        if (c < 0x80) {
            len += 1;
        } else if (c < 0x800) {
            len += 2;
        } else if (Character.isHighSurrogate(c) && i + 1 < s.length()
                   && Character.isLowSurrogate(s.charAt(i + 1))) {
            len += 4;
            i++; // Skip low surrogate
        } else {
            len += 3;
        }
    }
    return len;
}
```

---

### 4. LogSanitizer Truncation Edge Case

**File:** `brane-core/src/main/java/io/brane/core/LogSanitizer.java:44-48`

**Problem:**
If `MAX_LOG_LENGTH` is less than `TRUNCATION_SUFFIX.length()`, this would cause an `IndexOutOfBoundsException`.

**Current Code:**
```java
if (sanitized.length() > MAX_LOG_LENGTH) {
    int truncateAt = MAX_LOG_LENGTH - TRUNCATION_SUFFIX.length();
    sanitized = sanitized.substring(0, truncateAt) + TRUNCATION_SUFFIX;
}
```

**Acceptance Criteria:**
- [ ] Add bounds check to prevent negative index
- [ ] Add test for edge case where constants are misconfigured
- [ ] Consider adding static assertion/validation for constant relationship

**Proposed Fix:**
```java
if (sanitized.length() > MAX_LOG_LENGTH) {
    int truncateAt = Math.max(0, MAX_LOG_LENGTH - TRUNCATION_SUFFIX.length());
    sanitized = sanitized.substring(0, truncateAt) + TRUNCATION_SUFFIX;
}
```

---

### 5. Transaction `to` Field API Breaking Change

**File:** `brane-core/src/main/java/io/brane/core/model/Transaction.java`

**Problem:**
The `to` field changed from `Optional<Address>` to nullable `Address`. While `toOpt()` was added for compatibility, existing code using `.to().orElse(...)` will break at compile time.

**Acceptance Criteria:**
- [ ] Document this breaking change in release notes
- [ ] Consider if this is acceptable for current version (pre-1.0?)
- [ ] Update migration guide if one exists

---

## MEDIUM PRIORITY

### 6. TupleSchema.typeName() Uses Inefficient String Reduction

**File:** `brane-core/src/main/java/io/brane/core/abi/TypeSchema.java:178-181`

**Problem:**
Using `reduce` with string concatenation creates intermediate strings. For tuples with many components, this is O(n^2).

**Current Code:**
```java
return "(" + components.stream()
        .map(TypeSchema::typeName)
        .reduce((a, b) -> a + "," + b)
        .orElse("") + ")";
```

**Acceptance Criteria:**
- [ ] Replace with `Collectors.joining(",")` for O(n) performance
- [ ] Add test with large tuple to verify no performance regression

**Proposed Fix:**
```java
return "(" + components.stream()
        .map(TypeSchema::typeName)
        .collect(Collectors.joining(",")) + ")";
```

---

### 7. Array Record Breaking Change

**File:** `brane-core/src/main/java/io/brane/core/abi/Array.java`

**Problem:**
Adding `elementTypeName` as a required parameter to the record is a breaking change. All external callers must be updated.

**Acceptance Criteria:**
- [ ] Document in release notes
- [ ] Verify all internal usages have been updated (tests show they have)
- [ ] Consider if deprecation period is needed

---

### 8. Bytes Record Doesn't Enforce Exact Size for bytesN

**File:** `brane-core/src/main/java/io/brane/core/abi/Bytes.java:16-21`

**Problem:**
Validates that bytesN types must be 1-32 bytes, but doesn't enforce that the actual byte length matches the declared N. Creating a `bytes16` with 32 bytes would silently succeed.

**Acceptance Criteria:**
- [ ] Consider adding a `size` parameter to `Bytes` for static types
- [ ] Enforce exact size matching when size is specified
- [ ] Add tests for size mismatch scenarios

---

## LOW PRIORITY

### 9. LogFormatter CONTINUATION_INDENT is Fragile

**File:** `brane-core/src/main/java/io/brane/core/LogFormatter.java:114`

**Problem:**
The hardcoded 10-space indent "Aligns with [CALL] prefix" per comment, but this is fragile if prefix changes.

```java
private static final String CONTINUATION_INDENT = "          ";
```

**Acceptance Criteria:**
- [ ] Consider deriving from prefix length
- [ ] Or add comment documenting the exact alignment requirement

---

## Summary

| # | Issue | Priority | Status |
|---|-------|----------|--------|
| 1 | PrivateKey.destroy() race condition | CRITICAL | DONE |
| 2 | PrivateKey zeros input array | CRITICAL | DONE |
| 3 | Utf8String surrogate handling | HIGH | DONE |
| 4 | LogSanitizer edge case | HIGH | DONE |
| 5 | Transaction `to` breaking change | HIGH | DONE |
| 6 | TupleSchema O(n^2) string concat | MEDIUM | DONE |
| 7 | Array record breaking change | MEDIUM | DONE |
| 8 | Bytes size enforcement | MEDIUM | DONE |
| 9 | LogFormatter fragile indent | LOW | DONE |

---

## What's Well Done

1. **Thread Safety Focus** - Multiple commits address thread safety (HexData, FastSigner documentation, PrivateKey volatile fields)
2. **Destroyable Implementation** - Adding `Destroyable` to `PrivateKey` follows security best practices
3. **Record Usage** - Converting `AccessListEntry` to a record with proper defensive copying is textbook Java 21
4. **Input Validation** - Added validation throughout (Transaction, TransactionRequest, Bytes, BytesSchema)
5. **Performance Optimization** - `Utf8String.utf8ByteLength()` avoids allocation in hot path
6. **Debug Logging** - Adding DEBUG logging to catch blocks improves observability
7. **Comprehensive Javadoc** - Excellent documentation for builders and security notes for PrivateKey
8. **Good Test Coverage** - New tests for HexData concurrency, PrivateKey Destroyable, TransactionRequest validation, FastSigner concurrency
9. **Exception Hierarchy** - Integrating `BraneTxBuilderException` into Brane exception hierarchy
10. **Duplicate Code Removal** - Removing `hexToBytes` in favor of `Hex.decode`
