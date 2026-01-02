---
name: brane-code-review
description: Review Brane SDK code for correctness, Java 21 patterns, type safety, and architectural consistency. Use when reviewing PRs, checking code changes, or validating implementations against Brane standards.
---

# Brane SDK Code Review

## Architecture Overview

Brane is a type-safe Ethereum SDK for Java 21. The codebase follows these principles:
- **Zero external dependencies in public APIs** - Only JDK and Brane types exposed
- **Immutable value types** - Records for data, no mutable state in public types
- **Explicit error handling** - Typed exceptions, no silent failures
- **Thread-safe by default** - Safe for concurrent use without external synchronization

---

## Module Structure

| Module | Purpose | Dependencies |
|--------|---------|--------------|
| `brane-primitives` | Low-level Hex/RLP encoding | None (foundation) |
| `brane-core` | Types, ABI, Crypto, Models, Errors | `brane-primitives` |
| `brane-rpc` | JSON-RPC clients (PublicClient, WalletClient) | `brane-core` |
| `brane-contract` | High-level contract binding via dynamic proxy | `brane-core`, `brane-rpc` |
| `brane-examples` | Integration tests and usage examples | All modules |
| `brane-benchmark` | Performance benchmarks (may use external libs) | All modules |

**Dependency Rule**: Lower modules MUST NOT depend on higher modules.

---

## Reference Code (Study These First)

Before reviewing, understand these exemplary implementations:

- **Record + validation**: `brane-core/.../types/Address.java`
- **Exception hierarchy**: `brane-core/.../error/RpcException.java`
- **Complex client**: `brane-rpc/.../DefaultWalletClient.java`
- **Dynamic proxy**: `brane-contract/.../BraneContract.java`
- **Javadoc style**: `brane-contract/.../BraneContract.java`

---

## Java 21 Patterns (Required)

### Records for Data Types

```java
// GOOD - immutable record with validation in compact constructor
public record Address(@JsonValue String value) {
    public Address {
        Objects.requireNonNull(value, "address");
        if (!HEX.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid address: " + value);
        }
        value = value.toLowerCase(Locale.ROOT);
    }
}

// BAD - mutable class for simple data
public class Address {
    private String value;
    public void setValue(String v) { this.value = v; }
}
```

### Switch Expressions

```java
// GOOD - expression form returns value
return switch (type) {
    case UINT -> decodeUint(data);
    case ADDRESS -> decodeAddress(data);
    case BOOL -> decodeBool(data);
};

// BAD - statement form with breaks
switch (type) {
    case UINT:
        return decodeUint(data);
    case ADDRESS:
        return decodeAddress(data);
    default:
        throw new IllegalArgumentException();
}
```

### Pattern Matching

```java
// GOOD - pattern matching in instanceof
if (value instanceof Address addr) {
    return addr.value();
}

// BAD - separate cast
if (value instanceof Address) {
    Address addr = (Address) value;
    return addr.value();
}
```

### Text Blocks for Multi-line Strings

```java
// GOOD - text block
private static final String ABI_JSON = """
        [
          {"type": "function", "name": "transfer"}
        ]
        """;

// BAD - concatenation
private static final String ABI_JSON =
    "[\n" +
    "  {\"type\": \"function\"}\n" +
    "]";
```

### var for Obvious Types

```java
// GOOD - type obvious from RHS
var address = new Address("0x...");
var mapper = new ObjectMapper();
var logs = new ArrayList<LogEntry>();

// BAD - var obscures type
var result = process(input);  // What type is result?

// GOOD - explicit when unclear
TransactionReceipt result = process(input);
```

### Stream.toList() over Collectors

```java
// GOOD
return topics.stream().map(Hash::new).toList();

// BAD
return topics.stream().map(Hash::new).collect(Collectors.toList());
```

---

## Type Safety Rules

### Public API Types

Public methods/constructors/fields MUST only use:
- Java standard types: `String`, `BigInteger`, `List`, `Map`, `byte[]`
- Brane types: `Address`, `Hash`, `HexData`, `Wei`, `Transaction`, `TransactionReceipt`
- Brane exceptions: `RpcException`, `RevertException`, `AbiEncodingException`, `AbiDecodingException`

### Null Handling

