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

#### Scenario D: ABI Wrapper (Usability)
*   **Action**: Use `BraneContract.bind(Erc20.class)` to interact with the deployed token via a Java interface.
*   **Expectation**: `erc20.balanceOf(recipient)` returns the correct value.
*   **Coverage**: `BraneContract`, `ContractInvocationHandler`, `AbiBinding`.

### 2. Implementation Details

#### Structure
```
brane/
├── verify_smoke_test.sh (Orchestrator)
└── smoke-test/
    ├── build.gradle
    └── src/main/java/io/brane/smoke/SmokeApp.java (Runs all 4 scenarios)
```

#### `SmokeApp.java` Logic
```java
public class SmokeApp {
    public static void main(String[] args) {
        setup();
        testCoreTransfer(); // Scenario A
        testErrorHandling(); // Scenario B
        testEventLogs(); // Scenario C
        testAbiWrapper(); // Scenario D
        System.out.println("✅ All Smoke Tests Passed!");
    }
}
```

### 3. Acceptance Criteria
- [ ] **Isolation**: `smoke-test` is a standalone Gradle project.
- [ ] **Completeness**: All 4 scenarios run and pass.
- [ ] **Reliability**: The test fails if any scenario fails (e.g., revert not caught, balance mismatch).

