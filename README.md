
# Brane 0.1.0-alpha: A Modern JVM EVM SDK

Brane is a lightweight, modern, and production-grade **SDK** for interacting with Ethereum and EVM-compatible blockchains on the JVM.

Built by forking and refining the established **web3j** library, Brane acts as the connector between your application and Ethereum nodes (such as **Besu** or **Geth**). It streamlines the developer experience by stripping away bloat and focusing on reliability.

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

### 🪛 Debug Mode & RPC Logging

Turn on verbose, sanitized RPC logging across Brane with a single toggle:

```java
import io.brane.core.BraneDebug;

BraneDebug.setEnabled(true); // Log RPC requests, responses, and transaction lifecycle
```

Every RPC call gets a unique request ID that appears in logs and exceptions:

```
[RPC] id=1 method=eth_getBlockByNumber durationMicros=1234 ...
[RPC-ERROR] id=6 method=eth_sendRawTransaction code=-32003 ...
```

Exceptions also include the request ID for easy correlation:
```
[requestId=1] Network error during JSON-RPC call
```

Try it out end-to-end via the example app:

```bash
./gradlew :brane-examples:run \
  -PmainClass=io.brane.examples.DebugExample \
  -Dbrane.examples.rpc=http://127.0.0.1:8545
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

A minimal, production-usable JVM EVM SDK that covers:

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
  brane-primitives/  // Core primitive utilities (Hex, RLP) with zero dependencies
  brane-core/        // Core types, error model, chain profiles
  brane-rpc/         // Synchronous JSON-RPC client
  brane-contract/    // ABI and contract wrappers
  brane-examples/    // (Optional) usage examples
```

**Recent improvements:**
- ✅ **Java 21 native** - Leverages records, pattern matching, virtual threads, and text blocks
- ✅ **Web3j independence (Phase 1 & 2)** - Custom Hex and RLP utilities with zero external dependencies
- ✅ **Comprehensive test suite** - Integration tests covering all examples with real testnet validation
- ✅ **Unified Transaction Builder** - Typed, fluent API for creating Legacy and EIP-1559 transactions with auto-fill capabilities
- ✅ **Minimal Revert Decoding** - Automatic decoding of Error(string), Panic(uint256), and extensible custom error support
- ✅ **Debug Mode & RPC Logging** - Global toggle for sanitized, structured logging of RPC calls and transaction lifecycles
- ✅ **Smart Gas Defaults + Retry** - Automatic gas estimation and EIP-1559 fee calculation with transient error retry logic
- ✅ **Request ID Correlation** - Every RPC call has a unique, monotonic ID that appears in logs and exceptions for easy tracing
- ✅ **Access List Support (EIP-2930)** - Specify accessed addresses and storage keys to reduce gas costs in EIP-1559 transactions

Maven Coordinates: `io.brane:brane-core:0.1.0-alpha`, `io.brane:brane-rpc:0.1.0-alpha`, etc.

-----

## 📦 Key Component Details

### `brane-primitives`

Zero-dependency foundational utilities that other modules build upon:
  * **Hex**: Fast, table-based hex encoding/decoding.
  * **RLP**: High-performance Recursive Length Prefix encoding/decoding (strings, lists, integers).

### `brane-core`

Defines the core types and the error hierarchy:

  * `io.brane.core.error` – `BraneException`, `RpcException`, `RevertException`, `RevertDecoder`.
  * `io.brane.core` – `BraneDebug` (global toggle), `LogSanitizer` (redaction), `DebugLogger`.
  * `io.brane.core.types` – reusable value objects (`Address`, `Hash`, `HexData`, `Wei`).
  * `io.brane.core.model` – domain DTOs (`Transaction`, `TransactionReceipt`, `LogEntry`, `TransactionRequest`, `ChainProfile`, etc.).
  * `io.brane.core.chain` – typed chain profiles (`ChainProfiles`) capturing chainId, default RPC URL, and EIP-1559 support.
  * **Revert Decoding**: `RevertDecoder` automatically decodes EVM reverts:
    * `Error(string)` → human-readable revert message
    * `Panic(uint256)` → mapped panic reasons (e.g., "division by zero", "arithmetic overflow")
    * Custom errors → extensible via ABI map (falls back to raw hex)
    * All reverts surface as typed `RevertException` with `RevertKind` (ERROR_STRING, PANIC, CUSTOM, UNKNOWN)
  * Error diagnostics: `RpcException` helpers for `isBlockRangeTooLarge()`/`isFilterNotFound()`; `TxnException` helpers for `isInvalidSender()`/`isChainIdMismatch()`.

### `brane-rpc`

Provides the JSON-RPC transport and public client API:

  * `Client` + `HttpClient`: typed wrapper over the transport for contract ABI calls.
  * `BraneProvider` + `HttpBraneProvider`: low-level JSON-RPC 2.0 transport abstraction that handles request/response serialization and **debug logging**.
  * `PublicClient`: high-level read-only client for chain data (`getBlockByNumber`, `getTransactionByHash`, `eth_call`, etc.) that maps node JSON into Brane’s value types (`BlockHeader`, `Transaction`, `Hash`, `Address`, `Wei`, ...).
  * `BranePublicClient`: builder/wrapper that constructs a `PublicClient` from a `ChainProfile` with optional RPC URL override (keeps `PublicClient.from(BraneProvider)` intact).
  * Provider errors are wrapped in `RpcException` with method context; wallet send errors surface `TxnException` subclasses such as `InvalidSenderException`.
  * `WalletClient` + `DefaultWalletClient`: fills nonce/gas/fees, enforces chainId, signs raw transactions via a provided signer (see `PrivateKeyTransactionSigner`), sends via `eth_sendRawTransaction`, and can poll for receipts.
  * **Smart Gas Defaults**: Automatically fills missing gas fields with intelligent defaults:
    * Gas limit via `eth_estimateGas` + 20% buffer
    * EIP-1559 fees calculated from `baseFeePerGas * 2 + defaultPriorityFee`
    * Falls back to `eth_gasPrice` when EIP-1559 is not supported
    * User-provided values are never overwritten
  * **RPC Retry Logic**: Automatically retries transient network errors (timeouts, "header not found") with exponential backoff while failing fast on permanent errors (reverts, invalid sender)
  * **Unified Transaction Builder**: Typed, fluent API for creating Legacy and EIP-1559 transactions.
    ```java
    // EIP-1559 (auto-filled fees and gas limit)
    TransactionRequest tx = TxBuilder.eip1559()
        .to(recipient)
        .value(Wei.of("1.0"))
        .build();  // gas limit, maxFeePerGas, maxPriorityFeePerGas auto-filled

    // Legacy (explicit gas price, auto-filled gas limit)
    TransactionRequest legacy = TxBuilder.legacy()
        .to(recipient)
        .gasPrice(Wei.gwei(20))
        .build();
    ```

    Access list support is available on the EIP-1559 builder:

    ```java
    List<AccessListEntry> access = List.of(
        new AccessListEntry(recipient, List.of(new Hash("0x...storageKey")))
    );

    TransactionRequest accessListTx = TxBuilder.eip1559()
        .to(recipient)
        .accessList(access)
        .build();
    ```

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
  * `io.brane.examples.TxBuilderIntegrationTest` – verifies EIP-1559 and contract deployment via the builder:
    ```bash
    ./gradlew :brane-examples:run --no-daemon \
      -PmainClass=io.brane.examples.TxBuilderIntegrationTest \
      -Dbrane.examples.rpc=http://127.0.0.1:8545 \
      -Dbrane.examples.pk=0xYourPrivateKey
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