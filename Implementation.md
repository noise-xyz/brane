# Phase 3.2: Runtime ABI Wrapper Binding (P0.15)

## Goal

Introduce a **runtime ABI wrapper** API that lets users bind a Java interface to a contract ABI without codegen:

```java
MyErc20 token = BraneContract.bind(
    new Address("0x..."),
    ERC20_ABI_JSON,
    publicClient,
    walletClient,
    MyErc20.class
);

BigInteger balance = token.balanceOf(user);
TransactionReceipt receipt = token.transfer(user, BigInteger.TEN);
```

Scope for this phase:

* **Runtime binding only** (no compile-time codegen).
* Support **functions** (view + write) via reflection.
* No events, no struct/tuple mapping yet.
* Minimal, predictable semantics.

---

## Design Overview

1. **Enhance `Abi` Interface**: Update `Abi` and `InternalAbi` to expose function metadata (specifically `stateMutability`) so we can distinguish view vs. write functions without re-parsing JSON.
2. **`BraneContract.bind(...)`**: Entrypoint that creates a JDK dynamic proxy.
3. **`ContractInvocationHandler`**: Intercepts method calls, resolves them to ABI functions, encodes arguments, routes to the appropriate client (`PublicClient` or `WalletClient`), and decodes results.

---

## 1. Public API Shape

### 1.1. Binding API

**Location:** `brane-contract/src/main/java/io/brane/contract/BraneContract.java`

```java
public final class BraneContract {
    public static <T> T bind(
            Address address,
            String abiJson,
            PublicClient publicClient,
            WalletClient walletClient,
            Class<T> contractInterface
    ) {
        // Validates interface
        // Parses ABI
        // Returns Proxy
    }
}
```

---

## 2. ABI Binding Internals

### 2.1. ABI Metadata Enhancements

**Location:** `brane-contract/src/main/java/io/brane/contract/Abi.java` & `InternalAbi.java`

*   **Modify `Abi` interface**:
    ```java
    Optional<FunctionMetadata> getFunction(String name);
    
    record FunctionMetadata(String name, String stateMutability, List<String> inputs, List<String> outputs) {
        boolean isView() { return "view".equals(stateMutability) || "pure".equals(stateMutability); }
    }
    ```
*   **Update `InternalAbi`**:
    *   Parse `stateMutability` from JSON.
    *   **Fallback**: If `stateMutability` is missing (older ABIs), treat `constant: true` as `"view"`, otherwise `"nonpayable"`.
    *   **P0 Rule**: Throw `AbiEncodingException` at parse time if the ABI contains overloaded functions (multiple functions with the same name). This simplifies `getFunction` to return `Optional<FunctionMetadata>`.

### 2.2. ABI Binding Helper

**Location:** `brane-contract/src/main/java/io/brane/contract/AbiBinding.java`

Helper to resolve Java `Method` -> `Abi.FunctionMetadata`.

*   **Responsibility**:
    *   Wraps an `Abi` instance.
    *   Caches a `Map<Method, FunctionMetadata>` during construction/binding to avoid repeated lookups.
    *   `FunctionMetadata resolve(Method method)`:
        *   Look up in cache.
        *   If not found (should be caught at bind time), throw.

### 2.3. Invocation Handler

**Location:** `brane-contract/src/main/java/io/brane/contract/ContractInvocationHandler.java`

Implements `InvocationHandler`.

*   **`invoke` logic**:
    1.  Handle `Object` methods (`toString`, `equals`, `hashCode`).
    2.  Resolve `FunctionMetadata` via `AbiBinding`.
    3.  Encode arguments using existing primitives (`FunctionEncoder`, etc.).
    4.  **Dispatch**:
        *   If `metadata.isView()`:
            *   Call `PublicClient.call(...)`.
            *   Decode result using `FunctionReturnDecoder`.
        *   Else (Write):
            *   Build `TransactionRequest` (EIP-1559).
            *   Call `WalletClient.sendTransactionAndWait(...)`.
            *   Return `TransactionReceipt` (or `void`).

