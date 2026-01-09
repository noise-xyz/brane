# HD Wallet / Mnemonic Support (BIP-32/39/44)

## Overview

Add hierarchical deterministic (HD) wallet support to Brane SDK, enabling users to derive multiple Ethereum addresses from a single mnemonic phrase. This is critical for MEV bots (multi-address strategies), exchanges (per-user deposit addresses), and backend services (multi-tenant isolation).

**Priority**: Critical
**Effort**: High
**Module**: `brane-core` (`io.brane.core.crypto.hd`)

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| Wordlist languages | **English only** | 99%+ of production mnemonics; add others on demand |
| Extended key export (xpub/xprv) | **No** | Security risk; not needed for primary use cases |
| Watch-only wallets | **No** | Different codepath; add later as separate class |
| Key caching | **On-demand derivation** | Memory safety > performance; derivation is ~1ms |
| Dependencies | **BouncyCastle only** | Already in tree; no new deps to audit |
| Signer type | **Reuse PrivateKeySigner** | No duplication of signing logic |
| Wallet lifecycle | **Not Destroyable** | Derived signers manage their own lifecycle; seed cleared on GC (see Security Considerations in class Javadoc) |

---

## Required Changes to Existing Code

### PrivateKeySigner Modifications

The HD wallet feature requires two changes to `PrivateKeySigner`:

1. **Expose existing `Destroyable` support** - `PrivateKey` already implements `Destroyable`; we expose this through `PrivateKeySigner` so callers can clean up derived key material
2. **Add package-private constructor** - Accept `PrivateKey` directly (avoid hex roundtrip)

```java
// brane-core/src/main/java/io/brane/core/crypto/PrivateKeySigner.java

public final class PrivateKeySigner implements Signer, Destroyable {

    private final PrivateKey privateKey;
    private final Address address;

    // Existing public constructor (unchanged)
    public PrivateKeySigner(final String privateKeyHex) {
        this.privateKey = PrivateKey.fromHex(privateKeyHex);
        this.address = privateKey.toAddress();
    }

    // NEW: Package-private constructor for HD wallet derivation
    // Avoids hex encoding/decoding roundtrip and keeps key material in bytes
    PrivateKeySigner(final PrivateKey privateKey) {
        java.util.Objects.requireNonNull(privateKey, "privateKey");
        this.privateKey = privateKey;
        this.address = privateKey.toAddress();
    }

    // ... existing methods unchanged ...

    // NEW: Destroyable implementation
    @Override
    public void destroy() {
        privateKey.destroy();
    }

    @Override
    public boolean isDestroyed() {
        return privateKey.isDestroyed();
    }
}
```

**Rationale**:
- Package-private constructor prevents leaking `PrivateKey` in public API
- `Destroyable` delegates to underlying `PrivateKey.destroy()`
- No breaking changes to existing public API

---

## API Design

### Public API

