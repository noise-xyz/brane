# Java 21 Patterns Guide for Brane SDK

This document defines the preferred Java 21 patterns for the Brane SDK codebase. Follow these patterns when writing new code or refactoring existing code.

---

## 1. Pattern Matching for `instanceof`

### ❌ BAD - Old-style instanceof + cast
```java
if (value instanceof Address) {
    Address addr = (Address) value;
    return addr.value();
}

// Or in ternary (pattern matching not supported in ternary)
Address a = (value instanceof Address) ? (Address) value : new Address((String) value);
```

### ✅ GOOD - Pattern matching
```java
if (value instanceof Address addr) {
    return addr.value();
}

// For ternary replacement, use if-else
Address a;
if (value instanceof Address addr) {
    a = addr;
} else {
    a = new Address((String) value);
}
```

### ✅ BEST - Switch expression with pattern matching
```java
return switch (value) {
    case Address addr -> addr.value();
    case String s -> new Address(s).value();
    default -> throw new IllegalArgumentException("Expected Address or String");
};
```

**Existing good examples in codebase:**
- `TypedDataEncoder.java:375-431` - if-else with pattern matching
- `InternalAbi.java:687` - switch expression with pattern matching
- `AbiDecoder.java:101, 139, 154` - switch expressions

---

## 2. Switch Expressions (Arrow Syntax)

### ❌ BAD - Old-style switch with break
```java
switch (c) {
    case '"':
        buf.writeByte('\\');
        buf.writeByte('"');
        break;
    case '\\':
        buf.writeByte('\\');
        buf.writeByte('\\');
        break;
    default:
        buf.writeByte(c);
}
```

### ✅ GOOD - Arrow syntax (no break needed)
```java
switch (c) {
    case '"' -> {
        buf.writeByte('\\');
        buf.writeByte('"');
    }
    case '\\' -> {
        buf.writeByte('\\');
        buf.writeByte('\\');
    }
    default -> buf.writeByte(c);
}
```

### ✅ BEST - Switch expression (when returning value)
```java
String result = switch (type) {
    case "address" -> "0x" + addr.value();
    case "uint256" -> value.toString();
    default -> throw new IllegalArgumentException("Unknown type: " + type);
};
```

---

## 3. Null Handling with `Objects.requireNonNullElse()`

### ❌ BAD - Ternary null check (when default is constant)
```java
final Wei valueOrZero = value != null ? value : Wei.of(0);  // Use Wei.ZERO constant
final HexData dataOrEmpty = data != null ? data : HexData.EMPTY;
final Object[] args = args == null ? new Object[0] : args;  // Use EMPTY_ARGS constant
```

### ✅ GOOD - Objects.requireNonNullElse()
```java
final Wei valueOrZero = Objects.requireNonNullElse(value, Wei.ZERO);
final HexData dataOrEmpty = Objects.requireNonNullElse(data, HexData.EMPTY);
final Object[] args = Objects.requireNonNullElse(args, EMPTY_ARGS);  // Define EMPTY_ARGS constant
```

### ✅ BEST - Use static constants for empty defaults
```java
// Avoids allocation on every call
private static final Object[] EMPTY_ARGS = new Object[0];
private static final List<String> EMPTY_TOPICS = List.of();

// Zero allocation when args is non-null
final Object[] invocationArgs = Objects.requireNonNullElse(args, EMPTY_ARGS);

// List.of() already returns cached singleton, so this is fine too:
final List<String> topics = Objects.requireNonNullElse(inputTopics, List.of());
```

**Note**: `List.of()`, `Map.of()`, `Set.of()` return cached singletons, so constants aren't needed for these. But `new Object[0]` allocates each time.

### ⚠️ EXCEPTION - Keep ternary when default requires lazy evaluation
```java
// KEEP AS-IS - fetchGasPrice() should only be called when needed
final Wei gasPrice = withDefaults.gasPrice() != null
        ? withDefaults.gasPrice()
        : Wei.of(fetchGasPrice());

// Or use requireNonNullElseGet() for lazy evaluation
final Wei gasPrice = Objects.requireNonNullElseGet(
        withDefaults.gasPrice(),
        () -> Wei.of(fetchGasPrice()));
```

