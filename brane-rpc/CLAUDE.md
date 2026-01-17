# brane-rpc

JSON-RPC client layer with HTTP and WebSocket transports.

## Commands

```bash
# Compile
./gradlew :brane-rpc:compileJava

# Run tests
./gradlew :brane-rpc:test

# Run specific test
./gradlew :brane-rpc:test --tests "sh.brane.rpc.MyTest"
```

## Package Structure

| Package | Purpose |
|---------|---------|
| `sh.brane.rpc` | Clients and providers |
| `sh.brane.rpc.exception` | RPC-specific exceptions (`RetryExhaustedException`, `SimulateNotSupportedException`) |
| `sh.brane.rpc.internal` | Internal utilities |

## Key Classes

### Brane Client API (High-Level)
- **`Brane`** - Sealed interface entry point for blockchain interaction
- **`Brane.Reader`** - Read-only client (queries, calls, subscriptions)
- **`Brane.Signer`** - Full client with transaction signing capability
- **`Brane.Tester`** - Test node client (Anvil/Hardhat) with state manipulation
- **`DefaultReader`** - Implementation of `Brane.Reader`
- **`DefaultSigner`** - Implementation of `Brane.Signer`

### Providers (Transport Layer)
- **`BraneProvider`** - Core interface: `send(method, params)`, `subscribe()`, `unsubscribe()`
- **`HttpBraneProvider`** - HTTP/HTTPS transport (virtual thread-based)
- **`WebSocketProvider`** - WebSocket transport (Netty + Disruptor)

### Utilities
- **`SmartGasStrategy`** - Intelligent gas estimation with fallback metadata
- **`RpcRetry`** - Retry logic for transient failures
- **`LogFilter`** - Event log query builder
- **`Subscription`** - WebSocket subscription handle
- **`BraneMetrics`** - Observability interface for request/connection metrics
- **`MulticallBatch`** - Batches multiple calls into single `eth_call` via Multicall3
- **`CallRequest`** - Type-safe builder for `eth_call` parameters
- **`SimulateRequest`** / **`SimulateResult`** - `eth_simulateV1` support
- **`SnapshotId`** - Blockchain state snapshot identifier for `Tester`
- **`ImpersonationSession`** - AutoCloseable session for impersonating addresses

### Internal
- **`RpcUtils`** - Shared hex encoding, URL validation, ObjectMapper

## Client Hierarchy

```
Brane (sealed interface)
├── Brane.Reader (read-only operations)
│   └── DefaultReader
├── Brane.Signer (extends Reader + transaction signing)
│   └── DefaultSigner
└── Brane.Tester (test node operations: mining, time, state manipulation)
    └── DefaultTester
```

## Patterns

### Creating Clients

```java
// Simple read-only client
Brane client = Brane.connect("https://eth-mainnet.g.alchemy.com/v2/...");

// With signer for transactions
Brane.Signer client = Brane.connect("https://eth.example.com", signer);

// Using builder for advanced configuration
Brane client = Brane.builder()
    .rpcUrl("https://eth.example.com")
    .chain(ChainProfiles.MAINNET)
    .retries(5)
    .build();

// Explicit Reader or Signer via builder
Brane.Reader reader = Brane.builder().rpcUrl("...").buildReader();
Brane.Signer signer = Brane.builder().rpcUrl("...").signer(key).buildSigner();
```

### Pattern Matching on Client Type

```java
Brane client = Brane.connect("https://eth.example.com");
switch (client) {
    case Brane.Reader r -> System.out.println("Read-only client");
    case Brane.Signer s -> System.out.println("Signing client");
}

// Or use canSign() helper
if (client.canSign()) {
    ((Brane.Signer) client).sendTransaction(request);
}
```

### Read Operations

```java
BigInteger balance = client.getBalance(address);
BlockHeader block = client.getLatestBlock();
List<LogEntry> logs = client.getLogs(filter);

// Type-safe eth_call
CallRequest request = CallRequest.builder()
    .to(contractAddress)
    .data(encodedFunctionCall)
    .build();
HexData result = client.call(request);
```

### Write Operations (Brane.Signer only)

```java
// Fire and forget
Hash txHash = client.sendTransaction(request);

// Wait for confirmation (default 60s timeout)
TransactionReceipt receipt = client.sendTransactionAndWait(request);

// Custom timeout and poll interval
TransactionReceipt receipt = client.sendTransactionAndWait(request, 120_000, 2_000);
```

### EIP-4844 Blob Transactions (Brane.Signer only)

```java
// Build blob transaction request (see brane-core for Eip4844Builder)
BlobTransactionRequest blobRequest = Eip4844Builder.create()
    .to(recipient)
    .blobData(rawBytes)
    .build(kzg);

// Send blob transaction and wait for confirmation
TransactionReceipt receipt = client.sendBlobTransactionAndWait(blobRequest);

// Fire and forget (returns hash immediately)
Hash txHash = client.sendBlobTransaction(blobRequest);

// Custom timeout and poll interval
TransactionReceipt receipt = client.sendBlobTransactionAndWait(blobRequest, 120_000, 2_000);
```

