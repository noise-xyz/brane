# Brane Performance Benchmarks

> **Last Updated:** 2025-12-09  
> **Java Version:** 21 (OpenJDK)  
> **Test Environment:** Local Anvil devnet (ws://127.0.0.1:8545)

## Key Results

| Provider | Mode | Throughput (ops/s) | Latency |
|----------|------|-------------------|---------|
| WebSocket | Sequential | 8,200 | 0.12 ms |
| WebSocket | Batch 100 | **110,000** | - |
| HTTP (Loom) | Batch 100 | 22,600 | - |
| **WebSocket/HTTP Speedup** | | **4.8x** | |

> **Note:** Batch tests measure 100 parallel requests using WebSocket's Disruptor batching vs HTTP's virtual thread pool.

## How to Run

```bash
# Start local devnet
anvil --host 0.0.0.0 --port 8545

# Run quick benchmark
./gradlew :brane-benchmark:runQuickBenchmark

# Run full JMH suite
./gradlew :brane-benchmark:jmh
```

## Provider Comparison (Local Anvil)

### Sequential Requests (1 at a time)

| Benchmark | Throughput | Latency |
|-----------|-----------|---------|
| WebSocket `send` (sync) | 8,200 ops/s | 0.12 ms/op |
| HTTP (Loom virtual threads) | ~5,000 ops/s | 0.20 ms/op |

### Parallel Requests (Batch of 100)

| Benchmark | Throughput |
|-----------|-----------|
| `ws_batch100` (Disruptor) | **110,000 ops/s** |
| `http_batch100` (virtual threads) | 22,600 ops/s |
| **WebSocket Advantage** | **4.8x faster** |

### Why WebSocket is Faster

1. **Persistent Connection** — No TCP handshake per request
2. **Disruptor Batching** — `sendAsyncBatch()` uses LMAX Disruptor ring buffer for ultra-low latency
3. **Zero-Copy Serialization** — Direct JSON → Netty ByteBuf, no intermediate copies
4. **Request Multiplexing** — Multiple in-flight requests on single connection

## Real Network Performance

When benchmarking against public RPC endpoints (Base, Arbitrum, Ethereum):

| Network | Provider | Throughput |
|---------|----------|-----------|
| Ethereum Mainnet | Brane WebSocket | ~50 ops/s |
| Base Mainnet | Brane WebSocket | ~55 ops/s |

> **Note:** Real network latency (~20ms roundtrip) dominates. Local Anvil benchmarks isolate client performance.

## ABI Encoding

| Benchmark | Throughput |
|-----------|-----------|
| Brane `encodeCall` | ~2,500,000 ops/s |
| web3j `encodeFunction` | ~400,000 ops/s |
| **Brane Speedup** | **6.3x** |

See [brane-benchmark/RESULTS.md](../brane-benchmark/RESULTS.md) for detailed ABI results.

## Signer Performance

| Benchmark | Throughput |
|-----------|-----------|
| Brane `sign` | ~15,000 ops/s |
| web3j `signMessage` | ~12,000 ops/s |

## Methodology

1. **Warmup:** 100 iterations
2. **Measurement:** 1,000 iterations  
3. **Environment:** Local Anvil devnet to eliminate network variability
4. **JVM:** OpenJDK 21 with `-Xms1G -Xmx1G -XX:+UseG1GC`

## Running Against Real Networks

Create a `.env` file:

```bash
HTTP_RPC_URL=https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY
WS_RPC_URL=wss://eth-mainnet.g.alchemy.com/v2/YOUR_KEY
```

Run:
```bash
./gradlew :brane-benchmark:runRealWorldBenchmark
```
