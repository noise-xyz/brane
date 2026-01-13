# 🤖 Brane SDK: AI Agent Guide

**The modern EVM SDK for Java**
Type-safe, lightweight, and built for correctness.
Inspired by the ergonomics of `viem` (TS) and `alloy` (Rust).

This document is the **System Prompt** for any AI agent (LLM) working on the Brane SDK. Read this before generating code or answering questions.

## 1. Project Context
*   **Name**: Brane SDK
*   **Goal**: A modern, type-safe Java SDK for Ethereum/EVM, with zero dependencies on other web3 libraries in its public API.
*   **Status**: `v0.3.0`
*   **Stack**: Java 21, Gradle, JUnit 5, Foundry (Anvil).

## 2. Critical Rules (The "Must Haves")
1.  **Java 21 Only**: Use modern features (Records, Pattern Matching, Virtual Threads).
2.  **Zero Dependencies (Strict on Web3j)**:
    *   **Strict**: We cannot use `web3j` unless it's for testing and benchmarking. It must **never** leak into public APIs.
    *   **Careful**: Be very careful not to use heavy libraries. We build our own primitives where reasonable.
3.  **Type Safety**: Use `Address`, `Wei`, `HexData` types. Avoid raw `String` or `BigInteger` where possible.
4.  **Async/Sync**:
    *   We offer both synchronous (blocking) and asynchronous APIs.
    *   **Virtual Threads**: For blocking operations, rely on Virtual Threads (Project Loom) rather than complex reactive chains.
    *   **Future**: We plan to offer more robust async features.

## 3. Architecture Overview
*   **`brane-core`**: The heart. Types, ABI encoding (`sh.brane.core.abi`), Crypto (`Signer`), Models.
*   **`brane-rpc`**: JSON-RPC client. `Brane.Reader` (Read), `Brane.Signer` (Write).
*   **`brane-contract`**: High-level runtime binding (`BraneContract.bind`). **NO CODEGEN**.
*   **`brane-primitives`**: Low-level Hex/RLP utils.

---

## 4. Development Standards

**Target:** Modern Java 21 Enterprise Development
**Philosophy:** "Make the invalid unrepresentable."

### I. Core Principles
1. **Immutability by Default:** Unless mutable state is strictly necessary for performance within a critical loop, all variables and fields should be `final`.
2. **Concise Expression:** Prefer expressions over statements. Use `switch` expressions, `var`, and direct returns.
3. **Blocking is Cheap (Again):** With Virtual Threads, do not write reactive/async callback hell. Write simple, synchronous, blocking code.
4. **Isolation by Design:** External dependencies (like web3j) are implementation details that must never leak into public APIs.

### II. Architectural Guardrails: Web3j Isolation
> [!IMPORTANT]
> Brane vendors web3j under `sh.brane.internal.web3j.*`. **web3j is an implementation detail only** and must **never** leak into Brane's public API.

#### 1. Package Restrictions

✅ **web3j may ONLY be referenced in:**
- `sh.brane.internal.web3j.*`
- Small, clearly-marked adapter classes in `sh.brane.internal.*`

❌ **web3j is FORBIDDEN in:**
- `sh.brane.core.*`
- `sh.brane.rpc.*`
- `sh.brane.contract.*`
- `sh.brane.examples.*`

**Rule of thumb:** If a package is public-facing (core/rpc/contract/examples), it must have **zero** `org.web3j.*` imports.

#### 2. Type Safety in Public APIs
Public APIs must **only** use:
- Java standard types (`String`, `BigInteger`, `List`, etc.)
- Brane types (`Address`, `Hash`, `HexData`, `Wei`, `Transaction`, `RpcException`, etc.)

**Forbidden in any public method/constructor/field:**
- `org.web3j.protocol.Web3j`
- `org.web3j.protocol.core.methods.response.*`
- `org.web3j.abi.datatypes.*`
- `org.web3j.crypto.*`

#### 3. Exception Wrapping
`org.web3j.*` exceptions must **never** bubble out of public methods.

**Correct pattern:**
```java
public Object read(...) throws RpcException, RevertException {
    try {
        // web3j call inside internal adapter
    } catch (org.web3j.protocol.exceptions.ClientConnectionException e) {
        throw new RpcException(-32000, "Connection failed", null, e);
    }
}
```

