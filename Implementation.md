## Implementation.md (Step 5 – Wallet / Signer Completion)

````markdown
# Step 5 – Wallet / Signer Client Completion

## Context

We already have:

- Core error model in `brane-core` (including `ChainMismatchException`).
- Transaction request model (`TransactionRequest`).
- Read-only flow:
  - `BraneProvider` + `PublicClient`
  - `Abi`, `ReadOnlyContract`
  - `Erc20Example` (decimals + balanceOf via 3 paths)
- Write-path scaffolding:
  - `WalletClient` + `DefaultWalletClient` (in `brane-rpc`)
  - `TransactionSigner` (in `brane-rpc`)
  - `ReadWriteContract` (in `brane-contract`)
  - `Erc20TransferExample` (in `brane-examples`)

Codex also identified an old, minimal write path in `io.brane.contract.Contract.write(...)` that is not robust, and a lack of an explicit private-key signer type.

This step finishes the write path so we can say:

> “Point Brane at an ABI + address + signer, and you can send an ERC-20 transfer and get a receipt with good errors.”

---

## Goals

1. **Signer abstraction is complete**  
   - Concrete private-key signer (`PrivateKeyTransactionSigner`) implements the existing `TransactionSigner` interface.
   - Clean handling of invalid keys and signing failures.

2. **Wallet client is production-grade (for a minimal single-account use case)**  
   - `DefaultWalletClient` is the canonical write-path client:
     - Builds **legacy + EIP-1559** transactions.
     - Fills **nonce** automatically.
     - Fills **gas** (either estimate + margin or a safe default).
     - Uses **chainId** correctly and throws `ChainMismatchException` when mismatched.
     - Sends tx and can optionally **poll for a receipt**.

3. **Contract write façade is cohesive**  
   - `ReadWriteContract` wraps:
     - ABI encoding
     - `WalletClient` send / send-and-wait logic
   - It feels symmetric with `ReadOnlyContract`’s `.call(...)`.

4. **Examples + docs prove the flow end-to-end**  
   - `Erc20TransferExample` uses the new wallet/signer to send a real ERC-20 transfer on Anvil.
   - README documents the flow and CLI invocation.

---

## 1. Signer abstraction and private-key implementation

**Module:** `brane-rpc`

### 1.1 Confirm / keep the `TransactionSigner` interface

- Do **not** change the public method signature(s) already in `TransactionSigner.java`.
- This interface is the generic signing abstraction used by `DefaultWalletClient`.

### 1.2 Implement `PrivateKeyTransactionSigner`

**File:** `brane-rpc/src/main/java/io/brane/rpc/PrivateKeyTransactionSigner.java`

Responsibilities:

- Constructed from a **hex private key string** (no `0x` or with `0x`, normalize internally).
- Uses the existing vendored crypto / web3j internals (already in `brane-core`) to:
  - Parse the private key.
  - Sign the given payload (whatever `TransactionSigner` expects).
- On invalid key format or signing failure:
  - Throw a **checked or runtime** exception that matches the existing error model (preferably a subclass of `BraneException` or reuse an existing signing-related exception if present).
- No new external dependencies; reuse the vendored web3j-like code already present in `brane-core`.

### 1.3 Unit tests

**File:** `brane-rpc/src/test/java/io/brane/rpc/PrivateKeyTransactionSignerTest.java`

Cover:

- Valid private key:
  - Construct `PrivateKeyTransactionSigner`.
  - Call its `sign(...)` method.
  - Assert result:
    - Not null / not empty.
    - **Optionally** compare against a known reference signature if that’s easy, otherwise just sanity-check format.
- Invalid private key (too short, non-hex, etc.):
  - Constructor throws a **clear** exception (e.g. `IllegalArgumentException` or a Brane-specific error).

---

## 2. Harden `DefaultWalletClient`

**Module:** `brane-rpc`

We already have `WalletClient` and `DefaultWalletClient`. This step ensures they are the canonical write path.

### 2.1 Factory / construction

- Provide a clear factory method, e.g.:

```java
public static DefaultWalletClient create(
        BraneProvider provider,
        Address from,
        TransactionSigner signer
)
````

Requirements:

* No **new** module dependencies (keep `brane-rpc` independent of `brane-contract`).
* Store:

  * `provider`
  * `from` address
  * `signer`
  * optional `chainId` if configured on `BraneProvider`.

### 2.2 Transaction building behavior

Given a `TransactionRequest` (or equivalent), `DefaultWalletClient` must:

1. **Fill nonce** if missing:

   * Call `eth_getTransactionCount(from, "latest")`.
   * Parse hex to `BigInteger`.

2. **Fill gas / gasLimit** if missing:

   * Build an estimate request (same fields as the final tx, but without gas/gasPrice/maxFeePerGas).
   * Call `eth_estimateGas`.
   * Apply a small safety margin (e.g. +10% or at least a minimum gas).

3. **Fee model selection:**

   * If `TransactionRequest` already has fee fields:

     * Use them as-is.
   * Else:

     * Try EIP-1559 first:

       * Optionally call `eth_maxPriorityFeePerGas` (or use a sane default priority fee).
       * Call `eth_feeHistory` or `eth_gasPrice` as a fallback to derive `maxFeePerGas`.
     * Fallback to **legacy gasPrice** if the node doesn’t support EIP-1559 (or if that’s simpler for now).

4. **Chain ID handling:**

   * If `BraneProvider` exposes chainId:

     * Ensure every signed transaction uses that chain ID.
     * If the transaction request carries a different chain ID:

       * Throw `ChainMismatchException` (with clear message: expected vs actual).
   * If no chainId is configured:

     * Either detect it via `eth_chainId` or treat as “no enforcement” for now (document the behavior in code comments).

### 2.3 Sending and receipt polling

Implement methods like:

```java
TxHash send(TransactionRequest request) throws RpcException, BraneException;

TransactionReceipt sendAndWait(TransactionRequest request) throws RpcException, BraneException;
```

Behavior:

* `send`:

  * Build final tx (fill nonce/gas/fees/chainId).
  * Encode as raw transaction bytes.
  * Sign using `TransactionSigner`.
  * Call `eth_sendRawTransaction`.
  * Return `TxHash` type (whatever wrapper you already use, or plain string if that’s the current model).

* `sendAndWait`:

  * Call `send(...)` to get hash.
  * Poll `eth_getTransactionReceipt` until:

    * Receipt is non-null, then return it.
    * A timeout is reached, then throw a clear exception (e.g. `RpcException` with “receipt not found after N attempts”).

### 2.4 Tests

**File:** `brane-rpc/src/test/java/io/brane/rpc/DefaultWalletClientTest.java`

Ensure coverage for:

* **Legacy send path**:

  * Fake provider returns nonce, gas estimate, gas price, and a tx hash.
  * Verify:

    * Calls are made in the expected order with expected params.
    * Hash is propagated correctly.

* **Receipt polling**:

  * First few `eth_getTransactionReceipt` calls return `null`.
  * Then return a valid receipt.
  * `sendAndWait` returns the final receipt and stops polling.

* **Chain mismatch**:

  * Provider chainId = A, TransactionRequest chainId = B.
  * `send(...)` throws `ChainMismatchException`.

---

## 3. Write-path contract façade (`ReadWriteContract`)

**Module:** `brane-contract`

We already have `ReadOnlyContract` and `ReadWriteContract`. This step ensures the write path is polished and matches the roadmap.

### 3.1 API shape

`ReadWriteContract` should look roughly like:

```java
public final class ReadWriteContract extends ReadOnlyContract {

    public static ReadWriteContract from(
            Address address,
            Abi abi,
            WalletClient walletClient
    ) { ... }

    public String send(
            String functionName,
            Object... args
    ) throws RpcException, BraneException { ... }