```java
/**
 * BIP-32/39/44 hierarchical deterministic wallet for Ethereum.
 *
 * <p>Derives multiple addresses from a single mnemonic phrase using standard
 * Ethereum derivation paths ({@code m/44'/60'/account'/0/index}).
 *
 * <h2>Security Considerations</h2>
 *
 * <p><strong>Seed Lifetime:</strong> This class holds the master seed in memory
 * for its entire lifetime. The seed is only cleared when the object is garbage
 * collected. For maximum security:
 * <ol>
 *   <li>Derive all needed signers upfront</li>
 *   <li>Store the signers (not the wallet) for long-running use</li>
 *   <li>Let the wallet reference go out of scope for GC</li>
 *   <li>Call {@link javax.security.auth.Destroyable#destroy()} on each signer when done</li>
 * </ol>
 *
 * <p><strong>Mnemonic Exposure:</strong> The {@link #phrase()} method returns the
 * raw mnemonic. Never log, persist, or transmit this value.
 *
 * @see DerivationPath
 * @since 0.4.0
 */
public final class MnemonicWallet {

    // ===== Static Utilities =====

    /**
     * Validates a BIP-39 mnemonic phrase.
     * Checks word count, wordlist membership, and checksum.
     *
     * @param phrase the mnemonic phrase to validate (may be null)
     * @return true if valid, false if null or invalid
     */
    public static boolean isValidPhrase(String phrase);

    /**
     * Generates a new random 12-word mnemonic phrase.
     * Uses {@link java.security.SecureRandom} for cryptographic entropy.
     *
     * @return a new 12-word BIP-39 mnemonic phrase
     */
    public static String generatePhrase();

    /**
     * Generates a new random mnemonic phrase with specified word count.
     * Uses {@link java.security.SecureRandom} for cryptographic entropy.
     *
     * @param wordCount must be 12, 15, 18, 21, or 24
     * @return a new BIP-39 mnemonic phrase
     * @throws IllegalArgumentException if wordCount is invalid
     */
    public static String generatePhrase(int wordCount);

    // ===== Factory Methods =====

    /**
     * Creates a wallet from an existing mnemonic phrase.
     *
     * @param phrase the BIP-39 mnemonic phrase (must not be null)
     * @return a new MnemonicWallet instance
     * @throws NullPointerException if phrase is null
     * @throws IllegalArgumentException if phrase is invalid
     */
    public static MnemonicWallet fromPhrase(String phrase);

    /**
     * Creates a wallet from an existing mnemonic phrase with passphrase.
     *
     * <p><strong>Note:</strong> Same mnemonic + different passphrase produces
     * a completely different wallet. This is by BIP-39 design.
     *
     * @param phrase the BIP-39 mnemonic phrase (must not be null)
     * @param passphrase optional passphrase for additional security (may be null or empty)
     * @return a new MnemonicWallet instance
     * @throws NullPointerException if phrase is null
     * @throws IllegalArgumentException if phrase is invalid
     */
    public static MnemonicWallet fromPhrase(String phrase, String passphrase);

    // ===== Instance Methods =====

    /**
     * Derives a signer at the given address index.
     * Uses standard Ethereum path: m/44'/60'/0'/0/{addressIndex}
     *
     * <p>Each call returns a new independent {@link Signer} instance.
     * The caller owns the lifecycle and should call {@link Destroyable#destroy()}
     * when done to clear key material from memory.
     *
     * @param addressIndex the address index (0 to 2^31-1)
     * @return a new Signer instance (caller owns lifecycle)
     * @throws IllegalArgumentException if addressIndex is negative
     */
    public Signer derive(int addressIndex);

    /**
     * Derives a signer at a custom derivation path.
     * Use for account isolation or non-standard paths.
     *
     * <p>Each call returns a new independent {@link Signer} instance.
     * The caller owns the lifecycle and should call {@link Destroyable#destroy()}
     * when done to clear key material from memory.
     *
     * @param path the derivation path (must not be null)
     * @return a new Signer instance (caller owns lifecycle)
     * @throws NullPointerException if path is null
     */
    public Signer derive(DerivationPath path);

    /**
     * Returns the mnemonic phrase.
     *
     * <p><strong>Security Warning:</strong> The returned string contains sensitive
     * key material capable of deriving all wallet addresses. Never log, persist
     * to disk, or transmit this value over a network. Consider using
     * {@link #derive(int)} to obtain signers without exposing the phrase.
     *
     * @return the BIP-39 mnemonic phrase
     */
    public String phrase();
}
```

### DerivationPath Record

```java
/**
 * BIP-44 derivation path for Ethereum.
 * Format: m/44'/60'/{account}'/0/{addressIndex}
 *
 * <p>Both account and addressIndex must be in range [0, 2^31-1] per BIP-32.
 */
public record DerivationPath(int account, int addressIndex) {

    /** Maximum valid index per BIP-32 (2^31 - 1). */
    public static final int MAX_INDEX = 0x7FFFFFFF;

    public DerivationPath {
        if (account < 0 || account > MAX_INDEX) {
            throw new IllegalArgumentException(
                "account must be 0 to " + MAX_INDEX + ", got " + account);
        }
        if (addressIndex < 0 || addressIndex > MAX_INDEX) {
            throw new IllegalArgumentException(
                "addressIndex must be 0 to " + MAX_INDEX + ", got " + addressIndex);
        }
    }

    /** Creates path for address index with default account 0. */
    public static DerivationPath of(int addressIndex) {
        return new DerivationPath(0, addressIndex);
    }

    /** Creates path for specific account and address index. */
    public static DerivationPath of(int account, int addressIndex) {
        return new DerivationPath(account, addressIndex);
    }

    /**
     * Parses a path string like "m/44'/60'/0'/0/5".
     *
     * @param path the BIP-44 path string
     * @return parsed DerivationPath
     * @throws NullPointerException if path is null
     * @throws IllegalArgumentException if path format is invalid
     */
    public static DerivationPath parse(String path);

    /** Returns the full BIP-44 path string. */
    public String toPath() {
        return "m/44'/60'/" + account + "'/0/" + addressIndex;
    }
}
```