#### 4. Core Module Purity
`brane-core` (`sh.brane.core.*`) **must not depend on web3j at all:**
- No imports from `org.web3j.*`
- No references to "web3j" in type names, method names, or Javadoc
- Pure Brane domain: types + errors only

#### 5. Review Checklist
When adding/modifying code:
1. ✅ File not under `sh.brane.internal.*` → **no** `org.web3j.*` imports
2. ✅ Public methods/constructors/fields → only JDK + Brane types
3. ✅ Exceptions → wrap web3j exceptions in `RpcException`/`RevertException`

### III. Concurrency & Project Loom
**Principle:** The "Thread-per-Request" model is back. Stop pooling threads for business logic.

* **DO** use `Executors.newVirtualThreadPerTaskExecutor()` for concurrent tasks.
* **DO NOT** use `CompletableFuture` chains or reactive streams unless required by legacy library.
* **DO NOT** pool virtual threads—they are disposable.

### IV. Data Modeling & Control Flow (Project Amber)
**Principle:** Data should be dumb, and logic should be explicit.

* **DO** use `record` for data holders (DTOs, events, config).
* **DO** use `switch` expressions over `if-else` chains.
* **DO** use Record Patterns to deconstruct data.

### V. Collections & Streams
**Principle:** Order matters, and streams should be clean.

* **DO** use `SequencedCollection`, `SequencedSet`, `SequencedMap` when order is significant.
* **DO** use `Stream.toList()` instead of `.collect(Collectors.toList())`.

### VI. Syntax & Style
* **DO** use `var` where type is obvious from right-hand side.
* **DO** use Text Blocks (`"""`) for SQL, JSON, HTML, or multi-line strings.

### VII. Legacy Traps to Avoid (Anti-Patterns)
1. **NO `Optional.get()`:** Always use `.orElseThrow()`, `.ifPresent()`, or `.map()`.
2. **NO `public` fields:** Unless `static final` constants.
3. **NO Raw Types:** Ensure all Generics are typed (`List<String>`, not `List`).
4. **NO Swallowing Exceptions:** Never `catch (Exception e) {}`. At minimum, log it.

### VIII. Factory Method Naming Convention

Brane SDK follows a consistent naming convention for static factory methods:

| Method Name | Purpose | Example |
|-------------|---------|---------|
| `create()` | Simple factory that constructs an instance directly | `WebSocketProvider.create(url)` |
| `builder()` | Starts a builder pattern, returns a `Builder` with `.build()` | `HttpBraneProvider.builder(url).build()` |
| `from()` | Conversion/adaptation from an existing object or explicit parameters | `Brane.connect(url)` |
| `of()` | Static factory for value objects (records, immutable types) | `CallRequest.of(to, data)` |
| `forChain()` | Domain-specific factory for chain configuration (returns builder) | `Brane.builder().chain(profile)` |

**Guidelines:**
1. **`create()`** - Use when construction is straightforward and doesn't require many optional parameters.
2. **`builder()`** - Use when the object has many optional configuration parameters.
3. **`from()`** - Use when wrapping, adapting, or converting from another type. The semantics imply "derive this from that."
4. **`of()`** - Reserved for value objects where the factory closely resembles a constructor call.
5. **`forChain()`** - Domain-specific; returns a builder for chain-aware configuration.

**Anti-patterns:**
- Avoid mixing `create()` and `from()` with identical semantics in the same class.
- Don't use `new*()` or `make*()` prefixes - they're not idiomatic Java.
- Avoid `get*()` for factories - reserve `get` for accessors.

---

## 5. Testing Protocol

We adopt a layered approach to ensure robustness and efficiency.

### The Pyramid of Testing

| Level | Type | Scope | Script | Speed |
| :--- | :--- | :--- | :--- | :--- |
| **0** | **Sanity** | Environment checks (Java, Anvil, RPC). | `./scripts/test_sanity.sh` | Instant |
| **1** | **Unit** | Logic correctness. **NO** external I/O. Mock everything. | `./scripts/test_unit.sh` | Fast (<10s) |
| **2** | **Integration** | Component interactions & Canonical Examples. Requires Anvil. | `./scripts/test_integration.sh` | Moderate |
| **3** | **Smoke** | End-to-end user flows (`SmokeApp`). High concurrency. | `./scripts/test_smoke.sh` | Slow |
| **4** | **Performance** | Micro-benchmarks (`brane-benchmark`). Critical paths only. | `./scripts/test_perf.sh` | Slow |

