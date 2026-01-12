# Contributing to Brane SDK

Thank you for your interest in contributing to Brane! This document provides guidelines and information for contributors.

## Getting Started

### Prerequisites

- **Java 21+** (required - we use records, sealed classes, pattern matching)
- **Gradle 8.7+** (wrapper included)
- **Anvil** (for integration tests) - Install via [Foundry](https://book.getfoundry.sh/getting-started/installation)

### Setting Up the Development Environment

```bash
# Clone the repository
git clone https://github.com/noise-xyz/brane.git
cd brane

# Build the project
./gradlew build

# Run unit tests
./gradlew test -Pbrane.unit.tests

# Run integration tests (requires Anvil running on localhost:8545)
anvil &
./gradlew test
```

## Development Workflow

### Branch Naming

- `feat/description` - New features
- `fix/description` - Bug fixes
- `refactor/description` - Code refactoring
- `docs/description` - Documentation updates
- `chore/description` - Maintenance tasks

### Code Style

We use [Spotless](https://github.com/diffplug/spotless) for code formatting:

```bash
# Check formatting
./gradlew spotlessCheck

# Auto-fix formatting
./gradlew spotlessApply
```

### Key Conventions

1. **Use Java 21 features**: Records for data, sealed classes for type hierarchies, pattern matching
2. **Type safety**: Use `Address`, `Wei`, `HexData`, `Hash` - avoid raw `String`/`BigInteger`
3. **Immutability**: Prefer immutable types and `final` fields
4. **Use `var`**: When the type is obvious from the right-hand side
5. **Virtual threads**: Write simple blocking code - no reactive chains

### Example

```java
// Good
Address recipient = Address.from("0x...");
Wei amount = Wei.fromEther("1.5");
var receipt = signer.sendTransactionAndWait(request);

// Avoid
String recipient = "0x...";
BigInteger amount = new BigInteger("1500000000000000000");
```

## Testing

### Test Layers

| Level       | Command                               | Requirements |
|-------------|---------------------------------------|--------------|
| Unit        | `./gradlew test -Pbrane.unit.tests`   | None         |
| Integration | `./gradlew test -Pbrane.integration.tests` | Anvil        |
| All         | `./gradlew test`                      | Anvil        |
| Full        | `./verify_all.sh`                     | Anvil        |

### Writing Tests

- Unit tests go in `src/test/java` with suffix `Test.java`
- Integration tests use suffix `IntegrationTest.java`
- Use JUnit 5, Mockito, and AssertJ
- Test files should mirror source structure

```java
class MyFeatureTest {
    @Test
    void shouldParseValidAddress() {
        // Given
        var hex = "0x742d35Cc6634C0532925a3b844Bc9e7595f8fE00";

        // When
        var address = Address.from(hex);

        // Then
        assertThat(address.value()).isEqualToIgnoringCase(hex);
    }
}
```

## Pull Request Process

1. **Create a branch** from `main`
2. **Make your changes** with clear, atomic commits
3. **Write/update tests** for your changes
4. **Run the test suite** locally
5. **Submit a PR** using the template
6. **Address review feedback**

### PR Requirements

- [ ] Tests pass (`./gradlew test -Pbrane.unit.tests`)
- [ ] Code is formatted (`./gradlew spotlessCheck`)
- [ ] No new warnings
- [ ] Documentation updated (if applicable)

### Commit Messages

Write clear, concise commit messages:

```
feat(core): add EIP-712 typed data signing

- Add TypedData record for structured data
- Implement domain separator hashing
- Add sign() method to Signer interface
```

Format: `type(scope): description`

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`

## Module Overview

| Module | Purpose |
|--------|---------|
| `brane-primitives` | Hex/RLP utilities (zero dependencies) |
| `brane-core` | Types, ABI, crypto, EIP-712, builders |
| `brane-kzg` | KZG commitments for EIP-4844 |
| `brane-rpc` | JSON-RPC client layer |
| `brane-contract` | Contract binding (no codegen) |

## Getting Help

- **Questions**: Open a [Discussion](https://github.com/noise-xyz/brane/discussions)
- **Bugs**: File an [Issue](https://github.com/noise-xyz/brane/issues)
- **Security**: See [SECURITY.md](./SECURITY.md)

## License

By contributing, you agree that your contributions will be dual-licensed under Apache-2.0 and MIT licenses.
