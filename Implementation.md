
# Phase 3.1: Access List Support (EIP-2930) (P0.14)

## Goal

Add **access list** support (EIP-2930-style) so Brane can:

* Attach an `accessList` to outbound transactions.
* Pass access lists through to JSON-RPC (`eth_estimateGas`, `eth_sendRawTransaction`, etc.).
* Include access lists when signing typed transactions (EIP-1559 with access list).

Scope for this phase:

* **Model + builder + wiring** only.
* Support **EIP-1559 transactions with access lists** in the write path.
* Access list + pure legacy (gasPrice-only EIP-2930 type-0x01) can be added later.

---

## Implementation Details

### 1. Core Model: Access List Types

**Location:** `brane-core/src/main/java/io/brane/core/model/AccessListEntry.java`

Add a simple value type:

```java
package io.brane.core.model;

import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import java.util.List;
import java.util.Objects;

public final class AccessListEntry {

    private final Address address;
    private final List<Hash> storageKeys;

    public AccessListEntry(final Address address, final List<Hash> storageKeys) {
        this.address = Objects.requireNonNull(address, "address");
        this.storageKeys = List.copyOf(Objects.requireNonNull(storageKeys, "storageKeys"));
    }

    public Address address() {
        return address;
    }

    public List<Hash> storageKeys() {
        return storageKeys;
    }

    @Override
    public String toString() {
        return "AccessListEntry{" + "address=" + address + ", storageKeys=" + storageKeys + '}';
    }
}
```

We use `Hash` (32-byte) for `storageKeys` to match EIP-2930’s 32-byte storage key semantics.

---

### 2. Extend TransactionRequest

**Location:** `brane-core/src/main/java/io/brane/core/model/TransactionRequest.java`

Add an `accessList` field:

```java
public record TransactionRequest(
    Address to,
    HexData data,
    Long nonce,
    Long gasLimit,
    Wei gasPrice,
    Wei maxFeePerGas,
    Wei maxPriorityFeePerGas,
    List<AccessListEntry> accessList // NEW
) {

    public Optional<Long> nonceOpt() { ... }
    public Optional<Long> gasLimitOpt() { ... }
    public Optional<Wei> gasPriceOpt() { ... }
    public Optional<Wei> maxFeePerGasOpt() { ... }
    public Optional<Wei> maxPriorityFeePerGasOpt() { ... }

    public List<AccessListEntry> accessListOrEmpty() {
        return accessList == null ? List.of() : accessList;
    }
}
```

**Notes:**

* Keep `accessList` nullable:

  * `null` → “no access list”.
  * Non-null but empty list → “explicitly no accesses” (still valid).
* If you already added helper constructors, update them to set `accessList` to `null` (or `List.of()` consistently).

Update all call sites where `TransactionRequest` is constructed to pass `null` for `accessList` for now, unless explicitly set via TxBuilder (below).

---

### 3. Extend TxBuilder (EIP-1559 only for now)

**Location:** `brane-core/src/main/java/io/brane/core/builder/Eip1559Builder.java`

Add state + fluent setter:

```java
public final class Eip1559Builder implements TxBuilder<Eip1559Builder> {

    private Address to;
    private Wei value;
    private HexData data;
    private Long nonce;
    private Long gasLimit;
    private Wei maxFeePerGas;
    private Wei maxPriorityFeePerGas;
    private List<AccessListEntry> accessList; // NEW

    // existing methods...

    public Eip1559Builder accessList(final List<AccessListEntry> accessList) {
        this.accessList = accessList == null ? null : List.copyOf(accessList);
        return this;
    }

    @Override
    public TransactionRequest build() {
        // existing validation...
        return new TransactionRequest(
            to,
            data,
            nonce,
            gasLimit,
            null,                // gasPrice unused for EIP-1559
            maxFeePerGas,
            maxPriorityFeePerGas,
            accessList           // NEW
        );
    }
}
```

**P0 scope decision:**

* Add `accessList(...)` **only** to `Eip1559Builder` for now.
* Leave `LegacyBuilder` unchanged (pure EIP-2930 type 0x01 support can come later).

Update `TxBuilder` Javadoc to mention that `accessList` is visible via `Eip1559Builder`.

---

### 4. SmartGasStrategy & JSON-RPC Mapping

#### 4.1. SmartGasStrategy (no semantic change, just propagation)

**Location:** `brane-rpc/src/main/java/io/brane/rpc/SmartGasStrategy.java`

Where we build JSON tx maps for `eth_estimateGas` / `eth_call`, **include accessList if present**:

```java
private Map<String, Object> toTxObject(TransactionRequest request) {
    Map<String, Object> tx = new LinkedHashMap<>();

    // existing fields: from, to, value, data, gas, gasPrice / maxFee / maxPriority...

    if (request.accessList() != null && !request.accessList().isEmpty()) {
        tx.put("accessList", toJsonAccessList(request.accessList()));
    }

    return tx;
}

private List<Map<String, Object>> toJsonAccessList(List<AccessListEntry> entries) {
    return entries.stream()
        .map(entry -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("address", entry.address().value());
            m.put("storageKeys",
                entry.storageKeys().stream().map(Hash::value).toList());
            return m;
        })
        .toList();
}
```

This ensures:

* Estimation behaves as the node expects when you pass an access list.
* No behavior change if `accessList` is null or empty.

---

### 5. Signing & Encoding (PrivateKeyTransactionSigner)

**Location:** `brane-rpc/src/main/java/io/brane/rpc/PrivateKeyTransactionSigner.java`
(and/or wherever the raw transaction is built / encoded).

**Behavior rules:**

1. If `accessList == null` or `accessList.isEmpty()`:

   * Keep **current behavior** (legacy or EIP-1559 typed tx as you already do).

2. If `accessList` is non-empty **and** EIP-1559 fields are present:

   * Encode the tx as an EIP-1559 typed transaction **with access list**.
   * This may be implemented by:

     * Using the vendored encoder’s “EIP-1559 with access list” path, or
     * Extending your RLP encoding to include the access list fields in the right position for type 0x02.

3. Pure EIP-2930 (type 0x01, gasPrice + accessList, no maxFee fields) is **out of scope** for this phase:

   * If a user sets `accessList` on a legacy-style request, you may:

     * Currently ignore it (documented), or
     * Throw a `BraneTxBuilderException` later if we want to be strict.
   * For P0, we only promise: “Access lists are supported on EIP-1559 tx via `Eip1559Builder`.”

**Minimal implementation detail for Codex:**

* When building the internal “raw transaction” object to sign:

  * Map `List<AccessListEntry>` to `List<io.brane.internal.web3j.crypto.AccessListObject>`.
  * Use `RawTransaction.createTransaction(...)` overload that accepts `accessList`.
  * Ensure `AccessListObject` is populated with 0x-prefixed hex strings (from `Address.value()` and `Hash.value()`).

---

### 6. Public API & Docs

Update README / Javadoc to show usage:

```java
List<AccessListEntry> access = List.of(
    new AccessListEntry(
        someContract,
        List.of(new Hash("0x...storage-key-1"), new Hash("0x...storage-key-2"))
    )
);

TransactionRequest tx = TxBuilder.eip1559()
    .to(someContract)
    .value(Wei.of(0))
    .accessList(access)
    .build();

wallet.sendTransactionAndWait(tx, 10_000, 500);
```

---

## Steps

1. [ ] Add `AccessListEntry` model type.
2. [ ] Extend `TransactionRequest` with `List<AccessListEntry> accessList`.
3. [ ] Update all `TransactionRequest` constructors / call sites to pass `null` for `accessList` by default.
4. [ ] Extend `Eip1559Builder` with:

   * `List<AccessListEntry> accessList` field.
   * `accessList(...)` setter.
   * `build()` to pass the access list into `TransactionRequest`.
5. [ ] Update `SmartGasStrategy` (and any other RPC tx-building code) to:

   * Map `accessList` into JSON-RPC shape for `eth_estimateGas` and `eth_call`.
6. [ ] Update `PrivateKeyTransactionSigner` (or equivalent signing path) to:

   * Map public `AccessListEntry` to internal `AccessListObject`.
   * Use `RawTransaction.createTransaction` with `accessList` for EIP-1559.
   * Keep existing behavior when access list is absent.
7. [ ] Update README / Javadoc to mention access list support.
8. [ ] Add tests (unit, integration, sanity) as below.

---

## Testing

### A. Unit Tests

#### 1. AccessListEntry

**File:** `brane-core/src/test/java/io/brane/core/model/AccessListEntryTest.java`

* `constructorCopiesList`:

  * Create with a mutable list.
  * Mutate original.
  * Assert `entry.storageKeys()` is unchanged.
* `nullChecks`:

  * Assert NPE when `address` is null.
  * Assert NPE when `storageKeys` is null.

#### 2. TransactionRequest + Access List

**File:** extend `TransactionRequestTest` (if present)