```java
// GOOD - explicit null check with message
Objects.requireNonNull(provider, "provider");

// GOOD - Optional for truly optional values
public Optional<Long> nonceOpt() { ... }

// BAD - nullable without documentation
public Address getAddress() { return address; }  // Can this be null?

// BAD - Optional.get() without check
return optional.get();  // Use orElseThrow() instead
```

### Raw Types

```java
// GOOD - fully typed
List<LogEntry> logs = new ArrayList<>();
Map<String, Object> params = new LinkedHashMap<>();

// BAD - raw types
List logs = new ArrayList();
Map params = new HashMap();
```

---

## Exception Handling

### Exception Hierarchy

```
BraneException (base)
├── RpcException (JSON-RPC errors)
├── RevertException (contract reverts with decoded reason)
├── AbiEncodingException (encoding failures)
├── AbiDecodingException (decoding failures)
├── ChainMismatchException (wrong chain ID)
├── InvalidSenderException (signer mismatch)
└── TxnException (transaction failures)
```

### Exception Wrapping

```java
// GOOD - wrap with context, preserve cause
try {
    return provider.send(method, params);
} catch (IOException e) {
    throw new RpcException(-32000, "Connection failed: " + endpoint, null, e);
}

// BAD - lose cause
catch (IOException e) {
    throw new RpcException(-32000, "Connection failed", null);
}

// BAD - swallow exception
catch (IOException e) {
    return null;  // Silent failure
}
```

### Never Catch Generic Exception in Public API

```java
// BAD - catches too much
try {
    process(input);
} catch (Exception e) {
    // What exception? NPE? IllegalArgument? OutOfMemory?
}

// GOOD - catch specific exceptions
try {
    process(input);
} catch (RpcException e) {
    handleRpcError(e);
} catch (AbiDecodingException e) {
    handleDecodingError(e);
}
```

---

## Concurrency Patterns

### Thread-Safe Caching

```java
// GOOD - AtomicReference for lazy initialization
private final AtomicReference<Long> cachedChainId = new AtomicReference<>();

public long getChainId() {
    Long cached = cachedChainId.get();
    if (cached != null) {
        return cached;
    }
    long actual = fetchChainId();
    cachedChainId.set(actual);
    return actual;
}

// BAD - not thread-safe
private Long cachedChainId;

public long getChainId() {
    if (cachedChainId == null) {
        cachedChainId = fetchChainId();  // Race condition
    }
    return cachedChainId;
}
```

### Virtual Threads (When Applicable)

```java
// GOOD - virtual threads for I/O-bound work
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    futures.forEach(f -> executor.submit(f));
}

// BAD - platform thread pool for I/O
ExecutorService executor = Executors.newFixedThreadPool(10);
```

---

## Documentation Standards

### Javadoc Requirements

Public classes and methods MUST have Javadoc with:
- **Summary sentence** - What it does (imperative mood)
- **@param** - For each parameter
- **@return** - What is returned
- **@throws** - Each checked and significant unchecked exception

```java
/**
 * Sends a signed transaction to the network and waits for confirmation.
 *
 * <p>This method blocks until the transaction is mined or timeout is reached.
 *
 * @param request the transaction parameters (to, value, data, gas settings)
 * @param timeoutMillis maximum time to wait for confirmation
 * @param pollIntervalMillis interval between receipt checks
 * @return the transaction receipt with status and logs
 * @throws RpcException if the RPC call fails
 * @throws RevertException if the transaction reverts
 * @throws IllegalArgumentException if request is null
 */
TransactionReceipt sendTransactionAndWait(
        TransactionRequest request,
        long timeoutMillis,
        long pollIntervalMillis);
```

### Code Examples in Javadoc

Use `{@code}` for inline code, `<pre>{@code ...}</pre>` for blocks:

```java
/**
 * Binds a Java interface to a deployed contract.
 *
 * <p><strong>Example:</strong>
 * <pre>{@code
 * Erc20Contract usdc = BraneContract.bind(
 *         new Address("0xA0b8..."),
 *         abiJson,
 *         publicClient,
 *         walletClient,
 *         Erc20Contract.class);
 *
 * BigInteger balance = usdc.balanceOf(myAddress);
 * }</pre>
 */
```

---

## Review Checklist