---

## Architecture

### Key Design Principles

1. **Derived signers are independent**: Each `Signer` returned by `derive()` owns its own `PrivateKey` and manages its own lifecycle via `Destroyable`.

2. **Reuse existing infrastructure**: `derive()` returns `PrivateKeySigner` instances, reusing all existing EIP-191/712 signing logic.

3. **No new dependencies**: Implement BIP-32/39 using BouncyCastle primitives already in the dependency tree.

4. **On-demand derivation**: Keys are derived fresh each call, not cached. Users can cache signers themselves.

### Lifecycle Model

```
MnemonicWallet (holds seed in memory)
    │
    ├── derive(0) → PrivateKeySigner(PrivateKey) ← independent, Destroyable
    ├── derive(1) → PrivateKeySigner(PrivateKey) ← independent, Destroyable
    └── derive(2) → PrivateKeySigner(PrivateKey) ← independent, Destroyable

Each PrivateKeySigner:
  - Owns a PrivateKey internally
  - Implements Destroyable (delegates to PrivateKey.destroy())
  - Destroying one signer does not affect others

The wallet itself is NOT Destroyable - let it be garbage collected.
```

### Security Notes

```java
/**
 * MnemonicWallet holds the master seed in memory for the lifetime of the object.
 *
 * For maximum security:
 * 1. Derive all needed signers upfront
 * 2. Store signers (not the wallet) for long-running use
 * 3. Let the wallet be garbage collected
 * 4. Call destroy() on each signer when done
 *
 * Each derived Signer independently manages its PrivateKey lifecycle.
 */
```

---

## Implementation

### File Structure

```
brane-core/src/main/java/io/brane/core/crypto/hd/
├── MnemonicWallet.java      # Public: main API + static utilities
├── DerivationPath.java      # Public: record for path representation
├── Bip39.java               # Package-private: mnemonic ↔ seed (PBKDF2)
├── Bip32.java               # Package-private: seed → child keys (HMAC-SHA512)
└── EnglishWordlist.java     # Package-private: 2048 BIP-39 words (no public modifier)

brane-core/src/test/java/io/brane/core/crypto/hd/
├── MnemonicWalletTest.java  # Integration + BIP test vectors
├── DerivationPathTest.java  # Path parsing/validation tests
├── Bip39Test.java           # Seed derivation tests
└── Bip32Test.java           # Key derivation tests
```

### Internal Classes

#### Bip39.java (Internal)

```java
/**
 * BIP-39 mnemonic phrase utilities.
 * Uses PBKDF2-HMAC-SHA512 for seed derivation.
 */
final class Bip39 {

    /** Validates mnemonic: word count, wordlist, checksum. */
    static boolean isValid(String mnemonic);

    /** Generates entropy and converts to mnemonic using SecureRandom. */
    static String generate(int wordCount, SecureRandom random);

    /** Derives 64-byte seed from mnemonic using PBKDF2. */
    static byte[] toSeed(String mnemonic, String passphrase);
}
```

**BIP-39 Seed Derivation**:
- Algorithm: PBKDF2-HMAC-SHA512
- Iterations: 2048 (fixed by spec)
- Salt: `"mnemonic" + passphrase` (UTF-8 NFKD normalized)
- Output: 512 bits (64 bytes)

**Implementation Note - Unicode Normalization**:
Both mnemonic and passphrase MUST be NFKD normalized before PBKDF2. This is critical for
interoperability with other wallets when using non-ASCII characters:
```java
import java.text.Normalizer;

String normalizedMnemonic = Normalizer.normalize(mnemonic, Normalizer.Form.NFKD);
String normalizedPassphrase = Normalizer.normalize(passphrase, Normalizer.Form.NFKD);
byte[] salt = ("mnemonic" + normalizedPassphrase).getBytes(StandardCharsets.UTF_8);
```
Failure to normalize will produce different seeds than MetaMask, Ledger, Trezor, etc.

#### Bip32.java (Internal)

