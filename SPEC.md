# Java Multicall3 SDK Implementation Guide

## 🎯 Objective

To build a high-performance, type-safe Java abstraction for Multicall3. The goal is to allow developers to bundle multiple contract reads into a single JSON-RPC eth_call without the manual boilerplate of encoding/decoding hex data.

## 🏗️ 1. Technical Foundations

### Multicall3 Contract

- **Standard Address**: `0xcA11bde05977b3631167028862bE2a173976CA11`
- **Function Signature**: `aggregate3((address target, bool allowFailure, bytes callData)[] calls) returns ((bool success, bytes returnData)[] returnData)`

### Strategic Approach

The SDK must intercept RemoteFunctionCall objects from existing contract wrappers, extract their metadata (target address and encoded calldata), and store them in a queue along with their original return types for later decoding.

## 🛠️ 2. Core Components

### A. The Registry (MulticallRegistry)

Provides the address of the Multicall3 contract based on the current chainId.

- **Pre-populated**: Include Ethereum Mainnet, Polygon, Arbitrum, Optimism, Base, and BSC using the standard address.
- **Dynamic**: Methods to `register(long chainId, String address)` for private or emerging networks.

### B. The Result Wrapper (BatchResult<T>)

Since calls can fail individually when `allowFailure` is true, results must be wrapped.

```java
public class BatchResult<T> {
    private final T data;
    private final boolean success;
    private final byte[] rawError; // Helpful for decoding Revert reasons
    
    // Standard getters: isSuccess(), getData(), getRawError()
}
```

### C. The Orchestrator (MulticallBatch)

This is the primary user interface. It manages a `List<CallContext>`.

## 🚀 3. Implementation Requirements

### 1. Fluent Builder Pattern

The API must feel "native" and fluent. Users should be able to "queue" calls and receive a handle to the future result.

```java
MulticallBatch batch = new MulticallBatch(web3j);

// The 'add' method should return a future-like handle
BatchElement<String> nameHandle = batch.add(erc20.name());
BatchElement<BigInteger> balanceHandle = batch.add(erc20.balanceOf(myAddress));

batch.execute(); // Performs the single RPC call

String name = nameHandle.get().getData(); 
```

### 2. Type-Safe Heterogeneous Decoding

This is the most critical technical hurdle.

- **Storage**: When `.add()` is called, the SDK must store the `List<TypeReference<?>>` expected by the original function.
- **Decoding**: After `execute()`, the SDK iterates through the `Result[]` array. For each index `i`, it uses `FunctionReturnDecoder.decode(results[i].returnData, calls[i].getReturnTypes())`.

### 3. Automatic Chunking

If a batch exceeds a certain threshold (e.g., 500 calls or a specific gas limit), the SDK should automatically split the batch into multiple `aggregate3` calls to prevent RPC timeouts or "response too large" errors.

## 📝 4. Development Checklist

- [ ] **ABI Definition**: Create a Java representation of Call3 and Result structs using StaticStruct.
- [ ] **Call Interception**: Implement a way to extract Function objects from RemoteFunctionCall without triggering a network request.
- [ ] **Address Manager**: Hardcode the deterministic `0xcA11` address for common chains.
- [ ] **Error Handling**: Implement logic to decode revert strings from returnData if success is false.
- [ ] **Async Support**: Ensure `executeAsync()` returns a `CompletableFuture` for non-blocking applications.

## 💡 5. Example "Ideal" Usage

```java
// Example of the target Developer Experience
MulticallBatch batch = sdk.createBatch();

var call1 = batch.add(dai.symbol());
var call2 = batch.add(dai.totalSupply());

batch.execute(); // One eth_call triggered here

if (call1.get().isSuccess()) {
    System.out.println("Symbol: " + call1.get().getData()); // Symbol: DAI
}
```

**Implementation Note**: Ensure that the SDK handles the `allowFailure` flag both at a global batch level and an individual call level to maximize flexibility.