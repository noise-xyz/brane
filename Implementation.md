# Phase 2.5: Unified Transaction Builder (P0.9)

## Goal
Implement a typed, fluent `TxBuilder` API to simplify transaction creation, validate fields at build time, and prepare for future transaction types (EIP-1559, Access Lists, etc.), leveraging Java 21 features.

## Motivation
- **Ergonomics**: Replace verbose `new TransactionRequest(...)` with fluent API.
- **Safety**: Prevent mixing Legacy (gasPrice) and EIP-1559 (maxFee/maxPriorityFee) fields via type system.
- **Future-proofing**: Extensible for new EIPs via **sealed interfaces**.
- **Correctness**: Enforce required fields and valid states at build time with explicit exceptions.

## Proposed API

```java
// Entry point
TransactionRequest tx = TxBuilder.eip1559()
    .to(recipient)
    .value(Wei.of("1.0"))
    .maxFeePerGas(Wei.gwei(20))
    .maxPriorityFeePerGas(Wei.gwei(2))
    .build();

// Legacy
TransactionRequest legacy = TxBuilder.legacy()
    .to(recipient)
    .gasPrice(Wei.gwei(20))
    .build();
```

## Implementation Details

### 1. `TxBuilder` Sealed Interface (Self-Typed)
- Location: `brane-core/src/main/java/io/brane/core/builder/TxBuilder.java`
- **Definition**:
  ```java
  public sealed interface TxBuilder<T extends TxBuilder<T>> permits LegacyBuilder, Eip1559Builder {
      // Common methods returning 'T' for fluent chaining
      T to(Address address);
      T value(Wei value);
      T data(HexData data);
      T nonce(long nonce);
      T gasLimit(long gasLimit);
      
      TransactionRequest build();
      
      // Static factories
      static Eip1559Builder eip1559() { return new Eip1559Builder(); }
      static LegacyBuilder legacy() { return new LegacyBuilder(); }
  }
  ```

### 2. Specific Builders & Validation
- **`Eip1559Builder`**:
    - Implements `TxBuilder<Eip1559Builder>`.
    - Adds `maxFeePerGas(Wei)`, `maxPriorityFeePerGas(Wei)`.
    - **Validation**: `maxFeePerGas` and `maxPriorityFeePerGas` are optional (auto-filled by WalletClient if null).
    - **Isolation**: Does NOT expose `gasPrice`.
    ```java
    public final class Eip1559Builder implements TxBuilder<Eip1559Builder> {

        // Builder state (address, value, gas params, etc.)
        // private Address to;
        // private Wei value;
        // ...

        // Common methods
        @Override
        public Eip1559Builder to(Address address) { ... }
        @Override
        public Eip1559Builder value(Wei value) { ... }
        @Override
        public Eip1559Builder data(HexData data) { ... }
        @Override
        public Eip1559Builder nonce(long nonce) { ... }
        @Override
        public Eip1559Builder gasLimit(long gasLimit) { ... }
        
        // EIP-1559 specific methods
        public Eip1559Builder maxFeePerGas(Wei maxFeePerGas) { ... }
        public Eip1559Builder maxPriorityFeePerGas(Wei maxPriorityFeePerGas) { ... }
        
        @Override
        public TransactionRequest build() { /* validation + mapping */ }
    }
    ```
- **`LegacyBuilder`**:
    - Implements `TxBuilder<LegacyBuilder>`.
    - Adds `gasPrice(Wei)`.
    - **Validation**: `gasPrice` is optional (auto-filled by WalletClient if null).
    - **Isolation**: Does NOT expose `maxFee`/`maxPriorityFee`.
    ```java
    public final class LegacyBuilder implements TxBuilder<LegacyBuilder> {

        // Common methods
        @Override
        public LegacyBuilder to(Address address) { ... }
        @Override
        public LegacyBuilder value(Wei value) { ... }
        @Override
        public LegacyBuilder data(HexData data) { ... }
        @Override
        public LegacyBuilder nonce(long nonce) { ... }
        @Override
        public LegacyBuilder gasLimit(long gasLimit) { ... }
        
        // Legacy specific methods
        public LegacyBuilder gasPrice(Wei gasPrice) { ... }
        
        @Override
        public TransactionRequest build() { /* validation + mapping */ }
    }
    ```
