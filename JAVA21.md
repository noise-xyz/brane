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

Use **canonical constructor** when you need different parameter names or complex transformations:
```java
public record HexData(byte[] bytes) {
    // Canonical - when parameter processing is complex
    public HexData(byte[] bytes) {
        this.bytes = bytes.clone();  // different name/transformation
    }
}
```

### Records with interfaces

```java
public record Transfer(Address from, Address to, Wei amount)
    implements Comparable<Transfer>, Serializable {

    @Override
    public int compareTo(Transfer o) {
        return amount.compareTo(o.amount);
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

### Array fields - BROKEN equality

Records with array fields have broken `equals`/`hashCode` because arrays use reference equality:

```java
// BROKEN - arrays compare by reference, not contents
record Transaction(byte[] data) {}
new Transaction(bytes).equals(new Transaction(bytes));  // false!

// FIX: Wrap in immutable type
record Transaction(HexData data) {}  // HexData has proper equals
```

**Rule:** Never use raw `byte[]` in records. Use `HexData`, `Hash`, or wrapper types.

### When NOT to use records

```java
// NO - need mutable state, inheritance, lazy init, or selective equals
record Counter(int value) {}           // can't increment
record SpecialTx() extends BaseTx {}   // records are final
record Expensive(BigObject data) {}    // can't lazy-init
record CachedResult(String key, Object cached) {}  // cached shouldn't be in equals
```

## Pattern Matching

### instanceof with binding (null-safe!)

```java
// Safe! Returns false for null, no NPE
String s = null;
if (s instanceof String str) { }  // false, no exception

// This is WHY pattern matching is safer than manual casting
if (obj instanceof Wei weiValue) {
    return weiValue.toBigInteger();
}
```

### Pattern variable scoping

Pattern variables have flow-sensitive scoping:

```java
// Works with && - variable in scope when condition true
if (obj instanceof String s && s.length() > 5) {
    return s.toUpperCase();
}

// Common pattern: early return with negated instanceof
if (!(response instanceof SuccessResponse success)) {
    return handleError(response);
}
// success is in scope for rest of method
return success.data();
```

### Switch expressions (exhaustive)

```java
// Sealed types - compiler enforces all cases
return switch (blockTag) {
    case BlockTag.Named n -> n.name();
    case BlockTag.Number n -> "0x" + Long.toHexString(n.number());
};
```

### Switch dominance rules - ORDER MATTERS

More specific patterns must come before general ones:

```java
// COMPILE ERROR - String dominates the guarded pattern
switch (obj) {
    case String s -> "any string";
    case String s when s.isEmpty() -> "empty";  // unreachable!
}

// CORRECT - specific patterns first
switch (obj) {
    case String s when s.isEmpty() -> "empty";
    case String s -> "any string";
}
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

```java
record Response(Result result, Metadata meta) {}
sealed interface Result permits Ok, Err {}
record Ok(String data, int count) implements Result {}
record Err(String message) implements Result {}

// One pattern extracts nested record fields
if (response instanceof Response(Ok(var data, var count), _)) {
    process(data, count);
}

// In switch expressions
return switch (response) {
    case Response(Ok(var data, _), _) -> processData(data);
    case Response(Err(var msg), _) -> handleError(msg);
};
```

**Note:** `_` for unused bindings requires Java 22+. On Java 21, use named variables.

## Null & Optional Handling

### `requireNonNullElse` for constant defaults

```java
// YES - constant default
final Wei value = Objects.requireNonNullElse(input, Wei.ZERO);

// NO - verbose ternary
final Wei value = input != null ? input : Wei.ZERO;
```

### KEEP ternary for lazy evaluation or mapping

```java
// KEEP - fetchGasPrice() should only be called when needed
final Wei gasPrice = withDefaults.gasPrice() != null
        ? withDefaults.gasPrice()
        : Wei.of(fetchGasPrice());

// KEEP - this is mapping, not defaulting (null in -> null out)
String toValue = to != null ? to.value() : null;
```

### `Optional.or()` for chained fallbacks

```java
// YES - lazy chain, each supplier called only if previous is empty
Optional<Config> config = loadFromFile()
    .or(() -> loadFromEnv())
    .or(() -> loadDefaults());

// NO - eager evaluation, calls ALL methods
Optional<Config> config = loadFromFile()
    .orElse(loadFromEnv().orElse(loadDefaults()));
```

