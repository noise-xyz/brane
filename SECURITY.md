# Security Policy

## Supported Versions

| Version | Supported          |
| ------- | ------------------ |
| 0.3.x   | :white_check_mark: |
| < 0.3   | :x:                |

## Reporting a Vulnerability

**Please do not report security vulnerabilities through public GitHub issues.**

Use GitHub's private vulnerability reporting:

**[Report a Vulnerability](https://github.com/noise-xyz/brane/security/advisories/new)**

### What to Include

- Description of the vulnerability
- Affected versions
- Steps to reproduce
- Potential impact
- Suggested fix (if any)

### What to Expect

- **Acknowledgment**: Within 48 hours
- **Initial Assessment**: Within 7 days
- **Resolution Timeline**: Depends on severity, typically 30-90 days

We will collaborate with you privately through GitHub's security advisory system to understand and address the issue. Once resolved, we'll publish an advisory and credit you (unless you prefer to remain anonymous).

## Security Considerations for Users

### Private Key Handling

```java
// Never hardcode keys in production - use environment variables, KMS, or secure storage
// fromBytes() zeros the input array; fromHex() is shown here for brevity
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

- Keep `MnemonicWallet` instances short-lived. The mnemonic phrase cannot be cleared from memory due to Java's String immutability, so minimizing the wallet's lifetime reduces its exposure.
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
