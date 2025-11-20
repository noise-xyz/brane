

````md
## Step 2 – Read-only “PublicClient” (brane-rpc)

Now that the Provider abstraction is solid and Contract.read works end-to-end, we add an ergonomic **read-only client** on top of BraneProvider.

The goals:

- Give developers a simple, typed API for **read** operations:
  - `getLatestBlock`
  - `getBlockByNumber`
  - `getTransactionByHash`
  - `call` (generic `eth_call`)
- Keep it in **brane-rpc** (no ABI, no signing, no web3j).
- Use our **core model types** from `brane-core` (`Hash`, `Address`, `Transaction`, etc.).
- Zero web3j type leakage (guardrails still apply).

---

### 1. Placement & dependencies

We keep the module graph:

```text
brane-core       ←  brane-rpc        ←  brane-contract
     ↑                                 ↑
     └─────────────────────────────────┘
````

PublicClient lives in **brane-rpc**:

```text
brane-rpc/src/main/java/io/brane/rpc/
  BraneProvider.java
  HttpBraneProvider.java
  JsonRpcRequest.java
  JsonRpcResponse.java
  JsonRpcError.java
  RpcConfig.java
  HttpClient.java
  PublicClient.java          // NEW (interface)
  DefaultPublicClient.java   // NEW (implementation)
```

It depends on `brane-core` value/model types:

* `io.brane.core.types.Hash`
* `io.brane.core.types.Address`
* `io.brane.core.types.Wei`
* `io.brane.core.model.Transaction`
* `io.brane.core.model.TransactionReceipt`
* `io.brane.core.model.LogEntry`
* `io.brane.core.model.BlockHeader` (we’ll add this).

---

### 2. Add BlockHeader model in brane-core

In `brane-core/src/main/java/io/brane/core/model/` add:

```text
BlockHeader.java
```

This is a minimal view of an Ethereum block for read-only APIs:

```java
package io.brane.core.model;

import io.brane.core.types.Hash;

public record BlockHeader(
    Hash hash,
    Long number,
    Hash parentHash,
    Long timestamp
) {}
```

Notes:

* We only include the fields we need for now:

  * `hash` (`blockHash` from JSON-RPC)
  * `number` (decoded from hex `number`)
  * `parentHash`
  * `timestamp` (decoded from hex)
* JSON-RPC returns hex strings; `DefaultPublicClient` will do the mapping.

Later, if needed, we can add more fields or a richer `Block` model. For 0.1.0, this is enough.

---

### 3. PublicClient interface (brane-rpc)

Create:

```java
package io.brane.rpc;

import io.brane.core.model.BlockHeader;
import io.brane.core.model.Transaction;
import io.brane.core.types.Hash;
import java.util.Map;

public interface PublicClient {

    BlockHeader getLatestBlock();

    BlockHeader getBlockByNumber(long blockNumber);

    Transaction getTransactionByHash(Hash hash);

    /**
     * Raw eth_call. Returns the hex-encoded result (e.g. "0x...").
     */
    String call(Map<String, Object> callObject, String blockTag);
}
```

Notes:

* `blockTag` is a JSON-RPC tag, e.g. `"latest"`, `"pending"`, or a hex block number string. For now we only need `"latest"`.
* `callObject` is the same shape we already use in `Contract.read`: `{ "to": "0x...", "data": "0x..." }`. PublicClient doesn’t know ABI; it just returns hex strings.

---

### 4. DefaultPublicClient implementation

Add:

```text
brane-rpc/src/main/java/io/brane/rpc/DefaultPublicClient.java
```

Implementation sketch:

```java
package io.brane.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.brane.core.error.RpcException;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.Transaction;
import io.brane.core.types.Hash;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

public final class DefaultPublicClient implements PublicClient {

    private final BraneProvider provider;
    private final ObjectMapper mapper;

    public DefaultPublicClient(BraneProvider provider) {
        this.provider = provider;
        this.mapper = new ObjectMapper();
    }

    @Override
    public BlockHeader getLatestBlock() throws RpcException {
        return getBlockByTag("latest");
    }

    @Override
    public BlockHeader getBlockByNumber(long blockNumber) throws RpcException {
        String hex = "0x" + Long.toHexString(blockNumber);
        return getBlockByTag(hex);
    }

