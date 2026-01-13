# Brane SDK Refactoring Plan

This document identifies boilerplate patterns in the Brane SDK codebase and proposes refactoring strategies to reduce code duplication and improve maintainability.

## Summary

| Boilerplate Type | Occurrences | Files Affected | Est. Line Reduction | Priority |
|------------------|-------------|----------------|---------------------|----------|
| RPC method wrapping | 59 | 3 | ~200 lines | High |
| Client delegation | 50+ methods | 2 | ~50 lines | High |
| Builder method repetition | 20+ methods | 3 | ~100 lines | High |
| ABI type records | 7 files | 7 | ~100 lines | Medium |
| Contract invocation handlers | 2 files | 2 | ~50 lines | Medium |
| Test setup boilerplate | 10+ instances | Multiple | ~80 lines | Medium |
| Null-check exception pattern | 15+ | Multiple | ~30 lines | Low |
| Validation logic | 133+ calls | Multiple | ~40 lines | Low |

**Total Estimated Reduction:** 600-800 lines (~12-15% of core module)

---

## 1. RPC Method Wrapping (High Priority)

### Problem

Every RPC method in `DefaultReader`, `DefaultSigner`, and `DefaultTester` follows the same pattern:

```java
// DefaultReader.java - This pattern repeats 59+ times
public BigInteger chainId() {
    ensureOpen();
    final JsonRpcResponse response = sendWithRetry("eth_chainId", List.of());
    final Object result = response.result();
    if (result == null) {
        throw new io.brane.core.error.RpcException(
                0, "eth_chainId returned null", (String) null, (Throwable) null);
    }
    return io.brane.rpc.internal.RpcUtils.decodeHexBigInteger(result.toString());
}

public BigInteger getBalance(final Address address) {
    ensureOpen();
    final JsonRpcResponse response = sendWithRetry("eth_getBalance", List.of(address.value(), "latest"));
    final Object result = response.result();
    if (result == null) {
        throw new io.brane.core.error.RpcException(
                0, "eth_getBalance returned null", (String) null, (Throwable) null);
    }
    return io.brane.rpc.internal.RpcUtils.decodeHexBigInteger(result.toString());
}
```

### Files Affected

- `brane-rpc/src/main/java/io/brane/rpc/DefaultReader.java` (711 lines)
- `brane-rpc/src/main/java/io/brane/rpc/DefaultSigner.java` (608 lines)
- `brane-rpc/src/main/java/io/brane/rpc/DefaultTester.java` (736 lines)

### Proposed Solution

Create a generic `RpcCall<T>` utility with functional composition:

```java
// New utility class
public final class RpcInvoker {

    private final Supplier<JsonRpcResponse> sender;

    public <T> T call(String method, List<Object> params, Function<String, T> decoder) {
        ensureOpen();
        final JsonRpcResponse response = sendWithRetry(method, params);
        final Object result = response.result();
        if (result == null) {
            throw RpcException.fromNullResult(method);
        }
        return decoder.apply(result.toString());
    }

    public <T> T callNullable(String method, List<Object> params, Function<String, T> decoder) {
        ensureOpen();
        final JsonRpcResponse response = sendWithRetry(method, params);
        final Object result = response.result();
        return result != null ? decoder.apply(result.toString()) : null;
    }
}

// Usage - reduces each method from 10 lines to 1 line
public BigInteger chainId() {
    return rpc.call("eth_chainId", List.of(), RpcUtils::decodeHexBigInteger);
}

public BigInteger getBalance(Address address) {
    return rpc.call("eth_getBalance", List.of(address.value(), "latest"), RpcUtils::decodeHexBigInteger);
}
```

### Estimated Impact

- **Lines reduced:** ~200
- **Effort:** 4-5 hours
- **Risk:** Low (internal refactor, no API changes)

---

## 2. Client Delegation (High Priority)

### Problem

`DefaultSigner` delegates 20+ methods to `DefaultReader` with zero business logic. `DefaultTester` does the same to `DefaultSigner`.

