# Brane Benchmarks

This module contains JMH benchmarks to compare Brane's performance against other libraries (mainly Web3j) and to measure internal latency.

## Running Benchmarks

### Default (Public Nodes)
By default, benchmarks run against public RPC nodes to avoid consuming paid credits.

```bash
./gradlew :brane-benchmark:jmh
```

### Using Infura
To run benchmarks against Infura (e.g., for Base Mainnet), you must:
1.  Set the `brane.benchmark.useInfura` system property to `true`.
2.  Provide the Infura WebSocket URL via environment variable or system property.

**Using `.env` file (Recommended):**
```bash
# Assuming your .env contains INFURA_BASE_WSS_URL
export $(cat ../.env | xargs)
./gradlew :brane-benchmark:jmh -Dbrane.benchmark.useInfura=true
```

**Passing explicitly:**
```bash
./gradlew :brane-benchmark:jmh \
  -Dbrane.benchmark.useInfura=true \
  -DINFURA_BASE_WSS_URL=wss://base-mainnet.infura.io/ws/v3/YOUR_KEY
```

## Benchmark Categories

- **MainnetBenchmark**: Connects to real networks (Base, Ethereum, Arbitrum) to measure throughput and latency.
- **WebSocketBenchmark**: Benchmarks WebSocket frame processing against a local Anvil node.
- **ClientOverheadBenchmark**: Measures internal parsing and Object allocation overhead.
