# HD Wallet Implementation Tasks

Reference: `spec/hd-wallet.md`

---

## Phase 0: Prerequisites

### P0-01: Add Destroyable to PrivateKeySigner

**File**: `brane-core/src/main/java/io/brane/core/crypto/PrivateKeySigner.java`

**Task**: Make `PrivateKeySigner` implement `javax.security.auth.Destroyable` by delegating to the underlying `PrivateKey`.

**Changes**:
1. Add `implements Destroyable` to class declaration
2. Add `destroy()` method that calls `privateKey.destroy()`
3. Add `isDestroyed()` method that calls `privateKey.isDestroyed()`

**Acceptance Criteria**:
- [ ] `PrivateKeySigner` implements `Destroyable`
- [ ] `destroy()` delegates to `privateKey.destroy()`
- [ ] `isDestroyed()` delegates to `privateKey.isDestroyed()`
- [ ] Existing tests pass
- [ ] New test: verify destroy() clears key material

---

### P0-02: Add Package-Private Constructor to PrivateKeySigner

**File**: `brane-core/src/main/java/io/brane/core/crypto/PrivateKeySigner.java`

**Task**: Add a package-private constructor that accepts `PrivateKey` directly (avoids hex roundtrip for HD wallet derivation).

**Changes**:
```java
// Package-private constructor for HD wallet derivation
PrivateKeySigner(final PrivateKey privateKey) {
    java.util.Objects.requireNonNull(privateKey, "privateKey");
    this.privateKey = privateKey;
    this.address = privateKey.toAddress();
}
```

**Acceptance Criteria**:
- [ ] Constructor is package-private (no `public` modifier)
- [ ] Null check with `Objects.requireNonNull`
- [ ] Existing tests pass

---

## Phase 1: Core BIP Implementation

### P1-01: Create EnglishWordlist

**File**: `brane-core/src/main/java/io/brane/core/crypto/hd/EnglishWordlist.java`

**Task**: Create package-private class that loads and provides the 2048 BIP-39 English words.

**Changes**:
1. Create `hd` package under `io.brane.core.crypto`
2. Create `EnglishWordlist.java` (package-private, no `public` modifier)
3. Load words from resource file `bip39-english.txt`
4. Provide `getWord(int index)` and `getIndex(String word)` methods

**Acceptance Criteria**:
- [ ] Class is package-private (`final class EnglishWordlist`)
- [ ] Contains exactly 2048 words
- [ ] `getWord(0)` returns "abandon"
- [ ] `getWord(2047)` returns "zoo"
- [ ] `getIndex("abandon")` returns 0
- [ ] `getIndex("unknown")` returns -1

---

### P1-02: Create Bip39 Implementation

**File**: `brane-core/src/main/java/io/brane/core/crypto/hd/Bip39.java`

**Task**: Create package-private BIP-39 implementation for mnemonic validation, generation, and seed derivation.

**Methods**:
- `static boolean isValid(String mnemonic)` - validates word count, wordlist, checksum
- `static String generate(int wordCount, SecureRandom random)` - generates mnemonic
- `static byte[] toSeed(String mnemonic, String passphrase)` - PBKDF2 seed derivation

**Implementation Notes**:
- Use `java.text.Normalizer` for NFKD normalization (critical for non-ASCII)
- PBKDF2-HMAC-SHA512, 2048 iterations
- Salt: `"mnemonic" + normalizedPassphrase`

**Acceptance Criteria**:
- [ ] Class is package-private
- [ ] Validates 12/15/18/21/24 word mnemonics
- [ ] Rejects invalid word count
- [ ] Rejects words not in wordlist
- [ ] Validates checksum
- [ ] BIP-39 test vector passes:
  - Mnemonic: `abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon abandon about`
  - Passphrase: `TREZOR`
  - Seed: `c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04`

---

### P1-03: Create Bip32 Implementation

**File**: `brane-core/src/main/java/io/brane/core/crypto/hd/Bip32.java`

**Task**: Create package-private BIP-32 implementation for hierarchical key derivation.

**Methods**:
- `static byte[] masterKey(byte[] seed)` - derives master key from seed
- `static byte[] deriveChild(byte[] parentKey, byte[] chainCode, int index, boolean hardened)` - derives child key
- `static byte[] derivePath(byte[] seed, int[] path, boolean[] hardened)` - derives key at full path

**Implementation Notes**:
- Master key: `HMAC-SHA512(seed, "Bitcoin seed")`
- Child derivation: `HMAC-SHA512(chainCode, data)`
- Hardened uses private key, normal uses public key
- Return 32-byte private key (first 32 bytes of HMAC output)

**Acceptance Criteria**:
- [ ] Class is package-private
- [ ] Master key derivation correct
- [ ] Hardened derivation (index >= 0x80000000) correct
- [ ] Normal derivation correct
- [ ] Full path derivation works

---

## Phase 2: Public API

### P2-01: Create DerivationPath Record

**File**: `brane-core/src/main/java/io/brane/core/crypto/hd/DerivationPath.java`

**Task**: Create public record for BIP-44 Ethereum derivation paths.

