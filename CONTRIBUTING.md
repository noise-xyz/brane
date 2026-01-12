# Contributing to Brane SDK

## Quick Start

```bash
git clone https://github.com/noise-xyz/brane.git && cd brane
./gradlew build
./scripts/test_sanity.sh      # Verify setup
```

**Requirements:** Java 21+, [Anvil](https://book.getfoundry.sh/getting-started/installation) (for integration tests)

## Development

### Code Style

```bash
./gradlew spotlessCheck       # Verify
./gradlew spotlessApply       # Auto-fix
```

**Key rules:**
- Use Java 21 features: records, sealed classes, pattern matching, `var`
- Use Brane types (`Address`, `Wei`, `Hash`) - avoid raw `String`/`BigInteger`
- Write blocking code with virtual threads - no reactive chains
- All fields `final` unless mutation is necessary

### Testing

We use a layered test approach:

| Layer | Command | Anvil? |
|-------|---------|--------|
| Unit | `./gradlew test -Pbrane.unit.tests` | No |
| Integration | `./scripts/test_integration.sh` | Yes |
| Smoke | `./scripts/test_smoke.sh` | Yes |
| **Full** | `./verify_all.sh` | Yes |

```bash
anvil &                               # Start local node
./verify_all.sh                       # Run all layers
```

**PR requirements:**
- All tests pass
- New features include unit tests
- Code is formatted

### Commits

Format: `type(scope): description`

```
feat(core): add EIP-712 typed data signing
fix(rpc): handle null response from provider
```

Types: `feat`, `fix`, `refactor`, `docs`, `test`, `chore`

## Common Issues

| Problem | Solution |
|---------|----------|
| Connection refused | Start Anvil: `anvil` |
| Tests hang | Use `sendTransactionAndWait()` |
| Blob tx fails | `anvil --hardfork cancun` |

## Help

- **Questions**: [Discussions](https://github.com/noise-xyz/brane/discussions)
- **Bugs**: [Issues](https://github.com/noise-xyz/brane/issues)
- **Security**: [SECURITY.md](./SECURITY.md)

## License

Contributions are dual-licensed under Apache-2.0 and MIT.
