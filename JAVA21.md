# Java 21 Patterns - Brane SDK

Quick reference for Java 21 patterns. Follow these conventions.

**Target:** Java 21 LTS. Preview features are disabled.

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

### Pattern variable scoping (non-obvious)

Pattern variables have flow-sensitive scoping:

```java
// Works with && - variable in scope when condition true
if (obj instanceof String s && s.length() > 5) {
    return s.toUpperCase();  // s in scope
}

// Works with || and negation - variable in scope when test "passes"
if (!(obj instanceof String s) || s.isEmpty()) {
    return "invalid";  // s NOT in scope here
}
// s IS in scope here - we only reach here if s is a non-empty String
process(s);

// Common pattern: early return with negated instanceof
if (!(response instanceof SuccessResponse success)) {
    return handleError(response);
}
// success is in scope for rest of method
return success.data();
```

### Negated instanceof - when NOT to use pattern matching

```java
// KEEP - value isn't used after check, just validation
if (!(provider instanceof WebSocketProvider)) {
    throw new UnsupportedOperationException("Requires WebSocket");
}
provider.subscribe(...);  // uses interface methods, not cast
```

### Switch expressions (exhaustive on sealed types and enums)

```java
// Sealed types - compiler enforces all cases
return switch (blockTag) {
    case BlockTag.Named n -> n.name();
    case BlockTag.Number n -> "0x" + Long.toHexString(n.number());
};  // compiler enforces exhaustiveness

// Enums - also exhaustive (compiler error if case missing)
enum TxType { LEGACY, EIP1559, EIP4844 }

int typeCode = switch (txType) {
    case LEGACY -> 0;
    case EIP1559 -> 2;
    case EIP4844 -> 3;
};  // add new enum value → compiler error until handled
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

### Nested record patterns

Deconstruct multiple levels in one pattern:

```java
// Nested deconstruction - extract inner fields directly
record Response(Result result, Metadata meta) {}
sealed interface Result permits Ok, Err {}
record Ok(String data, int count) implements Result {}
record Err(String message) implements Result {}

// One pattern extracts nested record fields
if (response instanceof Response(Ok(var data, var count), _)) {
    process(data, count);
}

// vs verbose alternative without nested patterns
if (response instanceof Response r && r.result() instanceof Ok ok) {
    var data = ok.data();
    var count = ok.count();
    process(data, count);
}

// In switch expressions
return switch (response) {
    case Response(Ok(var data, _), _) -> processData(data);
    case Response(Err(var msg), _) -> handleError(msg);
};
```

**Note:** `_` for unused bindings requires Java 22+. On Java 21, use named variables.

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

### Array fields - BROKEN equality by default

Records with array fields have broken `equals`/`hashCode` because arrays use reference equality:

```java
// BROKEN - arrays compare by reference, not contents
record Transaction(byte[] data) {}
new Transaction(bytes).equals(new Transaction(bytes));  // false!

// FIX 1: Wrap in immutable type
record Transaction(HexData data) {}  // HexData has proper equals

// FIX 2: Use List (worse performance, but works)
record Transaction(List<Byte> data) {}

// FIX 3: Override equals/hashCode (last resort)
record Transaction(byte[] data) {
    @Override public boolean equals(Object o) {
        return o instanceof Transaction t && Arrays.equals(data, t.data);
    }
    @Override public int hashCode() {
        return Arrays.hashCode(data);
    }
}
```

**Rule:** Never use raw `byte[]` in records. Use `HexData`, `Hash`, or wrapper types.

### When NOT to use records

Records are not always the right choice:

```java
// NO - need mutable state after construction
record Counter(int value) {}  // can't increment

// NO - need inheritance
record SpecialTx(...) extends BaseTx {}  // records are final

// NO - need lazy initialization
record Expensive(BigObject data) {}  // data computed on first access

// NO - circular references in construction
record Node(Node parent, List<Node> children) {}  // construction order issues

