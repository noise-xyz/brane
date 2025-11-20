Got you — here is a **clean, production-quality `Implementation.md` for Step 5**, plus a **Codex prompt** you can paste directly when you’re ready to have it implemented.

---

# ✅ **Step 5 — Wallet & Signer Client**

### `Implementation.md`

This document describes the required implementation for Milestone #5: **Wallet / Signer Client** and write-path contract interaction support.

The goal of this step is:

> **Enable sending signed transactions (e.g., ERC-20 transfers) on Anvil from Java using Brane.**
> *Milestone: “Send a local ERC-20 transfer and return receipt.”*

---

# 1. Overview

We are adding the following capabilities:

* A proper wallet client (`WalletClient`) with:

  * Nonce filling
  * Gas estimation
  * Legacy + EIP-1559 fee support
  * Chain ID enforcement
  * Signing via `Signer` (e.g., `PrivateKeySigner`)
  * Sending raw transaction bytes via `eth_sendRawTransaction`
  * Waiting for receipt (`sendTransactionAndWait`)
* A new contract façade (`ReadWriteContract`) that wraps:

  * ABI encoding
  * A wallet client
  * TransactionRequest building

This extends Brane beyond read-only operations into **full transaction execution**.

---

# 2. Add the Wallet Client API

## 2.1. `WalletClient` interface (in `brane-rpc`)

Create:

```java
package io.brane.rpc;

import io.brane.core.model.TransactionRequest;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.types.Hash;

public interface WalletClient {
    Hash sendTransaction(TransactionRequest request);

    TransactionReceipt sendTransactionAndWait(
        TransactionRequest request,
        long timeoutMillis,
        long pollIntervalMillis
    );
}
```

This API intentionally matches typical wallet semantics.

---

# 3. Implement `DefaultWalletClient`

Create `DefaultWalletClient` in `brane-rpc`:

```java
public final class DefaultWalletClient implements WalletClient {

    private final BraneProvider provider;
    private final PublicClient publicClient;
    private final Signer signer;
    private final long expectedChainId;

    // static factory: from(provider, publicClient, signer, expectedChainId)
}
```

### Responsibilities:

1. **Chain ID enforcement**

   * On first send, call `eth_chainId`.
   * Compare against `expectedChainId` (unless 0).
   * Throw `ChainMismatchException` (new error type).

2. **Nonce resolution**

   * If request.nonce == null → call:

     ```json
     eth_getTransactionCount(<from>, "pending")
     ```

3. **Gas estimation**

   * If request.gas == null → call:

     ```json
     eth_estimateGas({from,to,value,data})
     ```

4. **Fee model**

   * If request has:

     * `maxFeePerGas` or `maxPriorityFeePerGas` → treat as EIP-1559.
     * Else → legacy.
   * Defaults:

     * legacy gasPrice = eth_gasPrice
     * eip-1559 fallback:

       * use `eth_gasPrice` as `maxFeePerGas`
       * use same for `maxPriorityFeePerGas` on Anvil

5. **Signing**

   * Use existing `Signer.signTransaction(RawTransaction) → String rawTxHex`

6. **Sending**

   * Use `eth_sendRawTransaction(rawTxHex)`
   * Map JSON-RPC errors to `RpcException`.

7. **Receipt polling**

   * Use `eth_getTransactionReceipt`
   * Loop until timeout

---

# 4. Add `ChainMismatchException`

In `brane-core`:

```java
public final class ChainMismatchException extends BraneException {
    private final long expected;
    private final long actual;

    // constructor + getters
}
```

---

# 5. Create `ReadWriteContract` (brane-contract)

New class:

```java
public final class ReadWriteContract extends ReadOnlyContract {

    private final WalletClient walletClient;

    public static ReadWriteContract from(
        Address address,
        Abi abi,
        PublicClient publicClient,
        WalletClient walletClient
    ) { ... }

    public <T> Hash send(String fn, Object... args) {
        // encode function call to data
        // build TransactionRequest
        // call walletClient.sendTransaction
    }

    public <T> TransactionReceipt sendAndWait(String fn, long timeout, long interval, Object... args) {
        // same as send(), but use sendTransactionAndWait
    }
}
```

### TransactionRequest building rules:

* from = signer.address()
* to = contract address
* value = 0 (for now)
* input = ABI-encoded data
* gas / fee / nonce = left null → wallet client fills in

---

# 6. Tests

## 6.1. `DefaultWalletClientTest` (in `brane-rpc`)

Using a `FakeBraneProvider`, cover:

* Auto-nonce
* Auto-gas
* EIP-1559 path
* Legacy path
* Signing path
* Chain mismatch errors
* sendAndWait polling behavior

## 6.2. `ReadWriteContractTest` (in `brane-contract`)

Using a `FakeWalletClient`, cover:

* Correct ABI-encoded data passed into TransactionRequest.input
* Correct target (to=contract)
* Correct args encoding for a function like:

  ```
  transfer(address,uint256)
  ```

---

# 7. Add Example: `Erc20TransferExample`

File: `brane-examples/src/main/java/io/brane/examples/Erc20TransferExample.java`

Demonstrates:

* private key loaded via `-Dbrane.examples.erc20.pk`
* Build signer
* Build provider + public client
* Build wallet client
* Build ReadWriteContract
* Call:

  ```java
  Hash h = token.send("transfer", recipient, amount);
  ```
* Optionally wait:

  ```java
  TransactionReceipt r = token.sendAndWait("transfer", 30000L, 1000L, recipient, amount);
  ```

Use the BraneToken you already deployed on Anvil.

---

# 8. Commands to verify

```
./gradlew :brane-rpc:test --no-daemon
./gradlew :brane-contract:test --no-daemon
./gradlew clean check --no-daemon
```

And example run:

```
./gradlew :brane-examples:run --no-daemon \
  -PmainClass=io.brane.examples.Erc20TransferExample \
  -Dbrane.examples.erc20.rpc=http://127.0.0.1:8545 \
  -Dbrane.examples.erc20.contract=0x... \
  -Dbrane.examples.erc20.holder=0xf39F... \
  -Dbrane.examples.erc20.recipient=0x... \
  -Dbrane.examples.erc20.pk=0xac09...
```