    public TransactionReceipt sendAndWait(
            String functionName,
            Object... args
    ) throws RpcException, BraneException { ... }
}
```

Notes:

* Reuse the existing signatures / helpers that already exist in `ReadWriteContract.java`.
* Keep a **clear separation**:

  * `ReadOnlyContract.call(...)` is pure read-only.
  * `ReadWriteContract.send(...)` / `sendAndWait(...)` are write paths.

### 3.2 Implementation details

For `send` / `sendAndWait`:

1. Use `Abi` to encode the function call:

```java
Abi.FunctionCall call = abi.encodeFunction(functionName, args);
String data = call.data();
```

2. Build a `TransactionRequest` with:

   * `to = contractAddress`
   * `from` is **inside** `WalletClient` (configured at construction time).
   * `data = encoded data`
   * `value = 0` (for typical ERC-20 transfers; you can parameterize later).

3. Delegate to `walletClient.send(...)` / `walletClient.sendAndWait(...)`.

### 3.3 Tests

**File:** `brane-contract/src/test/java/io/brane/contract/ReadWriteContractTest.java`

Use a fake `WalletClient` to verify:

* Correct ABI encoding is passed into the `TransactionRequest.data`.
* `send(...)` delegates to `walletClient.send(...)` and returns the same hash.
* `sendAndWait(...)` delegates to `walletClient.sendAndWait(...)`.

---

## 4. Example + README

**Modules:** `brane-examples`, root `README.md`

### 4.1 Erc20TransferExample

We already have `Erc20TransferExample`. Confirm / adjust it so it:

* Reads system properties:

  * `brane.examples.erc20.rpc`
  * `brane.examples.erc20.contract`
  * `brane.examples.erc20.recipient`
  * `brane.examples.erc20.pk`
  * `brane.examples.erc20.amount` (optional, default to `1 * 10^decimals` or `1e18`)

* Builds:

```java
BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
PublicClient publicClient = PublicClient.from(provider);
Abi abi = Abi.fromJson(ERC20_ABI);

TransactionSigner signer = new PrivateKeyTransactionSigner(privateKeyHex);
WalletClient walletClient =
        DefaultWalletClient.create(provider, fromAddress, signer);

ReadWriteContract token =
        ReadWriteContract.from(tokenAddress, abi, walletClient);
```

* Sends the transfer via the contract:

```java
String txHash = token.send("transfer", recipient, amount);
System.out.println("Sent transfer tx: " + txHash);

// Optional:
TransactionReceipt receipt =
        token.sendAndWait("transfer", recipient, amount);
System.out.println("Mined tx in block: " + receipt.blockNumber());
```

### 4.2 README

In `README.md`:

* Add a **short “Write Path” section** showing:

```java
BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
PublicClient publicClient = PublicClient.from(provider);
Abi abi = Abi.fromJson(ERC20_ABI);

TransactionSigner signer = new PrivateKeyTransactionSigner(privateKey);
WalletClient wallet = DefaultWalletClient.create(provider, from, signer);

ReadWriteContract token = ReadWriteContract.from(tokenAddress, abi, wallet);
String hash = token.send("transfer", to, amount);
```

* Add the CLI example:

```bash
./gradlew :brane-examples:run --no-daemon \
  -PmainClass=io.brane.examples.Erc20TransferExample \
  -Dbrane.examples.erc20.rpc=http://127.0.0.1:8545 \
  -Dbrane.examples.erc20.contract=0x... \
  -Dbrane.examples.erc20.recipient=0x... \
  -Dbrane.examples.erc20.pk=<ANVIL_PRIVATE_KEY> \
  -Dbrane.examples.erc20.amount=1000000000000000000
```

---

## 5. Cleanup + verification

1. **Optionally** deprecate the old `io.brane.contract.Contract.write(...)`:

   * Either add `@Deprecated` with a Javadoc pointing to `ReadWriteContract`.
   * Or make it a thin wrapper internally delegating to the new `WalletClient` path (if that’s easy without introducing new dependencies).

2. Run:

```bash
./gradlew :brane-rpc:test --no-daemon
./gradlew :brane-contract:test --no-daemon
./gradlew clean check --no-daemon
```

3. Ensure no new module cycles:

   * `brane-rpc` MUST NOT depend on `brane-contract`.
   * `brane-contract` may depend on `brane-rpc` types like `WalletClient`.

Once all of this is green and the ERC-20 transfer example works on Anvil, Step 5 is functionally complete.

````