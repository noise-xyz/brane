# Brane


A modern, type-safe JVM SDK for Ethereum and EVM chains.
Built on Java 21. Focused on clarity, correctness, and developer experience.

## Quickstart
 
We use [JitPack](https://jitpack.io/#noise-xyz/brane) for distribution.
 
### 1. Add Repository
```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```
 
### 2. Add Dependencies
```groovy
dependencies {
    // Replace '0.1.0-alpha' with a specific tag or 'main-SNAPSHOT'
    implementation 'com.github.noise-xyz.brane:brane-core:0.1.0-alpha'
    implementation 'com.github.noise-xyz.brane:brane-rpc:0.1.0-alpha'
    implementation 'com.github.noise-xyz.brane:brane-contract:0.1.0-alpha'
    implementation 'com.github.noise-xyz.brane:brane-primitives:0.1.0-alpha'
}
```

## Features

### 1. Type-Safe Contract Binding
Interact with contracts using standard Java interfaces. No code generation required.

```java
public interface Erc20 {
    BigInteger balanceOf(Address account);
    TransactionReceipt transfer(Address to, BigInteger amount);
}

// Bind to deployed contract
Erc20 token = BraneContract.bind(
    new Address("0x..."),
    ERC20_ABI_JSON,
    publicClient,
    walletClient,
    Erc20.class
);

// Call methods (View & Write)
BigInteger balance = token.balanceOf(user);
TransactionReceipt receipt = token.transfer(user, BigInteger.TEN);
```
[See full example](brane-examples/src/main/java/io/brane/examples/CanonicalAbiExample.java)

### 2. Modern Transactions (EIP-1559)
Fluent builder with smart defaults (auto-estimated gas, EIP-1559 fees).

```java
TransactionRequest tx = TxBuilder.eip1559()
    .to(recipient)
    .value(Wei.fromEther(new BigDecimal("0.001")))
    .build(); 
    // ✅ Gas Limit: Auto-estimated + 20% buffer
    // ✅ Fees: Auto-calculated (2x baseFee + priority)

// Send and wait for receipt
TransactionReceipt receipt = walletClient.sendTransactionAndWait(tx, 60_000, 1_000);
```

### 3. Debugging & Error Handling
Typed exceptions for RPC failures and EVM reverts, plus structured logging.

```java
BraneDebug.setEnabled(true); // Enable detailed RPC/Tx logs

try {
    wallet.sendTransactionAndWait(tx, 10_000, 1_000);
} catch (RevertException e) {
    System.out.println("Revert: " + e.revertReason()); // e.g. "Insufficient funds"
    System.out.println("Kind: " + e.kind());           // ERROR, PANIC, or CUSTOM
} catch (RpcException e) {
    System.out.println("RPC Error: " + e.getMessage());
    System.out.println("Request ID: " + e.requestId());
}
```

### 4. ABI Utilities
Helpers for common tasks like constructor encoding.

```java
// Encode constructor arguments for deployment
Abi abi = Abi.fromJson(abiJson);
HexData encodedArgs = abi.encodeConstructor(arg1, arg2);
String deployData = bytecode + encodedArgs.value().substring(2);
```

## Why Brane?

*   **Typed Errors**: Distinct `RevertException` (with automatic decoding) and `RpcException`. No more parsing generic strings.
*   **Modern Defaults**: EIP-1559 by default. Automatic access lists (EIP-2930). Smart gas estimation.
*   **Zero-Codegen**: Runtime binding of interfaces (`BraneContract.bind`) speeds up iteration.
*   **Clean API**: Minimal surface area. Zero dependency on web3j (native crypto & RLP).

## Project Structure

*   `brane-core`: Core types (`Address`, `Wei`, `HexData`), native ABI system (`io.brane.core.abi`), error model, and chain profiles.
*   `brane-rpc`: JSON-RPC client, `PublicClient`, `WalletClient`.
*   `brane-contract`: High-level ABI utilities, `BraneContract` runtime binding.
*   `brane-primitives`: Zero-dependency Hex and RLP utilities.
*   `brane-benchmark`: JMH benchmarks for performance critical paths.

## Local Development

Prerequisites: Java 21, [Foundry](https://getfoundry.sh/) (for Anvil).

**Run Canonical Examples:**
The best way to explore the SDK is to run the canonical examples against a local Anvil node.

```bash
./scripts/test_integration.sh
```
This script checks for Anvil, starts the examples, and verifies:
*   ERC-20 interactions
*   Raw RPC calls
*   Debug logging
*   Modern Tx features (Access Lists)
*   ABI wrapper features

**Run Smoke Tests (Comprehensive):**
```bash
./scripts/test_smoke.sh
```

**Run Benchmarks:**
```bash
./scripts/test_perf.sh
```

**Run Tests:**
```bash
./gradlew check
```

**Generate Javadoc:**
```bash
./gradlew allJavadoc
```
Docs will be in `build/docs/javadoc`.