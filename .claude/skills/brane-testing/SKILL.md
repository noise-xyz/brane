---
name: brane-testing
description: Write and run tests for Brane SDK using strict TDD discipline. Use when writing tests, verifying code changes, or implementing new features. Enforces test-first development to prevent tests from being written to fit incorrect code.
---

# Brane SDK Testing (Test-First Discipline)

## The Golden Rule: Tests Before Code

**NEVER write implementation code before the test exists.**

This is non-negotiable. Writing tests after implementation leads to:
- Tests that "fit" incorrect code
- False confidence from passing tests that don't actually verify correctness
- Leaked implementation details into test assertions

---

## Module Test Map

| Module | What to Test | Test Location |
|--------|-------------|---------------|
| `brane-core` | Types, ABI encoding/decoding, Crypto, Models, Builders | `brane-core/src/test/java/io/brane/core/...` |
| `brane-rpc` | RPC clients, providers, retry logic, WebSocket | `brane-rpc/src/test/java/io/brane/rpc/...` |
| `brane-contract` | Contract binding, proxy invocation, event decoding | `brane-contract/src/test/java/io/brane/contract/...` |
| `brane-primitives` | Hex utilities, RLP encoding | `brane-primitives/src/test/java/io/brane/primitives/...` |
| `brane-examples` | Integration tests against Anvil | `brane-examples/src/main/java/io/brane/examples/...` |

---

## Reference Tests (Study These First)

Before writing new tests, read these to understand our patterns:

- **Simple type validation**: `brane-core/.../types/AddressTest.java`
- **ABI encoding**: `brane-core/.../abi/AbiEncoderTest.java`
- **Contract binding**: `brane-contract/.../BraneContractTest.java`
- **RPC client**: `brane-rpc/.../BraneTest.java`
- **Error handling**: `brane-core/.../error/RpcExceptionTest.java`

---

## Naming Conventions

### Test Classes
- Pattern: `{ClassUnderTest}Test.java`
- Examples: `AddressTest.java`, `AbiEncoderTest.java`, `BraneContractTest.java`

### Test Methods
Use descriptive verbs that indicate what's being tested:

| Pattern | Use For | Example |
|---------|---------|---------|
| `{verb}Valid{Thing}` | Happy path acceptance | `acceptsValidAddress()` |
| `rejects{Condition}` | Validation rejection | `rejectsInvalidLength()` |
| `{verb}On{Condition}` | Conditional behavior | `returnsNullOnMissingKey()` |
| `roundTrip{Thing}` | Serialization round-trip | `roundTripBytes()` |
| `throws{Exception}On{Condition}` | Exception cases | `throwsIllegalArgumentOnNull()` |
| `{action}{description}` | General behavior | `deployRequestEncodesCorrectly()` |

---

## The TDD Workflow

### Phase 1: Red (Write a Failing Test First)

1. **Understand the requirement** - What should this code do?
2. **Write the test** - Express the requirement as a test assertion
3. **Run the test** - Confirm it **FAILS** (this is critical!)

```bash
# Run ONLY your new test - it MUST fail
./gradlew :brane-core:test --tests "io.brane.core.types.MyNewTypeTest"
```