```java
// DefaultSigner.java - 50+ lines of pure delegation
@Override
public BigInteger chainId() {
    return reader.chainId();
}

@Override
public BigInteger getBalance(final Address address) {
    return reader.getBalance(address);
}

@Override
public HexData getCode(final Address address) {
    return reader.getCode(address);
}

// ... repeats for 20+ methods
```

```java
// DefaultTester.java - Another 50+ lines of identical delegation
@Override
public BigInteger chainId() {
    return signer.chainId();
}

@Override
public BigInteger getBalance(final Address address) {
    return signer.getBalance(address);
}

// ... repeats for 20+ methods
```

### Files Affected

- `brane-rpc/src/main/java/io/brane/rpc/DefaultSigner.java` (lines 84-156)
- `brane-rpc/src/main/java/io/brane/rpc/DefaultTester.java` (lines 102-200+)

### Proposed Solutions

**Option A: Lombok `@Delegate`**
```java
public class DefaultSigner implements Brane.Signer {
    @Delegate(types = Brane.Reader.class)
    private final DefaultReader reader;

    // Only implement Signer-specific methods
}
```

**Option B: Java Dynamic Proxy**
```java
public class DelegatingClient {
    public static <T> T createDelegate(Class<T> iface, Object delegate) {
        return (T) Proxy.newProxyInstance(
            iface.getClassLoader(),
            new Class<?>[] { iface },
            (proxy, method, args) -> method.invoke(delegate, args)
        );
    }
}
```

**Option C: Inheritance (if appropriate)**
```java
public class DefaultSigner extends DefaultReader implements Brane.Signer {
    // Only implement Signer-specific methods
}
```

### Estimated Impact

- **Lines reduced:** ~100
- **Effort:** 2 hours
- **Risk:** Low

---

## 3. Builder Method Repetition (High Priority)

### Problem

`Eip1559Builder`, `LegacyBuilder`, and `Eip4844Builder` have nearly identical setter methods:

```java
// Eip1559Builder.java (lines 50-77)
public Eip1559Builder from(final Address address) {
    this.from = address;
    return this;
}

public Eip1559Builder to(final Address address) {
    this.to = address;
    return this;
}

public Eip1559Builder value(final Wei value) {
    this.value = value;
    return this;
}

public Eip1559Builder data(final HexData data) {
    this.data = data;
    return this;
}

public Eip1559Builder nonce(final BigInteger nonce) {
    this.nonce = nonce;
    return this;
}

public Eip1559Builder gasLimit(final BigInteger gasLimit) {
    this.gasLimit = gasLimit;
    return this;
}
```

```java
// LegacyBuilder.java (lines 43-76) - Nearly identical
public LegacyBuilder from(final Address address) {
    this.from = address;
    return this;
}

public LegacyBuilder to(final Address address) {
    this.to = address;
    return this;
}
// ... same pattern continues
```

### Files Affected

- `brane-core/src/main/java/io/brane/core/builder/Eip1559Builder.java` (145 lines)
- `brane-core/src/main/java/io/brane/core/builder/LegacyBuilder.java` (110 lines)
- `brane-core/src/main/java/io/brane/core/builder/Eip4844Builder.java` (312 lines)

### Proposed Solution

Create an abstract base class with common setter methods:

```java
public abstract class TransactionBuilderBase<T extends TransactionBuilderBase<T>> {
    protected Address from;
    protected Address to;
    protected Wei value;
    protected HexData data;
    protected BigInteger nonce;
    protected BigInteger gasLimit;

    @SuppressWarnings("unchecked")
    protected T self() {
        return (T) this;
    }

    public T from(Address address) {
        this.from = address;
        return self();
    }

    public T to(Address address) {
        this.to = address;
        return self();
    }

    public T value(Wei value) {
        this.value = value;
        return self();
    }

    public T data(HexData data) {
        this.data = data;
        return self();
    }

    public T nonce(BigInteger nonce) {
        this.nonce = nonce;
        return self();
    }

    public T gasLimit(BigInteger gasLimit) {
        this.gasLimit = gasLimit;
        return self();
    }
}

// Usage
public final class Eip1559Builder extends TransactionBuilderBase<Eip1559Builder> {
    private BigInteger maxFeePerGas;
    private BigInteger maxPriorityFeePerGas;

    // Only type-specific methods
    public Eip1559Builder maxFeePerGas(BigInteger fee) {
        this.maxFeePerGas = fee;
        return this;
    }
}
```

