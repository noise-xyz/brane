# Phase 5: Internal Dogfooding (Smoke Test)

## Goal
Ensure that the `brane` SDK can be successfully consumed as an external library by other projects.

## Strategy: Standalone Smoke Test
We will create a **standalone** consumer project within the repository to simulate a real-world user.

*   **Location**: `smoke-test/` directory.
*   **Isolation**: It will have its own `settings.gradle` and `build.gradle`. It will **NOT** be part of the main project's Gradle build tree.
*   **Dependency**: It will depend on `com.github.noise-xyz.brane:brane-core` via `mavenLocal()`.

## Implementation Plan

### 1. Scenarios
The smoke test will execute 14 distinct scenarios to cover the entire SDK surface area:

#### Scenario A: End-to-End Token Transfer (Core)
*   **Action**: Deploy `BraneToken`, check balance, transfer tokens.
*   **Coverage**: `PublicClient`, `WalletClient`, `Contract`, `Abi`, `TransactionSigner`.

#### Scenario B: Error Handling (Resilience)
*   **Action**: Attempt to transfer more tokens than owned.
*   **Expectation**: Catch `RevertException` or `RpcException` with correct error code.
*   **Coverage**: `JsonRpcError`, `RevertDecoder`, `RpcRetry`.

#### Scenario C: Event Logs (Observability)
*   **Action**: Query `eth_getLogs` for the `Transfer` event emitted in Scenario A.
*   **Expectation**: Find exactly one log with correct topics and data.
*   **Coverage**: `LogFilter`, `PublicClient.getLogs`.

#### Scenario E: EIP-1559 & Access Lists (Modern Standards)
*   **Action**: Send a Type 2 transaction with an Access List using `TxBuilder.eip1559()`.
*   **Expectation**: Transaction succeeds and is mined.
*   **Coverage**: `Eip1559Transaction`, `AccessListEntry`, `TxBuilder`.

#### Scenario F: Raw Signing (Offline Capability)
*   **Action**: Manually sign a transaction using `signer.sign()` without broadcasting.
*   **Expectation**: Recover the sender address from the signature (or verify hash).
*   **Coverage**: `TransactionSigner`, `LegacyTransaction`.

#### Scenario G: Custom RPC (Flexibility)
*   **Action**: Call `anvil_mine` via `publicClient.call()`.
*   **Expectation**: The block number increases.
*   **Coverage**: `PublicClient.call` (arbitrary methods).

#### Scenario H: Chain ID Validation (Safety)
*   **Action**: Initialize `WalletClient` with an incorrect Chain ID (e.g., 1 for Mainnet) and attempt a transaction.
*   **Expectation**: Throw `ChainMismatchException`.
*   **Coverage**: `WalletClient`, `ChainMismatchException`.

### 2. Implementation Details

#### Structure
```
brane/
├── verify_smoke_test.sh (Orchestrator)
└── smoke-test/
    ├── build.gradle
    └── src/main/java/io/brane/smoke/SmokeApp.java (Runs all 8 scenarios)
```

#### `SmokeApp.java` Logic
```java
public class SmokeApp {
    public static void main(String[] args) {
        setup();
        testCoreTransfer();      // Scenario A
        testErrorHandling();     // Scenario B
        testEventLogs();         // Scenario C
        testAbiWrapper();        // Scenario D
        testEip1559();           // Scenario E
        testRawSigning();        // Scenario F
        testCustomRpc();         // Scenario G
        testChainIdMismatch();   // Scenario H
        System.out.println("✅ All Smoke Tests Passed!");
    }
}
```

### 3. Acceptance Criteria
- [ ] **Isolation**: `smoke-test` is a standalone Gradle project.
- [ ] **Completeness**: All 8 scenarios run and pass.
- [ ] **Reliability**: The test fails if any scenario fails.

### 4. Future Smoke Test Scenarios (Comprehensive Gap Analysis)
The following features are part of the SDK's API surface but are not covered by the current 8 scenarios. These should be considered for future expansion of the test suite.

#### Phase 5: Final Polish & Gaps (Completed)
 
The following scenarios verify developer experience features and modern Solidity support. These have been implemented and verified.
 
### Scenario M: Debug & Color Mode (Completed)
**Goal**: Verify that the SDK's debug logging and color output work as expected.
- **Actions**:
    - Enable `BraneDebug.setEnabled(true)`.
    - Set `FORCE_COLOR=true` (simulated or via env).
    - Perform a simple RPC call (e.g., `getChainId`).
- **Success Criteria**:
    - Logs appear in stdout.
    - Logs contain ANSI color codes (if enabled).
    - Logs contain RPC request/response payloads.
 
### Scenario N: Custom Error Decoding (Completed)
**Goal**: Verify that the SDK can decode Solidity custom errors (introduced in Solidity 0.8.4).
- **Actions**:
    - Update `ComplexContract` to include a function that reverts with a custom error: `error CustomError(uint256 code, string message)`.
    - Call this function using `ReadWriteContract`.
    - Catch `RevertException`.
    - Use `RevertDecoder.decode` with the known custom error ABI.
- **Success Criteria**:
    - `RevertException` is thrown.
    - Decoded reason matches `CustomError(code, message)`.

#### Public Client Read Operations
*   **`getLatestBlock()` / `getBlockByNumber()`**: Verify block header parsing (timestamp, parentHash, baseFee).
*   **`getTransactionByHash()`**: Verify transaction parsing (input, value, nonce, v/r/s).
*   **`getChainId()`**: Explicitly verify the chain ID reported by the node.

#### ABI Encoding/Decoding
*   **Complex Types**:
    *   **Arrays**: Test functions taking/returning `uint256[]`, `address[]`, etc.
    *   **Tuples/Structs**: Test functions returning structs (mapped to Java classes or `List<Object>`).
    *   **Fixed Bytes**: Test `bytes32` vs `bytes` (dynamic).
*   **Event Decoding**:
    *   **Indexed Primitives**: Test events with `indexed uint256` or `indexed bytes32`.
    *   **Anonymous Events**: Test decoding of anonymous events (if supported).

#### Utilities & Configuration
*   **`Wei` Unit Conversion**: Verify `Wei.fromEther()`, `Wei.toEther()`, and `Wei.gwei()`.
*   **Gas Strategy Configuration**: Test `DefaultWalletClient` with custom gas limit buffers (e.g., 1.5x multiplier).

#### Contract Features
*   **Multicall**: (If implemented) Batching multiple read calls.
*   **Deployment Helper**: If a high-level `Contract.deploy` exists (currently manual via `WalletClient`).