    private BlockHeader getBlockByTag(String tag) throws RpcException {
        JsonRpcResponse rpcResponse =
            provider.send("eth_getBlockByNumber", List.of(tag, false));

        Object result = rpcResponse.result();
        if (result == null) {
            return null; // block not found
        }

        Map<?, ?> blockMap = mapper.convertValue(result, Map.class);

        String hash = (String) blockMap.get("hash");
        String parentHash = (String) blockMap.get("parentHash");
        String numberHex = (String) blockMap.get("number");
        String timestampHex = (String) blockMap.get("timestamp");

        Long number = numberHex != null ? new BigInteger(numberHex.substring(2), 16).longValue() : null;
        Long timestamp = timestampHex != null ? new BigInteger(timestampHex.substring(2), 16).longValue() : null;

        return new BlockHeader(
            hash != null ? new Hash(hash) : null,
            number,
            parentHash != null ? new Hash(parentHash) : null,
            timestamp
        );
    }

    @Override
    public Transaction getTransactionByHash(Hash hash) throws RpcException {
        JsonRpcResponse rpcResponse =
            provider.send("eth_getTransactionByHash", List.of(hash.value()));

        Object result = rpcResponse.result();
        if (result == null) {
            return null; // tx not found
        }

        return mapper.convertValue(result, Transaction.class);
    }

    @Override
    public String call(Map<String, Object> callObject, String blockTag) throws RpcException {
        JsonRpcResponse rpcResponse =
            provider.send("eth_call", List.of(callObject, blockTag));

        Object result = rpcResponse.result();
        return result != null ? result.toString() : null;
    }
}
```

Rules:

* Do **not** catch `RpcException` here; propagate it.
* Do **not** introduce `org.web3j` types (guardrails).
* `result == null` is treated as “not found” and returns `null` for block/tx directly.

We can later add a static factory:

```java
public static PublicClient from(BraneProvider provider) {
    return new DefaultPublicClient(provider);
}
```

either in `PublicClient` or a small `PublicClients` utility.

---

### 5. Tests for PublicClient

Create:

```text
brane-rpc/src/test/java/io/brane/rpc/DefaultPublicClientTest.java
```

Use a simple FakeProvider (like we did for FakeClient in Contract tests) to simulate responses.

#### 5.1 FakeProvider

```java
private static final class FakeProvider implements BraneProvider {
    private final Map<String, JsonRpcResponse> responses;

    FakeProvider(Map<String, JsonRpcResponse> responses) {
        this.responses = responses;
    }

    @Override
    public JsonRpcResponse send(String method, List<?> params) throws RpcException {
        JsonRpcResponse resp = responses.get(method);
        if (resp == null) {
            throw new RpcException(-32601, "Method not mocked: " + method, null, null);
        }
        if (resp.error() != null) {
            throw new RpcException(resp.error().code(), resp.error().message(), String.valueOf(resp.error().data()), null);
        }
        return resp;
    }
}
```

#### 5.2 Tests

1. **getLatestBlock maps JSON → BlockHeader**

   * Mock `eth_getBlockByNumber` with result:

     ```json
     {
       "hash": "0xabc...",
       "parentHash": "0xdef...",
       "number": "0x10",
       "timestamp": "0x5"
     }
     ```

   * Assert:

     * `header.hash().value()` equals that hash.
     * `header.number()` is 16.
     * `header.timestamp()` is 5.

2. **getBlockByNumber(1) uses hex and maps correctly**

   * Same as above, but call `client.getBlockByNumber(1L)` and assert same mapping.

3. **getTransactionByHash returns Transaction**

   * Mock `eth_getTransactionByHash` returning a minimal tx JSON object with fields that match `io.brane.core.model.Transaction`.
   * Assert that the returned `Transaction` has the right hash and from/to addresses.

4. **call returns raw hex**

   * Mock `eth_call` returning `"0x2a"`.
   * Assert `client.call(callObject, "latest")` returns `"0x2a"`.

All tests must stay within Brane types and JDK; no web3j imports. Guardrails still apply.

---

### 6. Example usage (for docs)

Once implemented, we can document a simple usage in README (not part of this step’s code change, but for later):

```java
BraneProvider provider = BraneProvider.http("http://127.0.0.1:8545");
PublicClient publicClient = new DefaultPublicClient(provider);

BlockHeader latest = publicClient.getLatestBlock();
System.out.println("Latest block: #" + latest.number() + " hash=" + latest.hash().value());
```

This gives the “Brane: get latest block in 3 lines” demo we wanted.

---