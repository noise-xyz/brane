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
        // ...
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
- [x] **Type Hierarchy**: All Solidity types represented as records/sealed interfaces.
- [x] **Primitive Integration**: Uses `io.brane.primitives.Hex` and `io.brane.core.types.*`.
- [x] **Static Encoding**: `uint256`, `address`, `bool`, `bytes32` encode correctly (padded to 32 bytes).
- [x] **Dynamic Encoding**: `bytes`, `string`, `uint[]` encode with correct offsets and length prefixes.
- [x] **Complex Encoding**: `(uint, string, uint[])` tuples encode correctly.
- [x] **Tests**: Pass all vectors from Solidity ABI encoding test suite.

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
        // ...
    }
}
```

### Acceptance Criteria
- [x] **Bounds Checking**: Throws clear exception if data is too short.
- [x] **Offset Validation**: Throws if offsets point outside valid range.
- [x] **Type Validation**: Ensures decoded data matches expected constraints (e.g., `bool` is 0 or 1).
- [x] **Tests**: Round-trip fuzzing (`decode(encode(x)) == x`).

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
- [x] Build succeeds.

---

# Phase 4: Documentation Website

## Goal
Deploy a unified documentation site containing both the manual/guides (Next.js) and the generated Javadocs.

## Architecture
- **`/website`**: Next.js application for guides, tutorials, and landing page.
- **`/javadoc`**: Generated Javadocs served under the `/javadoc` route.

## Implementation Steps
1.  **Initialize Website**:
    - Create `website/` directory with a fresh Next.js project.
    - Use a modern, clean template (Tailwind CSS).
2.  **Build Automation (`vercel-build.sh`)**:
    - Create a script to:
        1.  Generate Javadocs (`./gradlew allJavadoc`).
        2.  Copy Javadocs to `website/public/javadoc`.
        3.  Build the Next.js app.
3.  **Vercel Configuration**:
    - Configure Vercel to use `vercel-build.sh` as the build command.
    - Ensure proper caching of Gradle and NPM artifacts.

## Verification Plan
### Automated Tests
- [x] **Build Script**: Verify `vercel-build.sh` runs successfully locally.
- [x] **Content Check**: Verify `website/public/javadoc/index.html` exists after build.
- [x] **Integration**: Verify `npm run build` in `website/` succeeds with the copied files.

### Manual Verification
- [ ] **Local Preview**: Run `npm run dev` in `website/` and access `localhost:3000/javadoc`.
- [ ] **Vercel Preview**: Deploy to Vercel and verify live URL.
