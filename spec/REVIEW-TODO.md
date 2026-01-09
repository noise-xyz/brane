# HD Wallet Feature Branch - Code Review (Second Pass)

**Branch:** `feat/hd-wallet`
**Reviewer:** Claude Code
**Date:** 2026-01-09
**Review Focus:** Code Quality, Maintainability, Architectural Soundness, Security
**Confidence:** High

---

## Executive Summary

The HD wallet implementation is **correct and well-tested** against BIP-39/BIP-32 test vectors. However, this deeper review uncovered several **security lifecycle issues**, **API inconsistencies**, and **defensive coding gaps** that should be addressed.

| Severity | Count | Summary |
|----------|-------|---------|
| **T1 (Confirmed Bug)** | 1 | Javadoc/code mismatch in validation |
| **T2 (Potential Bug)** | 3 | Security lifecycle, timing attack surface |
| **T3 (Design Concern)** | 6 | Architecture and API issues |
| **T4 (Suggestion)** | 10 | Code quality improvements |

---

## T1: Confirmed Bugs

### T1-01: DerivationPath Constructor Javadoc Claims MAX_INDEX Validation That Doesn't Exist

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/DerivationPath.java:34-43`

**Classification:** Confirmed Bug (Documentation/Contract Mismatch)

**Description:**
The compact constructor's Javadoc claims `@throws IllegalArgumentException if account or addressIndex is negative or exceeds MAX_INDEX` but the code only checks for negative values, not MAX_INDEX exceedance.

**Evidence - Execution Trace:**
```
JAVADOC CLAIM (line 34):
@throws IllegalArgumentException if account or addressIndex is negative or exceeds MAX_INDEX

ACTUAL CODE (lines 36-43):
public DerivationPath {
    if (account < 0) {
        throw new IllegalArgumentException("Account index cannot be negative: " + account);
    }
    if (addressIndex < 0) {
        throw new IllegalArgumentException("Address index cannot be negative: " + addressIndex);
    }
    // NO CHECK FOR > MAX_INDEX!
}

TRACE:
1. User calls: new DerivationPath(Integer.MAX_VALUE, 0)
2. account < 0 is false (Integer.MAX_VALUE is positive)
3. addressIndex < 0 is false
4. DerivationPath created successfully

EXPECTED per Javadoc: IllegalArgumentException (MAX_INDEX exceeded)
ACTUAL: Success - no exception thrown
```

**Existing Test Analysis:**
- `DerivationPathTest.testConstructorWithMaxIndex()` at line 24-28 passes MAX_INDEX and expects success
- Tests for exceeds-maximum only exist for `parse()` method, not constructor
- The Javadoc and code are inconsistent

**Counter-Argument:**
Since MAX_INDEX = Integer.MAX_VALUE, you literally cannot pass an int > MAX_INDEX in Java. The check `< 0` catches integer overflow (which wraps to negative). So the BEHAVIOR is correct, but the DOCUMENTATION is misleading - it says we check "> MAX_INDEX" when we actually check "< 0".

**Verdict:**
Documentation bug. The Javadoc promises a check that doesn't exist. While the outcome is correct due to integer overflow semantics, the contract is misleading.

**Acceptance Criteria:**
- [ ] Update Javadoc to accurately describe the validation: "if account or addressIndex is negative"
- [ ] OR: Add explicit `> MAX_INDEX` checks to match Javadoc
- [ ] Add test: `testConstructorDocumentationAccuracy()` to verify Javadoc matches behavior

---

## T2: Potential Bugs

### T2-01: MnemonicWallet Cannot Clear Sensitive Data from Memory

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/MnemonicWallet.java:56-71`

**Classification:** Potential Bug (Security)

**Description:**
`MnemonicWallet` holds the mnemonic phrase and master key indefinitely. Unlike derived `PrivateKeySigner` instances which implement `Destroyable`, the wallet itself cannot be cleared from memory.

