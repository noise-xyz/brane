# EIP-712 Code Review Fixes

**Status**: Post-Implementation Cleanup
**Review Date**: 2026-01-08
**Reviewer Confidence**: High

---

## Summary

Code review identified 4 design concerns and 3 suggestions. No confirmed bugs.
All changes improve maintainability, remove dead code, and consolidate duplicated logic.

---

## Phase 11: Dead Code Removal

### P11-01: Remove ValueCoercer (Dead Code)

**File**: `brane-core/src/main/java/io/brane/core/crypto/eip712/ValueCoercer.java`

**Description**: `ValueCoercer` is defined and tested but never called in production code.
It only exists in `ValueCoercerTest.java`. This is dead code that should be removed.

**Acceptance Criteria**:
- [ ] Delete `brane-core/src/main/java/io/brane/core/crypto/eip712/ValueCoercer.java`
- [ ] Delete `brane-core/src/test/java/io/brane/core/crypto/eip712/ValueCoercerTest.java`
- [ ] Run `./gradlew compileJava` to verify no compile errors
- [ ] Run `./gradlew test --tests "*eip712*"` to verify tests still pass

---

## Phase 12: API Fixes

### P12-01: Implement Eip712Domain.separator() Method

**File**: `brane-core/src/main/java/io/brane/core/crypto/eip712/Eip712Domain.java`

**Description**: The `separator()` method throws `UnsupportedOperationException` but
`TypedDataEncoder.hashDomain()` already implements this functionality. Either implement
the method or remove it.

**Acceptance Criteria**:
- [ ] Replace `separator()` implementation to delegate to `TypedDataEncoder.hashDomain(this)`
- [ ] Remove the TODO comment
- [ ] Add Javadoc explaining the method computes the domain separator hash
- [ ] Run `./gradlew test --tests "*Eip712Domain*"` to verify

**Current Code** (lines 45-48):
```java
public Hash separator() {
    // TODO: Implement when TypedDataEncoder is available
    throw new UnsupportedOperationException("TypedDataEncoder not yet implemented");
}
```

**Expected Code**:
```java
public Hash separator() {
    return TypedDataEncoder.hashDomain(this);
}
```

---

### P12-02: Consolidate Duplicate signHash Logic

**Files**:
- `brane-core/src/main/java/io/brane/core/crypto/eip712/TypedData.java`
- `brane-core/src/main/java/io/brane/core/crypto/eip712/TypedDataSigner.java`

**Description**: The `signHash()` method is duplicated between `TypedData` and
`TypedDataSigner` with identical logic. Consolidate into one location.

**Acceptance Criteria**:
- [ ] Move `signHash()` implementation to `TypedDataSigner` (make it package-private)
- [ ] Update `TypedData.signHash()` to delegate to `TypedDataSigner.signHash()`
- [ ] Ensure no code duplication remains
- [ ] Run `./gradlew test --tests "*TypedData*"` and `./gradlew test --tests "*TypedDataSigner*"`

---

### P12-03: Add Type Check in encodeString

**File**: `brane-core/src/main/java/io/brane/core/crypto/eip712/TypedDataEncoder.java`

**Description**: `encodeString()` directly casts to String without type checking,
unlike other encode methods that use pattern matching.

**Acceptance Criteria**:
- [ ] Add instanceof check before cast
- [ ] Throw `Eip712Exception.invalidValue("string", value)` for non-String values
- [ ] Run `./gradlew test --tests "*TypedDataEncoder*"`

**Current Code** (lines 320-323):
```java
private static byte[] encodeString(Object value) {
    String str = (String) value;
    return Keccak256.hash(str.getBytes(StandardCharsets.UTF_8));
}
```

**Expected Code**:
```java
private static byte[] encodeString(Object value) {
    if (!(value instanceof String str)) {
        throw Eip712Exception.invalidValue("string", value);
    }
    return Keccak256.hash(str.getBytes(StandardCharsets.UTF_8));
}
```

---

### P12-04: Add Null Check for Domain in TypedDataPayload

**File**: `brane-core/src/main/java/io/brane/core/crypto/eip712/TypedDataPayload.java`

