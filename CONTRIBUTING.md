# Contributing to Brane SDK

Thank you for your interest in contributing to Brane! This guide covers everything you need to get started.

## Getting Started

### Prerequisites

- **Java 21+** - We use records, sealed classes, pattern matching, and virtual threads
- **Gradle 9.2+** - Wrapper included, no manual install needed
- **Anvil** - For integration tests. Install via [Foundry](https://book.getfoundry.sh/getting-started/installation)

### Setup

```bash
# Clone and build
git clone https://github.com/noise-xyz/brane.git
cd brane
./gradlew build

# Verify your environment
./scripts/test_sanity.sh

# Run unit tests (no Anvil needed)
./gradlew test -Pbrane.unit.tests

# Run all tests (requires Anvil)
anvil &
./verify_all.sh
```

## Architecture

### Module Structure

```
brane-primitives (no deps)    - Hex/RLP encoding
       |
   brane-core                 - Types, ABI, Crypto, Builders
       |
   +---+---+
   |       |
brane-kzg  brane-rpc          - KZG commitments, JSON-RPC client
           |
      brane-contract          - Contract binding (no codegen)
```

### Key Classes

| Module | Purpose | Key Types |
|--------|---------|-----------|
| `brane-primitives` | Low-level utilities | `Hex`, `Rlp` |
| `brane-core` | Domain types & crypto | `Address`, `Wei`, `Hash`, `HexData`, `PrivateKey`, `Abi` |
| `brane-rpc` | Client layer | `Brane.Reader`, `Brane.Signer`, `Brane.Tester` |
| `brane-contract` | Contract binding | `BraneContract.bind()` |
| `brane-kzg` | EIP-4844 blobs | `CKzg`, `Kzg` |

## Development Standards

### Java 21 Patterns (Required)

| Pattern | Do | Don't |
|---------|-----|-------|
| **Data types** | `record Address(String value) {}` | Mutable class with getters/setters |
| **Type hierarchies** | `sealed interface` + `permits` | Open inheritance |
| **Control flow** | `switch` expressions | `if-else` chains |
| **Type checking** | `if (x instanceof Foo f)` | Cast after instanceof |
| **Local variables** | `var map = new HashMap<>()` | Explicit type when obvious |
| **Multi-line strings** | Text blocks `"""..."""` | String concatenation |
| **Collections** | `stream.toList()` | `.collect(Collectors.toList())` |

### Type Safety

Public APIs must use Brane types, not raw primitives:

```java
// Good
Address recipient = Address.from("0x...");
Wei amount = Wei.fromEther("1.5");
Hash txHash = signer.sendTransaction(request);

// Avoid
String recipient = "0x...";
BigInteger amount = new BigInteger("1500000000000000000");
```

### Immutability

- All fields should be `final` unless mutation is strictly necessary
- Prefer records for data types
- Return defensive copies from getters when needed

### Concurrency

With virtual threads, write simple blocking code:

```java
// Good - simple blocking call
TransactionReceipt receipt = signer.sendTransactionAndWait(request);

// Avoid - reactive chains
Mono.fromCallable(() -> signer.send(request))
    .flatMap(hash -> pollForReceipt(hash))
    .subscribe(...);
```

### Factory Method Naming

| Method | Use Case | Example |
|--------|----------|---------|
| `from()` | Convert/parse from another type | `Address.from("0x...")` |
| `of()` | Construct value objects | `CallRequest.of(to, data)` |
| `create()` | Simple construction | `WebSocketProvider.create(url)` |
| `builder()` | Complex construction | `HttpBraneProvider.builder().build()` |

## Code Style

### Formatting

We use [Spotless](https://github.com/diffplug/spotless):

```bash
./gradlew spotlessCheck   # Verify formatting
./gradlew spotlessApply   # Auto-fix formatting
```

### Anti-Patterns to Avoid

- `Optional.get()` - Use `.orElseThrow()` or `.map()`
- `public` fields - Use accessors (except `static final` constants)
- Raw types - Always parameterize generics: `List<String>` not `List`
- Swallowed exceptions - Always log or rethrow

## Testing

### Test Pyramid

| Level | Type | Command | Requirements |
|-------|------|---------|--------------|
| 0 | Sanity | `./scripts/test_sanity.sh` | None |
| 1 | Unit | `./gradlew test -Pbrane.unit.tests` | None |
| 2 | Integration | `./scripts/test_integration.sh` | Anvil |
| 3 | Smoke | `./scripts/test_smoke.sh` | Anvil |
| 4 | Full | `./verify_all.sh` | Anvil |

### Acceptance Criteria

For a PR to be merged:

1. **Pass all tests** - `./verify_all.sh` exits with code 0
2. **New features** - Include unit tests covering the logic
3. **New components** - Include integration test or add to `SmokeApp`
4. **No regressions** - Existing tests pass without modification

### Writing Tests

```java
class AddressTest {
    @Test
    void shouldParseValidAddress() {
        // Given
        var hex = "0x742d35Cc6634C0532925a3b844Bc9e7595f8fE00";

        // When
        Address address = Address.from(hex);

        // Then
        assertThat(address.value()).isEqualToIgnoringCase(hex);
    }
}
```

- **Unit tests**: `src/test/java/...Test.java` - Mock dependencies, no I/O
- **Integration tests**: `src/test/java/...IntegrationTest.java` - Real Anvil calls
- **Frameworks**: JUnit 5, Mockito, AssertJ

### Developer Workflow

1. Start Anvil: `anvil`
2. Run sanity check: `./scripts/test_sanity.sh`
3. Write code and tests
4. Run your specific test: `./gradlew test --tests "io.brane.core.MyTest"`
5. Run layer tests: `./gradlew test -Pbrane.unit.tests`
6. Full verification: `./verify_all.sh`

## Pull Request Process

### Branch Naming

- `feat/description` - New features
- `fix/description` - Bug fixes
- `refactor/description` - Code refactoring
- `docs/description` - Documentation
- `chore/description` - Maintenance

### Commit Messages

Use [Conventional Commits](https://www.conventionalcommits.org/):

```
feat(core): add EIP-712 typed data signing

- Add TypedData record for structured data
- Implement domain separator hashing
- Add sign() method to Signer interface
```

Format: `type(scope): description`

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`

### PR Checklist

- [ ] Tests pass: `./gradlew test -Pbrane.unit.tests`
- [ ] Formatted: `./gradlew spotlessCheck`
- [ ] No new warnings
- [ ] Documentation updated (if applicable)
- [ ] Breaking changes noted (if applicable)

## Common Gotchas

| Issue | Solution |
|-------|----------|
| "Connection refused" in tests | Start Anvil: `anvil` |
| Tests hang | Use `sendTransactionAndWait()`, not `sendTransaction()` |
| "Intrinsic gas too low" | Check gas limits or use `TxBuilder` |
| Memory leak in web apps | Call `Keccak256.cleanup()` in pooled threads |
| Blob transactions fail | Start Anvil with `anvil --hardfork cancun` |

### Default Test Key

Anvil's first pre-funded account:
```
0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80
```

## Getting Help

- **Questions**: [GitHub Discussions](https://github.com/noise-xyz/brane/discussions)
- **Bugs**: [GitHub Issues](https://github.com/noise-xyz/brane/issues)
- **Security**: See [SECURITY.md](./SECURITY.md)

## License

By contributing, you agree that your contributions will be dual-licensed under Apache-2.0 and MIT licenses.
