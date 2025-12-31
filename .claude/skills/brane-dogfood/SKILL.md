---
name: brane-dogfood
description: Help with testing Brane SDK as a consumer dependency. Use when setting up JitPack dependencies, running smoke tests, verifying releases, or testing Brane from an external project.
---

# Brane SDK Dogfooding Guide

## Using Brane as a Dependency (JitPack)

### 1. Add JitPack Repository

```groovy
repositories {
    mavenCentral()
    maven { url 'https://jitpack.io' }
}
```

### 2. Add Dependency

**Target a Release (Recommended for production):**
```groovy
dependencies {
    implementation 'com.github.noise-xyz.brane:brane-core:0.1.0'
}
```

**Target a Specific Commit (Debugging):**
```groovy
dependencies {
    implementation 'com.github.noise-xyz.brane:brane-core:a1b2c3d'
}
```

**Target a Branch Snapshot (Latest changes):**
```groovy
dependencies {
    implementation 'com.github.noise-xyz.brane:brane-core:main-SNAPSHOT'
}
```

## Local Verification

### Smoke Tests (Primary Verification)

```bash
./scripts/test_smoke.sh
```

**What it does:**
1. Starts a fresh Anvil instance (fails if port 8545 is busy)
2. Publishes SDK to `mavenLocal`
3. Compiles and runs `SmokeApp.java`
4. Verifies 15 scenarios
5. Cleans up Anvil

**Scenarios covered:**
| Scenario | Description |
|----------|-------------|
| A | End-to-End Token Transfer |
| B | Error Handling (RevertException, RpcException) |
| C | Event Logs (eth_getLogs) |
| D | ABI Wrapper (BraneContract) |
| E | EIP-1559 & Access Lists |
| F | Raw Signing (offline) |
| G | Custom RPC (anvil_mine) |
| H | Chain ID Validation |
| I | Public Client Read Ops |
| J | Wei Utilities |
| K | Complex ABI (arrays, tuples, fixed bytes) |
| L | Gas Strategy (buffering) |
| M | Debug & Color Mode |
| N | Custom Error Decoding |
| O | Complex Nested Structs |

### Real Network Verification (Sepolia)

Read-only tests against Sepolia testnet:

```bash
./gradlew :smoke-test:run --args='--sepolia'

# With custom RPC
BRANE_SEPOLIA_RPC=https://your-rpc.com ./gradlew :smoke-test:run --args='--sepolia'
```

### Canonical Examples

```bash
./scripts/test_integration.sh
```

Runs:
- `CanonicalErc20Example` - High-level binding
- `CanonicalRawExample` - Low-level RPC
- `CanonicalDebugExample` - Error handling
- `CanonicalTxExample` - EIP-1559 & Access Lists
- `CanonicalAbiExample` - ABI encoding/decoding

## Prerequisites

- **Java 21**: Required for building and running
- **Foundry (Anvil)**: Required for local blockchain simulation

Install Foundry:
```bash
curl -L https://foundry.paradigm.xyz | bash
foundryup
```

## Troubleshooting

| Issue | Solution |
|-------|----------|
| Port 8545 busy | Kill existing Anvil: `pkill anvil` |
| JitPack build fails | Check commit is pushed to remote |
| mavenLocal stale | Run `./gradlew publishToMavenLocal` |
| Sepolia timeout | Check RPC URL and network status |
