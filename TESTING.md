# Testing Standards & Acceptance Criteria

This document defines the testing strategy for the Brane SDK. It serves as the "Definition of Done" for all contributions and provides a streamlined workflow for developers and AI agents.

## The Pyramid of Testing

We adopt a layered approach to ensure robustness and efficiency.

| Level | Type | Scope | Script | Speed |
| :--- | :--- | :--- | :--- | :--- |
| **0** | **Sanity** | Environment checks (Java, Anvil, RPC). | `./scripts/test_sanity.sh` | Instant |
| **1** | **Unit** | Logic correctness. **NO** external I/O. Mock everything. | `./scripts/test_unit.sh` | Fast (<10s) |
| **2** | **Integration** | Component interactions & Canonical Examples. Requires Anvil. | `./scripts/test_integration.sh` | Moderate |
| **3** | **Smoke** | End-to-end user flows (`SmokeApp`). High concurrency. | `./scripts/test_smoke.sh` | Slow |
| **4** | **Performance** | Micro-benchmarks (`brane-benchmark`). Critical paths only. | `./scripts/test_perf.sh` | Slow |

## Acceptance Criteria (Definition of Done)

For a Pull Request to be merged, it must:

1.  **Pass All Tests**: Run `./verify_all.sh` and ensure it exits with code 0.
2.  **New Features**: Must include at least one **Unit Test** covering the logic.
3.  **New Components**: Must include an **Integration Test** or be added to `SmokeApp`.
4.  **No Regressions**: Existing tests must pass without modification (unless the change is a breaking change).

## Developer Workflow

1.  **Start Anvil**: Open a terminal and run `anvil`.
2.  **Run Sanity**: `./scripts/test_sanity.sh` to check your setup.
3.  **Develop**: Write code and Unit Tests.
4.  **Targeted Test**: Run *only* your new test to iterate fast.
    *   Unit: `./gradlew test --tests "com.package.MyTest"`
    *   Integration: `./gradlew :brane-examples:run -PmainClass=io.brane.examples.MyExample`
5.  **Test Loop**: Run `./scripts/test_unit.sh` to ensure no regressions.
6.  **Verify**: Run `./scripts/test_integration.sh` before pushing.
7.  **Final Check**: Run `./verify_all.sh` to run the full suite.

---

## 🤖 Instructions for AI Agents (LLM Guide)

If you are an AI assistant (like Gemini) helping with this codebase, follow these patterns to generate high-quality tests.

### 1. Scaffolding a Unit Test
*   **Goal**: Test logic in isolation.
*   **Location**: `brane-core/src/test/java/...` or `brane-rpc/src/test/java/...`
*   **Pattern**:
    *   Use JUnit 5 (`@Test`).
    *   **Mock** all dependencies (e.g., `BraneProvider`, `HttpClient`).
    *   **NEVER** make real network calls.
    *   Use `Assertions.assertEquals`, `Assertions.assertThrows`.

**Prompt Example**:
> "Create a unit test for `MyNewClass`. Mock the `BraneProvider` dependency. Ensure it handles the `null` response case."

### 2. Creating an Integration Test
*   **Goal**: Verify interaction with a real EVM (Anvil).
*   **Location**: `brane-examples/src/main/java/io/brane/examples/...` (as a standalone example) OR `brane-core/src/test/java/...` (with `@Tag("integration")`).
*   **Pattern**:
    *   Use `DefaultWalletClient` to connect to `http://127.0.0.1:8545`.
    *   Use the default Anvil private key: `0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80`.
    *   Deploy a contract or send a transaction.
    *   Assert the on-chain result.

**Prompt Example**:
> "Create a standalone integration example `MyFeatureExample.java`. It should connect to localhost:8545, deploy `MyContract`, and call the `myMethod` function."

### 3. Adding to Smoke Tests (`SmokeApp.java`)
*   **Goal**: Stress test a feature in a "real world" scenario.
*   **Location**: `smoke-test/src/main/java/io/brane/smoke/SmokeApp.java`
*   **Pattern**:
    *   Add a new `private static void testMyFeature()` method.
    *   Call it from `main()`.
    *   Throw `RuntimeException` on failure.
    *   Log success with `System.out.println("  ✓ ...")`.

### 4. Agent Protocol: The "Targeted -> Layer -> Full" Workflow (CRITICAL)

As an AI agent, you **MUST** strictly follow this 3-step protocol for **EVERY** code change. Do not skip steps.

#### Step 1: Targeted Verification (The "Single Test" Rule)
*   **Goal**: Verify your specific change works in isolation.
*   **Action**: Run **ONLY** the test case you just wrote or modified.
*   **Commands**:
    *   Unit: `./gradlew test --tests "io.brane.core.MyClassTest"`
    *   Integration: `./gradlew :brane-examples:run -PmainClass=io.brane.examples.MyNewExample`
*   **Rule**: If this fails, **STOP**. Fix the code. Do not run other tests.

#### Step 2: Layer Verification (The "Regression" Check)
*   **Goal**: Ensure you haven't broken other parts of the same component.
*   **Action**: Run the full suite for the specific layer you touched.
*   **Commands**:
    *   If you touched Unit logic: `./scripts/test_unit.sh`
    *   If you touched Integration logic: `./scripts/test_integration.sh`
*   **Rule**: If this fails, **STOP**. You likely introduced a regression.

#### Step 3: Full Verification (The "Definition of Done")
*   **Goal**: Ensure the entire SDK is healthy before requesting user review.
*   **Action**: Run the master script.
*   **Command**: `./verify_all.sh`
*   **Success Criteria**:
    *   ✅ Sanity Checks PASS
    *   ✅ Unit Tests PASS
    *   ✅ Integration Tests PASS
    *   ✅ Smoke Tests PASS
*   **Rule**: You are **NOT DONE** until this script prints "🎉 ALL CHECKS PASSED!".

---

### 5. Common Pitfalls for Agents
*   **Anvil Issues**: If integration tests fail with "Connection refused", check if Anvil is running (`./scripts/test_sanity.sh`).
*   **Timeout**: If tests hang, you might be waiting for a transaction that was never mined. Ensure you are using `sendTransactionAndWait`.
*   **Gas Errors**: If you see "intrinsic gas too low", check your gas limits or use `TxBuilder`.