### Estimated Impact

- **Lines reduced:** ~100
- **Effort:** 2-3 hours
- **Risk:** Low (internal refactor)

---

## 4. ABI Type Records (Medium Priority)

### Problem

All 7 ABI type record classes implement nearly identical methods:

```java
// Bool.java
public record Bool(boolean value) implements AbiType {
    @Override
    public int byteSize() {
        return 32;
    }

    @Override
    public boolean isDynamic() {
        return false;
    }

    @Override
    public String typeName() {
        return "bool";
    }
}

// AddressType.java - Same structure
public record AddressType(Address value) implements AbiType {
    @Override
    public int byteSize() {
        return 32;  // Same
    }

    @Override
    public boolean isDynamic() {
        return false;  // Same
    }

    @Override
    public String typeName() {
        return "address";  // Only this differs
    }
}

// Int.java, UInt.java, Bytes.java... all follow the same pattern
```

### Files Affected

- `brane-core/src/main/java/io/brane/core/abi/Bool.java`
- `brane-core/src/main/java/io/brane/core/abi/AddressType.java`
- `brane-core/src/main/java/io/brane/core/abi/Int.java`
- `brane-core/src/main/java/io/brane/core/abi/UInt.java`
- `brane-core/src/main/java/io/brane/core/abi/Bytes.java`
- `brane-core/src/main/java/io/brane/core/abi/Utf8String.java`
- `brane-core/src/main/java/io/brane/core/abi/Tuple.java`

### Proposed Solution

Create a sealed interface hierarchy with default implementations:

```java
public sealed interface AbiType permits StaticAbiType, DynamicAbiType {
    int byteSize();
    boolean isDynamic();
    String typeName();
}

public sealed interface StaticAbiType extends AbiType
        permits Bool, AddressType, Int, UInt, FixedBytes {

    @Override
    default int byteSize() {
        return 32;
    }

    @Override
    default boolean isDynamic() {
        return false;
    }
}

public sealed interface DynamicAbiType extends AbiType
        permits Bytes, Utf8String, DynamicArray, Tuple {

    @Override
    default boolean isDynamic() {
        return true;
    }
}

// Simplified implementations
public record Bool(boolean value) implements StaticAbiType {
    @Override
    public String typeName() {
        return "bool";
    }
}

public record AddressType(Address value) implements StaticAbiType {
    @Override
    public String typeName() {
        return "address";
    }
}
```

### Estimated Impact

- **Lines reduced:** ~100
- **Effort:** 2 hours
- **Risk:** Low

---

## 5. Contract Invocation Handlers (Medium Priority)

### Problem

`ReadOnlyContractInvocationHandler` and `SignerContractInvocationHandler` share 60%+ identical code:

```java
// ReadOnlyContractInvocationHandler.java (lines 65-86)
private Object invokeView(final Method method, final Abi.FunctionCall call) {
    final CallRequest request = CallRequest.builder()
            .to(address)
            .data(new HexData(call.data()))
            .build();

    try {
        final HexData output = client.call(request, BlockTag.LATEST);
        final String outputValue = output != null ? output.value() : null;
        if (outputValue == null || outputValue.isBlank() || "0x".equals(outputValue)) {
            throw new AbiDecodingException(
                    "eth_call returned empty result for function call");
        }
        if (method.getReturnType() == void.class || method.getReturnType() == Void.class) {
            return null;
        }
        return call.decode(outputValue, method.getReturnType());
    } catch (RpcException e) {
        RevertDecoder.throwIfRevert(e);
        throw e;
    }
}

// SignerContractInvocationHandler.java (lines 87-108) - Nearly identical
private Object invokeView(final Method method, final Abi.FunctionCall call) {
    final CallRequest request = CallRequest.builder()
            .to(address)
            .data(new HexData(call.data()))
            .build();

    try {
        final HexData output = signer.call(request, BlockTag.LATEST);  // Only difference
        // ... rest is identical
    }
}
```

