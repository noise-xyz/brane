# Java 21 Patterns - Brane SDK

Quick reference for Java 21 patterns. Follow these conventions.

## Sealed Types

Use `sealed` for closed hierarchies where exhaustive handling matters.

```java
// Define permitted subtypes explicitly
public sealed interface BlockTag permits BlockTag.Named, BlockTag.Number {
    record Named(String name) implements BlockTag {}
    record Number(long number) implements BlockTag {}
}

// Use non-sealed to allow extension
public sealed class BraneException extends RuntimeException permits ... {}
public non-sealed class TxnException extends BraneException {} // extensible
```

**When to use:**
- Exception hierarchies (enables exhaustive catch)
- API types with fixed variants (BlockTag, RlpItem)
- Domain models where invalid states should be unrepresentable

## Pattern Matching

### instanceof with binding

```java
// YES - pattern matching
if (obj instanceof Wei weiValue) {
    return weiValue.toBigInteger();
}

// NO - old style cast
if (obj instanceof Wei) {
    Wei weiValue = (Wei) obj;
}
```

### Negated instanceof - when NOT to use pattern matching

```java
// KEEP - value isn't used after check, just validation
if (!(provider instanceof WebSocketProvider)) {
    throw new UnsupportedOperationException("Requires WebSocket");
}
provider.subscribe(...);  // uses interface methods, not cast
```

### Switch expressions (exhaustive on sealed types)

```java
return switch (blockTag) {
    case BlockTag.Named n -> n.name();
    case BlockTag.Number n -> "0x" + Long.toHexString(n.number());
};  // compiler enforces exhaustiveness
```

### Guarded patterns (`when` clause)

```java
return switch (value) {
    case String s when s.startsWith("0x") -> s;
    case String s -> "0x" + s;
    case byte[] bytes -> Hex.encode(bytes);
    default -> throw new IllegalArgumentException();
};
```

### Record patterns (deconstruction)

```java
return switch (schema) {
    case TypeSchema.UIntSchema(int width) -> decodeUInt(data, width);
    case TypeSchema.IntSchema(int width) -> decodeInt(data, width);
    case TypeSchema.AddressSchema() -> decodeAddress(data);
    default -> throw new IllegalArgumentException();
};
```

## Records

### Compact constructor for validation

```java
public record RlpList(List<RlpItem> items) implements RlpItem {
    public RlpList {  // compact constructor - no params
        Objects.requireNonNull(items);
        items = List.copyOf(items);  // defensive copy
    }
}
```

### Local records for ad-hoc grouping

```java
void process() {
    record GasEstimate(BigInteger gasLimit, BigInteger maxFee) {}
    GasEstimate estimate = new GasEstimate(gas, fee);
}
```

### Jackson annotations

```java
@JsonIgnoreProperties(ignoreUnknown = true)
public record JsonRpcResponse(
    String jsonrpc,
    @Nullable Object result,
    @Nullable JsonRpcError error,
    @Nullable Object id
) {}
```

## Null Handling

### Use `Objects.requireNonNullElse` for constant defaults

```java
// YES
final Wei value = Objects.requireNonNullElse(input, Wei.ZERO);
final Object[] args = Objects.requireNonNullElse(input, EMPTY_ARGS);

// NO - verbose ternary
final Wei value = input != null ? input : Wei.ZERO;
```

### KEEP ternary for lazy evaluation

```java
// KEEP - fetchGasPrice() should only be called when needed
final Wei gasPrice = withDefaults.gasPrice() != null
        ? withDefaults.gasPrice()
        : Wei.of(fetchGasPrice());

// requireNonNullElse evaluates BOTH args eagerly - wastes RPC call!
// Alternative if you must change:
final Wei gasPrice = Objects.requireNonNullElseGet(
        withDefaults.gasPrice(),
        () -> Wei.of(fetchGasPrice()));  // but ternary is cleaner
```

### KEEP ternary for mapping (null in -> null out)

```java
// KEEP - this is mapping, not defaulting
String toValue = to != null ? to.value() : null;  // null preserved

// Cannot use requireNonNullElse - it replaces null!
```

### Use `@Nullable` for optional fields/params

```java
public record Response(String data, @Nullable String error) {}
```

### `Optional` for return types only

```java
// YES - return type
public Optional<Address> findAddress(String name) { }

// NO - parameter (use @Nullable instead)
public void process(Optional<String> name) { }
```

## Static Constants

### Use predefined constants for common values