```java
/**
 * BIP-32 hierarchical deterministic key derivation.
 * Uses HMAC-SHA512 for child key derivation.
 */
final class Bip32 {

    /** Derives master key from seed. */
    static byte[] masterKey(byte[] seed);

    /** Derives child key at path component. */
    static byte[] deriveChild(byte[] parentKey, byte[] chainCode, int index, boolean hardened);

    /** Derives key at full path from seed. */
    static byte[] derivePath(byte[] seed, int[] path, boolean[] hardened);
}
```

**BIP-32 Key Derivation**:
- Master key: `HMAC-SHA512(seed, "Bitcoin seed")`
- Child derivation: `HMAC-SHA512(chainCode, data)` where data depends on hardened/normal
- Hardened: Uses private key in HMAC input (index >= 2^31)
- Normal: Uses public key in HMAC input (index < 2^31)

---

## BIP Standards Reference

### BIP-39: Mnemonic Phrases

| Word Count | Entropy Bits | Checksum Bits | Total Bits |
|------------|--------------|---------------|------------|
| 12 | 128 | 4 | 132 |
| 15 | 160 | 5 | 165 |
| 18 | 192 | 6 | 198 |
| 21 | 224 | 7 | 231 |
| 24 | 256 | 8 | 264 |

Checksum = first N bits of SHA-256(entropy)

### BIP-44: Derivation Path

**Format**: `m / purpose' / coin_type' / account' / change / address_index`

**Ethereum**: `m/44'/60'/account'/0/address_index`

| Component | Value | Hardened | Notes |
|-----------|-------|----------|-------|
| purpose | 44 | Yes | BIP-44 constant |
| coin_type | 60 | Yes | Ethereum (SLIP-0044) |
| account | 0, 1, ... | Yes | Account isolation (max 2^31-1) |
| change | 0 | No | External (always 0 for ETH) |
| address_index | 0, 1, ... | No | Address counter (max 2^31-1) |

---

## Test Vectors

### BIP-39 Test Vector

```java
@Test
void bip39SeedDerivation() {
    String mnemonic = "abandon abandon abandon abandon abandon abandon " +
                      "abandon abandon abandon abandon abandon about";
    String passphrase = "TREZOR";

    byte[] seed = Bip39.toSeed(mnemonic, passphrase);

    assertEquals(
        "c55257c360c07c72029aebc1b53c05ed0362ada38ead3e3e9efa3708e53495531f09a6987599d18264c1e1c92f2cf141630c7a3c4ab7c81b2f001698e7463b04",
        Hex.encode(seed)
    );
}
```

### BIP-32/44 Ethereum Test Vector

```java
@Test
void ethereumAddressDerivation() {
    String mnemonic = "abandon abandon abandon abandon abandon abandon " +
                      "abandon abandon abandon abandon abandon about";

    MnemonicWallet wallet = MnemonicWallet.fromPhrase(mnemonic);

    // m/44'/60'/0'/0/0
    Signer signer0 = wallet.derive(0);
    assertEquals(
        Address.from("0x9858EfFD232B4033E47d90003D41EC34EcaEda94"),
        signer0.address()
    );
}
```

### Anvil Compatibility Test

```java
@Test
void anvilDefaultMnemonic() {
    // Anvil's default test mnemonic
    String mnemonic = "test test test test test test test test test test test junk";

    MnemonicWallet wallet = MnemonicWallet.fromPhrase(mnemonic);

    // First Anvil account
    Signer signer0 = wallet.derive(0);
    assertEquals(
        Address.from("0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"),
        signer0.address()
    );

    // Second Anvil account
    Signer signer1 = wallet.derive(1);
    assertEquals(
        Address.from("0x70997970C51812dc3A010C7d01b50e0d17dc79C8"),
        signer1.address()
    );
}
```

### DerivationPath Bounds Test

```java
@Test
void derivationPathBounds() {
    // Valid bounds
    assertDoesNotThrow(() -> new DerivationPath(0, 0));
    assertDoesNotThrow(() -> new DerivationPath(DerivationPath.MAX_INDEX, DerivationPath.MAX_INDEX));

    // Invalid: negative
    assertThrows(IllegalArgumentException.class, () -> new DerivationPath(-1, 0));
    assertThrows(IllegalArgumentException.class, () -> new DerivationPath(0, -1));

    // Invalid: overflow (would wrap to negative in int)
    assertThrows(IllegalArgumentException.class, () -> DerivationPath.of(Integer.MIN_VALUE));
}
```