**Evidence - Code Path:**
```java
// MnemonicWallet.java
public final class MnemonicWallet {  // Does NOT implement Destroyable!
    private final String phrase;              // Cannot be zeroed (String immutable)
    private final Bip32.ExtendedKey masterKey; // byte[] could be zeroed, but isn't

    // NO destroy() method exists
}
```

**Trigger Scenario:**
1. Application creates `MnemonicWallet.fromPhrase(userPhrase)`
2. Wallet held in field for session duration
3. User clicks "logout" expecting keys cleared
4. Memory dump reveals phrase and master key bytes
5. Attacker gains access to ALL derived keys

**Why Existing Tests Miss It:**
- Tests focus on functional correctness
- No tests for `Destroyable` interface compliance
- Security lifecycle not tested

**Counter-Argument:**
- Java Strings are immutable - even with `Destroyable`, we cannot zero the phrase
- Derived signers DO implement `Destroyable`
- GC will eventually clean up unreferenced wallets

**Verdict:**
Real security concern. While we can't zero the String phrase, we CAN and SHOULD:
1. Implement `Destroyable` to zero `masterKey.keyBytes()` and `masterKey.chainCode()`
2. Document the String limitation clearly
3. Throw on `derive()` after destruction

**Acceptance Criteria:**
- [ ] `MnemonicWallet` implements `javax.security.auth.Destroyable`
- [ ] `destroy()` zeros masterKey byte arrays via `Arrays.fill(..., (byte) 0)`
- [ ] `derive()` throws `IllegalStateException` after `destroy()` called
- [ ] `isDestroyed()` returns correct state
- [ ] Javadoc documents that phrase String cannot be zeroed
- [ ] Add tests: `testDestroyZerosMasterKey()`, `testDeriveAfterDestroyThrows()`

---

### T2-02: ExtendedKey Record with byte[] Breaks Immutability Contract

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/Bip32.java:260-269`

**Classification:** Potential Bug (Architectural)

**Description:**
`ExtendedKey` is a record containing `byte[]` components. Records are expected to be immutable value types, but byte arrays are mutable. The record accessors return the array references directly, allowing callers to mutate "immutable" state.

**Evidence - Mutation Path:**
```java
// Bip32.java:260
record ExtendedKey(byte[] keyBytes, byte[] chainCode) { ... }

// Attack scenario:
ExtendedKey master = Bip32.masterKey(seed);
byte[] stolen = master.keyBytes();  // Gets reference to internal array
stolen[0] = 0x00;                   // Mutates master key!
// All subsequent derivations from this master are now corrupted

// Also: record equals() uses Object.equals on arrays (reference equality)
ExtendedKey key1 = new ExtendedKey(new byte[32], new byte[32]);
ExtendedKey key2 = new ExtendedKey(new byte[32], new byte[32]);
key1.equals(key2);  // FALSE! Even though contents are identical
```

**Trigger Scenario:**
- Currently mitigated by package-private visibility
- `MnemonicWallet.derive()` correctly clones before passing to `PrivateKey`
- But if ExtendedKey is ever exposed or used incorrectly internally, corruption occurs

**Why Existing Tests Miss It:**
- Tests use Hex.encode() for comparison, not equals()
- No tests verify array mutation doesn't affect key
- Package-private visibility limits exposure

**Counter-Argument:**
- `ExtendedKey` is package-private, limiting access
- Current usage in `MnemonicWallet.derive()` clones correctly:
  ```java
  PrivateKey.fromBytes(derivedKey.keyBytes().clone());  // Correct!
  ```
- No current code path allows external mutation

**Verdict:**
Design flaw with current mitigation. The pattern is fragile - future code could easily forget to clone.

**Acceptance Criteria:**
- [ ] Convert `ExtendedKey` from record to final class
- [ ] Constructor makes defensive copies: `this.keyBytes = keyBytes.clone()`
- [ ] Accessors return copies: `return keyBytes.clone()`
- [ ] Implement proper `equals()` using `Arrays.equals()`
- [ ] Implement proper `hashCode()` using `Arrays.hashCode()`
- [ ] Add `destroy()` method to zero arrays
- [ ] Alternative: Keep record, add Javadoc warning "accessors return shared arrays"

---

### T2-03: Non-Constant-Time Checksum Comparison (Timing Attack Surface)

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/Bip39.java:217-224`