```java
// YES
value = Wei.ZERO;
data = HexData.EMPTY;
args = EMPTY_ARGS;  // private static final Object[] EMPTY_ARGS = new Object[0];

// NO - allocates each time
value = Wei.of(0);
data = new HexData("0x");
args = new Object[0];
```

**Available constants:**
| Type | Constant |
|------|----------|
| `Wei` | `Wei.ZERO` |
| `HexData` | `HexData.EMPTY` |
| `List` | `List.of()` (JDK cached singleton) |
| `Map` | `Map.of()` (JDK cached singleton) |

## Collections

### SequencedCollection methods (Java 21)

```java
list.getFirst();    // instead of list.get(0)
list.getLast();     // instead of list.get(list.size() - 1)
list.reversed();    // reverse view
```

### Stream.toList()

```java
// YES
return items.stream().map(Item::name).toList();

// NO
return items.stream().map(Item::name).collect(Collectors.toList());

// OK - Collectors.joining() with prefix/suffix has no simpler alternative
return components.stream()
        .map(TypeSchema::typeName)
        .collect(Collectors.joining(",", "(", ")"));
```

## Factory Methods

### Use `RpcUtils.toRpcException()` for error conversion

```java
// YES - uses extractErrorData() for nested structures
if (response.hasError()) {
    throw RpcUtils.toRpcException(response.error());
}

// NO - manual construction misses error data extraction
throw new RpcException(err.code(), err.message(), err.data().toString());
```

### Naming conventions

| Method | Use Case | Example |
|--------|----------|---------|
| `create()` | Simple construction | `WebSocketProvider.create(url)` |
| `builder()` | Many optional params | `RpcConfig.builder().build()` |
| `from()` | Conversion/parsing | `Address.from("0x...")` |
| `of()` | Value objects | `CallRequest.of(to, data)` |

## Virtual Threads

### Default for I/O-bound work

```java
try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
    for (var request : requests) {
        exec.submit(() -> provider.send(request));
    }
}
```

### Pinning concerns - avoid in hot paths

```java
// AVOID synchronized in I/O code - pins carrier thread
synchronized (lock) {
    httpClient.send(request);
}

// PREFER ReentrantLock
lock.lock();
try {
    httpClient.send(request);
} finally {
    lock.unlock();
}
```

**Pinning sources:** `synchronized` blocks, native methods (JNI)
**Monitor:** `jdk.tracePinnedThreads` in production

### CPU-bound work: use platform threads

```java
ExecutorService cpuPool = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors()
);
```

## Text Blocks

```java
String json = """
    {
        "method": "eth_call",
        "params": [%s]
    }
    """.formatted(params);
```

- Trailing `\` removes newline
- `\s` preserves trailing whitespace
- Indentation stripped to leftmost non-whitespace

## Local Variable Type Inference

```java
// YES - type obvious from RHS
var address = new Address("0x...");
var mapper = new ObjectMapper();
var logs = new ArrayList<LogEntry>();

// NO - type unclear
var result = process(input);  // What type?

// YES - explicit type adds clarity for domain objects
Address sender = getData();
TransactionReceipt result = process(input);
```

## Anti-Patterns

```java
// NO: Optional.get()
optional.get();           // throws NoSuchElementException
optional.orElseThrow();   // explicit, same behavior - USE THIS

// NO: Raw types
List items = new ArrayList();
List<String> items = new ArrayList<>();  // USE THIS

// NO: Swallowing exceptions
catch (Exception e) {}
catch (Exception e) { log.warn("...", e); }  // at minimum log

// NO: public fields (except static final)
public String name;
public static final int MAX = 100;  // OK
```

## Preview Features

**Policy:** Do not use preview features (`--enable-preview`) in production code.

Avoid until finalized:
- String templates (JEP 430)
- Unnamed patterns `_` (JEP 443)
- Structured concurrency (JEP 453)

## Quick Reference

| Pattern | Bad | Good |
|---------|-----|------|
| Type check | `instanceof Foo` + cast | `instanceof Foo f` |
| Multi-type | if-else chain | `switch (x) { case Foo f -> }` |
| Null default (constant) | `x != null ? x : DEFAULT` | `requireNonNullElse(x, DEFAULT)` |
| Null default (lazy) | - | Keep ternary |
| Null mapping | - | Keep ternary (`x != null ? x.foo() : null`) |
| Switch statement | `case X: break;` | `case X -> { }` |
| Stream to list | `.collect(Collectors.toList())` | `.toList()` |
| Zero values | `Wei.of(0)` | `Wei.ZERO` |
| Error conversion | Manual `new RpcException(...)` | `RpcUtils.toRpcException(err)` |
