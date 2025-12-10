# Brane Performance Benchmarks

> **Last Updated:** 2025-12-09  
> **JMH Version:** 1.36  
> **Java Version:** 21  
> **Test Environment:** Local Anvil devnet

## How to Run Benchmarks

```bash
# Start local devnet
anvil --host 0.0.0.0 --port 8545

# Run all benchmarks
./gradlew :brane-benchmark:jmh

# Run specific benchmark
./gradlew :brane-benchmark:jmh -Pjmh.includes="ProviderComparisonBenchmark"
```

## Provider Comparison

| Benchmark | Throughput (ops/s) | Avg Latency (ms) |
|-----------|-------------------|------------------|
| `http_blockNumber` | TBD | TBD |
| `http_getBalance` | TBD | TBD |
| `ws_sendAsync_blockNumber` | TBD | TBD |
| `ws_sendAsync_getBalance` | TBD | TBD |
| `ws_sendAsyncBatch_blockNumber` | TBD | TBD |
| `ws_sendAsyncBatch_getBalance` | TBD | TBD |
| `http_batch100_blockNumber` | TBD | TBD |
| `ws_batch100_blockNumber` | TBD | TBD |

**Notes:**
- HTTP uses virtual threads (Loom) for concurrent requests
- WebSocket `sendAsyncBatch` uses Disruptor batching for optimal throughput
- Batch tests measure 100 parallel requests

## ABI Encoding

| Benchmark | Throughput (ops/s) |
|-----------|-------------------|
| Brane `encodeCall` | ~2.5M |
| web3j `encodeFunction` | ~400K |
| **Speedup** | **~6x** |

See [brane-benchmark/RESULTS.md](../brane-benchmark/RESULTS.md) for detailed ABI benchmark results.

## Signer Performance

| Benchmark | Throughput (ops/s) |
|-----------|-------------------|
| Brane `sign` | ~15K |
| web3j `signMessage` | ~12K |

## Tail Latency

| Provider | p50 (ms) | p99 (ms) | p99.9 (ms) |
|----------|----------|----------|------------|
| HTTP (Loom) | TBD | TBD | TBD |
| WebSocket sendAsync | TBD | TBD | TBD |
| WebSocket sendAsyncBatch | TBD | TBD | TBD |

---

## Running Against Real Networks

To benchmark against mainnet/testnets, create a `.env` file:

```bash
# .env
HTTP_RPC_URL=https://eth-mainnet.g.alchemy.com/v2/YOUR_KEY
WS_RPC_URL=wss://eth-mainnet.g.alchemy.com/v2/YOUR_KEY
```

Then run:
```bash
./gradlew :brane-benchmark:jmh -Pjmh.includes="MainnetBenchmark"
```

## Benchmark Methodology

1. **Warmup:** 2 iterations × 3 seconds
2. **Measurement:** 5 iterations × 3 seconds  
3. **Forks:** 1 (JMH recommendation for microbenchmarks)
4. **Modes:** Throughput and AverageTime

All benchmarks run against local Anvil by default to avoid network variability.