**Classification:** Potential Bug (Security)

**Description:**
The checksum verification uses early-return comparison, which can leak timing information about how many checksum bits match.

**Evidence:**
```java
// Bip39.java:217-224
for (int i = 0; i < checksumBits; i++) {
    boolean expectedBit = (hash[i / 8] & (1 << (7 - (i % 8)))) != 0;
    if (bits[entropyBits + i] != expectedBit) {
        return false;  // EARLY RETURN - timing leak!
    }
}
return true;
```

**Trigger Scenario:**
1. Attacker has partial knowledge of target's mnemonic (first 11 words)
2. Attacker measures validation time for candidate 12th words
3. Longer validation time = more checksum bits matched
4. With enough samples, attacker can deduce checksum and narrow candidates

**Why Existing Tests Miss It:**
- Timing attacks require statistical analysis of many calls
- Unit tests don't measure execution time
- Functional correctness tests pass

**Counter-Argument:**
- BIP-39 checksum is only 4-8 bits (depending on word count)
- Attacker needs 11 of 12 words known - unlikely scenario
- Timing differences are nanoseconds, hard to measure remotely
- Network latency dwarfs any timing signal
- This is validation, not authentication - no rate limiting concern

**Verdict:**
Low-severity theoretical vulnerability. The attack requires:
1. Knowing 11/12 words already
2. Local access or zero network latency
3. Many thousands of timing measurements

Still, constant-time comparison is a security best practice.

**Acceptance Criteria:**
- [ ] Replace early-return with XOR accumulator pattern:
  ```java
  int diff = 0;
  for (int i = 0; i < checksumBits; i++) {
      boolean expected = (hash[i / 8] & (1 << (7 - (i % 8)))) != 0;
      boolean actual = bits[entropyBits + i];
      diff |= (expected ? 1 : 0) ^ (actual ? 1 : 0);
  }
  return diff == 0;
  ```
- [ ] Add comment explaining constant-time requirement
- [ ] OR: Document as accepted risk with rationale

---

## T3: Design Concerns

### T3-01: Intermediate Derivation Keys Not Zeroed in derivePath()

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/Bip32.java:181-211`

**Classification:** Design Concern (Security)

**Description:**
When deriving path `m/44'/60'/0'/0/0`, five intermediate `ExtendedKey` objects are created. Each contains sensitive key material that persists in heap until GC.

**What:**
```java
ExtendedKey current = masterKey;
for (int i = 1; i < components.length; i++) {
    // ...
    current = deriveChild(current, index);  // Old 'current' now garbage but not zeroed
}
return current;
```

**Why It Matters:**
- Intermediate keys (especially `m/44'/60'/0'`) can derive many addresses
- Memory dump between derivation and GC exposes these keys
- Defense-in-depth: sensitive data should be cleared ASAP

**Reference:**
- BouncyCastle's key classes often implement `Destroyable`
- OWASP recommends minimizing sensitive data lifetime

**Acceptance Criteria:**
- [ ] Refactor `ExtendedKey` to support `destroy()` (see T2-02)
- [ ] `derivePath()` calls `destroy()` on intermediate keys
- [ ] OR: Add Javadoc warning about intermediate key lifetime
- [ ] Consider: Provide `derivePathSecure()` variant that zeros intermediates

---

### T3-02: Bip39.toSeed() Doesn't Validate Mnemonic (Intentional but Asymmetric)

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/Bip39.java:113-142`

**Classification:** Design Concern (API Consistency)

**Description:**
`toSeed()` accepts any string and produces a seed, while `isValid()` performs full validation. This is per BIP-39 spec but creates API asymmetry.

**What:**
```java
// This works - produces a valid seed from garbage input:
byte[] seed = Bip39.toSeed("not a valid mnemonic at all", "");

