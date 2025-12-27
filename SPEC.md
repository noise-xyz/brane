# Multicall3 Batching Specification (Brane)

## 🎯 Objective

Implement a high-performance, type-safe abstraction for Multicall3. This allows bundling multiple contract read operations into a single `eth_call` to the standard Multicall3 contract (`0xcA11...`), reducing network overhead and ensuring data atomicity.

## 🏗️ 1. Technical Foundations

### Multicall3 Contract
- **Deterministic Address**: `0xcA11bde05977b3631167028862bE2a173976CA11`
- **Primary Method**: `aggregate3((address target, bool allowFailure, bytes callData)[] calls) returns ((bool success, bytes returnData)[] returnData)`

### Architectural Strategy
Brane will implement an **Explicit Batcher** (similar to Alloy's Multicall Builder). Since Brane uses dynamic proxies instead of code generation, we will utilize a "Recording Mode" for contract proxies to capture function metadata (target, calldata, and return type) without triggering immediate network requests.

## 🛠️ 2. Core Components

### A. The Registry (`MulticallRegistry`)
A central utility to resolve the Multicall3 address for a given `chainId`.
- **Pre-populated**: Standard address for Ethereum, Polygon, Arbitrum, Optimism, Base, BSC, and common local development chains (Anvil/Hardhat).
- **Extensible**: Support for registering custom addresses for private networks.

### B. The Batch Orchestrator (`MulticallBatch`)
Handles the lifecycle of a batched request:
1. **Creation**: Initialized via `publicClient.createBatch()`.
2. **Collection**: Aggregates `CallContext` objects containing everything needed to encode and decode individual calls.
3. **Execution**: Performs the single RPC `eth_call` to the resolved Multicall3 address.
4. **Distribution**: Dispatches results back to the individual handles.

### C. Result Mapping (`BatchResult<T>`)
A container for individual call results, acknowledging that in a batch, some calls may fail while others succeed.

```java
public record BatchResult<T>(
    T data,
    boolean success,
    String revertReason // Human-readable if success is false
) {}
```

## 🚀 3. Proposed API Design

The goal is to make batching feel like a natural extension of the existing contract interaction patterns.

```java
// 1. Initialize batch from the public client
MulticallBatch batch = client.createBatch();

// 2. Bind a "recording" version of the contract
// This proxy will return default values (null/0) but capture call data
Erc20Contract token = batch.bind(Erc20Contract.class, address, abi);

// 3. Stack calls and receive handles
BatchHandle<BigInteger> balance = batch.add(token.balanceOf(user));
BatchHandle<String> symbol = batch.add(token.symbol());

// 4. Execute the batch (single RPC call)
batch.execute();

// 5. Use the results
if (balance.result().success()) {
    System.out.println("Balance: " + balance.result().data());
}
```

## 📝 4. Development Roadmap

- [x] **Data Models**: Create `Call3` and `MulticallResult` records in `brane-core` to match the Multicall3 ABI structs.
- [x] **Recording Proxy**: Implement `MulticallInvocationHandler` to intercept calls and generate `Abi.FunctionCall` metadata.
- [x] **Result Decoding**: Extend the existing `Abi` decoding logic to handle the `(bool success, bytes returnData)[]` array returned by `aggregate3`.
- [x] **Automatic Chunking**: Implement batch splitting (e.g., max 500 calls per request) to comply with RPC provider limits.
- [x] **Error Handling**: Integrate `RevertDecoder` to translate hex error data into human-readable strings within `BatchResult`.

## 💡 5. Design Philosophy (Brane vs. Others)

- **Type Safety**: Like **Alloy**, we use the builder pattern to stack calls, but like **Viem**, we provide a structured result object that makes handling partial failures straightforward.
- **No Code Gen**: Unlike Alloy/Ethers, we rely on Brane's runtime proxies, avoiding the need for a compilation step or `sol!` macros.
- **Provider-centric**: Multicall is treated as a capability of the `PublicClient`, keeping the entry point discoverable.
