# Brane SDK - Claude Context

Modern, type-safe Java 21 SDK for Ethereum/EVM. Inspired by viem (TS) and alloy (Rust).

## Quick Reference

| Module | Purpose | Key Classes |
|--------|---------|-------------|
| `brane-primitives` | Hex/RLP utilities (zero deps) | `Hex`, `Rlp` |
| `brane-core` | Types, ABI, crypto, models | `Address`, `Wei`, `Abi`, `PrivateKey`, `TxBuilder` |
| `brane-rpc` | JSON-RPC client layer | `PublicClient`, `WalletClient`, `BraneProvider` |
| `brane-contract` | Contract binding (no codegen) | `BraneContract.bind()`, `ReadOnlyContract` |
| `brane-examples` | Usage examples & integration tests | Various `*Example.java` |
| `brane-benchmark` | JMH performance benchmarks | `*Benchmark.java` |
| `smoke-test` | E2E integration tests | `SmokeApp.java` |

## Critical Rules

1. **Java 21 Only**: Records, sealed classes, pattern matching, virtual threads
2. **No web3j in Public API**: web3j is vendored under `io.brane.internal.web3j.*` - NEVER expose it
3. **Type Safety**: Use `Address`, `Wei`, `HexData`, `Hash` - avoid raw `String`/`BigInteger`
4. **Virtual Threads**: Write simple blocking code - no reactive chains

## Module Dependencies

```
brane-primitives (no deps)
       ↓
   brane-core (BouncyCastle, Jackson)
       ↓
    brane-rpc (Netty, Disruptor)
       ↓
  brane-contract
```

## Common Commands

```bash
# Compile
./gradlew compileJava

# Run specific test
./gradlew test --tests "io.brane.core.MyTest"

# Full verification (requires Anvil running)
./verify_all.sh
```

## Key Patterns

### Type-Safe Values
```java
Address addr = Address.from("0x...");
Wei amount = Wei.fromEther("1.5");
HexData data = HexData.from("0x...");
```

### Contract Binding (No Codegen)
```java
interface MyToken {
    BigInteger balanceOf(Address owner);
    Hash transfer(Address to, BigInteger amount);
}
MyToken token = BraneContract.bind(MyToken.class, abi, address, walletClient);
```

### Transaction Building
```java
Eip1559Builder.create()
    .to(recipient)
    .value(Wei.fromEther("0.1"))
    .data(calldata)
    .build(signer, publicClient);
```

## Error Hierarchy

```
BraneException (sealed root)
├── AbiDecodingException - ABI decoding failures
├── AbiEncodingException - ABI encoding failures
├── RevertException - EVM execution reverts (includes decoded reason)
├── RpcException - JSON-RPC communication failures
└── TxnException - Transaction-specific failures (non-sealed)
    ├── BraneTxBuilderException - Transaction building failures
    ├── ChainMismatchException - Chain ID mismatch errors
    └── InvalidSenderException - Invalid sender address errors
```

## Testing Layers

| Level | Command | Requirements |
|-------|---------|--------------|
| Unit | `./scripts/test_unit.sh` | None |
| Integration | `./scripts/test_integration.sh` | Anvil |
| Smoke | `./scripts/test_smoke.sh` | Anvil |
| Full | `./verify_all.sh` | Anvil |

## Gotchas

- **Keccak256 ThreadLocal**: Call `Keccak256.cleanup()` in pooled/web threads to prevent memory leaks
- **PrivateKey security**: Call `key.destroy()` when done; `fromBytes()` zeros input array
- **Anvil required**: Integration tests need `anvil` running on `127.0.0.1:8545`
- **Default test key**: `0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80`

## For Full Details

See `AGENT.md` for comprehensive development standards, testing protocol, and AI agent instructions.