// But this fails:
MnemonicWallet.fromPhrase("not a valid mnemonic at all");  // Throws!
```

**Why It Matters:**
- Users might expect toSeed() to validate
- Consistent APIs are easier to use correctly
- BIP-39 allows this, but it's surprising

**Reference:**
- BIP-39 spec: "The mnemonic must encode entropy in a multiple of 32 bits... however, any input generates a valid seed"

**Acceptance Criteria:**
- [ ] Add Javadoc to `toSeed()` explicitly noting: "Does not validate mnemonic format. Any string produces a valid seed per BIP-39. Use isValid() first if validation is required."
- [ ] Consider: Add `toSeedValidated()` that throws on invalid mnemonic

---

### T3-03: RuntimeException Instead of Typed Exception in Crypto Operations

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/Bip39.java:139-141, 234-236`

**Classification:** Design Concern (Exception Handling)

**Description:**
Crypto failures wrap in `RuntimeException`, bypassing Brane's exception hierarchy.

**What:**
```java
// Bip39.java:139-141
} catch (GeneralSecurityException e) {
    throw new RuntimeException("PBKDF2 derivation failed", e);
}

// Bip39.java:234-236
} catch (java.security.NoSuchAlgorithmException e) {
    throw new RuntimeException("SHA-256 not available", e);
}
```

**Why It Matters:**
- `RuntimeException` is generic, not part of Brane's error hierarchy
- Callers can't catch specifically
- Inconsistent with rest of codebase

**Reference:**
- `BraneException` hierarchy defined in `io.brane.core.error`
- Similar crypto code uses typed exceptions

**Counter-Argument:**
- These conditions should NEVER occur (SHA-256 and PBKDF2WithHmacSHA512 are mandated by Java spec)
- Making callers handle impossible cases adds noise
- `AssertionError` or `IllegalStateException` might be more appropriate than RuntimeException

**Acceptance Criteria:**
- [ ] Replace `RuntimeException` with `IllegalStateException` (more specific)
- [ ] OR: Create `CryptoInitializationException` extends `BraneException`
- [ ] Add comments explaining these are "should never happen" paths
- [ ] Document in Javadoc: "@throws IllegalStateException if JVM lacks required crypto algorithms"

---

### T3-04: MnemonicWallet.fromPhrase() Double-Normalizes Input

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/MnemonicWallet.java:141-150`

**Classification:** Design Concern (Performance/Clarity)

**Description:**
`fromPhrase()` validates with `Bip39.isValid()` which normalizes, then passes to constructor which calls `toSeed()` which normalizes again.

**What:**
```java
public static MnemonicWallet fromPhrase(String phrase, String passphrase) {
    // ... null checks ...
    if (!Bip39.isValid(phrase)) {  // NORMALIZES phrase internally
        throw new IllegalArgumentException("Invalid mnemonic phrase");
    }
    return new MnemonicWallet(phrase, passphrase);  // Constructor calls toSeed which NORMALIZES again
}
```

**Why It Matters:**
- NFKD normalization is called twice on phrase
- Minor performance overhead
- Code structure hides this duplication

**Acceptance Criteria:**
- [ ] Refactor: `isValid()` returns normalized phrase or Optional
- [ ] OR: Accept as minor inefficiency, add comment noting intentional double-normalize
- [ ] Consider: Cache normalized form if wallet is long-lived

---

### T3-05: PrivateKeySigner.fromPrivateKey() Public API for Internal Use

**Location:** `brane-core/src/main/java/io/brane/core/crypto/PrivateKeySigner.java:52-63`

**Classification:** Design Concern (API Design)

**Description:**
The factory method is public but documented as "intended for HD wallet derivation". This creates an unclear public API surface.

**What:**
```java
/**
 * <p>This factory method is intended for HD wallet derivation in the
 * {@code io.brane.core.crypto.hd} package, avoiding hex encoding roundtrips.
 */