### Correctness
- [ ] Logic matches documented behavior
- [ ] Edge cases handled (null, empty, boundary values)
- [ ] Thread safety for shared state
- [ ] Resources properly closed (try-with-resources)

### Java 21 Patterns
- [ ] Records for immutable data types
- [ ] Switch expressions over if-else chains
- [ ] Pattern matching for instanceof
- [ ] Text blocks for multi-line strings
- [ ] `var` only where type is obvious
- [ ] `Stream.toList()` not `.collect(Collectors.toList())`

### Type Safety
- [ ] No raw types (all generics fully typed)
- [ ] No `Optional.get()` - use `orElseThrow()`, `ifPresent()`, `map()`
- [ ] Null checks with `Objects.requireNonNull()`
- [ ] Public APIs use only JDK + Brane types

### Exceptions
- [ ] Exceptions preserve cause chain
- [ ] No swallowed exceptions (`catch (e) {}`)
- [ ] Specific exceptions, not generic `Exception`
- [ ] Documented with `@throws`

### Architecture
- [ ] Module dependencies flow downward (primitives → core → rpc → contract)
- [ ] No cycles between packages
- [ ] Public API stability (no breaking changes to public methods)

### Style
- [ ] No `public` fields unless `static final` constants
- [ ] Private constructor for utility classes
- [ ] Constants are `private static final`
- [ ] Meaningful variable names (not `x`, `temp`, `data`)

---

## Common Review Findings

### Finding: Missing Null Check

```java
// BAD
public void process(Address addr) {
    return addr.value();  // NPE if null
}

// GOOD
public void process(Address addr) {
    Objects.requireNonNull(addr, "addr");
    return addr.value();
}
```

### Finding: Mutable Return Type

```java
// BAD - caller can modify internal state
public List<LogEntry> getLogs() {
    return logs;
}

// GOOD - defensive copy or unmodifiable
public List<LogEntry> getLogs() {
    return List.copyOf(logs);
}
```

### Finding: Resource Leak

```java
// BAD - stream not closed
InputStream is = new FileInputStream(file);
byte[] data = is.readAllBytes();

// GOOD - try-with-resources
try (InputStream is = new FileInputStream(file)) {
    byte[] data = is.readAllBytes();
}
```

### Finding: Old Collection Patterns

```java
// BAD
List<String> result = new ArrayList<>();
for (Hash h : hashes) {
    result.add(h.value());
}
return result;

// GOOD
return hashes.stream().map(Hash::value).toList();
```

### Finding: Magic Numbers

```java
// BAD
if (data.length() != 42) { ... }

// GOOD
private static final int ADDRESS_HEX_LENGTH = 42;  // "0x" + 40 hex chars
if (data.length() != ADDRESS_HEX_LENGTH) { ... }
```

---

## Performance Considerations

### Avoid Unnecessary Allocations

```java
// BAD - allocates on every call
public String getValue() {
    return "0x" + Hex.encode(bytes);
}

// GOOD - cache if immutable and frequently accessed
private final String cachedValue;

public String getValue() {
    return cachedValue;
}
```

### Prefer Primitive Streams for Numeric Operations

```java
// GOOD
long total = values.stream().mapToLong(Wei::toLong).sum();

// BAD - boxing overhead
Long total = values.stream().map(Wei::value).reduce(0L, Long::sum);
```

### StringBuilder for String Concatenation in Loops

```java
// GOOD
var sb = new StringBuilder();
for (var item : items) {
    sb.append(item.value());
}

// BAD
String result = "";
for (var item : items) {
    result += item.value();  // Creates new String each iteration
}
```

---

## Security Considerations

### Input Validation

- Validate all external input (RPC responses, user parameters)
- Reject invalid hex strings early
- Check array bounds before access
- Validate address format before use

### Sensitive Data

- Never log private keys
- Sanitize transaction data in logs
- Use `LogSanitizer` for debug output

### Integer Overflow

```java
// GOOD - use BigInteger for Ethereum values
BigInteger value = new BigInteger(hexValue, 16);

// BAD - overflow risk with long
long value = Long.parseLong(hexValue, 16);  // Overflow if > Long.MAX_VALUE
```

---

## Verification Protocol (CRITICAL)

**Goal**: Every finding must be grounded in truth. No hallucinations. No vague claims.

