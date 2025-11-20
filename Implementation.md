## `implementation-step4-contract-facade.md`

### Goal of Step 4

Add a **simple, read-only contract façade** on top of `Abi` + `PublicClient` so users can do:

```java
Abi erc20 = Abi.fromJson(ERC20_ABI);
PublicClient publicClient = PublicClient.from(provider);

ReadOnlyContract token = ReadOnlyContract.from(
    new Address("0x..."),
    erc20,
    publicClient
);

BigInteger decimals = token.call("decimals", BigInteger.class);
BigInteger balance  = token.call("balanceOf", BigInteger.class, holderAddress);
```

This must be:

* **Purely read-only** (no signing, no `eth_sendRawTransaction`).
* Built on **existing pieces**: `Abi`, `Abi.FunctionCall`, `PublicClient.call`, and Brane’s error model.
* Zero **web3j type leakage** in public APIs (respect `guardrails.md`).

We **do not** change the existing `Contract.write(...)` path; that’s still the “write-capable” façade backed by `Client`/`HttpClient`.

---

## 1. New class: `ReadOnlyContract`

**Location**

Create a new class in brane-contract:

```text
brane-contract/src/main/java/io/brane/contract/ReadOnlyContract.java
```

**Package**

```java
package io.brane.contract;
```

**Dependencies allowed**

* `io.brane.core.types.Address`
* `io.brane.core.types.HexData` (if needed)
* `io.brane.core.error.*` (`RpcException`, `RevertException`, `AbiEncodingException`, `AbiDecodingException`)
* `io.brane.rpc.PublicClient`
* `io.brane.core.RevertDecoder`
* Standard Java collections (`Map`, `LinkedHashMap`, etc.)

**NO org.web3j / web3j types** in this class.

### 1.1. Fields & constructor

```java
public final class ReadOnlyContract {

    private final Address address;
    private final Abi abi;
    private final PublicClient client;

    private ReadOnlyContract(Address address, Abi abi, PublicClient client) {
        this.address = Objects.requireNonNull(address, "address must not be null");
        this.abi = Objects.requireNonNull(abi, "abi must not be null");
        this.client = Objects.requireNonNull(client, "client must not be null");
    }

    public static ReadOnlyContract from(Address address, Abi abi, PublicClient client) {
        return new ReadOnlyContract(address, abi, client);
    }

    public Address address() {
        return address;
    }

    public Abi abi() {
        return abi;
    }
}
```

---

### 1.2. Core API: `call(...)`

We want a **simple, generic call** method that:

* Encodes arguments via `Abi.encodeFunction(fnName, args...)`.
* Builds a JSON-RPC call object:

  * `to` = contract address (string)
  * `data` = encoded data (hex string)
* Calls `publicClient.call(callObject, "latest")`.
* Decodes the result with `FunctionCall.decode(rawResultHex, returnType)`.

Signature:

```java
public <T> T call(String functionName, Class<T> returnType, Object... args)
        throws RpcException, RevertException, AbiEncodingException, AbiDecodingException
```

Implementation sketch:

```java
public <T> T call(String functionName, Class<T> returnType, Object... args)
        throws RpcException, RevertException, AbiEncodingException, AbiDecodingException {

    final Abi.FunctionCall fnCall = abi.encodeFunction(functionName, args);
    final String data = fnCall.data();

    final Map<String, Object> callObject = new LinkedHashMap<>();
    callObject.put("to", address.value());
    callObject.put("data", data);

    try {
        final String raw = client.call(callObject, "latest");
        // PublicClient.call should never return null in the happy path;
        // if it does, treat it as ABI decode error.
        if (raw == null || raw.isBlank()) {
            throw new AbiDecodingException(
                    "eth_call returned empty result for function '" + functionName + "'");
        }
        return fnCall.decode(raw, returnType);
    } catch (RpcException e) {
        // If RpcException contains revert data, translate to RevertException
        handlePotentialRevert(e);
        throw e;
    }
}
```

`handlePotentialRevert` should mirror the logic in `Contract`:

```java
private static void handlePotentialRevert(final RpcException e) throws RevertException {
    final String raw = e.data();
    if (raw != null && raw.startsWith("0x") && raw.length() > 10) {
        final var decoded = RevertDecoder.decode(raw);
        throw new RevertException(decoded.reason(), decoded.rawDataHex(), e);
    }
}
```

**Notes**

* Use `AbiEncodingException` / `AbiDecodingException` as thrown from `Abi.encodeFunction` / `FunctionCall.decode`. Don’t swallow them.
* This class does **not** know about `Signer` or `eth_sendRawTransaction`.

---

### 1.3. Optional: multi-return support

If you want a second method now (optional but nice):

```java
public Object[] callTuple(String functionName, Object... args)
        throws RpcException, RevertException, AbiEncodingException, AbiDecodingException
```

Where `FunctionCall.decodeTuple(...)` (if available) returns `Object[]`. If your current ABI API only supports single return type, you can skip this for now.

---

## 2. Tests for `ReadOnlyContract`

Add a unit test class:

```text
brane-contract/src/test/java/io/brane/contract/ReadOnlyContractTest.java
```

Package:

```java
package io.brane.contract;
```

