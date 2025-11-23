
# Brane 0.1.0-alpha: Minimal JVM Ethereum Client

Brane is a modern, minimal, and production-usable Ethereum client for the JVM, built by forking and refining **web3j**.

The **0.1.0-alpha** release focuses on:
* **Table-stakes web3j functionality** (JSON-RPC, ABI encoding/decoding, simple transaction sending).
* **A clean, predictable error model** with specific exception types for RPC failures and EVM reverts.



## 🚀 Quickstart

Add the necessary dependencies to your Maven or Gradle project:

```xml
<dependencies>
    <dependency>
        <groupId>io.brane</groupId>
        <artifactId>brane-core</artifactId>
        <version>0.1.0-alpha</version>
    </dependency>
    <dependency>
        <groupId>io.brane</groupId>
        <artifactId>brane-rpc</artifactId>
        <version>0.1.0-alpha</version>
    </dependency>
    <dependency>
        <groupId>io.brane</groupId>
        <artifactId>brane-contract</artifactId>
        <version>0.1.0-alpha</version>
    </dependency>
</dependencies>
````

Here's an example of how to interact with a smart contract:

```java
import io.brane.contract.Abi;
import io.brane.contract.Contract;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.chain.ChainProfiles;
import io.brane.core.types.Address;
import io.brane.rpc.Client;
import io.brane.rpc.BranePublicClient;
import io.brane.rpc.HttpClient;
import java.math.BigInteger;
import java.net.URI;

Client client = new HttpClient(URI.create("http://127.0.0.1:8545"));
Abi abi = Abi.fromJson(MY_CONTRACT_ABI);
Contract contract = new Contract(new Address(MY_CONTRACT_ADDRESS), abi, client);

try {
    BigInteger balance = contract.read("balanceOf", BigInteger.class, userAddress);
    System.out.println("Balance: " + balance);
} catch (RevertException e) {
    // Contract-level execution failure (EVM revert)
    System.err.println("Reverted: " + e.revertReason()); // Decoded Error(string) reason
    System.err.println("Raw Data: " + e.rawDataHex());   // Full revert payload
} catch (RpcException e) {
    // JSON-RPC / Node-level communication failure
    System.err.println("RPC error: " + e.code() + " " + e.getMessage());
}