### Simulation (eth_simulateV1)

```java
SimulateRequest request = SimulateRequest.builder()
    .account(senderAddress)
    .call(SimulateCall.builder()
        .to(contractAddress)
        .data(encodedFunctionCall)
        .build())
    .traceAssetChanges(true)
    .build();
SimulateResult result = client.simulate(request);
for (CallResult callResult : result.results()) {
    System.out.println("Success: " + callResult.success());
}
```

### Subscriptions (WebSocket only)

```java
// Check if subscriptions are supported
if (client.canSubscribe()) {
    Subscription sub = client.onNewHeads(header -> {
        System.out.println("New block: " + header.number());
    });

    // Or subscribe to logs
    LogFilter filter = LogFilter.byContract(contractAddress, List.of(transferTopic));
    Subscription logSub = client.onLogs(filter, log -> {
        System.out.println("Log: " + log.data());
    });

    // Later, when done:
    sub.unsubscribe();
}
```

### WebSocket Connection State

```java
WebSocketProvider.ConnectionState state = wsProvider.getConnectionState();
// States: CONNECTING, CONNECTED, RECONNECTING, CLOSED
```

### Metrics Integration

```java
BraneMetrics metrics = new MyMetrics(); // Implement for Micrometer/Prometheus
provider.setMetrics(metrics);
// Hooks: onRequestStarted, onRequestCompleted, onRequestFailed, onConnectionLost, etc.
```

### Batching (Multicall)

```java
MulticallBatch batch = client.batch();
ERC20 token = batch.bind(ERC20.class, tokenAddress, ERC20_ABI);
BatchHandle<BigInteger> balance1 = batch.add(token.balanceOf(addr1));
BatchHandle<BigInteger> balance2 = batch.add(token.balanceOf(addr2));
batch.execute();
System.out.println("Balance 1: " + balance1.get().data());
System.out.println("Balance 2: " + balance2.get().data());
```

### Tester Operations (Anvil/Hardhat)

```java
// Connect to test node with default funded signer
try (Brane.Tester tester = Brane.connectTest("http://127.0.0.1:8545")) {
    // Snapshot and revert (isolated test cases)
    SnapshotId snapshot = tester.snapshot();
    // ... perform test operations ...
    tester.revert(snapshot);

    // Account manipulation
    tester.setBalance(address, Wei.fromEther("1000"));
    tester.setNonce(address, 42);
    tester.setCode(address, bytecode);
    tester.setStorageAt(address, slot, value);

    // Impersonation (send as any address without private key)
    try (ImpersonationSession session = tester.impersonate(whaleAddress)) {
        session.sendTransactionAndWait(request);
    } // Impersonation automatically stopped

    // Time manipulation
    tester.increaseTime(86400); // Advance 1 day
    tester.setNextBlockTimestamp(futureTimestamp);

    // Mining control
    tester.mine();           // Single block
    tester.mine(10);         // Multiple blocks
    tester.mine(5, 12);      // 5 blocks, 12 seconds apart
    tester.setAutomine(false); // Disable auto-mining

    // State dump/load (persist test fixtures)
    HexData state = tester.dumpState();
    tester.loadState(state);

    // Reset chain
    tester.reset();
    tester.reset(forkUrl, blockNumber);

    // EIP-4844 blob transactions (requires Cancun fork)
    // Start Anvil with: anvil --hardfork cancun
    BlobTransactionRequest blobRequest = Eip4844Builder.create()
        .to(recipient)
        .blobData(rawBytes)
        .build(kzg);
    Hash txHash = tester.sendBlobTransaction(blobRequest);
}
```

## Gotchas

- **Virtual threads**: HTTP provider uses virtual threads - don't block with synchronized
- **WebSocket callbacks**: Run on virtual threads, not Netty I/O thread
- **Retry logic**: `RpcRetry` handles transient failures; configure via `Brane.builder().retries()`
- **Connection pooling**: HTTP provider manages connection pooling internally
- **MulticallBatch ThreadLocal**: Call `batch.clearPending()` in catch blocks between proxy call and `add()` to prevent leaks in thread pools
- **Closed client state**: Calling methods on a closed `Brane` instance throws `IllegalStateException`
- **WebSocket state machine**: Check `getConnectionState()` before operations - `RECONNECTING` state may cause temporary failures
- **Subscription errors**: `unsubscribe()` is idempotent and swallows errors (logged at WARN level)
- **Tester snapshots**: Snapshots are consumed on revert - take a new snapshot if you need to revert multiple times
- **Tester impersonation cleanup**: Always use try-with-resources for `ImpersonationSession` to ensure cleanup
- **Blob transactions require Cancun**: Anvil must be started with `--hardfork cancun` for EIP-4844 support

## Dependencies

- Netty (WebSocket)
- Disruptor (WebSocket message queue)
- brane-core (types, models)