### Finding Classification

Classify each finding into one of four tiers, each with different evidence requirements:

| Tier | Type | Evidence Required | Action |
|------|------|-------------------|--------|
| **T1** | **Confirmed Bug** | Failing test OR concrete execution trace proving the failure | Must fix before merge |
| **T2** | **Potential Bug** | Code path trace + specific scenario description | Requires investigation |
| **T3** | **Design Concern** | Explanation with rationale, reference to standards/patterns | Discuss with author |
| **T4** | **Suggestion** | Brief explanation of improvement | Optional enhancement |

### T1: Confirmed Bug - Evidence Requirements

For a finding to be classified as **Confirmed Bug**, you MUST provide ONE of:

**Option A: Failing Test**
```java
@Test
void shouldRejectNullAddress() {
    // This test demonstrates the bug: NPE is thrown instead of IllegalArgumentException
    var client = new DefaultClient(provider);

    // EXPECTED: IllegalArgumentException with message "address required"
    // ACTUAL: NullPointerException at line 47
    assertThrows(IllegalArgumentException.class, () -> client.call(null));
}
```

**Option B: Execution Trace**
```
EXECUTION TRACE:
1. User calls: client.call(null)
2. → DefaultClient.call(Address addr) at line 42
3. → [NO null check] proceeds to line 47
4. → addr.value() called at line 47
5. → NullPointerException thrown (unintended)

EXPECTED: IllegalArgumentException at step 3
ACTUAL: NPE at step 5
```

### T2: Potential Bug - Evidence Requirements

For **Potential Bug**, you MUST provide:

1. **Code Path Trace** - Show the exact execution path
2. **Trigger Scenario** - Specific conditions that trigger the issue
3. **Why Existing Tests Miss It** - Explain the coverage gap

```
POTENTIAL BUG: Race condition in cached chain ID

CODE PATH:
1. Thread A calls getChainId(), sees cachedChainId == null
2. Thread A enters if-block, starts fetchChainId() (slow RPC call)
3. Thread B calls getChainId(), sees cachedChainId == null (not set yet)
4. Thread B also enters if-block, starts fetchChainId()
5. Both threads make redundant RPC calls

TRIGGER SCENARIO:
- Multiple virtual threads calling getChainId() on cold start
- High latency RPC endpoint (>100ms)

WHY TESTS MISS IT:
- Unit tests are single-threaded
- Integration tests use fast local Anvil, hiding the race window
```

### T3: Design Concern - Evidence Requirements

For **Design Concern**, provide:

1. **What**: Clear description of the concern
2. **Why It Matters**: Impact on maintainability/readability/extensibility
3. **Reference**: Link to standard, pattern, or existing code that demonstrates preferred approach

```
DESIGN CONCERN: Method does too many things (violates SRP)

WHAT: sendTransactionAndWait() handles signing, sending, polling, and timeout

WHY IT MATTERS:
- Hard to test individual behaviors
- Timeout logic duplicated if user wants different polling strategy
- 150 lines in single method reduces readability

REFERENCE: See viem's separation: signTransaction() + sendRawTransaction() + waitForTransactionReceipt()
```

### T4: Suggestion - Evidence Requirements

Brief explanation only:

```
SUGGESTION: Use switch expression instead of if-else chain at line 87
Currently 15 lines, could be 8 with switch expression for better readability.
```

---

## Counter-Argument Requirement

**For every T1 and T2 finding**, you MUST include a counter-argument section that argues why it might NOT be a bug:

```
FINDING: Null pointer risk in processTransaction(tx)

EVIDENCE: [execution trace as above]

COUNTER-ARGUMENT (Why this might NOT be a bug):
- The public API `submitTransaction()` validates tx != null before calling processTransaction()
- processTransaction() is private and only called from submitTransaction()
- Therefore, null can never reach this code path in practice

VERDICT: After tracing all call sites, confirmed processTransaction() is ONLY called from
submitTransaction() which has null check. This is NOT a bug - the internal method can
safely assume non-null. Downgrading to T4 Suggestion: add @Nullable annotation or
Objects.requireNonNull for defensive coding.
```

**This forces you to:**
1. Consider if the issue is actually reachable
2. Check preconditions enforced elsewhere
3. Avoid false positives from analyzing code in isolation