### 2.1. FakePublicClient

Create a simple fake implementation of `PublicClient` inside the test class (or as a private static nested class):

```java
private static final class FakePublicClient implements PublicClient {

    private final String result;
    private final RpcException toThrow;

    private FakePublicClient(String result, RpcException toThrow) {
        this.result = result;
        this.toThrow = toThrow;
    }

    @Override
    public String call(Object callObject, String blockTag) throws RpcException {
        if (toThrow != null) {
            throw toThrow;
        }
        return result;
    }

    // If PublicClient has other methods (getBlockByNumber, etc.), you can either:
    // - throw UnsupportedOperationException, or
    // - provide trivial implementations if required by interface.
}
```

Adjust to match the exact `PublicClient` interface you have (you might need to implement `getBlockByNumber`, `getTransactionByHash`, etc. as no-op throws).

### 2.2. Test 1 – happy path: echo-style function

Test that:

* `ReadOnlyContract.call("echo", BigInteger.class, 42)` returns 42 when the fake client returns a valid ABI-encoded `uint256`.

Use your existing internal ABI helpers in tests (just like `ContractReadTest`):

```java
@Test
void callReturnsDecodedValue() throws Exception {
    String encoded = "0x" + TypeEncoder.encode(new Uint256(BigInteger.valueOf(42)));
    PublicClient client = new FakePublicClient(encoded, null);

    Abi abi = Abi.fromJson(
            """
            [
              {
                "inputs": [{ "internalType": "uint256", "name": "x", "type": "uint256" }],
                "name": "echo",
                "outputs": [{ "internalType": "uint256", "name": "", "type": "uint256" }],
                "stateMutability": "pure",
                "type": "function"
              }
            ]
            """
    );

    ReadOnlyContract contract =
            ReadOnlyContract.from(
                    new Address("0x0000000000000000000000000000000000001234"),
                    abi,
                    client);

    BigInteger result = contract.call("echo", BigInteger.class, BigInteger.valueOf(42));

    assertEquals(BigInteger.valueOf(42), result);
}
```

(Use the same Address/TypeEncoder/U256 imports pattern as in `ContractReadTest`.)

### 2.3. Test 2 – revert → RevertException

Test that an RpcException with revert data becomes `RevertException`:

* Build revert payload using `FunctionEncoder` and `Error(string)` like your existing test.

```java
@Test
void callRevertThrowsRevertException() {
    List<Type> inputs = List.of((Type) new Utf8String("simple reason"));
    String rawData = FunctionEncoder.encode(new Function("Error", inputs, List.of()));

    RpcException rpcEx = new RpcException(3, "execution reverted", rawData, null);
    PublicClient client = new FakePublicClient(null, rpcEx);

    Abi abi = Abi.fromJson(ECHO_ABI);
    ReadOnlyContract contract =
            ReadOnlyContract.from(
                    new Address("0x0000000000000000000000000000000000001234"),
                    abi,
                    client);

    RevertException ex =
            assertThrows(
                    RevertException.class,
                    () -> contract.call("echo", BigInteger.class, BigInteger.valueOf(42)));

    assertEquals("simple reason", ex.revertReason());
    assertEquals(rawData, ex.rawDataHex());
}
```

### 2.4. Test 3 – empty / invalid result → AbiDecodingException

Test that if `PublicClient.call` returns an empty string/null/too-short hex, `ReadOnlyContract.call` throws `AbiDecodingException` (from the guard you added in `InternalAbi.Call.decode`):

```java
@Test
void emptyResultThrowsAbiDecodingException() {
    PublicClient client = new FakePublicClient("0x", null);

    Abi abi = Abi.fromJson(ECHO_ABI);
    ReadOnlyContract contract =
            ReadOnlyContract.from(
                    new Address("0x0000000000000000000000000000000000001234"),
                    abi,
                    client);

    assertThrows(
            AbiDecodingException.class,
            () -> contract.call("echo", BigInteger.class, BigInteger.valueOf(42)));
}
```

---

## 3. Guardrails to respect

When implementing Step 4:

* ❌ No `org.web3j.*` imports in any **public** API or non-`internal` package.
* ✅ Only use web3j internals under `io.brane.internal.web3j.*` inside tests or internal logic (if absolutely needed).
* ✅ `ReadOnlyContract` must speak in terms of **Brane types** (`Address`, `BraneException` subclasses, etc.).
* ✅ Don’t change existing behavior of:

  * `Contract.read`
  * `Contract.write`
  * `Erc20Example` / `Main` (except maybe adding a tiny usage of `ReadOnlyContract` in a new example later, not required for Step 4).

---

## 4. How to verify

After Codex implements Step 4:

1. Compile and run tests:

```bash
./gradlew :brane-contract:test --no-daemon
./gradlew :brane-rpc:test --no-daemon
./gradlew clean check --no-daemon
```

2. (Optional) Add a tiny usage to `Erc20Example`:

```java
ReadOnlyContract readOnly =
    ReadOnlyContract.from(token, abi, PublicClient.from(provider));
BigInteger balanceViaFacade = readOnly.call("balanceOf", BigInteger.class, holder);
```

But that’s optional; core milestone is the new façade + tests.