If the test passes before you write implementation, either:
- The feature already exists (investigate first)
- Your test is wrong (doesn't actually test the requirement)

### Phase 2: Green (Write Minimal Implementation)

1. Write the **minimum code** to make the test pass
2. No optimization, no extra features, no "while I'm here" changes
3. Run the test - it should pass now

### Phase 3: Refactor (Clean Up)

1. Now that tests pass, clean up the implementation
2. Run tests again to ensure refactoring didn't break anything
3. Proceed to verification ladder

---

## Gradle Test Commands

```bash
# Run single test class
./gradlew :brane-core:test --tests "io.brane.core.types.AddressTest"

# Run single test method
./gradlew :brane-core:test --tests "io.brane.core.types.AddressTest.acceptsValidAddress"

# Run all tests in a package
./gradlew :brane-core:test --tests "io.brane.core.types.*"

# Run with verbose output
./gradlew :brane-core:test --tests "..." --info

# Module-specific full runs
./gradlew :brane-core:test
./gradlew :brane-rpc:test
./gradlew :brane-contract:test
./gradlew :brane-primitives:test

# All unit tests
./gradlew test

# Integration example
./gradlew :brane-examples:run -PmainClass=io.brane.examples.MyExample
```

---

## The Verification Ladder

After TDD cycles, follow this strict order. **ALL STEPS ARE MANDATORY.**

### Step 1: Targeted Verification (Unit)
```bash
./gradlew :brane-core:test --tests "io.brane.core.MyClassTest"
```
**Rule**: If this fails, STOP. Fix it. Do not proceed.

### Step 2: Module Verification (Unit)
```bash
./gradlew :brane-core:test
```
**Rule**: If this fails, you introduced a regression. Fix it.

### Step 3: Downstream Module Tests (Unit)
```bash
# Run tests for all downstream modules
./gradlew :brane-rpc:test :brane-contract:test
```
**Rule**: Changes must not break downstream consumers.

### Step 4: Integration Tests (Requires Anvil)
```bash
# Start Anvil (no permission needed)
anvil &

# Run integration tests
./scripts/test_integration.sh
```
**Rule**: Real RPC calls must work. Never skip this step.

### Step 5: Smoke Tests (E2E)
```bash
./scripts/test_smoke.sh
```
**Rule**: Full SDK integration must work. Never skip this step.

### Step 6: Full Verification
```bash
./gradlew test
```
**Rule**: All tests must pass before considering work complete.

---

## Mandatory Test Layers

**NEVER SKIP ANY TEST LAYER. All three are equally important:**

| Layer | Purpose | Command | Skip? |
|-------|---------|---------|-------|
| Unit | Logic correctness, fast feedback | `./gradlew :module:test` | **NO** |
| Integration | Real RPC, real transactions | `./scripts/test_integration.sh` | **NO** |
| Smoke | Full E2E, consumer perspective | `./scripts/test_smoke.sh` | **NO** |

Starting Anvil requires no special permission - just run `anvil &` in background.

---

## Assertion Patterns

Use JUnit 5 assertions (`org.junit.jupiter.api.Assertions`):

```java
import static org.junit.jupiter.api.Assertions.*;

// Equality (expected first, actual second)
assertEquals("0x000000000000000000000000000000000000dead", address.value());
assertEquals(20, address.toBytes().length);

// Exception testing - ALWAYS use this pattern
assertThrows(IllegalArgumentException.class, () -> new Address("0x1234"));

// With message validation
var ex = assertThrows(IllegalArgumentException.class, () -> new Address("0x1234"));
assertTrue(ex.getMessage().contains("Invalid address length"));

// Null checks
assertNotNull(result);
assertNull(optionalValue);

// Boolean
assertTrue(result.isEmpty());
assertFalse(flag);

// Collections
assertEquals(List.of("a", "b"), result);
```

### Anti-Pattern: Don't Do This
```java
// BAD - manual try/catch for exceptions
try {
    new Address("0x1234");
    fail("Expected exception");
} catch (IllegalArgumentException e) {
    // good
}

// GOOD - use assertThrows
assertThrows(IllegalArgumentException.class, () -> new Address("0x1234"));
```

---

## Test Data Patterns

### Use Text Blocks for JSON/ABI
```java
private static final String ABI_JSON = """
        [
          {
            "inputs": [{"internalType": "uint256", "name": "amount", "type": "uint256"}],
            "name": "transfer",
            "type": "function"
          }
        ]
        """;
```

### Use Constants for Repeated Values
```java
private static final String VALID_ADDRESS = "0x000000000000000000000000000000000000dEaD";
private static final String ANVIL_PRIVATE_KEY =
    "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
```

---

## Mocking Guidelines

### Do NOT Mock (Use Real Implementations)
- **Brane types**: `Address`, `Hash`, `HexData`, `Wei` - they're immutable value objects
- **Pure functions**: ABI encoding/decoding, crypto hashing, RLP encoding
- **Builders**: `TxBuilder`, `TransactionRequest` - no I/O involved

### DO Mock
- **RPC providers** in unit tests (`BraneProvider`) - avoids network calls
- **External HTTP** responses
- **Time-dependent** behavior if needed

### Integration Tests (Use Real Anvil)
- Actual RPC calls
- Transaction signing + submission
- Contract deployment + interaction

---

## When to Write Which Test Type

| Scenario | Test Type | Location |
|----------|-----------|----------|
| Type validation (`Address`, `Hash`) | Unit test | `brane-core/src/test/...` |
| ABI encoding/decoding | Unit test | `brane-core/src/test/...` |
| RPC client method logic | Unit test with mocked provider | `brane-rpc/src/test/...` |
| Contract proxy behavior | Unit test | `brane-contract/src/test/...` |
| Full transaction flow | Integration test | `brane-examples/src/main/...` |
| Stress/concurrency | Smoke test | `smoke-test/src/main/...` |

---

## Test Scaffolding

### Unit Test Template
```java
package io.brane.core;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MyFeatureTest {

    @Test
    void acceptsValidInput() {
        // Arrange
        var input = "test";

        // Act
        var result = MyFeature.process(input);

        // Assert
        assertEquals("expected", result);
    }

    @Test
    void rejectsNullInput() {
        assertThrows(IllegalArgumentException.class, () -> {
            MyFeature.process(null);
        });
    }
}
```

### Integration Test Template (brane-examples)
```java
package io.brane.examples;

public class MyFeatureExample {

    private static final String ANVIL_URL = "http://127.0.0.1:8545";
    private static final String PRIVATE_KEY =
        "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

    public static void main(String[] args) {
        var client = Brane.connect(ANVIL_URL, PrivateKey.from(PRIVATE_KEY));

        // Test against real EVM
        // Throw RuntimeException on failure
        System.out.println("[PASS] My feature works");
    }
}
```

---

## Common Pitfalls

| Symptom | Cause | Fix |
|---------|-------|-----|
| Test passes before implementation | Test doesn't verify requirement | Rewrite test to actually fail first |
| "Connection refused" | Anvil not running | Start Anvil: `anvil` |
| Test hangs | Transaction never mined | Use proper wait mechanisms |
| "intrinsic gas too low" | Gas limit issue | Use `TxBuilder` with proper gas |
| Flaky test | Race condition or timing | Add proper synchronization or increase timeouts |

---

## Anti-Patterns (Never Do These)

1. **Writing test after implementation** - Defeats TDD purpose
2. **Copying implementation logic into test** - Tests verify behavior, not mirror code
3. **Testing implementation details** - Test the "what", not the "how"
4. **Skipping the "Red" phase** - If your test never failed, you don't know it works
5. **Mocking value objects** - Don't mock `Address`, `Hash`, etc.
6. **Running full suite before targeted test passes** - Wastes time, masks failures
7. **Ignoring test failures** - Fix immediately, don't defer