---

## Existing Test Verification

**Before claiming any bug (T1 or T2)**, you MUST:

### Step 1: Find Related Tests

```bash
# Find tests for the class under review
./gradlew test --tests "*ClassName*" --dry-run

# Search for test methods covering the specific functionality
grep -r "methodName\|ClassName" */src/test/
```

### Step 2: Analyze Test Coverage

Ask yourself:
- Do tests cover this code path?
- If tests exist and pass, why don't they catch this bug?
- Is the test incorrect, or is my analysis wrong?

### Step 3: Document in Finding

```
EXISTING TEST ANALYSIS:
- Found: DefaultClientTest.java lines 45-67 test call() method
- Coverage gap: Tests only pass Address objects, never test null input
- Conclusion: Bug is real, tests have coverage gap
```

OR

```
EXISTING TEST ANALYSIS:
- Found: DefaultClientTest.shouldRejectNullAddress() at line 89
- Test passes and expects IllegalArgumentException
- Re-checking my analysis...

CORRECTION: I misread the code. Null check exists at line 41, I was looking at wrong method.
No bug here.
```

---

## Finding Report Template

Use this template for each finding:

```markdown
### [T1/T2/T3/T4] [Short Title]

**Location**: `module/path/to/File.java:LINE`

**Classification**: [Confirmed Bug | Potential Bug | Design Concern | Suggestion]

**Description**:
[1-2 sentences describing the issue]

**Evidence**:
[For T1: Failing test or execution trace]
[For T2: Code path + trigger scenario + test gap analysis]
[For T3: What + Why + Reference]
[For T4: Brief explanation]

**Existing Test Analysis**: (Required for T1/T2)
[What tests exist? Why don't they catch this?]

**Counter-Argument**: (Required for T1/T2)
[Why might this NOT be a bug? What would make my analysis wrong?]

**Verdict**:
[Final assessment after considering counter-argument]

**Recommended Fix**: (If applicable)
[Code snippet or description of fix]
```

---

## Review Process

### Phase 1: Discovery
1. Read the code changes carefully
2. Note potential issues without classifying yet
3. DO NOT jump to conclusions

### Phase 2: Verification
For each potential issue:
1. Trace the code path
2. Check existing tests
3. Formulate counter-argument
4. Classify into T1/T2/T3/T4

### Phase 3: Report
1. Use the finding template
2. Order by severity (T1 first, T4 last)
3. Be specific about locations (file:line)

### Phase 4: Self-Check
Before submitting review, ask:
- [ ] Did I actually trace code paths or assume?
- [ ] Did I check existing tests?
- [ ] Can I articulate counter-arguments for T1/T2 findings?
- [ ] Would I bet money on each T1 being a real bug?

---

## Anti-Patterns (What NOT to Do)

### ❌ Vague Claims
```
BAD: "This might have null pointer issues"
GOOD: "Line 47 dereferences `addr.value()` without null check. Trace shows
       null can reach here via path X→Y→Z"
```

### ❌ Assumed Bugs Without Tracing
```
BAD: "The cache isn't thread-safe"
GOOD: "The cache uses plain field without synchronization. Trace shows Thread A
       can see partial write from Thread B when [specific scenario]"
```

### ❌ Ignoring Test Evidence
```
BAD: Claim bug exists without checking if tests cover it
GOOD: "Tests exist but don't cover this case because [reason]"
```

### ❌ Single-Path Thinking
```
BAD: "This input validation is missing" (without checking all entry points)
GOOD: "Checked all 3 call sites: submitTx(), batchSubmit(), internal retry().
       Only retry() lacks validation, but it's only called after validation
       in submitTx(). Not a bug."
```

---

## Confidence Calibration

After completing a review, rate your confidence:

| Confidence | Meaning | When to Use |
|------------|---------|-------------|
| **High** | Would bet money on it | Failing test exists, or exhaustive trace completed |
| **Medium** | Likely correct but edge cases unclear | Traced main path, some branches unchecked |
| **Low** | Uncertain, needs more investigation | Pattern-matched without deep trace |

**Rule**: Only report T1 (Confirmed Bug) with HIGH confidence. If confidence is Medium/Low, downgrade to T2 or investigate further.
