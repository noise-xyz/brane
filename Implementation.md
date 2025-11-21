````markdown
## 8. Robust error handling & diagnostics (#8)

**Goal**

Make Brane’s error model consistent and debuggable:

- Every *public* API either:
  - returns a domain type (or `null` for “not found”), **or**
  - throws a typed Brane error (subclass of `BraneException` / `RpcException` / `TxnException`),
- No random `RuntimeException`, `IOException`, or `NullPointerException` escapes from public-facing APIs,
- Provide small helper methods on error types for common failure modes (`block range too large`, `filter not found`, `invalid sender`, `chainId mismatch`).

We will **reuse existing error types** where possible (e.g. current `RpcException`, `AbiDecodingException`) instead of inventing a parallel hierarchy.

---

### 8.1 Confirm / refine core error types

**Files**

- `brane-core/src/main/java/io/brane/core/error/...` (or equivalent package)

**Tasks**

1. **Inventory existing core errors**:
   - Find `BraneException` / `RpcException` / `AbiDecodingException` / `TxnException` etc. and confirm their current role.
   - If there is no single base type yet, introduce:

     ```java
     public class BraneException extends RuntimeException { ... }
     ```

2. **Align hierarchy** (minimal disruption):
   - Ensure there is a clear RPC-level exception type, e.g.:

     ```java
     public class RpcException extends BraneException {
         private final int code;
         private final String data;
         // optional: String method, String endpoint
     }
     ```

     (If `RpcException` already exists, extend `BraneException` instead of `RuntimeException`.)

   - Ensure there is a transaction-level type, e.g.:

     ```java
     public class TxnException extends BraneException {
         // optionals: Hash txHash, String revertData
     }
     ```

   - Keep `AbiDecodingException` (or equivalent) as a `BraneException` subclass for ABI/contract decoding issues.

3. **Add domain-specific subclasses only if they are already referenced in the design**:
   - `InvalidSenderException extends TxnException`
   - `ChainIdMismatchException extends TxnException`

   Keep them lightweight and focused on message / fields.

---

### 8.2 Provider-level error wrapping (`BraneProvider` / HTTP)

**Files**

- `brane-rpc/src/main/java/io/brane/rpc/BraneProvider.java`
- `brane-rpc/src/main/java/io/brane/rpc/HttpBraneProvider.java`
- Any other `BraneProvider` implementation

**Tasks**

1. Audit `BraneProvider.send(...)` implementations:
   - Wrap **all** I/O (HTTP, timeouts, JSON mapping) failures into `RpcException` (or your chosen RPC error type).
   - Include as much useful context as possible:
     - RPC method name
     - Endpoint URL
     - Possibly truncated params string

2. When a `JsonRpcResponse` has `error`:
   - Always throw `RpcException` with:
     - `code` from `error.code`
     - `message` from `error.message`
     - `data` extracted from `error.data` (stringified / hex if needed)
   - Do **not** silently return `null` or an empty list in these cases.

3. Ensure `BraneProvider.send(...)` **never leaks** checked exceptions (`IOException`, etc.) or generic runtime wrappers; they should always be transformed into `RpcException` (subclass of `BraneException`).

---

### 8.3 PublicClient error behaviour (`DefaultPublicClient`)

**Files**

- `brane-rpc/src/main/java/io/brane/rpc/DefaultPublicClient.java`

**Tasks**

1. Audit each public method:

   - `getLatestBlock`
   - `getBlockByNumber`
   - `getTransactionByHash`
   - `call`
   - `getLogs`

   For each:

   - RPC errors → surface as `RpcException` (already thrown by provider).
   - Mapping/decoding bugs → throw `BraneException` (or `AbiDecodingException` where ABI is involved), not arbitrary `RuntimeException`.

2. **Special case: `getTransactionByHash`**:
   - Returning `null` for “not found” is acceptable (keep that contract).
   - Ensure errors are still `RpcException`.

3. **Special case: `getLogs`**:
   - For error responses like “block range too large” or “filter not found”, keep them as `RpcException` but wire in helper methods (see 8.6).
   - Do not return `List.of()` if there is a JSON-RPC error; only return empty list on genuine success + empty result.

---

### 8.4 Contract / ABI errors (`brane-contract`)

**Files**

- `brane-contract/src/main/java/io/brane/contract/Abi.java`
- `brane-contract/src/main/java/io/brane/contract/InternalAbi.java`
- `brane-contract/src/main/java/io/brane/contract/ReadOnlyContract.java`
- `brane-contract/src/main/java/io/brane/contract/ReadWriteContract.java` (or equivalent)

**Tasks**

1. **ABI decoding**:
   - Ensure ABI decode failures throw a dedicated exception (`AbiDecodingException` extending `BraneException`), not generic `RuntimeException`.
   - Error messages should mention:
     - Function/event name
     - Expected vs actual type count / type mismatch details.