public static PrivateKeySigner fromPrivateKey(final PrivateKey privateKey) {
```

**Why It Matters:**
- Public API should be intentionally designed for external use
- "Intended for X" suggests it shouldn't be public
- Users might use it incorrectly

**Acceptance Criteria:**
- [ ] Clarify Javadoc: "Advanced API for users who already have a PrivateKey. Most users should use the String constructor."
- [ ] Ensure examples show recommended patterns
- [ ] OR: Make package-private and move HD classes to same package

---

### T3-06: EnglishWordlist.getIndex() Confusing Error for Null Input

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/EnglishWordlist.java:72-78`

**Classification:** Design Concern (Error Messages)

**Description:**
Passing null to `getIndex()` throws `IllegalArgumentException` with message "Word not found in BIP-39 wordlist: null". This is technically correct but confusing - null isn't a "word not found", it's invalid input.

**What:**
```java
static int getIndex(String word) {
    Integer index = WORD_TO_INDEX.get(word);  // Returns null for null input
    if (index == null) {
        throw new IllegalArgumentException("Word not found in BIP-39 wordlist: " + word);
    }
    return index;
}
```

**Acceptance Criteria:**
- [ ] Add explicit null check: `Objects.requireNonNull(word, "word cannot be null")`
- [ ] Move "not found" check after null check
- [ ] OR: Accept current behavior, it's an internal API

---

## T4: Suggestions

### T4-01: Extract BIP-44 Constants in DerivationPath

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/DerivationPath.java:93-114`

**Description:**
Magic strings "44" and "60" used directly. Constants improve readability.

```java
// Current:
if (!isHardenedComponent(components[1], "44")) { ... }

// Suggested:
private static final String BIP44_PURPOSE = "44";
private static final String ETH_COIN_TYPE = "60";
private static final String EXTERNAL_CHAIN = "0";
```

**Acceptance Criteria:**
- [ ] Extract constants: `BIP44_PURPOSE`, `ETH_COIN_TYPE`, `EXTERNAL_CHAIN`

---

### T4-02: Add @since Annotations to New Public API

**Location:** Multiple files

**Description:**
New public API should have `@since` annotations for version tracking.

**Acceptance Criteria:**
- [ ] Add `@since 0.3.0` (or appropriate) to:
  - `MnemonicWallet`
  - `DerivationPath`
  - `PrivateKeySigner.fromPrivateKey()`

---

### T4-03: Bip32.toBytes32() Inconsistent Return Path

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/Bip32.java:240-252`

**Description:**
When `bytes.length == 32`, returns `bytes` directly. Other branches copy into `result`. This is correct but inconsistent.

```java
private static byte[] toBytes32(BigInteger value) {
    byte[] bytes = value.toByteArray();
    byte[] result = new byte[32];  // Allocated but unused when length==32
    if (bytes.length == 32) {
        return bytes;  // Direct return
    } else if (bytes.length < 32) {
        System.arraycopy(bytes, 0, result, 32 - bytes.length, bytes.length);
    } else {
        System.arraycopy(bytes, bytes.length - 32, result, 0, 32);
    }
    return result;
}
```

**Acceptance Criteria:**
- [ ] Refactor to avoid unnecessary allocation:
  ```java
  if (bytes.length == 32) return bytes;
  byte[] result = new byte[32];
  // ... rest
  ```
- [ ] OR: Always copy for consistency (minor perf impact)

---

### T4-04: entropyToMnemonic() Doesn't Zero Hash

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/Bip39.java:147-185`

**Description:**
The SHA-256 hash of entropy is computed but not zeroed. While hash doesn't reveal entropy, zeroing is consistent with other sensitive data handling.

```java
byte[] hash = sha256(entropy);
// ... use hash ...
// hash not zeroed
```

**Acceptance Criteria:**
- [ ] Add `Arrays.fill(hash, (byte) 0)` after use
- [ ] OR: Document why hash zeroing is unnecessary

---

### T4-05: Redundant Word Lookups in Bip39.isValid()

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/Bip39.java:59-67`

**Description:**
`isValid()` calls `contains()` for each word, then `verifyChecksum()` calls `getIndex()` for each word again. Two HashMap lookups per word.

**Acceptance Criteria:**
- [ ] Refactor: `verifyChecksum()` returns false for unknown words (catches its own exceptions)
- [ ] OR: Accept as minor overhead (HashMap lookup is O(1))

---

### T4-06: MnemonicWallet.toString() Recomputes Word Count

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/MnemonicWallet.java:207-209`

**Description:**
Each `toString()` call splits the phrase to count words. Could cache.

```java
public String toString() {
    int wordCount = phrase.split("\\s+").length;  // Computed every time
    return "MnemonicWallet[" + wordCount + " words]";
}
```

**Acceptance Criteria:**
- [ ] Cache word count in constructor
- [ ] OR: Accept as minor overhead (toString rarely called in hot path)

---

### T4-07: Add Concurrent Access Tests

**Location:** Test files

**Description:**
No tests verify thread safety of `MnemonicWallet.derive()` under concurrent access.

**Acceptance Criteria:**
- [ ] Add test: Multiple threads calling `derive()` simultaneously
- [ ] Verify results are consistent and no exceptions thrown

---

### T4-08: Bip32.deriveChild() Index Semantics Unclear

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/Bip32.java:94-143`

**Description:**
The method uses `index < 0` to detect hardened derivation (high bit set). This works due to Java's signed int semantics but is non-obvious.

```java
if (index < 0) {
    // Hardened derivation
} else {
    // Normal derivation
}
```

**Acceptance Criteria:**
- [ ] Add comment explaining the signed/unsigned semantics
- [ ] Consider: Use `Integer.compareUnsigned(index, HARDENED_OFFSET) >= 0` for clarity

---

### T4-09: Leading Whitespace Handling Could Be More Forgiving

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/Bip39.java:47-68`

**Description:**
A mnemonic with leading/trailing whitespace fails validation due to `split("\\s+")` creating empty array elements.

```java
"  abandon abandon ... ".split("\\s+")  // ["", "abandon", ...] - 13 elements for 12-word phrase
```

**Acceptance Criteria:**
- [ ] Trim input before splitting: `normalized.trim().split("\\s+")`
- [ ] OR: Document that whitespace must be exact
- [ ] Consider: More forgiving input handling for user-pasted text

---

### T4-10: verifyChecksum() Doesn't Zero Entropy Array

**Location:** `brane-core/src/main/java/io/brane/core/crypto/hd/Bip39.java:206-226`

**Description:**
Reconstructed entropy from mnemonic words is not zeroed after checksum verification.

```java
byte[] entropy = new byte[entropyBytes];
// ... populate entropy from bits ...
byte[] hash = sha256(entropy);
// ... verify checksum ...
// entropy not zeroed!
return true;
```

**Acceptance Criteria:**
- [ ] Add try-finally to zero entropy: `Arrays.fill(entropy, (byte) 0)`
- [ ] Also zero hash array

---

## Summary of Required Actions

### Critical (Must Fix Before Merge)

| ID | Task | File | Priority |
|----|------|------|----------|
| T1-01 | Fix Javadoc/code mismatch in DerivationPath | DerivationPath.java | High |
| T2-01 | Implement Destroyable in MnemonicWallet | MnemonicWallet.java | High |

### Important (Should Fix Soon)

| ID | Task | File | Priority |
|----|------|------|----------|
| T2-02 | Fix ExtendedKey immutability | Bip32.java | Medium |
| T3-01 | Zero intermediate derivation keys | Bip32.java | Medium |
| T3-03 | Use typed exceptions | Bip39.java | Medium |
| T2-03 | Constant-time checksum comparison | Bip39.java | Low |

### Nice to Have

| ID | Task | File | Priority |
|----|------|------|----------|
| T3-02 | Document toSeed() validation behavior | Bip39.java | Low |
| T3-04 | Fix double-normalization | MnemonicWallet.java | Low |
| T3-05 | Clarify fromPrivateKey() API | PrivateKeySigner.java | Low |
| T3-06 | Better null handling in getIndex() | EnglishWordlist.java | Low |
| T4-* | All suggestions | Various | Optional |

---

## Test Checklist for Fixes

```java
// T1-01: Javadoc accuracy
@Test
void testConstructorJavadocAccuracy() {
    // Verify that the documented behavior matches actual behavior
    // Currently: Javadoc says throws on > MAX_INDEX, code only checks < 0
    var maxPath = new DerivationPath(DerivationPath.MAX_INDEX, DerivationPath.MAX_INDEX);
    assertEquals(DerivationPath.MAX_INDEX, maxPath.account());
    // If we add explicit MAX_INDEX check, this test would change
}

// T2-01: Destroyable implementation
@Test
void testMnemonicWalletDestroy() {
    MnemonicWallet wallet = MnemonicWallet.fromPhrase(TEST_PHRASE);
    assertFalse(wallet.isDestroyed());

    wallet.destroy();
    assertTrue(wallet.isDestroyed());

    assertThrows(IllegalStateException.class, () -> wallet.derive(0));
}

// T2-02: ExtendedKey immutability
@Test
void testExtendedKeyImmutability() {
    byte[] key = new byte[32];
    key[0] = 1;
    ExtendedKey extended = new ExtendedKey(key.clone(), new byte[32]);

    // Mutating the accessor result shouldn't affect internal state
    extended.keyBytes()[0] = 99;
    assertEquals(1, extended.keyBytes()[0]);  // Should still be 1 if defensive copy
}

// T2-03: Constant-time comparison
@Test
void testChecksumVerificationConstantTime() {
    // Statistical timing test - run many iterations and verify variance is low
    // Implementation depends on timing measurement framework
}

// T4-07: Concurrent access
@Test
void testConcurrentDerivation() throws Exception {
    MnemonicWallet wallet = MnemonicWallet.fromPhrase(TEST_PHRASE);
    int threads = 10;
    ExecutorService executor = Executors.newFixedThreadPool(threads);

    List<Future<String>> futures = new ArrayList<>();
    for (int i = 0; i < threads; i++) {
        final int index = i % 5;
        futures.add(executor.submit(() -> wallet.derive(index).address().value()));
    }

    // All same-index derivations should produce identical addresses
    Map<Integer, String> expected = new HashMap<>();
    for (int i = 0; i < 5; i++) {
        expected.put(i, wallet.derive(i).address().value());
    }

    for (int i = 0; i < threads; i++) {
        String result = futures.get(i).get(5, TimeUnit.SECONDS);
        assertEquals(expected.get(i % 5), result);
    }

    executor.shutdown();
}

// T4-09: Whitespace handling
@Test
void testMnemonicWithLeadingWhitespace() {
    // Current behavior: fails
    // After fix: should succeed
    String phraseWithWhitespace = "  " + TEST_PHRASE + "  ";

    // Current:
    assertFalse(MnemonicWallet.isValidPhrase(phraseWithWhitespace));

    // After T4-09 fix:
    // assertTrue(MnemonicWallet.isValidPhrase(phraseWithWhitespace));
}
```

---

## Architecture Assessment

### Strengths
- Clean separation: Bip32, Bip39, DerivationPath, MnemonicWallet
- Package-private internals (Bip32, Bip39, EnglishWordlist)
- Good use of Java 21 features (records, var)
- Comprehensive test coverage with BIP test vectors
- Proper seed zeroing after master key derivation

### Weaknesses
- Security lifecycle incomplete (MnemonicWallet not Destroyable)
- ExtendedKey record with mutable byte[] arrays
- Inconsistent exception types (RuntimeException vs BraneException)
- Some documentation/code mismatches

### Recommendations
1. Complete security lifecycle with Destroyable pattern
2. Convert ExtendedKey to proper immutable class
3. Standardize on typed exceptions
4. Add @since annotations before release

---

## Confidence Assessment

| Category | Confidence | Rationale |
|----------|------------|-----------|
| T1 Findings | High | Code traced, Javadoc verified |
| T2 Findings | High | Security patterns traced, counter-arguments considered |
| T3 Findings | High | Design patterns compared to standards |
| T4 Findings | High | Straightforward code quality observations |

**Overall Confidence:** Would bet money on T1-01 being a real doc bug. T2-01 and T2-02 are genuine security design issues that should be fixed before production use.
