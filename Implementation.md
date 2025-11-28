# Phase 2 – Crypto Independence (0.2.0)

## Executive Summary

Remove web3j's vendored transaction encoder/signer and replace with Brane-native implementation using:
- **Existing**: RLP encoder in `brane-primitives` (complete, production-ready)
- **Existing**: BouncyCastle dependency in `brane-core` (already present)
- **New**: Crypto primitives (Keccak, secp256k1) using BouncyCastle
- **Refactor**: Transaction models and signing abstraction

**Current State:**
- ✅ RLP: Complete implementation in `brane-primitives/rlp/`
- ✅ Hex: Complete implementation in `brane-primitives/Hex.java`
- ✅ BouncyCastle: Already in `brane-core/build.gradle`
- ❌ **Blocker**: ~105 web3j files vendored in `io.brane.internal.web3j`
- ❌ Transaction encoding uses `io.brane.internal.web3j.crypto.TransactionEncoder`
- ❌ Signing uses `io.brane.internal.web3j.crypto.Credentials`

---

## Architecture

```
brane-primitives (standalone, zero deps)
├── Hex ✅
└── RLP ✅

brane-core (depends on: primitives, BouncyCastle ✅)
├── crypto/
│   ├── Keccak256 (new)
│   ├── Secp256k1 (new)
│   ├── Signature (new)
│   └── PrivateKey (new)
├── tx/
│   ├── LegacyTransaction (new, replaces RawTransaction)
│   ├── Eip1559Transaction (new)
│   ├── UnsignedTransaction (new interface)
│   └── SignedTransaction (new)
└── model/
    └── TransactionRequest ✅ (update conversion logic)

brane-rpc (depends on: core)
├── TransactionSigner ✅ (refactor to use new types)
├── PrivateKeyTransactionSigner ✅ (remove web3j, use brane-core crypto)
└── DefaultWalletClient ✅ (update to use new transaction types)
```

---

## Milestone 1 – Crypto Primitives in `brane-core` (P0)

**Goal:** Add secp256k1 + Keccak using existing BouncyCastle dependency.

### Why `brane-core` not `brane-primitives`?

`brane-primitives` is **zero-dependency** (pure JDK). Crypto requires BouncyCastle, which is already in `brane-core`. Keep primitives clean.

### Tasks

* [ ] **`brane-core/src/main/java/io/brane/core/crypto/Keccak256.java`**
  ```java
  public final class Keccak256 {
      public static byte[] hash(byte[] input) {
          // Use org.bouncycastle.jcajce.provider.digest.Keccak$Digest256
      }
  }
  ```

* [ ] **`brane-core/src/main/java/io/brane/core/crypto/Signature.java`**
  ```java
  public record Signature(byte[] r, byte[] s, int v) {
      // Immutable signature holder
      // v encoding: EIP-155 format (chainId * 2 + 35 + yParity)
  }
  ```

* [ ] **`brane-core/src/main/java/io/brane/core/crypto/PrivateKey.java`**
  ```java
  public final class PrivateKey {
      private final byte[] keyBytes; // 32 bytes
      
      public static PrivateKey fromHex(String hex);
      public Address toAddress(); // derive via Keccak256(publicKey)[12:32]
      public Signature sign(byte[] messageHash);
      public static Address recoverAddress(byte[] messageHash, Signature sig);
  }
  ```
  - Uses BouncyCastle's `ECKeyPair` + `ECDSASigner`
  - Implements deterministic ECDSA (RFC 6979)
  - Low-s normalization
  - Public key recovery

### Acceptance Criteria

* `PrivateKey.sign()` + `recoverAddress()` roundtrip matches expected address
* Keccak256 matches Ethereum test vectors (.e.g. empty string = `0xc5d2460186f7233c927e7db2dcc703c0e500b653ca82273b7bfad8045d85a470`)
* **Zero web3j usage** in crypto code

---

## Milestone 2 – Transaction Models in `brane-core` (P0)

**Goal:** Define EIP-155/EIP-1559/EIP-2718 transaction types using native RLP encoding.

### Current Problem

`DefaultWalletClient` uses:
```java
io.brane.internal.web3j.crypto.RawTransaction  // ❌ vendored web3j
io.brane.internal.web3j.crypto.TransactionEncoder  // ❌ vendored web3j
```

### Solution: New Transaction Hierarchy

