# brane-kzg

KZG commitment implementation for EIP-4844 blob transactions. Wraps the c-kzg-4844 native library.

## Commands

```bash
# Compile
./gradlew :brane-kzg:compileJava

# Run tests
./gradlew :brane-kzg:test

# Run specific test
./gradlew :brane-kzg:test --tests "sh.brane.kzg.MyTest"
```

## Key Classes

- **`CKzg`** - Main implementation of `Kzg` interface, wraps native c-kzg-4844 bindings

## Patterns

### Loading Trusted Setup

```java
// Recommended: Load bundled mainnet trusted setup (do once at startup)
Kzg kzg = CKzg.loadFromClasspath();

// Alternative: Load from custom path
Kzg kzg = CKzg.loadTrustedSetup("/path/to/trusted_setup.txt");
```

### KZG Operations

```java
// Compute commitment from blob
KzgCommitment commitment = kzg.blobToCommitment(blob);

// Compute proof for blob
KzgProof proof = kzg.computeProof(blob, commitment);

// Verify single proof
boolean valid = kzg.verifyBlobKzgProof(blob, commitment, proof);

// Verify batch of proofs (more efficient)
boolean valid = kzg.verifyBlobKzgProofBatch(blobs, commitments, proofs);
```

### Using with Eip4844Builder

```java
// Load KZG once at startup
Kzg kzg = CKzg.loadFromClasspath();

// Build blob transaction
BlobTransactionRequest request = Eip4844Builder.create()
    .to(recipient)
    .blobData(rawBytes)
    .build(kzg);  // KZG needed for commitment/proof generation
```

## Gotchas

- **Global state**: c-kzg-4844 maintains a single global trusted setup across all `CKzg` instances in the JVM
- **Load once**: Call `loadFromClasspath()` once at startup and reuse the instance
- **Thread-safe**: All KZG operations can be called concurrently from multiple threads
- **No cleanup needed**: Native library stays in memory for JVM lifetime
- **Reloading**: Calling `loadFromClasspath()` multiple times reloads the global setup. Only load once at application startup

## Dependencies

- jc-kzg-4844 (native library bindings)
- brane-core (types: `Blob`, `KzgCommitment`, `KzgProof`, `Kzg` interface)
