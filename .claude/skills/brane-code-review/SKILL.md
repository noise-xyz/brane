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
