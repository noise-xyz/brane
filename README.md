
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
Client client = new HttpClient(URI.create("[http://127.0.0.1:8545](http://127.0.0.1:8545)"));
Abi abi = Abi.fromJson(MY_CONTRACT_ABI);
Contract contract = new Contract(MY_CONTRACT_ADDRESS, abi, client);

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
  brane-core/        // Core types, Error model
  brane-rpc/         // Synchronous JSON-RPC client
  brane-contract/    // ABI and Contract wrappers
  brane-examples/    // (Optional) Usage examples
```

Maven Coordinates: `io.brane:brane-core:0.1.0-alpha`, `io.brane:brane-rpc:0.1.0-alpha`, etc.

-----

## 📦 Key Component Details

### `brane-core`

Defines the core types and the error hierarchy:

  * `BraneException` (sealed base class)
      * `RpcException`: For JSON-RPC errors (with `code()` and raw `data()`).
      * `RevertException`: For EVM reverts (with decoded `revertReason()` and full `rawDataHex()`).
  * `RevertDecoder`: A utility to decode `Error(string)` from raw revert data.

### `brane-rpc`

Provides the minimal synchronous JSON-RPC client:

  * `Client` interface:
    ```java
    <T> T call(String method, Class<T> responseType, Object... params) throws RpcException;
    ```
  * `HttpClient` implementation: Handles request building, synchronous HTTP POST, and JSON-RPC response parsing/error checking.

### `brane-contract`

Provides high-level contract interaction:

  * `Abi` interface: A thin wrapper for ABI parsing via `Abi.fromJson(String json)`.
  * `Contract` class:
      * `read(...)`: Implements `eth_call`, handles ABI encoding/decoding, and specifically catches reverts to throw a decoded `RevertException`.
      * `write(...)`: Implements the simple raw transaction signing/sending flow.
  * `Signer` interface: Used for transaction signing (e.g., implemented by `PrivateKeySigner`).

-----

## 🔬 Testing Setup (P0)

The project uses **Foundry** for a reliable testing environment:

1.  **Solidity Contract:** A simple `RevertExample.sol` is used to test various revert paths (e.g., `revert("simple reason")` for `Error(string)` and `revert CustomError(...)` for custom reverts).
2.  **Local Node:** Tests run against an `anvil` instance (e.g., at `http://127.0.0.1:8545`).
3.  **JUnit Tests:** Verify:
      * `Error(string)` is caught and correctly decoded into `RevertException.revertReason()`.
      * Non-reverting calls pass and decode values correctly.
      * Custom errors are caught as `RevertException` but gracefully handle the lack of an `Error(string)` reason string.

<!-- end list -->