* [ ] **`brane-core/src/main/java/io/brane/core/tx/UnsignedTransaction.java`**
  ```java
  public sealed interface UnsignedTransaction 
      permits LegacyTransaction, Eip1559Transaction {
      
      byte[] encodeForSigning(long chainId);
      byte[] encodeAsEnvelope(Signature signature);
  }
  ```

* [ ] **`brane-core/src/main/java/io/brane/core/tx/LegacyTransaction.java`**
  ```java
  public record LegacyTransaction(
      long nonce,
      Wei gasPrice,
      long gasLimit,
      Address to,  // nullable for contract creation
      Wei value,
      HexData data
  ) implements UnsignedTransaction {
      
      public byte[] encodeForSigning(long chainId) {
          // RLP([nonce, gasPrice, gasLimit, to, value, data, chainId, 0, 0])
          return Rlp.encodeList(List.of(
              RlpNumeric.of(nonce),
              RlpNumeric.of(gasPrice.value()),
              RlpNumeric.of(gasLimit),
              RlpString.of(to != null ? to.bytes() : new byte[0]),
              RlpNumeric.of(value.value()),
              RlpString.of(data.bytes()),
              RlpNumeric.of(chainId),
              RlpNumeric.of(0),
              RlpNumeric.of(0)
          ));
      }
      
      public byte[] encodeAsEnvelope(Signature signature) {
          // RLP([nonce, gasPrice, gasLimit, to, value, data, v, r, s])
          return Rlp.encodeList(...);
      }
  }
  ```

* [ ] **`brane-core/src/main/java/io/brane/core/tx/Eip1559Transaction.java`**
  ```java
  public record Eip1559Transaction(
      long chainId,
      long nonce,
      Wei maxPriorityFeePerGas,
      Wei maxFeePerGas,
      long gasLimit,
      Address to,
      Wei value,
      HexData data,
      List<AccessListEntry> accessList
  ) implements UnsignedTransaction {
      
      public byte[] encodeForSigning(long chainId) {
          // 0x02 || RLP([chainId, nonce, maxPriorityFee, maxFee, gasLimit, to, value, data, accessList])
          byte[] rlp = Rlp.encodeList(...);
          byte[] result = new byte[rlp.length + 1];
          result[0] = 0x02;
          System.arraycopy(rlp, 0, result, 1, rlp.length);
          return result;
      }
      
      public byte[] encodeAsEnvelope(Signature signature) {
          // 0x02 || RLP([chainId, nonce, ..., accessList, yParity, r, s])
          // Note: v in EIP-1559 is just yParity (0 or 1), not EIP-155 encoding
          return ...;
      }
  }
  ```

* [ ] **Update `TransactionRequest.java`** (already exists in `brane-core`)
  ```java
  // Add method:
  public UnsignedTransaction toUnsignedTransaction(long chainId) {
      if (isEip1559) {
          return new Eip1559Transaction(chainId, nonce, ...);
      } else {
          return new LegacyTransaction(nonce, gasPrice, ...);
      }
  }
  ```

### Acceptance Criteria

* EIP-155 signing preimage matches Ethereum spec
* EIP-1559 envelope format matches spec (type byte `0x02` + RLP)
* Test vectors from Ethereum test suite pass
* Transaction can be sent to Anvil and mined successfully

---

## Milestone 3 – Update Signing Layer in `brane-rpc` (P0)

**Goal:** Remove web3j from `PrivateKeyTransactionSigner` and `DefaultWalletClient`.

### Current Code (brane-rpc)

```java
// TransactionSigner.java
@FunctionalInterface
public interface TransactionSigner {
    String sign(RawTransaction tx);  // ❌ uses web3j RawTransaction
}

// PrivateKeyTransactionSigner.java
public String sign(RawTransaction tx) {
    byte[] signed = TransactionEncoder.signMessage(tx, credentials);  // ❌ web3j
    return Numeric.toHexString(signed);  // ❌ web3j
}
```

### Refactored Code

* [ ] **Update `TransactionSigner.java`**
  ```java
  @FunctionalInterface
  public interface TransactionSigner {
      String sign(UnsignedTransaction tx, long chainId);
  }
  ```

