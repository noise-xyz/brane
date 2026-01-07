# Brane RPC Architecture Specification

> Java 21-native RPC layer using sealed types, pattern matching, and virtual threads.

---

## Table of Contents

1. [Architecture Overview](#architecture-overview)
2. [Core Design Principles](#core-design-principles)
3. [Type Hierarchy](#type-hierarchy)
4. [Transport Layer](#transport-layer)
5. [API Reference](#api-reference)
6. [Usage Examples](#usage-examples)
7. [Implementation Details](#implementation-details)
8. [Migration Guide](#migration-guide)

---

## Architecture Overview

### System Diagram

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                              USER APPLICATION                               │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                            Brane (sealed interface)                         │
│                                                                             │
│   ┌─────────────────────────────┐     ┌─────────────────────────────────┐   │
│   │       Brane.Reader          │     │         Brane.Signer            │   │
│   │      (read-only ops)        │     │    (read + write ops)           │   │
│   │                             │     │                                 │   │
│   │  • getBalance()             │     │  • getBalance()      [inherit]  │   │
│   │  • call()                   │     │  • call()            [inherit]  │   │
│   │  • getLogs()                │     │  • getLogs()         [inherit]  │   │
│   │  • getBlock()               │     │  • sendTransaction()            │   │
│   │  • estimateGas()            │     │  • sendTransactionAndWait()     │   │
│   │  • simulate()               │     │  • signer()                     │   │
│   └─────────────────────────────┘     └─────────────────────────────────┘   │
│                 │                                   │                       │
│                 │         ┌─────────────────────────┘                       │
│                 │         │                                                 │
│                 ▼         ▼                                                 │
│   ┌─────────────────────────────────────────────────────────────────────┐   │
│   │                      DefaultReader / DefaultSigner                  │   │
│   │                         (implementations)                           │   │
│   └─────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                         BraneProvider (interface)                           │
│                                                                             │
│            ┌──────────────────────┬──────────────────────┐                  │
│            │                      │                      │                  │
│            ▼                      ▼                      ▼                  │
│   ┌─────────────────┐   ┌─────────────────┐   ┌─────────────────────┐       │
│   │ HttpBraneProvider│   │WebSocketProvider│   │  Custom Provider   │       │
│   │                 │   │                 │   │   (e.g., IPC)      │       │
│   │  • HTTP/1.1     │   │  • Persistent   │   │                    │       │
│   │  • HTTP/2       │   │  • Subscriptions│   │                    │       │
│   │  • Request/Resp │   │  • Bidirectional│   │                    │       │
│   └─────────────────┘   └─────────────────┘   └────────────────────┘       │
│            │                      │                      │                  │
└────────────┼──────────────────────┼──────────────────────┼──────────────────┘
             │                      │                      │
             ▼                      ▼                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                           ETHEREUM JSON-RPC NODE                            │
│                     (Geth, Erigon, Anvil, Infura, etc.)                     │
└─────────────────────────────────────────────────────────────────────────────┘
```

### Capability Matrix

```
┌────────────────────────┬─────────────┬─────────────┐
│      Capability        │   Reader    │   Signer    │
├────────────────────────┼─────────────┼─────────────┤
│ eth_chainId            │     ✓       │     ✓       │
│ eth_getBalance         │     ✓       │     ✓       │
│ eth_call               │     ✓       │     ✓       │
│ eth_getLogs            │     ✓       │     ✓       │
│ eth_getBlockByNumber   │     ✓       │     ✓       │
│ eth_estimateGas        │     ✓       │     ✓       │
│ eth_simulateV1         │     ✓       │     ✓       │
│ eth_subscribe (WS)     │     ✓       │     ✓       │
├────────────────────────┼─────────────┼─────────────┤
│ eth_sendRawTransaction │     ✗       │     ✓       │
│ Transaction signing    │     ✗       │     ✓       │
└────────────────────────┴─────────────┴─────────────┘
```

### Transport Capabilities

```
┌────────────────────────┬─────────────┬─────────────┐
│      Feature           │    HTTP     │  WebSocket  │
├────────────────────────┼─────────────┼─────────────┤
│ Request/Response       │     ✓       │     ✓       │
│ Batch Requests         │     ✓       │     ✓       │
│ Subscriptions          │     ✗       │     ✓       │
│ Connection Pooling     │     ✓       │     N/A     │
│ Persistent Connection  │     ✗       │     ✓       │
│ Auto-Reconnect         │     N/A     │     ✓       │
└────────────────────────┴─────────────┴─────────────┘
```

---

## Core Design Principles

### 1. Single Entry Point (Like viem)

```java
// Simple: one method to remember
var client = Brane.connect("https://...");

// With signer: same method, extra param
var client = Brane.connect("https://...", mySigner);
```

**vs. Current Design:**
```java
// Must choose between two separate builders
PublicClient client = PublicClient.builder().rpcUrl(url).build();
WalletClient wallet = WalletClient.builder().rpcUrl(url).signer(s).build();
```

### 2. Compile-Time Safety via Sealed Types

```java
// The compiler KNOWS there are only two Brane types
Brane client = Brane.connect("https://...");

// Exhaustive pattern matching - compiler ensures all cases handled
String result = switch (client) {
    case Brane.Reader r -> "Read-only: " + r.getBalance(addr);
    case Brane.Signer s -> "Signer: " + s.sendTransaction(tx);
    // No default needed! Compiler knows this is exhaustive
};
```

### 3. Type-Safe Builder Variants

```java
// Compile-time guarantee: read-only
Brane.Reader reader = Brane.builder()
    .rpcUrl("https://...")
    .buildReader();

reader.sendTransaction();  // ❌ COMPILE ERROR - method doesn't exist!

// Compile-time guarantee: can sign
Brane.Signer signer = Brane.builder()
    .rpcUrl("https://...")
    .signer(mySigner)
    .buildSigner();

signer.sendTransaction(request);  // ✓ Works
```

### 4. Virtual Threads for Concurrency

```java
// Simple blocking code - virtual threads handle the scaling
var balance = client.getBalance(address);  // Blocks, but cheap

// Parallel execution with virtual threads
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    var futures = addresses.stream()
        .map(addr -> executor.submit(() -> client.getBalance(addr)))
        .toList();
    // Thousands of concurrent requests, minimal overhead
}
```

---

## Type Hierarchy

### Sealed Interface Diagram

```
                    ┌──────────────────────┐
                    │   Brane (sealed)     │
                    │   AutoCloseable      │
                    └──────────┬───────────┘
                               │
              ┌────────────────┴────────────────┐
              │ permits                         │ permits
              ▼                                 ▼
┌─────────────────────────┐       ┌─────────────────────────┐
│    Brane.Reader         │       │    Brane.Signer         │
│    (sealed interface)   │       │    (sealed interface)   │
└─────────────┬───────────┘       └─────────────┬───────────┘
              │ permits                         │ permits
              ▼                                 ▼
┌─────────────────────────┐       ┌─────────────────────────┐
│    DefaultReader        │       │    DefaultSigner        │
│    (final class)        │       │    (final class)        │
└─────────────────────────┘       └───────────┬─────────────┘
                                              │ has-a
                                              ▼
                                  ┌─────────────────────────┐
                                  │    DefaultReader        │
                                  │    (composition)        │
                                  └─────────────────────────┘
```

### Class Relationships

```
┌─────────────────────────────────────────────────────────────┐
│                        <<sealed>>                           │
│                          Brane                              │
├─────────────────────────────────────────────────────────────┤
│ + connect(url): Reader                          [static]    │
│ + connect(url, signer): Signer                  [static]    │
│ + builder(): Builder                            [static]    │
├─────────────────────────────────────────────────────────────┤
│ + chainId(): BigInteger                                     │
│ + getBalance(Address): BigInteger                           │
│ + call(CallRequest): HexData                                │
│ + getLogs(LogFilter): List<LogEntry>                        │
│ + estimateGas(TransactionRequest): BigInteger               │
│ + simulate(SimulateRequest): SimulateResult                 │
│ + batch(): MulticallBatch                                   │
│ + onNewHeads(Consumer): Subscription                        │
│ + onLogs(LogFilter, Consumer): Subscription                 │
│ + canSubscribe(): boolean                                   │
│ + close(): void                                             │
└─────────────────────────────────────────────────────────────┘
                              △
            ┌─────────────────┴─────────────────┐
            │                                   │
┌───────────┴───────────┐         ┌─────────────┴─────────────┐
│    <<sealed>>         │         │       <<sealed>>          │
│    Brane.Reader       │         │       Brane.Signer        │
├───────────────────────┤         ├───────────────────────────┤
│ (no additional        │         │ + sendTransaction(req):   │
│  methods - read-only  │         │     Hash                  │
│  by design)           │         │ + sendTransactionAndWait( │
└───────────────────────┘         │     req): Receipt         │
                                  │ + signer(): Signer        │
                                  └───────────────────────────┘
```

---

## Transport Layer

### Provider Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                    BraneProvider (interface)                    │
├─────────────────────────────────────────────────────────────────┤
│ + send(method, params): RpcResponse                             │
│ + sendBatch(requests): List<RpcResponse>                        │
│ + subscribe(method, params, callback): Subscription             │
│ + close(): void                                                 │
└─────────────────────────────────────────────────────────────────┘
                              △
         ┌────────────────────┼────────────────────┐
         │                    │                    │
┌────────┴────────┐  ┌────────┴────────┐  ┌───────┴────────┐
│HttpBraneProvider│  │WebSocketProvider│  │  IpcProvider   │
├─────────────────┤  ├─────────────────┤  ├────────────────┤
│ - httpClient    │  │ - wsClient      │  │ - socketPath   │
│ - connectionPool│  │ - subscriptions │  │                │
│                 │  │ - reconnectTask │  │                │
└─────────────────┘  └─────────────────┘  └────────────────┘
```

### HTTP Request Flow

```
┌────────┐     ┌─────────────┐     ┌──────────────────┐     ┌──────────┐
│ Client │────▶│ DefaultReader│────▶│ HttpBraneProvider│────▶│ RPC Node │
└────────┘     └─────────────┘     └──────────────────┘     └──────────┘
    │                │                      │                     │
    │ getBalance()   │                      │                     │
    │───────────────▶│                      │                     │
    │                │ send("eth_getBalance")                     │
    │                │─────────────────────▶│                     │
    │                │                      │  HTTP POST          │
    │                │                      │────────────────────▶│
    │                │                      │                     │
    │                │                      │  JSON Response      │
    │                │                      │◀────────────────────│
    │                │   RpcResponse        │                     │
    │                │◀─────────────────────│                     │
    │   BigInteger   │                      │                     │
    │◀───────────────│                      │                     │
```

### WebSocket Subscription Flow

```
┌────────┐     ┌─────────────┐     ┌──────────────────┐     ┌──────────┐
│ Client │────▶│ DefaultReader│────▶│WebSocketProvider │────▶│ RPC Node │
└────────┘     └─────────────┘     └──────────────────┘     └──────────┘
    │                │                      │                     │
    │ onNewHeads(cb) │                      │                     │
    │───────────────▶│                      │                     │
    │                │ subscribe("newHeads")│                     │
    │                │─────────────────────▶│                     │
    │                │                      │ eth_subscribe       │
    │                │                      │────────────────────▶│
    │                │                      │   subscription_id   │
    │                │                      │◀────────────────────│
    │  Subscription  │                      │                     │
    │◀───────────────│                      │                     │
    │                │                      │                     │
    │                │                      │  {"method":         │
    │                │                      │   "eth_subscription"│
    │                │   BlockHeader        │   ...}              │
    │                │◀─────────────────────│◀────────────────────│
    │   cb.accept()  │                      │                     │
    │◀───────────────│                      │         ...         │
    │                │                      │◀────────────────────│
    │◀───────────────│◀─────────────────────│         ...         │
```

### Async Operations with Virtual Threads

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Virtual Thread Pool                             │
│  ┌─────────────────────────────────────────────────────────────────┐    │
│  │                                                                 │    │
│  │   ┌─────────┐  ┌─────────┐  ┌─────────┐       ┌─────────┐      │    │
│  │   │ VThread │  │ VThread │  │ VThread │  ...  │ VThread │      │    │
│  │   │   #1    │  │   #2    │  │   #3    │       │   #N    │      │    │
│  │   └────┬────┘  └────┬────┘  └────┬────┘       └────┬────┘      │    │
│  │        │            │            │                 │           │    │
│  └────────┼────────────┼────────────┼─────────────────┼───────────┘    │
│           │            │            │                 │                │
│           ▼            ▼            ▼                 ▼                │
│  ┌─────────────────────────────────────────────────────────────────┐   │
│  │                    Blocking I/O (cheap!)                        │   │
│  │                                                                 │   │
│  │   getBalance()  call()     getLogs()    ...    estimateGas()   │   │
│  └─────────────────────────────────────────────────────────────────┘   │
│                                    │                                   │
└────────────────────────────────────┼───────────────────────────────────┘
                                     │
                                     ▼
                    ┌────────────────────────────────┐
                    │     HTTP Connection Pool       │
                    │   (Platform threads handle     │
                    │    actual network I/O)         │
                    └────────────────────────────────┘
```

---

## API Reference

### Brane Interface

```java
public sealed interface Brane extends AutoCloseable
    permits Brane.Reader, Brane.Signer {

    // ═══════════════════════════════════════════════════════════════
    // FACTORY METHODS
    // ═══════════════════════════════════════════════════════════════

    /** Connect to an Ethereum node (read-only). */
    static Reader connect(String rpcUrl);

    /** Connect with signing capability. */
    static Signer connect(String rpcUrl, Signer signer);

    /** Advanced configuration via builder. */
    static Builder builder();

    // ═══════════════════════════════════════════════════════════════
    // READ OPERATIONS (available on ALL clients)
    // ═══════════════════════════════════════════════════════════════

    BigInteger chainId();
    BigInteger getBalance(Address address);
    BlockHeader getLatestBlock();
    BlockHeader getBlockByNumber(long blockNumber);
    Transaction getTransactionByHash(Hash hash);
    TransactionReceipt getTransactionReceipt(Hash hash);
    HexData call(CallRequest request);
    HexData call(CallRequest request, BlockTag blockTag);
    List<LogEntry> getLogs(LogFilter filter);
    BigInteger estimateGas(TransactionRequest request);
    AccessListWithGas createAccessList(TransactionRequest request);
    SimulateResult simulate(SimulateRequest request);
    MulticallBatch batch();

    // ═══════════════════════════════════════════════════════════════
    // SUBSCRIPTIONS (require WebSocket transport)
    // ═══════════════════════════════════════════════════════════════

    Subscription onNewHeads(Consumer<BlockHeader> callback);
    Subscription onLogs(LogFilter filter, Consumer<LogEntry> callback);

    // ═══════════════════════════════════════════════════════════════
    // METADATA
    // ═══════════════════════════════════════════════════════════════

    Optional<ChainProfile> chain();
    boolean canSign();
    boolean canSubscribe();

    // ═══════════════════════════════════════════════════════════════
    // SEALED SUBTYPES
    // ═══════════════════════════════════════════════════════════════

    sealed interface Reader extends Brane permits DefaultReader {
        // Read-only by design - no additional methods
    }

    sealed interface Signer extends Brane permits DefaultSigner {
        Hash sendTransaction(TransactionRequest request);
        TransactionReceipt sendTransactionAndWait(TransactionRequest request);
        TransactionReceipt sendTransactionAndWait(
            TransactionRequest request,
            long timeoutMillis,
            long pollIntervalMillis);
        io.brane.core.Signer signer();
    }

    // ═══════════════════════════════════════════════════════════════
    // BUILDER
    // ═══════════════════════════════════════════════════════════════

    final class Builder {
        public Builder rpcUrl(String url);
        public Builder wsUrl(String url);
        public Builder provider(BraneProvider provider);
        public Builder signer(io.brane.core.Signer signer);
        public Builder chain(ChainProfile chain);
        public Builder retries(int maxRetries);
        public Builder retryConfig(RpcRetryConfig config);

        public Brane build();           // Returns Reader or Signer
        public Reader buildReader();    // Compile-time: read-only
        public Signer buildSigner();    // Compile-time: can sign
    }
}
```

---

## Usage Examples

### Example 1: Simple Read-Only

```java
// One line to connect
var client = Brane.connect("https://eth.llamarpc.com");

var balance = client.getBalance(address);
var block = client.getLatestBlock();

client.close();
```

### Example 2: With Signing

```java
// Add signer as second parameter
var client = Brane.connect("https://...", PrivateKey.from("0x..."));

// Single client for everything
var balance = client.getBalance(myAddress);
if (balance.compareTo(amount) > 0) {
    var receipt = client.sendTransactionAndWait(request);
    System.out.println("Mined in block: " + receipt.blockNumber());
}

client.close();
```

### Example 3: Pattern Matching (Java 21)

```java
// Config-driven client
Brane client = loadClientFromConfig();

// Exhaustive pattern matching - compiler enforces all cases
var result = switch (client) {
    case Brane.Reader reader -> {
        System.out.println("Read-only mode");
        yield reader.getBalance(address).toString();
    }
    case Brane.Signer signer -> {
        System.out.println("Signing mode with: " + signer.signer().address());
        yield signer.sendTransaction(request).value();
    }
};
```

### Example 4: Type-Safe Builder

```java
// When you KNOW you need signing at compile time
Brane.Signer signer = Brane.builder()
    .rpcUrl("https://...")
    .signer(PrivateKey.from("0x..."))
    .chain(Chains.MAINNET)
    .buildSigner();  // Returns Brane.Signer, not Brane

// Compile-time guarantee: sendTransaction exists
signer.sendTransaction(request);
```

### Example 5: WebSocket Subscriptions

```java
var client = Brane.builder()
    .wsUrl("wss://eth-mainnet.g.alchemy.com/v2/KEY")
    .buildReader();

// Subscribe to new blocks
var subscription = client.onNewHeads(block -> {
    System.out.println("New block: " + block.number());
});

// Later: unsubscribe
subscription.unsubscribe();
```

### Example 6: Parallel Requests with Virtual Threads

```java
var client = Brane.connect("https://...");
var addresses = List.of(addr1, addr2, addr3, /* ... thousands */);

try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    var balances = addresses.stream()
        .map(addr -> executor.submit(() -> client.getBalance(addr)))
        .map(future -> future.get())  // Blocking is cheap!
        .toList();
}
```

### Example 7: Guarded Pattern (Java 21)

```java
Brane client = getClient();

// Pattern with guard
if (client instanceof Brane.Signer s
        && s.signer().address().equals(expectedAddress)) {
    s.sendTransaction(request);
}
```

---

## Implementation Details

### DefaultReader

```java
final class DefaultReader implements Brane.Reader {
    private final BraneProvider provider;
    private final ChainProfile chain;
    private final int maxRetries;
    private final RpcRetryConfig retryConfig;
    private final AtomicBoolean closed = new AtomicBoolean();

    DefaultReader(BraneProvider provider, ChainProfile chain,
                  int maxRetries, RpcRetryConfig retryConfig) {
        this.provider = Objects.requireNonNull(provider);
        this.chain = chain;
        this.maxRetries = maxRetries;
        this.retryConfig = retryConfig;
    }

    @Override
    public BigInteger getBalance(Address address) {
        ensureOpen();
        return RpcRetry.run(() -> {
            var response = provider.send("eth_getBalance",
                List.of(address.value(), "latest"));
            return RpcUtils.decodeHexBigInteger(response.result().toString());
        }, maxRetries);
    }

    // ... other read methods ...

    @Override
    public Optional<ChainProfile> chain() {
        return Optional.ofNullable(chain);
    }

    @Override
    public boolean canSubscribe() {
        return provider instanceof WebSocketProvider;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            provider.close();
        }
    }

    private void ensureOpen() {
        if (closed.get()) throw new IllegalStateException("Client is closed");
    }
}
```

### DefaultSigner (Composition Pattern)

```java
final class DefaultSigner implements Brane.Signer {
    private final DefaultReader reader;  // Composition, not inheritance
    private final io.brane.core.Signer signer;
    private final SmartGasStrategy gasStrategy;

    DefaultSigner(BraneProvider provider, io.brane.core.Signer signer,
                  ChainProfile chain, int maxRetries, RpcRetryConfig retryConfig) {
        this.reader = new DefaultReader(provider, chain, maxRetries, retryConfig);
        this.signer = Objects.requireNonNull(signer);
        this.gasStrategy = new SmartGasStrategy(/* ... */);
    }

    // ═══════════════════════════════════════════════════════════════
    // DELEGATED READ OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    @Override public BigInteger chainId() { return reader.chainId(); }
    @Override public BigInteger getBalance(Address a) { return reader.getBalance(a); }
    @Override public BlockHeader getLatestBlock() { return reader.getLatestBlock(); }
    // ... all other read methods delegate to reader ...

    // ═══════════════════════════════════════════════════════════════
    // SIGNING OPERATIONS
    // ═══════════════════════════════════════════════════════════════

    @Override
    public Hash sendTransaction(TransactionRequest request) {
        var tx = buildAndSignTransaction(request);
        return sendRawTransaction(tx);
    }

    @Override
    public TransactionReceipt sendTransactionAndWait(TransactionRequest request) {
        return sendTransactionAndWait(request, 120_000, 1_000);
    }

    @Override
    public TransactionReceipt sendTransactionAndWait(
            TransactionRequest request, long timeoutMillis, long pollIntervalMillis) {
        var hash = sendTransaction(request);
        return waitForReceipt(hash, timeoutMillis, pollIntervalMillis);
    }

    @Override
    public io.brane.core.Signer signer() {
        return signer;
    }

    @Override
    public void close() {
        reader.close();
    }
}
```

---

## Migration Guide

### Before (Current API)

```java
// Read-only client
PublicClient publicClient = PublicClient.builder()
    .rpcUrl("https://...")
    .build();
var balance = publicClient.getBalance(address);

// Signing client
WalletClient walletClient = WalletClient.builder()
    .rpcUrl("https://...")
    .signer(privateKey)
    .build();
var receipt = walletClient.sendTransactionAndWait(request);
```

### After (New API)

```java
// Read-only client
var client = Brane.connect("https://...");
var balance = client.getBalance(address);

// Signing client
var client = Brane.connect("https://...", privateKey);
var receipt = client.sendTransactionAndWait(request);
```

### Migration Table

| Old API | New API |
|---------|---------|
| `PublicClient.builder().rpcUrl(url).build()` | `Brane.connect(url)` |
| `WalletClient.builder().rpcUrl(url).signer(s).build()` | `Brane.connect(url, s)` |
| `PublicClient` type | `Brane.Reader` type |
| `WalletClient` type | `Brane.Signer` type |
| `client.eth_getBalance()` | `client.getBalance()` |
| `client.eth_call()` | `client.call()` |

---

## Comparison: Architecture Options

| Aspect | Original (2 clients) | Unified (1 client) | **Sealed Brane** |
|--------|---------------------|-------------------|------------------|
| Entry points | 2 | 1 | **1** |
| Compile-time write safety | ✓ | ✗ | **✓** |
| Pattern matching | ✗ | ✗ | **✓** |
| Discoverability | Medium | High | **High** |
| Java 21 features | Some | Some | **All** |
| Interface count | 4 | 1 | **1 sealed + 2 nested** |
| Implementation count | 7 | 3 | **2** |
| Total public types | 11 | 4 | **3** |

---

## Summary

The sealed `Brane` interface provides:

| Feature | Benefit |
|---------|---------|
| **Single entry point** | `Brane.connect()` - nothing else to discover |
| **Sealed hierarchy** | Compiler knows all subtypes; enables exhaustive switch |
| **Type-safe variants** | `buildReader()` vs `buildSigner()` - compile-time guarantee |
| **Pattern matching** | Handle Reader/Signer cases elegantly |
| **Nested types** | `Brane.Reader`, `Brane.Signer` - clear namespace |
| **Virtual threads** | Simple blocking code that scales |
| **Composition** | `DefaultSigner` delegates to `DefaultReader` - clean separation |

This is the **simplest possible API surface** while maintaining **full compile-time type safety**.