* `accessListPropagates`:

  * Build a `TransactionRequest` with a non-empty access list.
  * Assert `accessList()` returns same content.
  * Assert `accessListOrEmpty()` returns that list.
* `accessListOrEmptyNullSafe`:

  * Build `TransactionRequest` with `accessList == null`.
  * Assert `accessListOrEmpty()` returns an empty list (not null).

#### 3. TxBuilder (Eip1559)

**File:** extend `TxBuilderTest`

* `eip1559BuilderAccessList`:

  * Call `.accessList(List.of(entry))` on `Eip1559Builder`.
  * `build()` → `TransactionRequest`.
  * Assert `request.accessList()` contains the given entries.
* `accessListFluentChaining`:

  * Ensure `accessList(...)` returns `Eip1559Builder` so `.build()` can chain correctly.
* (Optional) `legacyBuilderNoAccessListMethod`:

  * Compile-time guarantee that `LegacyBuilder` has no `accessList(...)` method.

#### 4. SmartGasStrategy Mapping

**File:** `brane-rpc/src/test/java/io/brane/rpc/SmartGasStrategyTest.java`

* `txObjectIncludesAccessListWhenPresent`:

  * Build `TransactionRequest` with non-empty access list.
  * Call internal `toTxObject` (or via a test hook).
  * Assert resulting map has `accessList` key with expected JSON structure:

    * `address` == `.value()` of Address.
    * `storageKeys` array equals list of Hash `.value()` strings.
* `txObjectOmitsAccessListWhenNullOrEmpty`:

  * With `accessList == null` or empty list.
  * Assert map does **not** contain `accessList`.

---

### B. Integration Tests

**File:** extend `brane-examples` / `DefaultWalletClientTest`

#### 1. EIP-1559 + Access List Happy Path

* Use a local dev node (Anvil / Hardhat) that accepts access lists.
* Deploy a simple contract.
* Build a tx with:

  * `TxBuilder.eip1559()`
  * `.to(contractAddress)`
  * `.accessList(List.of(new AccessListEntry(contractAddress, List.of(...some key...))))`
* Send via `WalletClient.sendTransactionAndWait`.
* Assert:

  * Tx is mined.
  * Receipt status is success.
* If possible, inspect the node’s trace / debug logs to confirm the access list was attached (optional but nice).

#### 2. Estimation with Access List

* Call `WalletClient` / `PublicClient` path that internally uses `SmartGasStrategy` for `eth_estimateGas`.
* Provide a `TransactionRequest` with an access list.
* Ensure:

  * The JSON-RPC request body (captured via fake provider / mock HTTP) includes `accessList`.
  * The estimation succeeds.

---

### C. Sanity / Example

**File:** `brane-examples/src/main/java/io/brane/examples/AccessListExample.java`

* Simple example that:

  * Reads `-Dbrane.examples.rpc` and `-Dbrane.examples.pk`.
  * Deploys a minimal contract or uses a pre-deployed address.
  * Builds an EIP-1559 tx with a small access list.
  * Sends it and prints:

    * Tx hash.
    * Whether `accessList` was set in the request.

Run:

```bash
./gradlew :brane-examples:run \
  -PmainClass=io.brane.examples.AccessListExample \
  -Dbrane.examples.rpc=http://127.0.0.1:8545 \
  -Dbrane.examples.pk=0x...
```

Verify it completes successfully and logs are sensible.

---

## Files to Modify

* `brane-core/src/main/java/io/brane/core/model/AccessListEntry.java` (new)
* `brane-core/src/main/java/io/brane/core/model/TransactionRequest.java`
* `brane-core/src/main/java/io/brane/core/builder/Eip1559Builder.java`
* `brane-core/src/test/java/io/brane/core/model/AccessListEntryTest.java` (new)
* `brane-core/src/test/java/io/brane/core/model/TransactionRequestTest.java` (extend)
* `brane-core/src/test/java/io/brane/core/builder/TxBuilderTest.java` (extend)
* `brane-rpc/src/main/java/io/brane/rpc/SmartGasStrategy.java`
* `brane-rpc/src/test/java/io/brane/rpc/SmartGasStrategyTest.java`
* `brane-rpc/src/main/java/io/brane/rpc/PrivateKeyTransactionSigner.java` (or equivalent)
* `brane-rpc/src/test/java/io/brane/rpc/DefaultWalletClientTest.java` (extend)
* `brane-examples/src/main/java/io/brane/examples/AccessListExample.java` (new)
* `README.md` (add short section on access list support)