2. **Read-only contract calls**:
   - `ReadOnlyContract.call(...)`:
     - Underlying RPC issues → `RpcException`.
     - ABI decode problems → `AbiDecodingException`.

3. **Read-write contract sends**:
   - `ReadWriteContract.send(...)` (or equivalent send API):
     - For tx failures / reverts, convert raw error info into:
       - `TxnException` or subclasses (`InvalidSenderException`, `ChainIdMismatchException`) when applicable.
   - Ensure callers can distinguish:
     - RPC/network error (`RpcException`)
     - EVM revert / invalid sender (`TxnException` subclass)

---

### 8.5 Wallet / signer error mapping

**Files**

- `brane-rpc` or wallet module:
  - `DefaultWalletClient`
  - `BraneSigner` / `TransactionSigner`
  - Any sendTransaction / sendRawTransaction wrappers

**Tasks**

1. Where chain IDs are checked before signing/sending:
   - On mismatch, throw `ChainIdMismatchException` with:
     - Expected chainId
     - Actual chainId
     - Optional network/profile info (e.g. `ChainProfile`).

2. When sender / from address is invalid:
   - Throw `InvalidSenderException` (or similar) with:
     - `from` address
     - Explanation (“sender is null”, “sender not unlocked”, etc.).

3. Ensure tx send flows do **not** throw random `RuntimeException` for these domain conditions.

---

### 8.6 Add diagnostic helpers on exceptions

**Files**

- Core error classes (e.g. `io.brane.core.error.RpcException`, `TxnException`)

**Tasks**

1. On the RPC error type (`RpcException`):

   ```java
   public boolean isBlockRangeTooLarge() {
       // e.g. inspect code and message/data for known patterns from getLogs
   }

   public boolean isFilterNotFound() {
       // e.g. "filter not found" in message or data
   }
````

These should be **best-effort** helpers:

* Implement via simple `contains(...)` checks or known error codes (`-32000`, etc.).
* Document that they’re node-dependent.

2. On the transaction error type (`TxnException`):

   ```java
   public boolean isInvalidSender() { ... }

   public boolean isChainIdMismatch() { ... }
   ```

   Implement using message / code / stored fields; don’t over-engineer.

---

### 8.7 Audit public APIs for “no random exceptions”

**Tasks**

1. For public types in:

   * `io.brane.rpc.*`
   * `io.brane.contract.*`
   * `io.brane.core.types.*`
   * Wallet / signer public classes

   confirm:

   * Failure modes are either:

     * Documented `BraneException` subclasses, or
     * Documented `IllegalArgumentException` for obvious misuse.

2. Replace patterns like:

   ```java
   throw new RuntimeException("rpc failed: " + e.getMessage(), e);
   ```

   with:

   ```java
   throw new BraneException("rpc failed: ...", e);
   ```

   or more specific types where appropriate.

3. Avoid leaking `NullPointerException` from public APIs due to unchecked assumptions; validate arguments where cheap and helpful.

---

### 8.8 Tests for error behaviour

**Files**

* `brane-core/src/test/java/io/brane/core/error/RpcExceptionTest.java`
* `brane-core/src/test/java/io/brane/core/error/TxnExceptionTest.java`
* `brane-rpc/src/test/java/io/brane/rpc/DefaultPublicClientErrorTest.java`
* `brane-contract/src/test/java/io/brane/contract/ContractErrorTest.java`

**Tasks**

1. **Exception helper tests:**

   * `RpcExceptionTest`:

     * Construct errors with messages like “block range is too large” / “filter not found”.
     * Assert:

       * `isBlockRangeTooLarge()` → `true` for block range message.
       * `isFilterNotFound()` → `true` for filter error.

   * `TxnExceptionTest`:

     * Create instances that should satisfy `isInvalidSender()` / `isChainIdMismatch()` and assert they do.

2. **PublicClient error tests:**

   * Use a fake `BraneProvider` that returns `JsonRpcResponse` with `error` fields:

     * For `getLogs(...)`, simulate:

       * “block range too large” → assert `RpcException` thrown and `isBlockRangeTooLarge()` is `true`.
       * “filter not found” → assert `isFilterNotFound()` is `true`.

3. **Contract error tests:**

   * For `ReadOnlyContract`:

     * Feed malformed ABI outputs → assert `AbiDecodingException`.
   * For tx send:

     * Fake a client that returns chain mismatch / invalid sender signals → assert appropriate `TxnException` subclasses are thrown.

---

### 8.9 Sanity commands

After implementing Step 8, run:

```bash
./gradlew :brane-core:test --no-daemon
./gradlew :brane-rpc:test --no-daemon
./gradlew :brane-contract:test --no-daemon
./gradlew clean check --no-daemon
```

All should pass. Then re-run:

* `Erc20TransferLogExample` (logs still decode correctly).
* `MultiChainLatestBlockExample` (multichain builder still works).

This ensures the stricter error model didn’t break the happy paths from Steps 6–7.

````