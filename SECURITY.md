# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.3.x   | :white_check_mark: |
| < 0.3   | :x:                |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

If you discover a security vulnerability in Brane SDK, please report it privately:

1. **Email**: security@noise.xyz
2. **Subject**: `[SECURITY] Brane SDK - Brief description`

### What to Include

- Description of the vulnerability
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

### What to Expect

- **Acknowledgment**: Within 48 hours
- **Initial Assessment**: Within 7 days
- **Resolution Timeline**: Depends on severity, typically 30-90 days

We will work with you to understand and address the issue. We appreciate responsible disclosure and will credit reporters (unless you prefer to remain anonymous).

## Security Considerations for Users

### Private Key Handling

```java
// Always destroy private keys when done
PrivateKey key = PrivateKey.fromHex("0x...");
try {
    // Use the key
} finally {
    key.destroy();
}
```

### ThreadLocal Cleanup

If using Brane in pooled/web thread environments:

```java
// Cleanup ThreadLocal Keccak256 instances to prevent memory leaks
Keccak256.cleanup();
```

### Mnemonic Wallet Security

- Keep `MnemonicWallet` instances short-lived
- Never log or persist mnemonic phrases
- Derived `Signer` instances can be held longer

## Scope

This security policy covers:

- `brane-primitives`
- `brane-core`
- `brane-kzg`
- `brane-rpc`
- `brane-contract`

Third-party dependencies are not covered, but we will help coordinate disclosure if needed.
