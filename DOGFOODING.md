# Internal Dogfooding Guide

## How to use `brane` in your project

We use [JitPack](https://jitpack.io) for internal distribution. This allows you to pull any branch, tag, or commit hash as a dependency.

### 1. Add the Repository
Add `maven { url 'https://jitpack.io' }` to your `repositories` block in `build.gradle`:

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

### 2. Add the Dependency
Add the dependency using the format `com.github.noise-xyz.brane:Module:Tag`.

#### Target a specific Release (Recommended)
```groovy
dependencies {
    implementation 'com.github.noise-xyz.brane:brane-core:0.1.0'
}
```

#### Target a specific Commit (For debugging)
```groovy
dependencies {
    implementation 'com.github.noise-xyz.brane:brane-core:a1b2c3d'
}
```

#### Target a Branch Snapshot (For testing latest changes)
```groovy
dependencies {
    implementation 'com.github.noise-xyz.brane:brane-core:main-SNAPSHOT'
}
```

## Local Verification (For Contributors)

If you are developing `brane` itself, use these scripts to verify your changes.

### 1. Smoke Tests (Fast & Comprehensive)
The smoke test suite is the primary verification tool. It compiles a standalone consumer app and runs it against a local Anvil node.

```bash
./scripts/test_smoke.sh
```
**What it does:**
1.  Starts a fresh Anvil instance (fails if port 8545 is busy).
2.  Publishes the SDK to `mavenLocal`.
3.  Compiles and runs `smoke-test/src/main/java/io/brane/smoke/SmokeApp.java`.
4.  Verifies 15 scenarios (Core, Errors, Events, ABI, EIP-1559, etc.).
5.  Cleans up Anvil process.

#### Scenarios Covered
*   **Scenario A: End-to-End Token Transfer**: Deploys `BraneToken`, checks balance, transfers tokens.
*   **Scenario B: Error Handling**: Verifies `RevertException` and `RpcException` handling.
*   **Scenario C: Event Logs**: Queries `eth_getLogs` for emitted events.
*   **Scenario D: ABI Wrapper**: Uses `BraneContract` for high-level interaction.
*   **Scenario E: EIP-1559 & Access Lists**: Sends modern transaction types.
*   **Scenario F: Raw Signing**: Manually signs transactions offline.
*   **Scenario G: Custom RPC**: Calls custom methods like `anvil_mine`.
*   **Scenario H: Chain ID Validation**: Ensures safety against wrong networks.
*   **Scenario I: Public Client Read Ops**: Verifies block and transaction reading.
*   **Scenario J: Wei Utilities**: Tests unit conversion.
*   **Scenario K: Complex ABI**: Tests arrays, tuples, and fixed bytes.
*   **Scenario L: Gas Strategy**: Verifies gas limit buffering.
*   **Scenario M: Debug & Color Mode**: Verifies logging output.
*   **Scenario N: Custom Error Decoding**: Decodes Solidity custom errors.
*   **Scenario O: Complex Nested Structs**: Verifies encoding/decoding of nested tuples.

### 2. Real Network Verification (Sepolia)
You can run the smoke tests against the Sepolia testnet in read-only mode. This verifies connectivity and data retrieval without spending gas.

```bash
# Note: You need to manually configure the SmokeApp for Sepolia if needed, 
# or use the integration tests which are more flexible.
# The current test_smoke.sh focuses on local Anvil.
```

### 3. Canonical Examples (Learning & Debugging)
Run the examples to see the SDK in action or to debug specific features.

```bash
./scripts/test_integration.sh
```
**Prerequisite:** You must have `anvil` running in a separate terminal (or let the script start it).

**What it does:**
- Runs `CanonicalErc20Example` (High-level binding)
- Runs `CanonicalRawExample` (Low-level RPC)
- Runs `CanonicalDebugExample` (Error handling)
- Runs `CanonicalTxExample` (EIP-1559 & Access Lists)
- Runs `CanonicalAbiExample` (ABI encoding/decoding)

### 4. Integration Tests (Deep Testing)
Run the full JUnit integration test suite.

```bash
./scripts/test_integration.sh
```
**What it does:**
- Runs standard JUnit tests in `brane-rpc` and `brane-core`.
- Requires Anvil (started automatically by some tests, or checks for local instance).

## Prerequisites
- **Java 21**: Required for building and running.
- **Foundry (Anvil)**: Required for local blockchain simulation. Install via `foundryup`.
