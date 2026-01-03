# brane-rpc

JSON-RPC client layer with HTTP and WebSocket transports.

## Package Structure

| Package | Purpose |
|---------|---------|
| `io.brane.rpc` | Clients and providers |
| `io.brane.rpc.internal` | Internal utilities |

## Key Classes

### Providers (Transport Layer)
- **`BraneProvider`** - Core interface: `send(method, params)`, `subscribe()`, `unsubscribe()`
- **`HttpBraneProvider`** - HTTP/HTTPS transport (virtual thread-based)
- **`WebSocketProvider`** - WebSocket transport (Netty + Disruptor)

### Clients (High-Level API)
- **`PublicClient`** - Read-only operations (interface)
- **`DefaultPublicClient`** - PublicClient implementation
- **`WalletClient`** - Transaction submission (interface)
- **`DefaultWalletClient`** - WalletClient implementation
- **`BraneAsyncClient`** - Async/futures-based API

### Utilities
- **`SmartGasStrategy`** - Intelligent gas estimation with fallback metadata
- **`RpcRetry`** - Retry logic for transient failures
- **`LogFilter`** - Event log query builder
- **`Subscription`** - WebSocket subscription handle
- **`BraneMetrics`** - Observability interface for request/connection metrics
- **`MulticallBatch`** - Batches multiple calls into single `eth_call` via Multicall3

### Internal
- **`RpcUtils`** - Shared hex encoding, URL validation, ObjectMapper

## Client Hierarchy

```
BraneProvider (raw RPC)
      ↓
PublicClient (read operations)
      ↓
WalletClient (extends PublicClient + write operations)
```

## Patterns

### Creating Clients
```java
// HTTP client
var provider = HttpBraneProvider.create("https://eth-mainnet.g.alchemy.com/v2/...");
var publicClient = DefaultPublicClient.create(provider);

// With signer for transactions
var walletClient = DefaultWalletClient.create(provider, signer);
```

### Read Operations
```java
BigInteger balance = publicClient.getBalance(address);
BlockHeader block = publicClient.getLatestBlock();
List<Log> logs = publicClient.getLogs(filter);
HexData result = publicClient.call(to, calldata);
```

### Write Operations
```java
// Fire and forget
Hash txHash = walletClient.sendTransaction(request);

// Wait for confirmation
TransactionReceipt receipt = walletClient.sendTransactionAndWait(request);
```

### Subscriptions (WebSocket only)
```java
var wsProvider = WebSocketProvider.create("wss://...");
Subscription sub = wsProvider.subscribe("newHeads", params, notification -> {
    // Handle new block
});
sub.unsubscribe();
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
var batch = publicClient.createBatch();
var balanceCall = batch.add(publicClient::getBalance, address);
var blockCall = batch.add(publicClient::getLatestBlock);
batch.execute();
BigInteger balance = balanceCall.get();
```

## Gotchas

- **Virtual threads**: HTTP provider uses virtual threads - don't block with synchronized
- **WebSocket callbacks**: Run on virtual threads, not Netty I/O thread
- **Retry logic**: `RpcRetry` handles transient failures; configure for your use case
- **Connection pooling**: HTTP provider manages connection pooling internally
- **MulticallBatch ThreadLocal**: Call `batch.clearPending()` in catch blocks between proxy call and `add()` to prevent leaks in thread pools
- **Closed client state**: Calling methods on a closed `BranePublicClient` throws `IllegalStateException`
- **WebSocket state machine**: Check `getConnectionState()` before operations - `RECONNECTING` state may cause temporary failures

## Dependencies

- Netty (WebSocket)
- Disruptor (WebSocket message queue)
- brane-core (types, models)
