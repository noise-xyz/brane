# Brane SDK - Claude Context

Modern, type-safe Java 21 SDK for Ethereum/EVM. Inspired by viem (TS) and alloy (Rust).

## Quick Reference

| Module | Purpose | Key Classes |
|--------|---------|-------------|
| `brane-primitives` | Hex/RLP utilities (zero deps) | `Hex`, `Rlp` |
| `brane-core` | Types, ABI, crypto, EIP-712, models | `Address`, `Wei`, `Abi`, `PrivateKey`, `MnemonicWallet`, `TypedData`, `TxBuilder`, `Blob`, `BlobSidecar`, `Eip4844Builder` |
| `brane-kzg` | KZG commitments for EIP-4844 blobs | `CKzg`, `Kzg` |
| `brane-rpc` | JSON-RPC client layer | `Brane`, `Brane.Reader`, `Brane.Signer`, `Brane.Tester`, `BraneProvider` |
| `brane-contract` | Contract binding (no codegen) | `BraneContract.bind()`, `ReadOnlyContract` |
| `brane-examples` | Usage examples & integration tests | Various `*Example.java` |
| `brane-benchmark` | JMH performance benchmarks | `*Benchmark.java` |
| `smoke-test` | E2E integration tests | `SmokeApp.java` |

## Critical Rules

1. **Java 21 Only**: Records, sealed classes, pattern matching, virtual threads
2. **No web3j in Public API**: web3j is vendored under `sh.brane.internal.web3j.*` - NEVER expose it
3. **Type Safety**: Use `Address`, `Wei`, `HexData`, `Hash` - avoid raw `String`/`BigInteger`
4. **Virtual Threads**: Write simple blocking code - no reactive chains
5. **Use `var` When Type is Obvious**: Use `var` for local variables when the type is immediately clear from the RHS (right-hand side), such as constructor calls (`var map = new LinkedHashMap<>()`), but keep explicit types for domain types (`Address`, `Wei`, `Hash`) where the type name documents intent

## Module Dependencies

```
brane-primitives (no deps)
       ↓
   brane-core (BouncyCastle, Jackson)
       ↓
   ┌───┴───┐
brane-kzg  brane-rpc (Netty, Disruptor)
(c-kzg)        ↓
          brane-contract
```

## Common Commands

```bash
# Compile
./gradlew compileJava

# Run specific test
./gradlew test --tests "sh.brane.core.MyTest"

# Full verification (requires Anvil running)
./verify_all.sh

# Publish to local Maven (~/.m2/repository)
./gradlew publishToMavenLocal

# Stage release artifacts (verify before publishing)
./gradlew stageRelease

# Deploy to Maven Central (requires credentials)
./gradlew jreleaserDeploy
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
MyToken token = BraneContract.bind(MyToken.class, abi, address, client);
```

### Transaction Building
```java
Eip1559Builder.create()
    .to(recipient)
    .value(Wei.fromEther("0.1"))
    .data(calldata)
    .build(signer, client);
```

### Blob Transactions (EIP-4844)
```java
// Load KZG trusted setup
Kzg kzg = CKzg.loadFromClasspath();

// Build blob transaction from raw data
BlobTransactionRequest request = Eip4844Builder.create()
    .to(recipient)
    .blobData(rawBytes)
    .build(kzg);

// Send blob transaction
TransactionReceipt receipt = signer.sendBlobTransactionAndWait(request);

// Decode data from blobs
byte[] decoded = BlobDecoder.decode(request.sidecar().blobs());
```

### HD Wallet (BIP-39/BIP-44)
```java
// Restore wallet from mnemonic phrase
MnemonicWallet wallet = MnemonicWallet.fromPhrase("abandon abandon ... about");

// Derive signers for different addresses (m/44'/60'/0'/0/N)
Signer account0 = wallet.derive(0);
Signer account1 = wallet.derive(1);

// Custom derivation path
Signer custom = wallet.derive(new DerivationPath(1, 5)); // m/44'/60'/1'/0/5

// Generate new wallet (save phrase securely!)
MnemonicWallet newWallet = MnemonicWallet.generatePhrase();
String phrase = newWallet.phrase();
```

### EIP-712 Typed Data Signing
```java
var domain = Eip712Domain.builder()
    .name("MyDapp")
    .version("1")
    .chainId(1L)
    .verifyingContract(contractAddress)
    .build();

// Type-safe signing with records
var typedData = TypedData.create(domain, Permit.DEFINITION, permit);
Signature sig = typedData.sign(signer);
Hash hash = typedData.hash();

// JSON parsing (WalletConnect, dapp requests)
TypedData<?> fromJson = TypedDataJson.parseAndValidate(jsonString);
```

### Integration Testing with Brane.Tester
```java
// Connect to local Anvil
Brane.Tester tester = Brane.connectTest();

// Snapshot/revert for test isolation
SnapshotId snapshot = tester.snapshot();
try {
    tester.setBalance(address, Wei.fromEther("1000"));
    // ... test operations ...
} finally {
    tester.revert(snapshot);
}

// Impersonate any address (no private key needed)
try (ImpersonationSession session = tester.impersonate(whaleAddress)) {
    session.sendTransactionAndWait(request);
}

// Time manipulation for testing locks/vesting
tester.increaseTime(86400); // 1 day
tester.mine();
```

## Error Hierarchy

```
BraneException (sealed root)
├── AbiDecodingException - ABI decoding failures
├── AbiEncodingException - ABI encoding failures
├── KzgException - KZG proof/commitment failures
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

## Large File Navigation

For large Java files (500+ lines), use `Grep` and targeted `Read` with line offsets instead of reading the entire file.

**DO NOT ask the user what they want - USE THE TOOLS PROACTIVELY.**

When asked about a large file, immediately:
1. Use `Grep` to find relevant symbols/patterns
2. Use `Read` with `offset` and `limit` to read specific sections

**Example - user asks "Read Brane.java":**
```
# WRONG: Asking "what would you like to know?"
# RIGHT: Immediately search and read targeted sections

Grep("sealed interface", path="brane-rpc/.../Brane.java")
→ Found: line 229: "public sealed interface Brane"
→ Found: line 840: "sealed interface Reader"

Read(file_path="brane-rpc/.../Brane.java", offset=229, limit=50)
→ Returns the Brane interface definition
```

**Large files in this codebase (>500 lines):**
- `brane-rpc/.../Brane.java` (~2300 lines) - Main client interface
- `brane-core/.../Abi.java` - ABI encoding/decoding

## Gotchas

- **Keccak256 ThreadLocal**: Call `Keccak256.cleanup()` in pooled/web threads to prevent memory leaks
- **PrivateKey security**: Call `key.destroy()` when done; `fromBytes()` zeros input array
- **MnemonicWallet seed lifetime**: Keep wallet instances short-lived; the mnemonic phrase in memory provides access to all derived keys. Derived `Signer` instances are independent and can be held longer
- **Anvil required**: Integration tests need `anvil` running on `127.0.0.1:8545`
- **Blob transactions require Cancun**: Start Anvil with `anvil --hardfork cancun` for EIP-4844 support
- **Default test key**: `0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80`

## For Full Details

- `JAVA21.md` - Java 21 patterns reference (sealed types, pattern matching, records, virtual threads)
- `TESTING.md` - Testing standards, acceptance criteria, and developer workflow
