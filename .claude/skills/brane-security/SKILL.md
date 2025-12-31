---
name: brane-security
description: Security best practices for Brane SDK development. Use when handling private keys, validating inputs, reviewing security-sensitive code, or implementing signing functionality.
---

# Brane SDK Security Guide

## Core Principles

1. **Private keys are radioactive** - Minimize exposure, never log, zero after use
2. **Validate all external input** - RPC responses, user input, hex strings
3. **Fail secure** - When in doubt, reject
4. **Defense in depth** - Multiple validation layers

---

## Private Key Handling

### Never Log Private Keys

```java
// CRITICAL: NEVER do this
logger.debug("Using key: " + privateKey);  // WRONG
System.out.println(signer);                 // WRONG if toString() includes key

// Brane's PrivateKey.toString() is safe (redacted)
// But never log the raw hex value
```

### Minimize Key Lifetime in Memory

```java
// GOOD - key loaded only when needed
public void signTransaction() {
    var key = loadKeyFromSecureStorage();
    try {
        var signature = key.sign(hash);
        // use signature
    } finally {
        key.destroy();  // if Destroyable
    }
}

// BAD - key lives forever in memory
public class MyApp {
    private final PrivateKey key = loadKey();  // Lives entire app lifetime
}
```

### Secure Key Storage

| Environment | Recommendation |
|-------------|----------------|
| Development | Environment variable, `.env` file (gitignored) |
| Production | HSM, AWS KMS, HashiCorp Vault, secure enclave |
| Testing | Anvil default keys only |

```java
// GOOD - load from environment
var keyHex = System.getenv("PRIVATE_KEY");
if (keyHex == null) {
    throw new IllegalStateException("PRIVATE_KEY not set");
}

// BAD - hardcoded (even for "testing")
var key = new PrivateKey("0xac0974...");  // Will end up in git
```

### Clear Sensitive Data

```java
// If using byte arrays for keys
byte[] keyBytes = ...;
try {
    // use key
} finally {
    Arrays.fill(keyBytes, (byte) 0);  // Zero out
}
```

---

## Input Validation

### Address Validation

```java
// GOOD - Address validates in constructor
try {
    var addr = new Address(userInput);
} catch (IllegalArgumentException e) {
    // Invalid address format
    throw new ValidationException("Invalid address: " + sanitize(userInput));
}

// Additional checks for specific contexts
if (addr.equals(Address.ZERO)) {
    throw new ValidationException("Zero address not allowed");
}
```

### Hex String Validation

```java
// GOOD - validate before processing
public HexData parseHex(String input) {
    Objects.requireNonNull(input, "input");
    if (!input.startsWith("0x")) {
        throw new IllegalArgumentException("Missing 0x prefix");
    }
    if (!input.matches("^0x[0-9a-fA-F]*$")) {
        throw new IllegalArgumentException("Invalid hex characters");
    }
    if (input.length() % 2 != 0) {
        throw new IllegalArgumentException("Odd length hex string");
    }
    return new HexData(input);
}
```

### Numeric Validation

```java
// GOOD - validate ranges
public Wei parseWei(String input) {
    var value = new BigInteger(input);
    if (value.signum() < 0) {
        throw new IllegalArgumentException("Wei cannot be negative");
    }
    if (value.compareTo(MAX_UINT256) > 0) {
        throw new IllegalArgumentException("Value exceeds uint256 max");
    }
    return Wei.of(value);
}
```

### RPC Response Validation

```java
// GOOD - don't trust RPC responses blindly
var response = provider.send("eth_getBalance", params);
if (response.hasError()) {
    throw new RpcException(...);
}
if (response.result() == null) {
    throw new RpcException(-32000, "Unexpected null result", null);
}

// Validate result format
String balanceHex = (String) response.result();
if (!balanceHex.startsWith("0x")) {
    throw new RpcException(-32000, "Invalid balance format", null);
}
```

---

## Signature Security

### Verify Signatures Before Use

```java
// When accepting signatures from external sources
public void processSignature(Signature sig, byte[] message) {
    // Recover the signer
    Address recovered = sig.recoverAddress(message);

    // Verify it's who we expect
    if (!recovered.equals(expectedSigner)) {
        throw new SecurityException("Signature not from expected signer");
    }
}
```

### Prevent Signature Malleability

EIP-2 requires `s` value in lower half of curve order:

```java
// Brane should handle this internally, but be aware
// s must be <= secp256k1n/2
// v must be 27/28 (legacy) or 0/1 (EIP-155+)
```

### Protect Against Replay Attacks

```java
// For signed messages, include:
// 1. Chain ID (EIP-155)
// 2. Contract address (EIP-712 domain)
// 3. Nonce (if applicable)

// EIP-712 domain prevents cross-contract replay
var domain = new EIP712Domain(
    "MyApp",           // name
    "1",               // version
    chainId,           // chain ID
    contractAddress    // verifying contract
);
```

---

## Transaction Security

### Verify Transaction Parameters

```java
// Before signing, verify critical fields
public void validateTransaction(TransactionRequest tx) {
    // Verify recipient isn't zero (unless intentional burn)
    if (tx.to() == null && tx.data() == null) {
        throw new SecurityException("Transaction has no recipient or data");
    }

    // Verify value isn't unexpectedly high
    if (tx.value() != null && tx.value().compareTo(maxAllowedValue) > 0) {
        throw new SecurityException("Value exceeds safety limit");
    }

    // Verify gas parameters are reasonable
    if (tx.gasLimit() != null && tx.gasLimit() > MAX_REASONABLE_GAS) {
        throw new SecurityException("Gas limit suspiciously high");
    }
}
```