* [ ] **Update `PrivateKeyTransactionSigner.java`**
  ```java
  public final class PrivateKeyTransactionSigner implements TransactionSigner {
      private final PrivateKey privateKey;  // brane-core crypto
      private final Address address;

      public PrivateKeyTransactionSigner(String privateKeyHex) {
          this.privateKey = PrivateKey.fromHex(privateKeyHex);
          this.address = privateKey.toAddress();
      }

      @Override
      public String sign(UnsignedTransaction tx, long chainId) {
          // 1. Encode signing preimage
          byte[] preimage = tx.encodeForSigning(chainId);
          
          // 2. Hash with Keccak256
          byte[] messageHash = Keccak256.hash(preimage);
          
          // 3. Sign
          Signature signature = privateKey.sign(messageHash);
          
          // 4. Encode envelope
          byte[] envelope = tx.encodeAsEnvelope(signature);
          
          // 5. Convert to hex
          return Hex.encode(envelope);
      }
  }
  ```

* [ ] **Update `DefaultWalletClient.java`**
  ```java
  // Replace web3j RawTransaction construction with:
  private String signAndSend(TransactionRequest request) {
      // Convert to UnsignedTransaction
      UnsignedTransaction tx = request.toUnsignedTransaction(expectedChainId);
      
      // Sign
      String signedHex = signer.sign(tx, expectedChainId);
      
      // Send
      return provider.sendRawTransaction(signedHex);
  }
  ```

### Acceptance Criteria

* `PrivateKeyTransactionSigner` has **zero web3j imports**
* `DefaultWalletClient` builds transactions without `RawTransaction`
* All examples run successfully with new signing logic

---

## Milestone 4 – Remove Vendored Web3j (P1)

**Goal:** Delete `io.brane.internal.web3j` package entirely.

### Current Situation

```
brane-core/src/main/java/io/brane/internal/web3j/
├── abi/          (ABI encoding - keep separate from crypto removal)
├── crypto/       ❌ DELETE (TransactionEncoder, Credentials)
└── utils/        ❌ DELETE (Numeric - replace with Hex)
```

### Tasks

* [ ] **Verify no usages of `io.brane.internal.web3j.crypto.*`**
  - Search codebase for imports
  - Ensure only ABI code remains (out of scope for this phase)

* [ ] **Delete** `io.brane.internal.web3j/crypto/` directory

* [ ] **Replace** `Numeric.toHexString()` with `Hex.encode()`
  - Search: `io.brane.internal.web3j.utils.Numeric`
  - Replace with: `io.brane.primitives.Hex`

### Acceptance Criteria

* `git grep "web3j.crypto"` returns zero results in `brane-rpc` and examples
* Build succeeds
* All tests pass

---

## Milestone 5 – Testing & Verification (P1)

**Goal:** Comprehensive testing against live networks.

### Tasks

* [ ] **Unit Tests**
  - `Keccak256`: Test vectors from Ethereum spec
  - `PrivateKey.sign()` + `recoverAddress()`: Roundtrip tests
  - `LegacyTransaction.encodeForSigning()`: EIP-155 test vectors
  - `Eip1559Transaction.encodeAsEnvelope()`: EIP-2718/1559 test vectors

* [ ] **Integration Tests**
  ```java
  @Test
  void signAndBroadcastLegacyTransaction() {
      // Start Anvil
      // Create transaction
      // Sign with PrivateKeyTransactionSigner
      // Broadcast via DefaultWalletClient
      // Verify receipt
  }
  
  @Test
  void signAndBroadcastEip1559Transaction() {
      // Same as above for EIP-1559
  }
  ```

* [ ] **Cross-validation**
  - Sign same transaction with web3j (in test only)
  - Verify Brane produces identical signed tx hex
  - Remove web3j from test after confirmation

### Acceptance Criteria

* All unit tests pass
* Integration tests mine transactions on Anvil
* Cross-validation confirms byte-for-byte match with web3j

---

## Migration Checklist

- [x] Crypto primitives (Keccak, secp256k1) in `brane-core/crypto/`
- [x] Transaction models (LegacyTransaction, Eip1559Transaction) in `brane-core/tx/`
- [x] `TransactionSigner` refactored to use `UnsignedTransaction`
- [x] `PrivateKeyTransactionSigner` removes web3j
- [x] `DefaultWalletClient` removes web3j
- [x] `TransactionRequest.toUnsignedTransaction()` method added
- [x] All examples updated
- [x] `io.brane.internal.web3j.crypto/` deleted
- [x] `io.brane.internal.web3j.utils.Numeric` replaced with `Hex`
- [x] All tests passing
- [x] Documentation updated

---

## Non-Goals (Deferred to 0.3.0)

* EIP-2930 access list transactions (structure exists, but not prioritized)
* EIP-4844 blob transactions
* Hardware wallet / KMS signer integration
* Transaction builder syntactic sugar improvements
* Smart fee estimation beyond what exists today