**API**:
```java
public record DerivationPath(int account, int addressIndex) {
    public static final int MAX_INDEX = 0x7FFFFFFF;
    public static DerivationPath of(int addressIndex);
    public static DerivationPath of(int account, int addressIndex);
    public static DerivationPath parse(String path);
    public String toPath();
}
```

**Acceptance Criteria**:
- [ ] Record with compact constructor validation
- [ ] Rejects negative account/addressIndex
- [ ] Rejects values > MAX_INDEX
- [ ] `of(5)` creates `DerivationPath(0, 5)`
- [ ] `toPath()` returns `m/44'/60'/0'/0/5` format
- [ ] `parse("m/44'/60'/0'/0/5")` returns correct record
- [ ] `parse()` rejects non-BIP-44 Ethereum paths

---

### P2-02: Create MnemonicWallet Class

**File**: `brane-core/src/main/java/io/brane/core/crypto/hd/MnemonicWallet.java`

**Task**: Create main public API for HD wallet functionality.

**API**:
```java
public final class MnemonicWallet {
    // Static utilities
    public static boolean isValidPhrase(String phrase);
    public static String generatePhrase();
    public static String generatePhrase(int wordCount);

    // Factory methods
    public static MnemonicWallet fromPhrase(String phrase);
    public static MnemonicWallet fromPhrase(String phrase, String passphrase);

    // Instance methods
    public Signer derive(int addressIndex);
    public Signer derive(DerivationPath path);
    public String phrase();
}
```

**Acceptance Criteria**:
- [ ] Class-level Javadoc with security warnings (as specified in spec)
- [ ] `generatePhrase()` uses `SecureRandom`
- [ ] `fromPhrase()` validates mnemonic
- [ ] `derive(int)` uses path `m/44'/60'/0'/0/{index}`
- [ ] `derive(DerivationPath)` uses custom account
- [ ] Returns `PrivateKeySigner` instances (via package-private constructor)
- [ ] Each `derive()` call returns independent signer
- [ ] Anvil test vector passes:
  - Mnemonic: `test test test test test test test test test test test junk`
  - derive(0) address: `0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266`
  - derive(1) address: `0x70997970C51812dc3A010C7d01b50e0d17dc79C8`

---

## Phase 3: Tests

### P3-01: Create Bip39Test

**File**: `brane-core/src/test/java/io/brane/core/crypto/hd/Bip39Test.java`

**Task**: Unit tests for BIP-39 implementation with official test vectors.

**Acceptance Criteria**:
- [ ] Test seed derivation with TREZOR test vector
- [ ] Test mnemonic validation (valid/invalid)
- [ ] Test mnemonic generation (12/24 words)
- [ ] Test checksum validation

---

### P3-02: Create Bip32Test

**File**: `brane-core/src/test/java/io/brane/core/crypto/hd/Bip32Test.java`

**Task**: Unit tests for BIP-32 key derivation.

**Acceptance Criteria**:
- [ ] Test master key derivation
- [ ] Test hardened child derivation
- [ ] Test full path derivation

---

### P3-03: Create DerivationPathTest

**File**: `brane-core/src/test/java/io/brane/core/crypto/hd/DerivationPathTest.java`

**Task**: Unit tests for DerivationPath record.

**Acceptance Criteria**:
- [ ] Test valid bounds (0, MAX_INDEX)
- [ ] Test invalid bounds (negative, overflow)
- [ ] Test parse() with valid paths
- [ ] Test parse() rejects invalid paths
- [ ] Test toPath() output format

---

### P3-04: Create MnemonicWalletTest

**File**: `brane-core/src/test/java/io/brane/core/crypto/hd/MnemonicWalletTest.java`

**Task**: Integration tests for MnemonicWallet with test vectors.

**Acceptance Criteria**:
- [ ] BIP-39 test vector (abandon...about + TREZOR)
- [ ] Anvil mnemonic compatibility (test...junk)
- [ ] Phrase generation validation
- [ ] derive() returns independent signers
- [ ] Signer destroy() works correctly

---

## Phase 4: Documentation

### P4-01: Add Example to brane-examples

**File**: `brane-examples/src/main/java/io/brane/examples/MnemonicWalletExample.java`

**Task**: Create usage example demonstrating HD wallet functionality.

**Acceptance Criteria**:
- [ ] Shows phrase generation
- [ ] Shows wallet creation from phrase
- [ ] Shows address derivation
- [ ] Shows lifecycle management (destroy)
- [ ] Compiles and runs without errors

---

### P4-02: Update CLAUDE.md

**File**: `CLAUDE.md`

**Task**: Add HD wallet patterns to quick reference.

**Acceptance Criteria**:
- [ ] Add `MnemonicWallet` to Key Classes table
- [ ] Add usage example in Patterns section
- [ ] Document security considerations

---

## Final Verification

### FV-01: Full Test Suite

**Task**: Run complete test suite and verify all tests pass.

```bash
./gradlew test
```

**Acceptance Criteria**:
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] No compilation warnings in new code

---

### FV-02: API Consistency Check

**Task**: Verify API matches spec exactly.

**Acceptance Criteria**:
- [ ] `MnemonicWallet` API matches spec
- [ ] `DerivationPath` API matches spec
- [ ] Javadoc includes security warnings
- [ ] No public exposure of internal classes