### Files Affected

- `brane-contract/src/main/java/io/brane/contract/ReadOnlyContractInvocationHandler.java`
- `brane-contract/src/main/java/io/brane/contract/SignerContractInvocationHandler.java`

### Proposed Solution

Extract shared logic into a helper class:

```java
public final class ContractInvocationHelper {

    public static Object invokeView(
            Method method,
            Abi.FunctionCall call,
            Address address,
            Function<CallRequest, HexData> caller) {

        final CallRequest request = CallRequest.builder()
                .to(address)
                .data(new HexData(call.data()))
                .build();

        try {
            final HexData output = caller.apply(request);
            final String outputValue = output != null ? output.value() : null;
            if (outputValue == null || outputValue.isBlank() || "0x".equals(outputValue)) {
                throw new AbiDecodingException(
                        "eth_call returned empty result for function call");
            }
            if (method.getReturnType() == void.class || method.getReturnType() == Void.class) {
                return null;
            }
            return call.decode(outputValue, method.getReturnType());
        } catch (RpcException e) {
            RevertDecoder.throwIfRevert(e);
            throw e;
        }
    }
}

// Usage in handlers
private Object invokeView(Method method, Abi.FunctionCall call) {
    return ContractInvocationHelper.invokeView(
        method, call, address,
        req -> client.call(req, BlockTag.LATEST)
    );
}
```

### Estimated Impact

- **Lines reduced:** ~50
- **Effort:** 1.5 hours
- **Risk:** Low

---

## 6. Test Setup Boilerplate (Medium Priority)

### Problem

Multiple test classes repeat the same setup pattern:

```java
// Repeated in TesterIntegrationTest, BlobSidecarTest, and others
@BeforeAll
static void setupClient() {
    String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
    var signer = new PrivateKeySigner(TEST_PRIVATE_KEY);
    provider = HttpBraneProvider.builder(rpcUrl).build();
    tester = Brane.builder()
            .provider(provider)
            .signer(signer)
            .testMode(TestNodeMode.ANVIL)
            .buildTester();
}

@BeforeEach
void createSnapshot() {
    snapshot = tester.snapshot();
}

@AfterEach
void revertSnapshot() {
    if (snapshot != null) {
        tester.revert(snapshot);
    }
}
```

### Files Affected

- `brane-rpc/src/test/java/io/brane/rpc/TesterIntegrationTest.java`
- `brane-core/src/test/java/io/brane/core/types/BlobSidecarTest.java`
- Multiple other integration test files

### Proposed Solution

Create a JUnit 5 extension:

```java
public class BraneTestExtension implements BeforeAllCallback, BeforeEachCallback,
        AfterEachCallback, ParameterResolver {

    private static Brane.Tester tester;
    private static BraneProvider provider;
    private SnapshotId snapshot;

    @Override
    public void beforeAll(ExtensionContext context) {
        String rpcUrl = System.getProperty("brane.examples.rpc", "http://127.0.0.1:8545");
        var signer = new PrivateKeySigner(TestConstants.TEST_PRIVATE_KEY);
        provider = HttpBraneProvider.builder(rpcUrl).build();
        tester = Brane.builder()
                .provider(provider)
                .signer(signer)
                .testMode(TestNodeMode.ANVIL)
                .buildTester();
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        snapshot = tester.snapshot();
    }

    @Override
    public void afterEach(ExtensionContext context) {
        if (snapshot != null) {
            tester.revert(snapshot);
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
        return paramCtx.getParameter().getType() == Brane.Tester.class;
    }

    @Override
    public Object resolveParameter(ParameterContext paramCtx, ExtensionContext extCtx) {
        return tester;
    }
}

// Usage - clean test classes
@ExtendWith(BraneTestExtension.class)
class MyIntegrationTest {

    @Test
    void testSomething(Brane.Tester tester) {
        // Test with injected tester
    }
}
```