### ⚠️ EXCEPTION - Keep ternary for mapping (not defaulting)
```java
// KEEP AS-IS - This is a mapping, not a default
// Cannot use requireNonNullElse because we want null output for null input
String toValue = to != null ? to.value() : null;
```

---

## 4. Stream Operations

### ❌ BAD - Collectors.toList()
```java
return topics.stream().map(Hash::new).collect(Collectors.toList());
```

### ✅ GOOD - Stream.toList()
```java
return topics.stream().map(Hash::new).toList();
```

### ℹ️ NOTE - Collectors.joining() is acceptable
```java
// This is fine - Collectors.joining() with prefix/suffix has no simpler alternative
return components.stream()
        .map(TypeSchema::typeName)
        .collect(Collectors.joining(",", "(", ")"));
```

---

## 5. Local Variable Type Inference (`var`)

### ✅ GOOD - Use var when type is obvious from RHS
```java
var address = new Address("0x...");
var mapper = new ObjectMapper();
var logs = new ArrayList<LogEntry>();
var result = someMethod();  // Only if method name makes type obvious
```

### ❌ BAD - var obscures type
```java
var result = process(input);  // What type is result?
var x = getData();            // Unclear
```

### ✅ GOOD - Explicit type when it adds clarity
```java
TransactionReceipt result = process(input);  // Type documents intent
Address sender = getData();                   // Domain type is clearer
```

---

## 6. Record Patterns (Java 21)

### ✅ GOOD - Deconstruct records in pattern matching
```java
// When working with sealed hierarchies
return switch (schema) {
    case TypeSchema.UIntSchema(int width) -> decodeUInt(data, width);
    case TypeSchema.IntSchema(int width) -> decodeInt(data, width);
    case TypeSchema.AddressSchema() -> decodeAddress(data);
    default -> throw new IllegalArgumentException();
};
```

---

## Quick Reference Table

| Pattern | Bad | Good |
|---------|-----|------|
| Type check | `if (x instanceof Foo)` + cast | `if (x instanceof Foo f)` |
| Multi-type check | if-else chain with instanceof | `switch (x) { case Foo f -> ... }` |
| Null default (constant) | `x != null ? x : DEFAULT` | `Objects.requireNonNullElse(x, DEFAULT)` |
| Null default (allocating) | `requireNonNullElse(x, new T[0])` | Use static `EMPTY_T` constant |
| Null default (lazy) | Keep ternary or use `requireNonNullElseGet` | |
| Switch statement | `case X: ... break;` | `case X -> { ... }` |
| Switch return | switch + variable assignment | `var result = switch (x) { ... }` |
| Stream to list | `.collect(Collectors.toList())` | `.toList()` |
| Local types | Explicit when obvious | `var` when RHS shows type |
| Repeated construction | Inline `new Exception(...)` everywhere | Factory method `toException()` |
| Zero values | `Wei.of(0)`, `new HexData("0x")` | `Wei.ZERO`, `HexData.EMPTY` |
| JsonRpcError → RpcException | Manual `new RpcException(err.code()...)` | `RpcUtils.toRpcException(err)` |

---

## When NOT to Refactor

These patterns should be left as-is. Understanding **why** prevents incorrect refactoring.

### 1. Lazy Evaluation Ternaries - KEEP AS TERNARY

**Problem**: `Objects.requireNonNullElse()` eagerly evaluates ALL arguments before the method runs.

```java
// CURRENT - Lazy (CORRECT)
final Wei gasPrice = withDefaults.gasPrice() != null
        ? withDefaults.gasPrice()
        : Wei.of(fetchGasPrice());  // fetchGasPrice() only called if gasPrice is null

// WITH requireNonNullElse - Eager (WRONG!)
final Wei gasPrice = Objects.requireNonNullElse(
        withDefaults.gasPrice(),
        Wei.of(fetchGasPrice()));  // fetchGasPrice() ALWAYS called, even if gasPrice exists!
```

**Why?** Java evaluates all method arguments before invoking the method:
1. Evaluate `withDefaults.gasPrice()` → gets value (maybe non-null)
2. Evaluate `Wei.of(fetchGasPrice())` → **makes RPC call regardless**
3. Call `requireNonNullElse()` with both values
4. Return first if non-null, else second

