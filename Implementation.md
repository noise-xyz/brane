## Implementation.md (Step 6 – Historical log fetching)

You can paste this as `Implementation.md` (or append under a new `## Step 6` section).

````md
# Step 6 – Historical log fetching (ERC-20 Transfer events)

## Goal

Add **log fetching + ABI-based event decoding** so Brane can:

1. Fetch logs via `eth_getLogs` with:
   - `fromBlock` / `toBlock`
   - `address`
   - `topics` array
2. Decode those logs into **typed Java event objects** using the existing ABI metadata.

Milestone:

> “Fetch and decode all ERC-20 `Transfer` events in a block range.”

We want a DX like:

```java
List<LogEntry> logs = publicClient.getLogs(filter);
List<TransferEvent> events = contract.decodeEvents("Transfer", logs, TransferEvent.class);
````

---

## Guardrails / Constraints

* **No new dependency from `brane-rpc` → `brane-contract`.**

  * `brane-contract` is allowed to depend on `brane-rpc` models (it already uses `Client`, `PublicClient`, etc.).
  * `brane-rpc` must remain free of `io.brane.contract.*` imports.
* Do **not** break Steps 0–5:

  * `DefaultWalletClient`, `TransactionSigner`, `PrivateKeyTransactionSigner`, `ReadOnlyContract`, `ReadWriteContract`, `Erc20Example`, `Erc20TransferExample` must all continue to compile and tests must remain green.
* Prefer **additive** changes:

  * Only touch existing code when necessary for integration.
  * Add new classes / methods where possible instead of rewriting old ones.
* Keep code style aligned with existing code (immutability bias, small value types, checked domain exceptions, etc.).

---

## 1. RPC log models + `getLogs` in `brane-rpc`

### 1.1 Add a `LogEntry` model

Create a log model in `brane-rpc`, e.g.:

* Package: `io.brane.rpc.model` (or wherever existing block/tx models live).
* Class: `LogEntry`

Include at least:

```java
public final class LogEntry {
    private final String address;          // hex-encoded address (0x...)
    private final List<String> topics;     // topic0..topicN (0x...)
    private final String data;             // 0x... ABI-encoded data

    private final String blockHash;        // nullable
    private final String blockNumber;      // hex string, nullable
    private final String transactionHash;  // nullable
    private final String transactionIndex; // hex, nullable
    private final String logIndex;         // hex, nullable
    private final boolean removed;         // default false

    // constructor, getters, equals/hashCode/toString
}
```

Implementation notes:

* Match JSON-RPC `eth_getLogs` shape.
* Use the same Jackson / JSON mapping strategy as existing RPC models
  (whatever `brane-rpc` is already using for `Block`, `Transaction`, etc.).

### 1.2 Add a `LogFilter` type

Create a filter type for log queries, e.g.:

* Package: `io.brane.rpc.model`
* Class: `LogFilter`

Fields:

* `String fromBlock;`

  * E.g. `"0x1"`, `"latest"`, `"earliest"`, or `"0x0"` etc.
* `String toBlock;`
* `String address;`

  * The contract address to filter on (ERC-20 address).
* `List<Object> topics;`

  * Represent the JSON-RPC `topics` array. Support:

    * `null` → wildcard
    * `String` (single topic)
    * `List<String>` (OR group, e.g. `[topic1, topic2]`)

Add a helper builder/static factory, e.g.:

```java
public static LogFilter forSingleAddressAndTopic(
        String fromBlock,
        String toBlock,
        String address,
        String topic0) {
    // topics[0] = topic0, rest = null
}
```

### 1.3 Implement `PublicClient.getLogs`

Extend `io.brane.rpc.PublicClient` with a **read-only** method:

```java
public List<LogEntry> getLogs(LogFilter filter) throws RpcException
```

Behavior:

* Build a JSON filter object:

  ```json
  {
    "fromBlock": "0x...",
    "toBlock": "0x...",
    "address": "0x...",
    "topics": [ "0x...", null, ... ]
  }
  ```

* Call `eth_getLogs` via the underlying `BraneProvider`.

* Decode the result into `List<LogEntry>`.

Error handling:

* If the node returns the typical `"query returned more than X results"` error (`-32005` or similar),
  wrap it into a **clear** `RpcException` message (or a dedicated `BraneRpcException` if that’s existing style),
  e.g. `"eth_getLogs: block range too large"`.

Testing:

* Add `PublicClientLogsTest` (or extend an existing test suite) that:

  * Uses a fake `BraneProvider` to capture the RPC method name + params.
  * Verifies:

    * Correct JSON shape for `eth_getLogs` requests.
    * Proper decoding of a small sample log response into `LogEntry`.
    * Clear error message when the provider returns an error for `eth_getLogs`.

---

## 2. ABI-based event decoding in `brane-contract`

We already have:

* `Abi` parsing JSON ABI.
* `eventTopic(String)` helper for `topic0`.
* Internal ABI machinery (`InternalAbi`) for encoding/decoding.

Now we want to reuse that to decode logs.

### 2.1 Add event decoding helper to `Abi` / `InternalAbi`

In `io.brane.contract`:

* Add methods (or a small internal type) to decode events from a single `LogEntry`.

Example shape (adjust to fit existing patterns):

```java
public final class Abi {

    // Existing stuff...

    public <T> T decodeEvent(
            String eventName,
            LogEntry log,
            Class<T> eventType) throws AbiDecodingException {
        // 1. Find the event in the ABI by name
        // 2. Compute topic0 via eventTopic(signature)
        // 3. Check log.address and log.topics[0] match
        // 4. Decode indexed + non-indexed arguments via InternalAbi
        // 5. Map decoded values into eventType (POJO or record)
    }