### `Optional` for return types only

```java
// YES - return type
public Optional<Address> findAddress(String name) { }

// NO - parameter (use @Nullable instead)
public void process(Optional<String> name) { }
```

## Collections & Streams

### SequencedCollection (Java 21)

```java
// SequencedCollection<E> - ordered collections with efficient ends
list.getFirst();      // instead of list.get(0)
list.getLast();       // instead of list.get(list.size() - 1)
list.addFirst(e);     // push to front
list.reversed();      // reverse view (not a copy)

// SequencedMap<K,V> - ordered maps (LinkedHashMap, TreeMap)
map.firstEntry();
map.lastEntry();
map.putFirst(k, v);   // LinkedHashMap only
map.sequencedKeySet();
```

### `Stream.toList()` returns UNMODIFIABLE list

```java
var list = stream.toList();
list.add("x");  // UnsupportedOperationException!

// If you need mutable:
var list = new ArrayList<>(stream.toList());
// or
var list = stream.collect(Collectors.toCollection(ArrayList::new));
```

### `Predicate.not()` for clean negation

```java
// YES - static import Predicate.not
items.stream().filter(not(String::isBlank))

// NO - verbose lambda
items.stream().filter(s -> !s.isBlank())
```

### Immutability semantics

```java
// List.of() - truly immutable, null-hostile
List<String> immutable = List.of("a", "b", "c");

// Arrays.asList() - fixed-size but elements mutable, allows null
List<String> fixedSize = Arrays.asList("a", "b", null);
fixedSize.set(0, "x");  // OK
fixedSize.add("d");     // UnsupportedOperationException

// Defensive copy for untrusted input
List<T> safe = List.copyOf(untrustedList);
```

## Utility Methods

### `Math.clamp()` (Java 21)

```java
// YES - Java 21
int bounded = Math.clamp(value, 0, 100);

// NO - verbose
int bounded = Math.max(0, Math.min(value, 100));
```

### `Objects.checkIndex()` for bounds checking

```java
// YES - optimized, clear semantics, throws IndexOutOfBoundsException
Objects.checkIndex(index, array.length);
Objects.checkFromToIndex(from, to, array.length);

// NO - manual bounds check
if (index < 0 || index >= array.length) throw new IndexOutOfBoundsException();
```

### `Path.of()` over `Paths.get()`

```java
// YES - Java 11+, preferred
Path p = Path.of("/foo/bar");

// NO - older API
Path p = Paths.get("/foo/bar");
```

## I/O Utilities

### `InputStream.transferTo()` and `readAllBytes()`

```java
// YES - clean I/O
try (var in = url.openStream(); var out = new FileOutputStream(file)) {
    in.transferTo(out);
}
byte[] bytes = inputStream.readAllBytes();

// NO - manual loop
byte[] buffer = new byte[8192];
int read;
while ((read = in.read(buffer)) != -1) { out.write(buffer, 0, read); }
```

### Other I/O conveniences

```java
Files.readString(path);           // read file to String
Files.writeString(path, content); // write String to file
Files.mismatch(path1, path2);     // find first differing byte, -1 if equal
Arrays.mismatch(arr1, arr2);      // same for arrays
```

## Virtual Threads

### Default for I/O-bound work

```java
try (var exec = Executors.newVirtualThreadPerTaskExecutor()) {
    for (var request : requests) {
        exec.submit(() -> provider.send(request));
    }
}
```

### Naming patterns for debugging

```java
// Named virtual threads - helpful in thread dumps
ThreadFactory factory = Thread.ofVirtual()
    .name("rpc-worker-", 0)  // rpc-worker-0, rpc-worker-1, ...
    .factory();

// One-off virtual thread
Thread.startVirtualThread(() -> doWork());
```

### Pinning concerns

Virtual threads "pin" to carrier threads during `synchronized` blocks:

```java
// AVOID synchronized in I/O code - pins carrier thread
synchronized (lock) {
    httpClient.send(request);
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

**When pinning matters:** Only at high concurrency (1000s of virtual threads).

**Diagnostics:** `-Djdk.tracePinnedThreads=short`

### ThreadLocal caution

`ThreadLocal` creates instances per virtual thread. With millions of virtual threads, this explodes memory. Options:
- Pass context as parameters
- Use `ScopedValue` (preview in 21, finalized in 23)
- Call `remove()` explicitly

### CPU-bound work: use platform threads

```java
ExecutorService cpuPool = Executors.newFixedThreadPool(
    Runtime.getRuntime().availableProcessors()
);
```

## Syntax & Inference

### Text blocks

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

### Local variable type inference

```java
// YES - type obvious from RHS
var address = new Address("0x...");
var mapper = new ObjectMapper();

// NO - type unclear
var result = process(input);

// YES - explicit type for domain objects
Address sender = getData();
```

### Try-with-resources

```java
// Effectively final variables can be used directly
InputStream in = connection.getInputStream();
OutputStream out = new FileOutputStream(file);
try (in; out) {
    in.transferTo(out);
}

// Multiple resources (closed in reverse order)
try (var conn = dataSource.getConnection();
     var stmt = conn.prepareStatement(sql);
     var rs = stmt.executeQuery()) {
    // process
}
```

### Static members in inner classes (Java 16+)

```java
class Outer {
    class Inner {
        static final int CONSTANT = 42;  // Now allowed!
        static void helper() { }          // Now allowed!
    }
}
```

## Gotchas & Anti-Patterns

| Anti-Pattern | Fix |
|--------------|-----|
| `optional.get()` | `optional.orElseThrow()` - explicit about throwing |
| Raw types `List items` | `List<String> items` |
| Swallowing exceptions `catch (e) {}` | At minimum log: `log.warn("...", e)` |
| Public fields | Use accessors (except `static final`) |
| `byte[]` in records | Use `HexData`, `Hash` wrapper types |
| `synchronized` with virtual threads | Use `ReentrantLock` |

### Helpful NPE messages (Java 14+, default in 21)

```java
// Old: NullPointerException
// New: Cannot invoke "String.length()" because "str" is null
```

If disabled: `-XX:+ShowCodeDetailsInExceptionMessages`

## Factory Method Naming

| Method | Semantic | Example |
|--------|----------|---------|
| `of()` | Static factory, usually immutable | `Wei.of(100)` |
| `from()` | Conversion/parsing | `Address.from("0x...")` |
| `copyOf()` | Defensive copy, always immutable | `List.copyOf(items)` |
| `create()` | May have side effects | `WebSocketProvider.create(url)` |
| `builder()` | Staged construction | `RpcConfig.builder().build()` |
| `parse()` | String parsing | `Instant.parse("...")` |

## Performance Notes

- **Records** are syntactic sugar, not faster than equivalent classes
- **Pattern switch** has ~10-100μs bootstrap on first call (negligible for most code)
- **`instanceof` patterns** compile to same bytecode as manual cast - no overhead
- **Virtual threads:** ~1μs creation, ~1KB stack. Don't spawn for nano-operations

## Preview Features

**Policy:** Do not use preview features in production code.

| Feature | Status |
|---------|--------|
| String templates | **Withdrawn** in Java 23 - never use |
| Unnamed patterns/variables `_` | Finalized in Java 22 |
| Structured concurrency | Preview through Java 24 |
| ScopedValue | Finalized in Java 23 |

## Quick Reference

| Pattern | Bad | Good |
|---------|-----|------|
| Type check | `instanceof Foo` + cast | `instanceof Foo f` |
| Multi-type | if-else chain | `switch (x) { case Foo f -> }` |
| Null default | `x != null ? x : DEFAULT` | `requireNonNullElse(x, DEFAULT)` |
| Chained optional | `.orElse(load())` | `.or(() -> load())` |
| Stream to list | `.collect(toList())` | `.toList()` |
| Negate predicate | `s -> !s.isBlank()` | `not(String::isBlank)` |
| Bounds check | manual if/throw | `Objects.checkIndex()` |
| Clamp value | `max(min, min(val, max))` | `Math.clamp(val, min, max)` |
| First/last | `get(0)` / `get(size-1)` | `getFirst()` / `getLast()` |
| File path | `Paths.get()` | `Path.of()` |
| Copy stream | manual loop | `in.transferTo(out)` |
| Read file | manual loop | `readAllBytes()` / `readString()` |