### Estimated Impact

- **Lines reduced:** ~80 across test files
- **Effort:** 2 hours
- **Risk:** Low

---

## 7. Null-Check Exception Pattern (Low Priority)

### Problem

The same null-check pattern repeats 15+ times:

```java
if (result == null) {
    throw new io.brane.core.error.RpcException(
            0, "eth_chainId returned null", (String) null, (Throwable) null);
}

if (result == null) {
    throw new io.brane.core.error.RpcException(
            0, "eth_getBalance returned null", (String) null, (Throwable) null);
}
```

### Proposed Solution

Add a factory method to `RpcException`:

```java
public class RpcException extends BraneException {

    public static RpcException fromNullResult(String methodName) {
        return new RpcException(0, methodName + " returned null", null, null);
    }
}

// Usage
if (result == null) {
    throw RpcException.fromNullResult("eth_chainId");
}
```

### Estimated Impact

- **Lines reduced:** ~30
- **Effort:** 1 hour
- **Risk:** Very low

---

## 8. Validation Logic (Low Priority)

### Problem

Similar validation patterns appear across value types:

```java
// Address.java
Objects.requireNonNull(value, "address");
if (!HEX.matcher(value).matches()) {
    throw new IllegalArgumentException("Invalid address: " + value);
}

// Wei.java
Objects.requireNonNull(value, "value");
if (value.signum() < 0) {
    throw new IllegalArgumentException("Wei must be non-negative");
}

// Int.java
Objects.requireNonNull(value, "value cannot be null");
if (width % 8 != 0 || width < 8 || width > 256) {
    throw new IllegalArgumentException("Invalid int width: " + width);
}
```

### Proposed Solution

Create a `Validators` utility class:

```java
public final class Validators {

    public static <T> T requireNonNull(T value, String name) {
        return Objects.requireNonNull(value, name + " cannot be null");
    }

    public static String requireHexPattern(String value, Pattern pattern, String name) {
        requireNonNull(value, name);
        if (!pattern.matcher(value).matches()) {
            throw new IllegalArgumentException("Invalid " + name + ": " + value);
        }
        return value;
    }

    public static BigInteger requireNonNegative(BigInteger value, String name) {
        requireNonNull(value, name);
        if (value.signum() < 0) {
            throw new IllegalArgumentException(name + " must be non-negative");
        }
        return value;
    }

    public static int requireValidWidth(int width) {
        if (width % 8 != 0 || width < 8 || width > 256) {
            throw new IllegalArgumentException("Invalid width: " + width);
        }
        return width;
    }
}

// Usage
public record Address(String value) {
    public Address {
        Validators.requireHexPattern(value, HEX, "address");
    }
}
```

### Estimated Impact

- **Lines reduced:** ~40
- **Effort:** 2 hours
- **Risk:** Very low

---

## Implementation Roadmap

### Phase 1: High-Impact Refactors

1. **RPC Method Wrapping** - Create `RpcInvoker` utility
2. **Client Delegation** - Evaluate Lombok vs proxy vs inheritance
3. **Builder Base Class** - Extract `TransactionBuilderBase`

### Phase 2: Medium-Impact Refactors

4. **ABI Type Hierarchy** - Create `StaticAbiType` / `DynamicAbiType`
5. **Contract Handlers** - Extract `ContractInvocationHelper`
6. **Test Extension** - Create `BraneTestExtension`

### Phase 3: Low-Impact Cleanups

7. **Exception Factories** - Add `RpcException.fromNullResult()`
8. **Validators Utility** - Create `Validators` class

---

## Notes

- All refactors maintain backward API compatibility
- No public API changes required
- Each refactor can be done independently
- Recommend comprehensive test coverage before refactoring