// Performing a write call using the default Anvil key:
Signer signer = new PrivateKeySigner("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
String txHash = contract.write(signer, "setValue", BigInteger.valueOf(1337));
System.out.println("sent tx: " + txHash);

// Build a read-only client from a typed chain profile
BranePublicClient baseSepolia = BranePublicClient
        .forChain(ChainProfiles.BASE_SEPOLIA)
        .withRpcUrl("https://sepolia.base.org") // override the default if needed
        .build();
System.out.println("Base Sepolia chainId: " + baseSepolia.profile().chainId);
```

-----

## 🌟 Why Brane (vs. web3j)?

Brane aims to provide a cleaner, more predictable API for production use cases.

  * **Typed RevertException** instead of generic exceptions for EVM reverts.
  * **Revert reason decoding** for `Error(string)` reverts by default within `Contract.read(...)`.
  * **Clean error model** with clear separation:
      * `RpcException` for JSON-RPC failures or node errors.
      * `RevertException` for contract-level EVM reverts.
  * **Minimal, composable API surface**. Core **web3j** internals are vendored under `io.brane.internal.web3j.*` and are never exposed in public APIs, ensuring better API stability.

-----

## 🛠️ Project Structure & Architecture

### 1\. High-Level Goals for 0.1.0-alpha

A minimal, production-usable JVM Ethereum client that covers:

  * Connect to a node via HTTP.
  * Make JSON-RPC calls (`eth_call`, `eth_sendRawTransaction`, etc.).
  * Encode/decode ABI for contracts.
  * Load a private key and sign/send a transaction.
  * **P0:** Clean `RpcException` and `RevertException` model with `Error(string)` decoding.
  * **P0:** Minimal `Abi.fromJson(...)` and `Contract.read/write(...)` wrappers.

### 2\. Out of Scope for 0.1.0-alpha (Explicitly Ignored)

  * Async / reactive APIs.
  * WebSockets / subscriptions.
  * Indexer framework / reorg-safe cursors.
  * Chain registry / multi-chain config.
  * Custom error decoding (beyond basic `Error(string)`).
  * ENS, personal API, Parity/Geth special APIs.
  * Codegen for Java wrappers.

### 3\. Project Modules

The minimal multi-module layout:

```
brane/
  brane-primitives/  // Core primitive helpers (hex utilities)
  brane-core/        // Core types, Error model
  brane-rpc/         // Synchronous JSON-RPC client
  brane-contract/    // ABI and Contract wrappers
  brane-examples/    // (Optional) Usage examples
```

Maven Coordinates: `io.brane:brane-core:0.1.0-alpha`, `io.brane:brane-rpc:0.1.0-alpha`, etc.

-----

## 📦 Key Component Details

### `brane-primitives`

Zero-dependency foundational utilities such as hex encoding/decoding helpers that other modules build upon.

### `brane-core`

Defines the core types and the error hierarchy:

  * `io.brane.core.error` – `BraneException`, `RpcException`, `RevertException`, `RevertDecoder`.
  * `io.brane.core.types` – reusable value objects (`Address`, `Hash`, `HexData`, `Wei`).
  * `io.brane.core.model` – domain DTOs (`Transaction`, `TransactionReceipt`, `LogEntry`, `TransactionRequest`, `ChainProfile`, etc.).
  * `io.brane.core.chain` – typed chain profiles (`ChainProfiles`) capturing chainId, default RPC URL, and EIP-1559 support.
  * Error diagnostics: `RpcException` helpers for `isBlockRangeTooLarge()`/`isFilterNotFound()`; `TxnException` helpers for `isInvalidSender()`/`isChainIdMismatch()`.

### `brane-rpc`

Provides the JSON-RPC transport and public client API:

  * `Client` + `HttpClient`: typed wrapper over the transport for contract ABI calls.
  * `BraneProvider` + `HttpBraneProvider`: low-level JSON-RPC 2.0 transport abstraction that handles request/response serialization.
  * `PublicClient`: high-level read-only client for chain data (`getBlockByNumber`, `getTransactionByHash`, `eth_call`, etc.) that maps node JSON into Brane’s value types (`BlockHeader`, `Transaction`, `Hash`, `Address`, `Wei`, ...).
  * `BranePublicClient`: builder/wrapper that constructs a `PublicClient` from a `ChainProfile` with optional RPC URL override (keeps `PublicClient.from(BraneProvider)` intact).
  * Provider errors are wrapped in `RpcException` with method context; wallet send errors surface `TxnException` subclasses such as `InvalidSenderException`.
  * `WalletClient` + `DefaultWalletClient`: fills nonce/gas/fees, enforces chainId, signs raw transactions via a provided signer (see `PrivateKeyTransactionSigner`), sends via `eth_sendRawTransaction`, and can poll for receipts.
  * `getLogs(LogFilter)`: fetch historical logs (e.g., ERC-20 `Transfer` events) with block/topic filters.

### `brane-contract`

Provides high-level contract interaction:

  * `Abi` interface: parse ABI JSON, encode/decode function calls, and raise `AbiEncodingException` / `AbiDecodingException` on invalid inputs.
  * `Contract` class:
      * `read(...)`: Implements `eth_call`, handles ABI encoding/decoding, and specifically catches reverts to throw a decoded `RevertException`.
      * `write(...)`: Implements the simple raw-transaction signing/sending flow using `Signer`.
  * `ReadOnlyContract`: a lightweight façade over `Abi` + `PublicClient.call` for read-only calls (no signing), with revert decoding.
  * `ReadWriteContract`: extends `ReadOnlyContract` and wires in a `WalletClient` for sending transactions (builds `TransactionRequest` with ABI-encoded input).
  * `Signer` interface / `PrivateKeySigner`: Wrap private keys via our value types (no web3j types leak out).

### `brane-examples`

Runnable demos that exercise `Contract.read`/`write` against a running node.

  * `io.brane.examples.Main` – echo example calling `echo(uint256)`.
  * `io.brane.examples.Erc20Example` – calls `decimals()` and `balanceOf(address)` (via both `Contract.read` and `PublicClient` + `Abi`). Run with:

    ```bash
    ./gradlew :brane-examples:run \
      -PmainClass=io.brane.examples.Erc20Example \
      -Dbrane.examples.erc20.rpc=http://127.0.0.1:8545 \
      -Dbrane.examples.erc20.contract=0xYourTokenAddress \
      -Dbrane.examples.erc20.holder=0xHolderAddress
    ```
    Ensure the RPC endpoint is running and the contract/holder addresses are valid.
  * `io.brane.examples.Erc20TransferExample` – sends an ERC-20 `transfer` using the new wallet client + signer:

    ```bash
    ./gradlew :brane-examples:run \
      -PmainClass=io.brane.examples.Erc20TransferExample \
      -Dbrane.examples.erc20.rpc=http://127.0.0.1:8545 \
      -Dbrane.examples.erc20.contract=0xYourTokenAddress \
      -Dbrane.examples.erc20.recipient=0xRecipient \
      -Dbrane.examples.erc20.pk=0xYourPrivateKey \
      -Dbrane.examples.erc20.amount=1
    ```
    Uses `PrivateKeyTransactionSigner` + `DefaultWalletClient` + `ReadWriteContract`.
  * `io.brane.examples.MultiChainLatestBlockExample` – shows how to build clients from `ChainProfiles`. Always queries Anvil; optionally queries Base Sepolia when an RPC URL is provided:

    ```bash
    ./gradlew :brane-examples:run --no-daemon \
      -PmainClass=io.brane.examples.MultiChainLatestBlockExample \
      -Dbrane.examples.rpc.base-sepolia=https://sepolia.base.org
    ```
  * `io.brane.examples.ErrorDiagnosticsExample` – exercises the error model and diagnostics:

    Helpers only (no network):
    ```bash
    ./gradlew :brane-examples:run --no-daemon \
      -PmainClass=io.brane.examples.ErrorDiagnosticsExample \
      -Pbrane.examples.mode=helpers
    ```
    RPC error demo (uses a bad URL to show `RpcException`):
    ```bash
    ./gradlew :brane-examples:run --no-daemon \
      -PmainClass=io.brane.examples.ErrorDiagnosticsExample \
      -Pbrane.examples.mode=rpc-error
    ```
  * `io.brane.examples.MultiChainLatestBlockExample` – shows how to build clients from `ChainProfiles`. Always queries Anvil; optionally queries Base Sepolia when an RPC URL is provided:

    ```bash
    ./gradlew :brane-examples:run --no-daemon \
      -PmainClass=io.brane.examples.MultiChainLatestBlockExample \
      -Dbrane.examples.rpc.base-sepolia=https://sepolia.base.org
    ```

-----

## 🔬 Testing Setup (P0)

The project uses **Foundry** for a reliable testing environment:

1.  **Solidity Contract:** A simple `RevertExample.sol` is used to test various revert paths (e.g., `revert("simple reason")` for `Error(string)` and `revert CustomError(...)` for custom reverts).
2.  **Local Node:** Tests run against an `anvil` instance (e.g., at `http://127.0.0.1:8545`).
3.  **JUnit Tests:** Verify:
      * Core value types (`Address`, `Hash`, `HexData`, `Wei`) behave as expected.
      * `Contract.read` correctly decodes values and generates `RevertException` when `error.data` contains revert payloads.
      * `Contract.write` signs/sends transactions via `Signer`.

<!-- end list -->
## ✅ Local testing checklist

1. **Pure unit tests**

   ```bash
   ./gradlew :brane-core:test
   ./gradlew :brane-contract:test        # without extra props runs only pure tests
   ```

2. **Anvil-backed integration tests**

   Deploy `RevertExample.sol` + `Storage.sol` (see `foundry/anvil-tests/`), then run:

   ```bash
   ./gradlew :brane-contract:test \
     -Dbrane.anvil.rpc=http://127.0.0.1:8545 \
     -Dbrane.anvil.revertExample.address=0x5FbDB2315678afecb367f032d93F642f64180aa3 \
     -Dbrane.anvil.storage.address=0xe7f1725E7734CE288F8367e1Bb143E90bb3F0512 \
     -Dbrane.anvil.signer.privateKey=0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
   ```

3. **Examples**

   ```bash
   ./gradlew :brane-examples:run \
     -Dbrane.examples.rpc=http://127.0.0.1:8545 \
     -Dbrane.examples.contract=0x5FbDB2315678afecb367f032d93F642f64180aa3
   ```

   *Echo example* (default): runs `io.brane.examples.Main` and calls `echo(uint256)`.

   *ERC-20 example*: calls `decimals()` and `balanceOf(address)` two ways (Contract.read and PublicClient + Abi). If your build allows overriding the main class, run:

   ```bash
   ./gradlew :brane-examples:run \
     -PmainClass=io.brane.examples.Erc20Example \
     -Dbrane.examples.erc20.rpc=http://127.0.0.1:8545 \
     -Dbrane.examples.erc20.contract=0xYourTokenAddress \
     -Dbrane.examples.erc20.holder=0xHolderAddress
   ```

   Make sure the RPC node is running, the contract address points to a deployed ERC-20, and the holder has a balance to see a non-zero result.

4. **Full verification**

   ```bash
   ./gradlew clean check
   ```

The guardrails in `Guardrail.md` ensure web3j internals never leak into public APIs; the layout above follows those rules.
