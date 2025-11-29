# Phase 3: Native ABI System (Crypto Independence)

## Context: Why Replace Web3j?
We are removing the vendored `web3j` ABI implementation because it fundamentally conflicts with Brane's design goals:
1.  **Type Safety**: Web3j relies heavily on raw types and unchecked casts (e.g., `Type<T>`), leading to runtime errors that should be compile-time errors.
2.  **Modern Java**: It predates modern Java features. We want to use **Sealed Interfaces**, **Records**, and **Pattern Matching** to represent Solidity types safely and concisely.
3.  **Performance**: It forces `BigInteger` for all numeric types (even `uint8`) and has excessive object allocation overhead.
4.  **Complexity**: The inheritance hierarchy (`Type` -> `NumericType` -> `IntType` -> `Int256`) is overly deep and rigid.

## Architecture: `io.brane.core.abi`
We will build a clean, zero-dependency ABI layer in `brane-core`.

### Leveraging Existing Primitives
We must utilize our existing, high-performance primitives from `brane-primitives`:
*   **`io.brane.primitives.Hex`**: Use for all hex encoding/decoding (e.g., `Hex.decode(data)`).
*   **`io.brane.core.types.Address`**: Use as the backing value for `AbiType.Address`.
*   **`io.brane.core.types.HexData`**: Use for `bytes` and `bytesN` types.
*   **`io.brane.core.crypto.Keccak256`**: Use for function selector hashing.

### Core Type System (`AbiType`)
A sealed interface hierarchy representing valid Solidity types.

```java
package io.brane.core.abi;

import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import java.math.BigInteger;
import java.util.List;

public sealed interface AbiType permits 
    UInt, Int, AddressType, Bool, Bytes, Utf8String, Array, Tuple {
    
    // Returns the static byte size (32) or dynamic size
    int byteSize();
    
    // True if the type is dynamic (bytes, string, T[], or tuple containing dynamic)
    boolean isDynamic();
    
    // The canonical Solidity type name (e.g., "uint256", "address[]")
    String typeName();
}

// Examples
record UInt(int width, BigInteger value) implements AbiType { ... }
record Int(int width, BigInteger value) implements AbiType { ... }
record AddressType(Address value) implements AbiType { ... }
record Bool(boolean value) implements AbiType { ... }
record Bytes(HexData value, boolean isDynamic) implements AbiType { ... } // Covers bytes and bytesN
record Utf8String(String value) implements AbiType { ... }
record Array<T extends AbiType>(List<T> values, Class<T> type) implements AbiType { ... }
record Tuple(List<AbiType> components) implements AbiType { ... }
```

---

## Milestone 1: Type System & Encoder
**Goal**: Implement the types and the ability to encode them into a byte array.

### Template: `AbiEncoder`
```java
import io.brane.primitives.Hex;
import io.brane.core.crypto.Keccak256;

public final class AbiEncoder {
    
    /**
     * Encodes a list of ABI types according to the Solidity Contract ABI Specification.
     * Handles:
     * 1. Head/Tail encoding for dynamic types.
     * 2. 32-byte padding for static types.
     * 3. Offset calculation.
     */
    public static byte[] encode(List<AbiType> args) {
        // 1. Calculate static size
        // 2. Iterate args:
        //    - If static: append to head
        //    - If dynamic: append offset to head, append data to tail
        // 3. Combine head + tail
    }
    
    /**
     * Encodes a function call with selector.
     * selector = Keccak256.hash(signature.getBytes(StandardCharsets.UTF_8)).substring(0, 4)
     */
    public static byte[] encodeFunction(String signature, List<AbiType> args) {
        // ...
    }
}
```

### Acceptance Criteria
- [ ] **Type Hierarchy**: All Solidity types represented as records/sealed interfaces.
- [ ] **Primitive Integration**: Uses `io.brane.primitives.Hex` and `io.brane.core.types.*`.
- [ ] **Static Encoding**: `uint256`, `address`, `bool`, `bytes32` encode correctly (padded to 32 bytes).
- [ ] **Dynamic Encoding**: `bytes`, `string`, `uint[]` encode with correct offsets and length prefixes.
- [ ] **Complex Encoding**: `(uint, string, uint[])` tuples encode correctly.
- [ ] **Tests**: Pass all vectors from Solidity ABI encoding test suite.

---

## Milestone 2: The Decoder
**Goal**: Parse a byte array back into `AbiType` instances.

### Template: `AbiDecoder`
```java
import io.brane.primitives.Hex;

public final class AbiDecoder {

    /**
     * Decodes ABI-encoded data into typed objects.
     * @param data The byte array from the EVM.
     * @param types The expected schema (e.g., [UInt.class, String.class]).
     */
    public static List<AbiType> decode(byte[] data, List<TypeDescriptor> types) {
        // 1. Validate data length >= static size
        // 2. Iterate types:
        //    - If static: read 32 bytes from current offset
        //    - If dynamic: read offset, jump to offset, read length, read data
        // 3. Return list of AbiType
    }
}
```

### Acceptance Criteria
- [ ] **Bounds Checking**: Throws clear exception if data is too short.
- [ ] **Offset Validation**: Throws if offsets point outside valid range.
- [ ] **Type Validation**: Ensures decoded data matches expected constraints (e.g., `bool` is 0 or 1).
- [ ] **Tests**: Round-trip fuzzing (`decode(encode(x)) == x`).

---

## Milestone 3: Integration (The Swap)
**Goal**: Replace `web3j` usage in `InternalAbi` with the new native system.

### Plan
1.  **Refactor `InternalAbi`**:
    *   Map `InternalAbi.AbiParameter` -> `AbiType` schema.
    *   Replace `FunctionEncoder.encode()` with `AbiEncoder.encode()`.
    *   Replace `FunctionReturnDecoder.decode()` with `AbiDecoder.decode()`.
2.  **Update `AbiBinding`**:
    *   Ensure the runtime proxy converts Java types (`BigInteger`, `String`) to `AbiType` before encoding.

### Acceptance Criteria
- [x] **Zero Web3j Imports**: `InternalAbi.java` has NO imports starting with `io.brane.internal.web3j`.
- [x] **Zero Web3j Usage**: All encoding/decoding logic uses `io.brane.core.abi.*`.
- [x] **Tests Pass**: `AbiWrapperIntegrationTest` passes with the new implementation.

---

## Milestone 4: Cleanup
**Goal**: Remove the dead code.

### 4. Cleanup & Verification
- [x] Delete `brane-core/src/main/java/io/brane/internal/web3j`.
- [x] Remove `jackson-databind` if it was only used by web3j (check usage). -> *Kept, used for ABI JSON parsing*
- [x] Run `./gradlew test` to ensure no regressions.
- [x] `git grep "web3j"` returns 0 matches.
- [ ] Build succeeds.
