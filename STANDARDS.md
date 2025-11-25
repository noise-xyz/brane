# Brane Development Standards

**Target:** Modern Java 21 Enterprise Development  
**Philosophy:** "Make the invalid unrepresentable."

This document defines coding standards, architectural guardrails, and best practices for the Brane SDK.

---

## Table of Contents
1. [Core Principles](#i-core-principles)
2. [Architectural Guardrails](#ii-architectural-guardrails-web3j-isolation)
3. [Concurrency & Project Loom](#iii-concurrency--project-loom)
4. [Data Modeling & Control Flow](#iv-data-modeling--control-flow-project-amber)
5. [Collections & Streams](#v-collections--streams)
6. [Syntax & Style](#vi-syntax--style)
7. [Legacy Traps to Avoid](#vii-legacy-traps-to-avoid-anti-patterns)
8. [Example: The Golden Standard](#viii-example-the-golden-standard)

---

## I. Core Principles

1. **Immutability by Default:** Unless mutable state is strictly necessary for performance within a critical loop, all variables and fields should be `final`.
2. **Concise Expression:** Prefer expressions over statements. Use `switch` expressions, `var`, and direct returns.
3. **Blocking is Cheap (Again):** With Virtual Threads, do not write reactive/async callback hell. Write simple, synchronous, blocking code.
4. **Isolation by Design:** External dependencies (like web3j) are implementation details that must never leak into public APIs.

---

## II. Architectural Guardrails: Web3j Isolation

> [!IMPORTANT]
> Brane vendors web3j under `io.brane.internal.web3j.*`. **web3j is an implementation detail only** and must **never** leak into Brane's public API.

### 1. Package Restrictions

✅ **web3j may ONLY be referenced in:**
- `io.brane.internal.web3j.*`
- Small, clearly-marked adapter classes in `io.brane.internal.*`

❌ **web3j is FORBIDDEN in:**
- `io.brane.core.*`
- `io.brane.rpc.*`
- `io.brane.contract.*`
- `io.brane.examples.*`

**Rule of thumb:** If a package is public-facing (core/rpc/contract/examples), it must have **zero** `org.web3j.*` imports.

### 2. Type Safety in Public APIs

Public APIs must **only** use:
- Java standard types (`String`, `BigInteger`, `List`, etc.)
- Brane types:
  - `io.brane.core.types.*` (`Address`, `Hash`, `HexData`, `Wei`)
  - `io.brane.core.model.*` (`Transaction`, `TransactionReceipt`, `LogEntry`, `ChainProfile`)
  - `io.brane.core.error.*` (`BraneException`, `RpcException`, `RevertException`)

🚫 **Forbidden in any public method/constructor/field:**
- `org.web3j.protocol.Web3j`
- `org.web3j.protocol.core.methods.response.*`
- `org.web3j.abi.datatypes.*`
- `org.web3j.crypto.*`
- Any other `org.web3j.*` type

**Example:**
```java
// ❌ Bad: leaks web3j type
public org.web3j.protocol.core.methods.response.TransactionReceipt sendTx(...);

// ✅ Good: uses Brane types
public io.brane.core.model.TransactionReceipt sendTx(...);
```

### 3. Exception Wrapping

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

### 4. Core Module Purity

`brane-core` (`io.brane.core.*`) **must not depend on web3j at all:**
- No imports from `org.web3j.*`
- No references to "web3j" in type names, method names, or Javadoc
- Pure Brane domain: types + errors only

### 5. Review Checklist

When adding/modifying code:
1. ✅ File not under `io.brane.internal.*` → **no** `org.web3j.*` imports
2. ✅ Public methods/constructors/fields → only JDK + Brane types
3. ✅ Exceptions → wrap web3j exceptions in `RpcException`/`RevertException`

---

## III. Concurrency & Project Loom

**Principle:** The "Thread-per-Request" model is back. Stop pooling threads for business logic.

### 1. Virtual Threads

* **DO** use `Executors.newVirtualThreadPerTaskExecutor()` for concurrent tasks
* **DO NOT** use `CompletableFuture` chains or reactive streams unless required by legacy library
* **DO NOT** pool virtual threads—they are disposable

**Correct:**
```java
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    executor.submit(() -> processOrder(orderId));
}
```

### 2. Avoid "Pinning"

* **AVOID** `synchronized` blocks in virtual thread contexts (pins to OS thread)
* **PREFER** `ReentrantLock` for mutual exclusion

### 3. Structured Concurrency

**DO** organize related concurrent subtasks using `StructuredTaskScope` or `ExecutorService.invokeAll()` patterns to ensure cleanup on failure.

---

## IV. Data Modeling & Control Flow (Project Amber)

**Principle:** Data should be dumb, and logic should be explicit.

### 1. Records for Data Carriers

* **DO** use `record` for data holders (DTOs, events, config)
* **DO NOT** write JavaBeans manually

**Correct:**
```java
public record UserTransaction(UUID id, BigDecimal amount, Instant timestamp) {}
```

### 2. Pattern Matching & Switch

* **DO** use `switch` expressions over `if-else` chains
* **DO** use Record Patterns to deconstruct data
* **DO** use Guarded Patterns (`when`) to fold logic

**Correct:**
```java
String response = switch (payload) {
    case LoginEvent(var user, var time) when time.isBefore(CUTOFF) -> "Welcome back " + user;
    case LoginEvent(var user, _) -> "Login too late, " + user;
    case LogoutEvent _ -> "Goodbye";
    case null -> "Invalid payload";
    default -> throw new IllegalArgumentException("Unknown event");
};
```

### 3. Sealed Interfaces

**DO** use `sealed` interfaces when the hierarchy is known and finite—enables exhaustiveness checking in switch expressions.

---

## V. Collections & Streams

**Principle:** Order matters, and streams should be clean.

### 1. Sequenced Collections

* **DO** use `SequencedCollection`, `SequencedSet`, `SequencedMap` when order is significant
* **DO** use `.addFirst()`, `.addLast()`, `.getFirst()`, `.getLast()` instead of index access

### 2. Stream Purity

* **DO** use `Stream.toList()` instead of `.collect(Collectors.toList())`
* **DO NOT** perform side effects inside `.map()` or `.peek()`

---

## VI. Syntax & Style

### 1. Type Inference (`var`)

* **DO** use `var` where type is obvious from right-hand side
* **DO NOT** use `var` for nulls, lambdas without explicit targets, or where it obscures meaning

**Correct:**
```java
var transactions = repository.findAll(); // Clear
var map = new HashMap<String, List<String>>(); // Clear
```

### 2. Text Blocks

* **DO** use Text Blocks (`"""`) for SQL, JSON, HTML, or multi-line strings
* **DO NOT** use concatenation (`+`) for multi-line strings

---

## VII. Legacy Traps to Avoid (Anti-Patterns)

1. **NO `Optional.get()`:** Always use `.orElseThrow()`, `.ifPresent()`, or `.map()`
2. **NO `public` fields:** Unless `static final` constants
3. **NO Raw Types:** Ensure all Generics are typed (`List<String>`, not `List`)
4. **NO Swallowing Exceptions:** Never `catch (Exception e) {}`. At minimum, log it

---

## VIII. Example: The Golden Standard

A well-written service method combining these principles:

```java
public TransactionResult processPayment(PaymentRequest req) {
    // 1. Validation using Records & Switch
    if (req.amount().compareTo(BigDecimal.ZERO) <= 0) {
        throw new InvalidPaymentException("Amount must be positive");
    }

    // 2. Concurrency using Virtual Threads
    try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
        var fraudCheck = executor.submit(() -> fraudService.check(req.userId()));
        var balanceCheck = executor.submit(() -> balanceService.verify(req.userId(), req.amount()));
        
        // Blocking is cheap in Java 21
        if (!fraudCheck.get() || !balanceCheck.get()) {
            return new TransactionResult(Status.FAILED, "Verification failed");
        }
    } catch (ExecutionException | InterruptedException e) {
        throw new RuntimeException("Concurrency error", e);
    }

    // 3. Immutable Data Return
    return new TransactionResult(Status.SUCCESS, "Processed");
}
```

---

## Compliance Verification

Use these quick checks during code review:

| Check | Criterion |
|-------|-----------|
| ✅ Imports | No `org.web3j.*` in public packages |
| ✅ Types | Public APIs use only JDK + Brane types |
| ✅ Exceptions | web3j exceptions wrapped in `RpcException`/`RevertException` |
| ✅ Immutability | Fields are `final` or `AtomicReference` |
| ✅ Streams | Using `.toList()` not `.collect(Collectors.toList())` |
| ✅ Records | DTOs use `record` not POJOs |
