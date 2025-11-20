## 🚫 Web3j Type Leakage Policy

Brane vendors web3j under `io.brane.internal.web3j.*`.
**web3j is an implementation detail only.** It must **never** leak into Brane’s public API.

These are the strict rules:

### 1. Allowed packages for web3j

* ✅ web3j **may only be referenced** under:

  * `io.brane.internal.web3j.*`
  * or very small, clearly-marked adapter classes that live in `io.brane.internal.*` (not in `core`, `rpc`, or `contract` root packages).

Example (allowed):

```java
package io.brane.internal.web3j.abi;
// web3j-based ABI helpers
```

Example (NOT allowed):

```java
package io.brane.contract;
// ❌ Don't use org.web3j.* here
```

---

### 2. No web3j imports in public modules

In these packages, **never** import `org.web3j.*`:

* `io.brane.core.*`
* `io.brane.rpc.*`
* `io.brane.contract.*`
* `io.brane.examples.*`

**Rule of thumb:**
If the file’s package starts with `io.brane.core`, `io.brane.rpc`, `io.brane.contract`, or `io.brane.examples`, it must have **zero** imports from `org.web3j.*`.

Example (forbidden):

```java
package io.brane.contract;

import org.web3j.abi.datatypes.Function;  // ❌ not allowed

public class Contract { ... }             // ❌ leaking web3j into public API
```

---

### 3. No web3j types in public signatures

Public APIs must **only** use:

* Java standard types (`String`, `BigInteger`, etc.)
* Brane types:

  * `io.brane.core.types.*` (`Address`, `Hash`, `HexData`, `Wei`, …)
  * `io.brane.core.model.*` (`Transaction`, `TransactionReceipt`, `LogEntry`, `ChainProfile`, …)
  * `io.brane.core.error.*` (`BraneException`, `RpcException`, `RevertException`, …)
  * `io.brane.rpc.*` and `io.brane.contract.*` classes

🚫 **Forbidden in any public method/constructor/field:**

* `org.web3j.protocol.Web3j`
* `org.web3j.protocol.core.methods.response.*`
* `org.web3j.abi.datatypes.*`
* `org.web3j.crypto.*`
* Any other `org.web3j.*` type

Example (forbidden):

```java
// ❌ bad: leaks web3j type
public org.web3j.protocol.core.methods.response.TransactionReceipt sendRawTx(...);
```

Correct version:

```java
// ✅ good: uses Brane types
public io.brane.core.model.TransactionReceipt sendRawTx(...);
```

---

### 4. Wrap web3j behind Brane abstractions

If you need web3j functionality:

1. Implement it under `io.brane.internal.web3j.*` (or another `io.brane.internal.*` package).
2. Expose it to the rest of Brane via **Brane-level** types and interfaces.

Example:

```java
// internal adapter (allowed)
package io.brane.internal.web3j.signing;

import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.TransactionEncoder;

public final class Web3jTransactionSigner {
    // uses web3j internally
}
```

```java
// public contract layer (no web3j types)
package io.brane.contract;

import io.brane.core.types.Wei;
import io.brane.core.types.Address;
import io.brane.core.types.HexData;

public interface Signer {
    HexData signTransaction(TransactionRequest request);
}
```

`Signer` may *internally* delegate to `Web3jTransactionSigner`, but user code never sees web3j.

---

### 5. No web3j exceptions escaping

* `org.web3j.*` exceptions must **never** bubble out of public methods.
* Catch them inside internal adapters and rethrow as:

  * `RpcException` / `RevertException`, or
  * Another `BraneException` subtype.

Example (forbidden):

```java
public Object read(...) throws IOException { // ❌ or throws org.web3j.protocol.core.JsonRpcError
    // ...
}
```

Correct:

```java
public Object read(...) throws RpcException, RevertException {
    try {
        // web3j call inside internal adapter
    } catch (org.web3j.protocol.exceptions.ClientConnectionException e) {
        throw new RpcException(/* map code/message */, e);
    }
}
```

---

### 6. Core module must be web3j-free

* `brane-core` (`io.brane.core.*`) **must not depend** on web3j at all.

  * No imports from `org.web3j.*`.
  * No references to “web3j” in type names, method names, or Javadoc.
* `brane-core` is pure Brane domain: types + errors only.

---

### 7. CI / review guideline (quick checks)

When adding/modifying code:

1. Check imports:

   * If the file is not under `io.brane.internal.*`, there must be **no** `org.web3j.*` imports.
2. Check public APIs:

   * Any `public` method, constructor, field, or record component must use only JDK + Brane types.
3. If you’re unsure:

   * Put the code in `io.brane.internal.web3j.*` and adapt it via Brane types.