**Impact**:
| Pattern | When gasPrice exists | When gasPrice is null |
|---------|---------------------|----------------------|
| Ternary | No RPC call | 1 RPC call |
| `requireNonNullElse` | **1 wasted RPC call** | 1 RPC call |

**Alternative** (if you must change): Use `requireNonNullElseGet()` with a Supplier:
```java
final Wei gasPrice = Objects.requireNonNullElseGet(
        withDefaults.gasPrice(),
        () -> Wei.of(fetchGasPrice()));  // Lambda only executed if null
```
But this is more verbose than the ternary, so **keep the ternary**.

**Rule**: If default involves a method call, computation, or side effect → **keep ternary**.

### 2. Mapping Ternaries - KEEP AS TERNARY

**Problem**: These transform non-null values but preserve null. `requireNonNullElse()` cannot do this.

```java
// CURRENT - Mapping pattern (CORRECT)
String toValue = to != null ? to.value() : null;  // null in → null out

// CANNOT use requireNonNullElse - it replaces null!
String toValue = Objects.requireNonNullElse(to?.value(), ???);  // No way to preserve null
```

**Why it's different from defaulting**:
| Pattern | Input null | Input non-null |
|---------|-----------|----------------|
| **Defaulting** | → default value | → input value |
| **Mapping** | → null | → transformed value |

**Examples in codebase**:
```java
// Mapping - KEEP AS-IS (null in → null out, transforms non-null)
withDefaults.to() != null ? withDefaults.to().value() : null
this.accessList = accessList == null ? null : List.copyOf(accessList)

// Defaulting - SHOULD change to requireNonNullElse with constant
value != null ? value : Wei.of(0)  →  Objects.requireNonNullElse(value, Wei.ZERO)
```

**Alternative** (more verbose):
```java
String toValue = Optional.ofNullable(to).map(Address::value).orElse(null);
```
But this is longer and less clear, so **keep the ternary**.

### 3. Collectors.joining() - ALREADY IDIOMATIC

**Problem**: There's no simpler alternative for `Collectors.joining()` with prefix/suffix.

```java
// This is FINE - no change needed
return components.stream()
        .map(TypeSchema::typeName)
        .collect(Collectors.joining(",", "(", ")"));

// String.join() cannot add prefix/suffix:
String.join(",", list);  // Only delimiter, no parentheses
```

**When to use each**:
| Need | Use |
|------|-----|
| Just delimiter | `String.join(",", list)` or `.toList()` then join |
| Delimiter + prefix + suffix | `Collectors.joining(",", "(", ")")` |

**Note**: Simple `.collect(Collectors.toList())` SHOULD be changed to `.toList()`.

### 4. Performance-Critical Indexed Loops - KEEP AS FOR LOOP

```java
// KEEP - Need index for array access, byte manipulation
for (int i = 0; i < data.length; i++) {
    buffer.put(data[i]);
}

// KEEP - Need index for parallel array access
for (int i = 0; i < blobs.size(); i++) {
    commitments[i] = kzg.commit(blobs.get(i));
}
```

These cannot be converted to streams without boxing overhead or losing clarity.

---

## 7. Factory Methods for Repeated Patterns

### ❌ BAD - Manual RpcException construction from JsonRpcError
```java
// DON'T do this - manual construction misses extractErrorData()
if (response.error() != null) {
    throw new RpcException(response.error().code(), response.error().message(),
            response.error().data() != null ? response.error().data().toString() : null);
}
```

### ✅ GOOD - ALWAYS use RpcUtils.toRpcException()
```java
// ALWAYS use the factory method for JsonRpcError → RpcException
if (response.hasError()) {
    throw RpcUtils.toRpcException(response.error());
}

// The factory method in RpcUtils.java:
public static RpcException toRpcException(final JsonRpcError err) {
    return new RpcException(err.code(), err.message(), extractErrorData(err.data()), (Long) null);
}
```

**Benefits**:
- Uses `extractErrorData()` to handle nested error data structures (Map, Array, Iterable)
- Single point of change for error handling logic
- Consistent error construction across codebase
- Easier to add logging, metrics, or error enrichment later

**Rule**: ANY time you convert `JsonRpcError` to `RpcException`, use `RpcUtils.toRpcException()`.

### Related: Use extractErrorData() for error data