### Chain ID Verification

```java
// Always verify chain ID before signing
public void ensureCorrectChain(long expected) {
    long actual = publicClient.getChainId();
    if (expected != actual) {
        throw new ChainMismatchException(expected, actual);
    }
}
```

### Nonce Management

```java
// Fetch nonce with "pending" to include mempool transactions
var nonce = publicClient.getTransactionCount(address, "pending");

// For high-throughput, track nonces locally
// But verify periodically against chain
```

---

## Contract Interaction Security

### Verify Contract Before Interaction

```java
// Check contract has code
var code = publicClient.getCode(contractAddress);
if (code == null || code.isEmpty() || code.equals("0x")) {
    throw new SecurityException("No contract at address");
}

// For upgradeable proxies, verify implementation
var implSlot = publicClient.getStorageAt(
    contractAddress,
    "0x360894a13ba1a3210667c828492db98dca3e2076cc3735a920a3ca505d382bbc"
);
```

### Simulate Before Send

```java
// Always eth_call before eth_sendTransaction for state-changing calls
try {
    publicClient.call(request);  // Dry run
} catch (RevertException e) {
    // Will revert, don't waste gas
    throw new ContractException("Call would revert: " + e.reason());
}

// If dry run succeeds, send real transaction
walletClient.sendTransaction(request);
```

### Handle Reverts Properly

```java
try {
    var receipt = walletClient.sendTransactionAndWait(request);
    if (!receipt.status()) {
        // Transaction mined but reverted
        throw new ContractException("Transaction reverted");
    }
} catch (RevertException e) {
    // Decode and log, but don't expose internal details to users
    logger.warn("Contract reverted: {} ({})", e.kind(), e.reason());
    throw new UserFacingException("Transaction failed");
}
```

---

## Logging Security

### Use LogSanitizer

```java
// Brane's LogSanitizer redacts sensitive data
DebugLogger.logTx(LogFormatter.formatTxSend(...));

// If logging manually, sanitize
logger.info("Transaction to: {}", LogSanitizer.sanitize(tx.toString()));
```

### What NOT to Log

| Never Log | Why |
|-----------|-----|
| Private keys | Obviously |
| Full transaction data | May contain sensitive calldata |
| User addresses (in some contexts) | Privacy |
| Full RPC responses | May contain sensitive data |
| Error details to end users | Information disclosure |

### What TO Log

| Safe to Log | Format |
|-------------|--------|
| Transaction hash | Full hash is public |
| Block numbers | Public |
| Contract addresses | Public (but consider context) |
| Error codes | Without sensitive details |
| Timing/metrics | For debugging |

---

## Common Vulnerabilities

### Integer Overflow

```java
// BAD - overflow risk
long value = Long.parseLong(hexValue, 16);  // Overflows > 2^63

// GOOD - use BigInteger
BigInteger value = new BigInteger(hexValue.substring(2), 16);
```

### Unchecked Array Access

```java
// BAD - can throw IndexOutOfBoundsException
var firstLog = receipt.logs().get(0);

// GOOD - check first
if (receipt.logs().isEmpty()) {
    throw new ContractException("Expected logs but found none");
}
var firstLog = receipt.logs().get(0);
```

### Null Pointer Dereference

```java
// BAD - NPE if to() is null
var recipient = tx.to().value();

// GOOD - handle null
var recipient = tx.to() != null ? tx.to().value() : null;
// Or
var recipient = tx.toOpt().map(Address::value).orElse(null);
```

### Resource Leaks

```java
// BAD - connection leak
HttpClient client = HttpClient.newHttpClient();
// ... use and forget

// GOOD - close resources
try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
    // use executor
}
```

---

## Security Checklist for Code Review

### Private Keys
- [ ] Never logged or printed
- [ ] Not hardcoded
- [ ] Loaded from secure source
- [ ] Minimized lifetime in memory
- [ ] Cleared after use (if possible)

### Input Validation
- [ ] All user input validated
- [ ] All RPC responses validated
- [ ] Hex strings validated (prefix, characters, length)
- [ ] Addresses validated (format, not zero if inappropriate)
- [ ] Numbers validated (non-negative, within bounds)

### Transactions
- [ ] Chain ID verified before signing
- [ ] Nonce fetched with "pending"
- [ ] Gas parameters validated
- [ ] eth_call before eth_sendTransaction (when appropriate)
- [ ] Revert reasons handled

### Error Handling
- [ ] Sensitive data not in error messages
- [ ] Exceptions don't leak internal state
- [ ] Failed operations don't leave partial state

### Logging
- [ ] No private keys in logs
- [ ] Sensitive data sanitized
- [ ] Error details appropriate for audience

---

## Security Testing

### Test with Anvil

```bash
# Test against local node - safe to use test keys
anvil
```

### Never Use Real Keys in Tests

```java
// Test constants - ONLY for Anvil/local testing
// These are Anvil's default keys - everyone knows them
private static final String TEST_KEY =
    "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";
```

### Fuzz Testing Considerations

- Test with malformed hex strings
- Test with boundary values (0, MAX_UINT256)
- Test with invalid addresses
- Test with null inputs