**Description**: The record constructor validates `primaryType`, `types`, and `message`
but not `domain`. A null domain would cause NPE in `TypedDataEncoder.hashDomain()`.

**Acceptance Criteria**:
- [ ] Add `Objects.requireNonNull(domain, "domain")` to compact constructor
- [ ] Run `./gradlew test --tests "*TypedDataPayload*"` or `*TypedDataJson*"`

---

### P12-05: Add Missing Field Validation in encodeData

**File**: `brane-core/src/main/java/io/brane/core/crypto/eip712/TypedDataEncoder.java`

**Description**: `encodeData()` doesn't validate that required fields exist before
accessing them. A missing field results in confusing errors from deep in the encoding
stack rather than a clear message.

**Acceptance Criteria**:
- [ ] In `encodeData()`, after getting `data.get(field.name())`, check if value is null
- [ ] If null and key doesn't exist in data, throw `Eip712Exception.missingField(typeName, field.name())`
- [ ] Run `./gradlew test --tests "*TypedDataEncoder*"`

**Current Code** (lines 170-172):
```java
for (var field : fields) {
    var value = data.get(field.name());
    var fieldEncoded = encodeField(field.type(), value, types);
```

**Expected Code**:
```java
for (var field : fields) {
    var value = data.get(field.name());
    if (value == null && !data.containsKey(field.name())) {
        throw Eip712Exception.missingField(typeName, field.name());
    }
    var fieldEncoded = encodeField(field.type(), value, types);
```

---

## Phase 13: Documentation Updates

### P13-01: Update Javadoc for Modified Methods

**Description**: Ensure all modified methods have accurate, complete Javadoc.

**Acceptance Criteria**:
- [ ] `Eip712Domain.separator()` - Update Javadoc to describe return value and computation
- [ ] `TypedDataSigner.signHash()` - Add Javadoc explaining package-private usage
- [ ] `TypedDataEncoder.encodeString()` - Document the exception thrown for invalid input
- [ ] `TypedDataEncoder.encodeData()` - Document `Eip712Exception.missingField` thrown for missing fields
- [ ] `TypedDataPayload` constructor - Document that domain cannot be null
- [ ] Run `./gradlew javadoc` to verify Javadoc generates without errors

---

### P13-02: Update brane-core/CLAUDE.md

**File**: `brane-core/CLAUDE.md`

**Description**: Update the EIP-712 package documentation to reflect removed ValueCoercer
and newly implemented separator() method.

**Acceptance Criteria**:
- [ ] Remove `ValueCoercer` from package listing (if present)
- [ ] Update `Eip712Domain` description to mention `separator()` method
- [ ] Verify accuracy of all EIP-712 class descriptions

---

### P13-03: Update docs/brane-signer/eip712.mdx (if needed)

**File**: `docs/brane-signer/eip712.mdx`

**Description**: Ensure external documentation reflects the API accurately.

**Acceptance Criteria**:
- [ ] Verify `Eip712Domain.separator()` is documented if appropriate for public API
- [ ] Ensure no references to removed `ValueCoercer`
- [ ] Verify examples still compile and work

---

## Phase 14: Final Verification

### P14-01: Run Full Test Suite

**Description**: Verify all changes compile and tests pass.

**Acceptance Criteria**:
- [ ] Run `./gradlew compileJava` - no errors
- [ ] Run `./gradlew javadoc` - no errors
- [ ] Run `./scripts/test_unit.sh` - all tests pass
- [ ] Run `./scripts/test_integration.sh` - all tests pass (requires Anvil)
- [ ] Run `./scripts/test_smoke.sh` - all tests pass (requires Anvil)
- [ ] Run `./verify_all.sh` - complete verification passes

---

## Verification Commands

```bash
# Phase 11: After removing dead code
./gradlew compileJava
./gradlew test --tests "*eip712*"

# Phase 12: After each fix
./gradlew test --tests "*Eip712Domain*"
./gradlew test --tests "*TypedData*"
./gradlew test --tests "*TypedDataSigner*"
./gradlew test --tests "*TypedDataEncoder*"
./gradlew test --tests "*TypedDataJson*"

# Phase 13: Documentation
./gradlew javadoc

# Phase 14: Full verification
./verify_all.sh
```