// NO - some fields shouldn't be in equals/hashCode
record CachedResult(String key, int hash, Object cached) {}  // cached is derived
```

**Use records when:**
- All fields are set at construction and never change
- No inheritance needed
- All fields should participate in equality
- No circular dependencies during construction

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

### SequencedCollection (Java 21's flagship feature)

New interfaces add first/last element access to ordered collections:

```java
// SequencedCollection<E> - ordered collections with efficient ends
list.getFirst();      // instead of list.get(0)
list.getLast();       // instead of list.get(list.size() - 1)
list.addFirst(e);     // push to front
list.addLast(e);      // push to back
list.removeFirst();   // poll from front
list.removeLast();    // poll from back
list.reversed();      // reverse view (not a copy)

// SequencedSet<E> - ordered sets (LinkedHashSet, TreeSet)
set.reversed();       // returns SequencedSet, not just Collection

// SequencedMap<K,V> - ordered maps (LinkedHashMap, TreeMap)
map.firstEntry();     // first key-value pair
map.lastEntry();      // last key-value pair
map.pollFirstEntry(); // remove and return first
map.pollLastEntry();  // remove and return last
map.putFirst(k, v);   // insert at front (LinkedHashMap only)
map.putLast(k, v);    // insert at back (LinkedHashMap only)
map.reversed();       // reverse view of map
map.sequencedKeySet();    // SequencedSet view of keys
map.sequencedValues();    // SequencedCollection view of values
map.sequencedEntrySet();  // SequencedSet view of entries
```

**Use cases:**
- Processing transactions in order with efficient head/tail access
- LRU caches with LinkedHashMap
- Ordered event streams

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

### Immutability semantics

```java
// List.of() - truly immutable, null-hostile (throws on null)
List<String> immutable = List.of("a", "b", "c");

// Arrays.asList() - fixed-size but elements are mutable, allows null
List<String> fixedSize = Arrays.asList("a", "b", null);
fixedSize.set(0, "x");  // OK - element mutation allowed
fixedSize.add("d");     // UnsupportedOperationException

// Defensive copy for untrusted input
List<T> safe = List.copyOf(untrustedList);   // null-hostile
Map<K,V> safe = Map.copyOf(untrustedMap);    // null-hostile
Set<T> safe = Set.copyOf(untrustedSet);      // null-hostile
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

| Method | Semantic | Mutability | Example |
|--------|----------|------------|---------|
| `of()` | Static factory for values | Usually immutable | `Wei.of(100)` |
| `copyOf()` | Defensive copy | Always immutable | `List.copyOf(items)` |
| `from()` | Conversion/parsing | Depends | `Address.from("0x...")` |
| `create()` | May have side effects | Depends | `WebSocketProvider.create(url)` |
| `builder()` | Staged construction | Mutable until `build()` | `RpcConfig.builder().build()` |
| `parse()` | String parsing | Usually immutable | `Instant.parse("...")` |
| `wrap()` | Lightweight view | Shares backing data | `ByteBuffer.wrap(bytes)` |

## Virtual Threads

### Default for I/O-bound work

```java
try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
    for (var request : requests) {
        exec.submit(() -> provider.send(request));
    }
}
```

### Pinning concerns

Virtual threads "pin" to carrier threads during:
- `synchronized` blocks (use `ReentrantLock` instead)
- Native methods (JNI calls)

```java
// AVOID synchronized in I/O code - pins carrier thread
synchronized (lock) {
    httpClient.send(request);  // blocks carrier thread
}

// PREFER ReentrantLock - virtual thread unmounts while waiting
private final ReentrantLock lock = new ReentrantLock();

lock.lock();
try {
    httpClient.send(request);
} finally {
    lock.unlock();
}
```

**When pinning matters:** Only at high concurrency (1000s of virtual threads). For typical SDK usage, pinning has negligible impact.

**Diagnostics:** `-Djdk.tracePinnedThreads=short` to log pinning events.

### ThreadLocal vs ScopedValue

`ThreadLocal` is problematic with virtual threads due to unbounded cardinality. Use `ScopedValue` (preview in 21, finalized in 23) for request-scoped data:

```java
// ThreadLocal - works but creates millions of instances with virtual threads
private static final ThreadLocal<RequestContext> CONTEXT = new ThreadLocal<>();

// ScopedValue (preview) - designed for virtual threads
private static final ScopedValue<RequestContext> CONTEXT = ScopedValue.newInstance();

// Usage - value automatically scoped to this call tree
ScopedValue.runWhere(CONTEXT, new RequestContext(requestId), () -> {
    // All code here (including spawned virtual threads) sees CONTEXT.get()
    processRequest();
});
```

**For Java 21 LTS without preview:** Continue using `ThreadLocal` but call `remove()` explicitly, or pass context as parameters.

### CPU-bound work: use platform threads

```java
// Virtual threads are for I/O, not CPU work
ExecutorService cpuPool = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors()
);
```

### Structured concurrency (preview)

Not available without `--enable-preview`. Use traditional try-with-resources pattern:

```java
// Available pattern on Java 21 LTS
try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
    var futures = tasks.stream()
        .map(t -> exec.submit(() -> process(t)))
        .toList();

    for (var future : futures) {
        results.add(future.get());  // blocks until complete
    }
}  // executor shutdown waits for all tasks
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

## Try-With-Resources

### Effectively final variables (Java 9+)

Existing effectively-final variables can be used directly:

```java
// YES - direct use of existing variable
FileInputStream fis = new FileInputStream(file);
try (fis) {
    return fis.readAllBytes();
}

// Also works with multiple resources
InputStream in = connection.getInputStream();
OutputStream out = new FileOutputStream(file);
try (in; out) {
    in.transferTo(out);
}
```

### Multi-resource declarations

```java
// Multiple resources in one try (closed in reverse order)
try (var conn = dataSource.getConnection();
     var stmt = conn.prepareStatement(sql);
     var rs = stmt.executeQuery()) {
    // process results
}
```

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

## Performance Considerations

### Records are not inherently faster

Records are syntactic sugar, not a performance optimization:
```java
// These have identical runtime performance
record Point(int x, int y) {}
class Point { final int x, y; ... }  // with equals/hashCode
```

Records generate the same bytecode as equivalent hand-written classes.

### Pattern matching switch uses invokedynamic

First invocation of pattern switch is slower (bootstrap overhead):
```java
// First call: ~10-100μs bootstrap
// Subsequent calls: fast (inlined)
switch (obj) {
    case String s -> ...
    case Integer i -> ...
}
```

**Impact:** Negligible for most code. Avoid in ultra-hot paths (millions/sec).

### instanceof patterns have no overhead

Pattern matching `instanceof` compiles to the same bytecode as manual cast:
```java
// These generate identical bytecode
if (obj instanceof String s) { ... }
if (obj instanceof String) { String s = (String) obj; ... }
```

### Virtual threads are cheap but not free

- **Creation:** ~1μs (vs ~1ms for platform threads)
- **Memory:** ~1KB initial stack (vs ~1MB for platform threads)
- **Context switch:** ~100ns when not pinned

**Don't spawn virtual threads for CPU-bound nano-operations** - the overhead exceeds the work.

## Preview Features

**Policy:** Do not use preview features (`--enable-preview`) in production code.

| Feature | Status | When Safe |
|---------|--------|-----------|
| String templates (JEP 430) | **Withdrawn** in Java 23 | Never - removed from language |
| Unnamed patterns `_` (JEP 443) | Finalized in Java 22 | Java 22+ |
| Unnamed variables `_` (JEP 456) | Finalized in Java 22 | Java 22+ |
| Structured concurrency | Preview in 21-24 | Not yet finalized |
| ScopedValue | Preview in 21-22, Finalized in 23 | Java 23+ |
| Primitive patterns in switch | Preview in 23+ | Not yet finalized |

**For Java 21 LTS:**
- Use named variables instead of `_` in patterns
- Use `ThreadLocal` + explicit `remove()` instead of `ScopedValue`
- Use try-with-resources instead of structured concurrency

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
| First/last element | `list.get(0)` / `list.get(size-1)` | `list.getFirst()` / `list.getLast()` |
| Records with byte[] | `record Tx(byte[] data)` | `record Tx(HexData data)` |
| Defensive copy | Trust mutable input | `List.copyOf()` / `Map.copyOf()` |
| Locking with VT | `synchronized` block | `ReentrantLock` |