    public <T> List<T> decodeEvents(
            String eventName,
            List<LogEntry> logs,
            Class<T> eventType) throws AbiDecodingException {
        // map over logs, filter by address + topic0, decode each
    }
}
```

Implementation notes:

* **Address filtering:** optional but nice. In most cases, the caller will already filter by address in `eth_getLogs`, but filtering again is a cheap guard.
* **Topic filtering:**

  * Use `Abi.eventTopic("Transfer(address,address,uint256)")` internally
  * Compare with `log.getTopics().get(0)`
* **Mapping into `eventType`:**

  * For this step, you can assume `eventType` is a **record** or simple POJO in the example
    whose constructor parameters match the decoded event arguments in order.
  * For ERC-20 `Transfer`, that means: `from`, `to`, `value` (plus optionally `txHash`, `blockNumber` if you choose to include metadata).
* Throw `AbiDecodingException` with a clear message when:

  * The event name is not found in the ABI.
  * Topics/data length doesn’t match expectations.
  * Mapping into `eventType` fails.

### 2.2 Expose event decoding via `ReadOnlyContract` / `ReadWriteContract`

Extend the contract facades to make event decoding ergonomic.

In `ReadOnlyContract` (and inherited by `ReadWriteContract`):

```java
public <T> List<T> decodeEvents(
        String eventName,
        List<LogEntry> logs,
        Class<T> eventType) throws AbiDecodingException {
    return abi.decodeEvents(eventName, logs, eventType);
}
```

* No RPC calls here — this is purely a helper on top of the ABI and log data.
* Keep it symmetrical with how function calls are encoded/decoded.

Testing:

* Add/extend a test class, e.g. `AbiEventDecodingTest` or `ReadOnlyContractEventTest`, to:

  * Build a fake ERC-20 ABI containing a `Transfer` event.
  * Construct a synthetic `LogEntry` with:

    * `topics[0] = eventTopic("Transfer(address,address,uint256)")`
    * indexed topic encodings for `from` and `to`
    * data encoding for `value`
  * Verify `decodeEvents("Transfer", List.of(log), TransferEvent.class)` returns the expected values.

---

## 3. Example: ERC-20 Transfer event history

Add a small example to `brane-examples` that ties everything together.

### 3.1 `Erc20TransferLogExample`

Create:

* `brane-examples/src/main/java/io/brane/examples/Erc20TransferLogExample.java`

Behavior:

1. Read system properties:

   ```text
   -Dbrane.examples.erc20.rpc=<RPC URL>
   -Dbrane.examples.erc20.contract=<ERC-20 contract address>
   -Dbrane.examples.erc20.fromBlock=<hex or "latest - N">
   -Dbrane.examples.erc20.toBlock=<hex or "latest">
   ```

   For simplicity, you can require hex strings, e.g. `0x1`, `0x3`, etc.

2. Construct:

   ```java
   BraneProvider provider = HttpBraneProvider.builder(rpcUrl).build();
   PublicClient publicClient = PublicClient.from(provider);
   Abi abi = Abi.fromJson(ERC20_ABI_WITH_TRANSFER);
   ReadOnlyContract contract = ReadOnlyContract.from(tokenAddress, abi, publicClient);
   ```

   Make sure the ERC-20 ABI for this example includes the `Transfer` event definition.

3. Build a `LogFilter`:

   ```java
   LogFilter filter =
       LogFilter.forSingleAddressAndTopic(
           fromBlock,
           toBlock,
           tokenAddress,
           Abi.eventTopic("Transfer(address,address,uint256)").value());
   ```

4. Fetch logs:

   ```java
   List<LogEntry> logs = publicClient.getLogs(filter);
   ```

5. Define a small `TransferEvent` record inside the example:

   ```java
   public record TransferEvent(Address from, Address to, BigInteger value) {}
   ```

6. Decode:

   ```java
   List<TransferEvent> events =
       contract.decodeEvents("Transfer", logs, TransferEvent.class);
   ```

7. Print them:

   ```java
   for (TransferEvent e : events) {
       System.out.println(
           "Transfer from " + e.from()
               + " to " + e.to()
               + " value " + e.value());
   }
   ```

A typical run command (assuming Anvil and your `BraneToken` example):

```bash
./gradlew :brane-examples:run --no-daemon \
  -PmainClass=io.brane.examples.Erc20TransferLogExample \
  -Dbrane.examples.erc20.rpc=http://127.0.0.1:8545 \
  -Dbrane.examples.erc20.contract=0xa513E6E4b8f2a923D98304ec87F64353C4D5C853 \
  -Dbrane.examples.erc20.fromBlock=0x0 \
  -Dbrane.examples.erc20.toBlock=latest
```

You should see the `Transfer` events emitted by:

* The constructor mint
* Any `transfer` calls you made via `Erc20TransferExample`

---

## 4. Verification checklist

After implementing Step 6:

1. All tests remain green:

   ```bash
   ./gradlew :brane-rpc:test --no-daemon
   ./gradlew :brane-contract:test --no-daemon
   ./gradlew clean check --no-daemon
   ```

2. On a running Anvil + `BraneToken` deployment, you can:

   * Use `Erc20TransferExample` to send a few transfers.
   * Use `Erc20TransferLogExample` to:

     * Fetch logs over a block range.
     * Decode them into `TransferEvent` objects.
     * Print them with sensible values.

At that point Step 6 is functionally complete.

````