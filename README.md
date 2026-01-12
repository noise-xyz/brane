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
