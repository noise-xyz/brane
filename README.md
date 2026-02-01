# Brane

A modern, type-safe Java 21 SDK for Ethereum and EVM chains.

## Installation

Available on [Maven Central](https://central.sonatype.com/namespace/sh.brane).

```groovy
repositories {
    mavenCentral()
}

dependencies {
    // Core modules
    implementation 'sh.brane:brane-core:0.3.0'       // Types, ABI, crypto, tx builders
    implementation 'sh.brane:brane-rpc:0.3.0'        // JSON-RPC client
    implementation 'sh.brane:brane-contract:0.3.0'   // Type-safe contract binding

    // Optional
    implementation 'sh.brane:brane-primitives:0.3.0' // Hex/RLP utilities (zero deps)
    implementation 'sh.brane:brane-kzg:0.3.0'        // EIP-4844 blob transactions
}
```

## Modules

| Module | Purpose |
|--------|---------|
| `brane-core` | Types (`Address`, `Wei`, `Hash`), ABI encoding, crypto, transaction builders |
| `brane-rpc` | JSON-RPC client (`Brane.Reader`, `Brane.Signer`, `Brane.Tester`) |
| `brane-contract` | Runtime contract binding via `BraneContract.bind()` |
| `brane-primitives` | Zero-dependency Hex and RLP utilities |
| `brane-kzg` | KZG commitments for EIP-4844 blob transactions |

## Performance

Brane is designed to be allocation-conscious, minimizing GC pressure for high-throughput applications.

### Allocation Budgets

| Operation | Allocations | Notes |
|-----------|-------------|-------|
| `Hex.decode()` | 48 B/op | Result array only |
| `Hex.decodeTo()` | 0 B/op | Writes to pre-allocated buffer |
| `Hex.encode()` | 264 B/op | char[] + String |
| `Hex.encodeTo()` | 0 B/op | Writes to pre-allocated buffer |
| `FastAbiEncoder` (pre-allocated) | ~0 B/op | Direct buffer encoding |

### Zero-Allocation APIs

For hot paths, use the `*To()` variants with pre-allocated buffers:

```java
byte[] buffer = new byte[32];
Hex.decodeTo(hexString, 0, hexString.length(), buffer, 0);

char[] charBuf = new char[66];
Hex.encodeTo(bytes, charBuf, 0, true);
```

### Running Benchmarks

```bash
# Run allocation benchmarks
./gradlew :brane-benchmark:jmh -Pjmh.includes="AllocationBenchmark" -Pjmh.prof="gc"
```

## Development

Prerequisites: Java 21, [Foundry](https://getfoundry.sh/) (for Anvil).

```bash
./gradlew compileJava          # Compile
./gradlew check                # Run tests
./scripts/test_smoke.sh        # Run smoke tests (requires Anvil)
./gradlew allJavadoc           # Generate docs
```

## License

Dual-licensed under [Apache 2.0](LICENSE-APACHE) or [MIT](LICENSE-MIT).
