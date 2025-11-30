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
The smoke test will execute 4 distinct scenarios to cover the entire SDK surface area:

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