```java
// ❌ BAD - doesn't handle nested structures
err.data() != null ? err.data().toString() : null

// ✅ GOOD - handles Map, Array, Iterable wrapping
RpcUtils.extractErrorData(err.data())
```

**Note**: If you're throwing `RpcException` from `JsonRpcError`, just use `toRpcException()` which calls `extractErrorData()` internally.

---

## 8. Negated instanceof - When NOT to Use Pattern Matching

### ✅ GOOD - Keep negated instanceof when value isn't used

```java
// Value isn't used after the check, just validation
if (!(provider instanceof WebSocketProvider)) {
    throw new UnsupportedOperationException("Requires WebSocket");
}
// provider is used via interface methods, not cast
provider.subscribe(...);
```

### ❌ BAD - Refactoring adds verbosity without benefit

```java
// Don't do this - more verbose, no benefit
if (provider instanceof WebSocketProvider ws) {
    ws.subscribe(...);  // But we might not even need ws-specific methods!
} else {
    throw new UnsupportedOperationException("Requires WebSocket");
}
```

**Rule**: Use pattern matching when you need the casted value. Keep negated instanceof for validation-only checks.

---

## 9. Use Static Constants for Common Values

### ❌ BAD - Creating new instances for well-known values
```java
value = Wei.of(0);                    // Allocates new BigInteger
data = new HexData("0x");             // Allocates new String
args = new Object[0];                 // Allocates new array
```

### ✅ GOOD - Use predefined constants
```java
value = Wei.ZERO;                     // Cached singleton
data = HexData.EMPTY;                 // Cached singleton
args = EMPTY_ARGS;                    // Private static final constant
```

### Available constants in Brane SDK

| Type | Constant | Value |
|------|----------|-------|
| `Wei` | `Wei.ZERO` | `BigInteger.ZERO` |
| `HexData` | `HexData.EMPTY` | `"0x"` |
| `List` | `List.of()` | Empty immutable list (JDK) |
| `Map` | `Map.of()` | Empty immutable map (JDK) |

**Note**: `List.of()` and `Map.of()` return cached singletons, so no need for custom constants.

### When to define your own constant
```java
// Arrays don't have built-in empty constant, define one:
private static final Object[] EMPTY_ARGS = new Object[0];

// Use in requireNonNullElse to avoid allocation:
final Object[] args = Objects.requireNonNullElse(input, EMPTY_ARGS);
```

---

## Codebase Examples

### Good Examples (follow these)
- `TypedDataEncoder.java` - Pattern matching in if-else chains
- `AbiDecoder.java` - Switch expressions with sealed types
- `Eip712TypeParser.java` - Switch expressions for type parsing
- `SmartGasStrategy.java:341` - Arrow syntax switch statement

### Refactored in REVIEW4 (now good examples)
- `WebSocketProvider.java:writeJsonValue` - Pattern matching switch expression
- `WebSocketProvider.java:writeEscapedString` - Arrow syntax switch
- `InternalAbi.java:548-552, 653-658` - Pattern matching for instanceof
- `DefaultSigner.java:100-101` - Uses `Objects.requireNonNullElse()`
- `CallResult.java:50-57` - Pattern matching for instanceof

### Refactored in REVIEW5 (now good examples)
- `InternalAbi.java:38` - Uses `EMPTY_ARGS` constant
- `ReadOnlyContractInvocationHandler.java:21` - Uses `EMPTY_ARGS` constant
- `SignerContractInvocationHandler.java:27,64` - Uses `EMPTY_ARGS` + pattern matching instanceof
- `MulticallInvocationHandler.java:32` - Uses `EMPTY_ARGS` constant
- `SmartGasStrategy.java:254,371` - Uses `RpcUtils.extractErrorData()`
- `RpcRetry.java:329` - Uses `RpcUtils.extractErrorData()`

### Good examples of constant usage
- `BlobTransactionRequest.java:194` - Uses `Wei.ZERO` ✓
- `DefaultSigner.java:435,441` - Uses `Wei.ZERO` ✓

### Still needs cleanup (REVIEW6)
- `WebSocketProvider.java:997-998` - Should use `RpcUtils.toRpcException()`
- `SignerContractInvocationHandler.java:68` - Should use `Wei.ZERO`
- `DefaultSigner.java:101` - Should use `Wei.ZERO`
- `TransactionRequest.java:162` - Should use `Wei.ZERO`