### Acceptance Criteria (Definition of Done)
For a Pull Request to be merged, it must:
1.  **Pass All Tests**: Run `./verify_all.sh` and ensure it exits with code 0.
2.  **New Features**: Must include at least one **Unit Test** covering the logic.
3.  **New Components**: Must include an **Integration Test** or be added to `SmokeApp`.
4.  **No Regressions**: Existing tests must pass without modification (unless the change is a breaking change).

### Developer Workflow
1.  **Start Anvil**: Open a terminal and run `anvil`.
2.  **Run Sanity**: `./scripts/test_sanity.sh` to check your setup.
3.  **Develop**: Write code and Unit Tests.
4.  **Targeted Test**: Run *only* your new test to iterate fast.
    *   Unit: `./gradlew test --tests "com.package.MyTest"`
    *   Integration: `./gradlew :brane-examples:run -PmainClass=sh.brane.examples.MyExample`
5.  **Test Loop**: Run `./scripts/test_unit.sh` to ensure no regressions.
6.  **Verify**: Run `./scripts/test_integration.sh` before pushing.
7.  **Final Check**: Run `./verify_all.sh` to run the full suite.

### The "Targeted -> Layer -> Full" Workflow (CRITICAL)
**You MUST follow this workflow for every change:**

#### Step 1: Targeted Verification (The "Single Test" Rule)
*   **Goal**: Verify your specific change works in isolation.
*   **Action**: Run **ONLY** the test case you just wrote or modified.
*   **Commands**:
    *   Unit: `./gradlew test --tests "sh.brane.core.MyClassTest"`
    *   Integration: `./gradlew :brane-examples:run -PmainClass=sh.brane.examples.MyNewExample`
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

## 6. Instructions for AI Agents (LLM Guide)

### Scaffolding a Unit Test
*   **Goal**: Test logic in isolation.
*   **Location**: `brane-core/src/test/java/...` or `brane-rpc/src/test/java/...`
*   **Pattern**:
    *   Use JUnit 5 (`@Test`).
    *   **Mock** all dependencies (e.g., `BraneProvider`, `HttpClient`).
    *   **NEVER** make real network calls.
    *   Use `Assertions.assertEquals`, `Assertions.assertThrows`.

### Creating an Integration Test
*   **Goal**: Verify interaction with a real EVM (Anvil).
*   **Location**: `brane-examples/src/main/java/sh/brane/examples/...` (as a standalone example) OR `brane-core/src/test/java/...` (with `@Tag("integration")`).
*   **Pattern**:
    *   Use `Brane.connect(url, signer)` to connect to `http://127.0.0.1:8545`.
    *   Use the default Anvil private key: `0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80`.
    *   Deploy a contract or send a transaction.
    *   Assert the on-chain result.

### Adding to Smoke Tests (`SmokeApp.java`)
*   **Goal**: Stress test a feature in a "real world" scenario.
*   **Location**: `smoke-test/src/main/java/sh/brane/smoke/SmokeApp.java`
*   **Pattern**:
    *   Add a new `private static void testMyFeature()` method.
    *   Call it from `main()`.
    *   Throw `RuntimeException` on failure.
    *   Log success with `System.out.println("  ✓ ...")`.

---

## 7. Common Pitfalls for Agents
*   **Anvil Issues**: If integration tests fail with "Connection refused", check if Anvil is running (`./scripts/test_sanity.sh`).
*   **Timeout**: If tests hang, you might be waiting for a transaction that was never mined. Ensure you are using `sendTransactionAndWait`.
*   **Gas Errors**: If you see "intrinsic gas too low", check your gas limits or use `TxBuilder`.
*   **Hallucinating Dependencies**: Do not invent libraries. Check `build.gradle`.
*   **Old Java Syntax**: Do not use pre-Java 21 syntax (e.g., old `switch`, verbose `try-catch`).