- **Resulting Developer Experience**:
    ```java
    TransactionRequest tx = TxBuilder.eip1559()
        .to(recipient)
        .value(Wei.of("1.0"))
        .maxFeePerGas(Wei.gwei(20))
        .maxPriorityFeePerGas(Wei.gwei(2))
        .build();
    ```
    ```java
    TransactionRequest legacyTx = TxBuilder.legacy()
        .to(recipient)
        .value(Wei.of("1.0"))
        .gasPrice(Wei.gwei(20))
        .build();
    ```

### 3. `TransactionRequest` & Nullability
- Ethereum transactions often contain **optional fields** (e.g. `nonce`, `gasLimit`, `gasPrice`, `maxFeePerGas`, `maxPriorityFeePerGas`). To correctly distinguish between
    - **unset** (e.g. `null` or `Optional.empty()`)
    - **explicitly set to 0** (e.g. `Optional.of(0)`)
- Brane uses a nullable-field internal mode, with Optional-reflecting accessors where appropriate.
- This avoids the ambiguity of `Optional.empty()` vs. "value not provided at all," while keeping the DTO lightweight and Java idiomatic.
- **Design Rule**: 
    - Inside `TransactionRequest`, fields maybe `null` to represent "unset".
    - Outside (public API): use accessor helpers returning `Optional<T>` if needed
- **Canonical Structure**: 
    ```java
    public record TransactionRequest(
        Address to,
        HexData data,
        Long nonce,              // null → auto-fill
        Long gasLimit,           // null → auto-fill or RPC default
        Wei gasPrice,            // null → only for legacy tx; filled by WalletClient if needed
        Wei maxFeePerGas,        // null → only for EIP-1559
        Wei maxPriorityFeePerGas // null → same
    ) {
        public Optional<Long> nonceOpt() {
            return Optional.ofNullable(nonce);
        }

        public Optional<Long> gasLimitOpt() {
            return Optional.ofNullable(gasLimit);
        }

        public Optional<Wei> gasPriceOpt() {
            return Optional.ofNullable(gasPrice);
        }

        public Optional<Wei> maxFeePerGasOpt() {
            return Optional.ofNullable(maxFeePerGas);
        }

        public Optional<Wei> maxPriorityFeePerGasOpt() {
            return Optional.ofNullable(maxPriorityFeePerGas);
        }
    }
    ```

### 4. Validation Rules (in `build()`)
- **Contract Creation**:
    - If `to` is NULL, `data` MUST be present.
    - If `to` is present, `data` is optional.
    - Throw `BraneTxBuilderException` if neither is present.
- **Gas Params**:
    - Throw if required gas params are missing (based on builder type).
- **Thread Safety**:
    - Builders are **mutable** and **not thread-safe**. Intended for single-threaded, per-transaction use.

### 5. `BraneTxBuilderException`
- A specific runtime exception for builder validation failures.
- Thrown explicitly on invalid state during `build()`.

### 6. Responsibilities & Semantics
- **Nonce**:
    - Builder: `nonce(long)` sets an explicit nonce.
    - WalletClient: If explicit nonce is present, use it. If missing, auto-fill via `eth_getTransactionCount`.
- **From**:
    - Builder: Should NOT require `from`.
    - WalletClient: Injects the signer's address as `from` if not provided.
- **Chain ID**:
    - Builder: Does not hardcode chainId.
    - WalletClient: Source of truth for chainId.

## Steps
1.  [ ] Create `io.brane.core.builder` package.
2.  [ ] Define `BraneTxBuilderException`.
3.  [ ] Implement `TxBuilder` sealed interface with self-typed generics.
4.  [ ] Implement `LegacyBuilder` and `Eip1559Builder` with strict validation.
5.  [ ] Add unit tests for builder validation (missing fields, mixed params, contract creation rules).
6.  [ ] Verify compatibility with `WalletClient`.
7.  [ ] Add `TxBuilder` demo to `brane-examples`.

## Testing
- **Unit Tests**: Verify correct `TransactionRequest` is built for each type.
- **Validation Tests**: Ensure `BraneTxBuilderException` is thrown for:
    - Missing `to` AND `data`.
    - Missing gas fees (if enforced).
    - Contract creation violations.
- **Isolation Tests**: Verify `LegacyBuilder` never sets EIP-1559 fields and vice-versa.