---

## 3. Rules & Semantics (P0)

### 3.1. Return Type Rules

**View/Pure Functions**:
*   **`void`**: Allowed only if ABI has **0 outputs**.
*   **`BigInteger`**: Allowed only if ABI has exactly **1 output** of type `uint*` / `int*`.
*   **`Address`**: Allowed only if ABI has exactly **1 output** of type `address`.
*   **`Boolean` / `boolean`**: Allowed only if ABI has exactly **1 output** of type `bool`.

**Write Functions (Non-view)**:
*   **`TransactionReceipt`**: Wrapper calls `WalletClient.sendTransactionAndWait` and returns the receipt.
*   **`void`**: Wrapper calls `WalletClient.sendTransactionAndWait` and ignores the receipt.
*   **Any other type**: **Not supported** in P0. Bind-time error.

### 3.2. Value / Payable

*   **Value is always 0**: P0 does not support sending ETH value.
*   **Payable functions**: Can be called, but `value` will be 0.
*   (Optional strictness): `bind` MAY reject methods mapped to `payable` functions for P0.

### 3.3. Write Call Defaults

When invoking a non-view function:
*   Use `TxBuilder.eip1559()`:
    *   `to = contractAddress`
    *   `data = encodedFunctionCall`
    *   `value = 0`
    *   Gas fields left null (handled by `SmartGasStrategy`).
*   Call `walletClient.sendTransactionAndWait(request, 10_000, 500)`.
    *   Default timeout: **10 seconds**.
    *   Default poll interval: **500 ms**.

---

## 4. Bind-Time Validation

`BraneContract.bind(...)` MUST fail fast if:

1.  `contractInterface` is not an interface.
2.  For each method in `contractInterface` (excluding `Object` methods):
    *   **Resolution**: No ABI function found with matching name.
    *   **Overloads**: (Handled by `InternalAbi` parsing, but verify no ambiguity).
    *   **Arguments**: Parameter count does not match ABI inputs.
    *   **Types**: Java parameter types are not supported.
    *   **Return Type**: Java return type violates rules in §3.1.

If validation fails, throw `IllegalArgumentException` and **do not create the proxy**.

---

## 5. Implementation Steps

1.  [ ] **Update `Abi` / `InternalAbi`**:
    *   Add `FunctionMetadata`.
    *   Parse `stateMutability` (with fallback).
    *   Throw on overloads.
2.  [ ] **Create `AbiBinding`**:
    *   Implement resolution and caching.
3.  [ ] **Create `ContractInvocationHandler`**:
    *   Implement dispatch logic (View vs Write) with specified defaults.
4.  [ ] **Implement `BraneContract.bind`**:
    *   Implement strict bind-time validation.
5.  [ ] **Tests**:
    *   `AbiBindingTest`: Resolution rules.
    *   `ContractInvocationHandlerTest`: Mocked client dispatch assertions.
    *   `AbiWrapperIntegrationTest`: End-to-end against Anvil.

---

## 6. Testing Plan

### Unit Tests
*   **`AbiTest`**: Verify `stateMutability` parsing and fallback. Verify overload rejection.
*   **`AbiBindingTest`**: Verify method resolution, caching, and validation errors.
*   **`ContractInvocationHandlerTest`**:
    *   `viewMethodUsesPublicClientOnly`: Assert `PublicClient` called, `WalletClient` NOT called.
    *   `writeMethodUsesWalletClientOnly`: Assert `WalletClient` called, `PublicClient` NOT called.

### Integration Tests
*   **`AbiWrapperIntegrationTest`**:
    *   Deploy ERC20.
    *   Bind interface.
    *   Call `balanceOf` (View) -> Verify result.
    *   Call `transfer` (Write) -> Verify receipt and state change.

### Example
*   **`AbiWrapperExample`**: Runnable demo.