---

## Usage Examples

### MEV Bot: Multiple Strategy Addresses

```java
MnemonicWallet wallet = MnemonicWallet.fromPhrase(mnemonic);

// Derive signers for different strategies
List<Signer> liquidationBots = IntStream.range(0, 10)
    .mapToObj(wallet::derive)
    .toList();

// Account isolation for different strategies
List<Signer> arbBots = IntStream.range(0, 10)
    .mapToObj(i -> wallet.derive(new DerivationPath(1, i)))  // account 1
    .toList();

// Use with Brane client
Brane.Signer client = Brane.connect(rpcUrl, liquidationBots.get(0));
```

### Exchange: Per-User Deposit Addresses

```java
MnemonicWallet wallet = MnemonicWallet.fromPhrase(mnemonic);

// User 12345 deposit address (deterministic)
Signer userSigner = wallet.derive(12345);
Address depositAddress = userSigner.address();

// Later: regenerate same address from user ID
Signer sameSigner = wallet.derive(12345);
assert depositAddress.equals(sameSigner.address());
```

### Multi-Tenant Backend

```java
MnemonicWallet wallet = MnemonicWallet.fromPhrase(mnemonic);

// Tenant isolation via account index
Signer tenant1 = wallet.derive(new DerivationPath(1, 0));  // m/44'/60'/1'/0/0
Signer tenant2 = wallet.derive(new DerivationPath(2, 0));  // m/44'/60'/2'/0/0
```

### Secure Lifecycle Management

```java
// Derive all needed signers upfront
MnemonicWallet wallet = MnemonicWallet.fromPhrase(mnemonic);
List<Signer> signers = IntStream.range(0, 50)
    .mapToObj(wallet::derive)
    .toList();

// Let wallet be garbage collected (seed cleared)
wallet = null;

// Use signers for long-running operations
for (Signer signer : signers) {
    // ... bot operations ...
}

// Clean up when done - destroy each signer to clear key material
for (Signer signer : signers) {
    if (signer instanceof Destroyable d) {
        d.destroy();
    }
}
```

---

## Implementation Plan

### Phase 0: Prerequisite Changes

1. Add `Destroyable` implementation to `PrivateKeySigner`
2. Add package-private `PrivateKeySigner(PrivateKey)` constructor
3. Add tests for new `PrivateKeySigner` functionality

### Phase 1: Core BIP Implementation

1. Create `EnglishWordlist.java` - load 2048 words from resource
2. Create `Bip39.java` - validation, generation, PBKDF2 seed derivation
3. Create `Bip32.java` - HMAC-SHA512 key derivation
4. Unit tests with official BIP-39/32 test vectors

### Phase 2: Public API

1. Create `DerivationPath.java` - record with validation and parsing
2. Create `MnemonicWallet.java` - factory methods and derive()
3. Integration tests with Anvil mnemonic compatibility

### Phase 3: Documentation

1. Add `MnemonicWalletExample.java` to `brane-examples`
2. Update `CLAUDE.md` with HD wallet patterns
3. Update roadmap to mark complete

---

## Dependencies

**No new dependencies required.**

Uses existing BouncyCastle primitives:
- `org.bouncycastle.crypto.generators.PKCS5S2ParametersGenerator` - PBKDF2
- `org.bouncycastle.crypto.macs.HMac` - HMAC-SHA512
- `org.bouncycastle.crypto.digests.SHA512Digest` - SHA-512
- `org.bouncycastle.crypto.digests.SHA256Digest` - SHA-256 (for checksum)

---

## References

- [BIP-32 Specification](https://github.com/bitcoin/bips/blob/master/bip-0032.mediawiki)
- [BIP-39 Specification](https://github.com/bitcoin/bips/blob/master/bip-0039.mediawiki)
- [BIP-44 Specification](https://github.com/bitcoin/bips/blob/master/bip-0044.mediawiki)
- [SLIP-0044 Coin Types](https://github.com/satoshilabs/slips/blob/master/slip-0044.md)
- [BIP-39 Test Vectors](https://github.com/trezor/python-mnemonic/blob/master/vectors.json)
- [viem mnemonicToAccount](https://viem.sh/docs/accounts/local/mnemonicToAccount)
- [alloy MnemonicBuilder](https://alloy.rs/examples/wallets/mnemonic_signer/)
