# Internal Dogfooding Guide

## How to use `brane` in your project

Brane SDK is published to **Maven Central** under group `sh.brane`.

### 1. Add Dependencies (Release)
```groovy
repositories {
    mavenCentral()
}

dependencies {
    implementation 'sh.brane:brane-core:0.3.0'
    implementation 'sh.brane:brane-rpc:0.3.0'
    implementation 'sh.brane:brane-contract:0.3.0'
}
```

### 2. Using Snapshots (Pre-release Testing)
For testing unreleased changes:
```groovy
repositories {
    mavenCentral()
    maven { url 'https://central.sonatype.com/repository/maven-snapshots/' }
}

dependencies {
    implementation 'sh.brane:brane-core:0.4.0-SNAPSHOT'
}
```

### 3. Using Local Builds (Development)
For testing local changes before they're published:
```groovy
repositories {
    mavenLocal()  // ~/.m2/repository
    mavenCentral()
}

dependencies {
    implementation 'sh.brane:brane-core:0.3.0'
}
```

## Publishing Workflow (For Contributors)

### Local Development
```bash
# Publish to ~/.m2/repository for local testing
./gradlew publishToMavenLocal
```

### Snapshot Release
```bash
# Publish snapshot to Sonatype (requires credentials)
./gradlew publishSnapshot
```

### Official Release
```bash
# 1. Stage artifacts locally (verify before release)
./gradlew stageRelease

# 2. Deploy to Maven Central via JReleaser
./gradlew jreleaserDeploy
```

Required environment variables for release:
```bash
export JRELEASER_GPG_SECRET_KEY="..."
export JRELEASER_GPG_PASSPHRASE="..."
export JRELEASER_MAVENCENTRAL_CENTRAL_USERNAME="..."
export JRELEASER_MAVENCENTRAL_CENTRAL_PASSWORD="..."
```

## Local Verification (For Contributors)

If you are developing `brane` itself, use these scripts to verify your changes.

### 1. Smoke Tests (Fast & Comprehensive)
The smoke test suite is the primary verification tool. It compiles a standalone consumer app and runs it against a local Anvil node.

```bash
./scripts/test_smoke.sh
```
**What it does:**
1.  Starts a fresh Anvil instance (fails if port 8545 is busy).
2.  Publishes the SDK to `mavenLocal`.
3.  Compiles and runs `smoke-test/src/main/java/io/brane/smoke/SmokeApp.java`.
4.  Verifies 15 scenarios (Core, Errors, Events, ABI, EIP-1559, etc.).
5.  Cleans up Anvil process.

#### Scenarios Covered
*   **Scenario A: End-to-End Token Transfer**: Deploys `BraneToken`, checks balance, transfers tokens.
*   **Scenario B: Error Handling**: Verifies `RevertException` and `RpcException` handling.
*   **Scenario C: Event Logs**: Queries `eth_getLogs` for emitted events.
*   **Scenario D: ABI Wrapper**: Uses `BraneContract` for high-level interaction.
*   **Scenario E: EIP-1559 & Access Lists**: Sends modern transaction types.
*   **Scenario F: Raw Signing**: Manually signs transactions offline.
*   **Scenario G: Custom RPC**: Calls custom methods like `anvil_mine`.
*   **Scenario H: Chain ID Validation**: Ensures safety against wrong networks.
*   **Scenario I: Public Client Read Ops**: Verifies block and transaction reading.
*   **Scenario J: Wei Utilities**: Tests unit conversion.
*   **Scenario K: Complex ABI**: Tests arrays, tuples, and fixed bytes.
*   **Scenario L: Gas Strategy**: Verifies gas limit buffering.
*   **Scenario M: Debug & Color Mode**: Verifies logging output.
*   **Scenario N: Custom Error Decoding**: Decodes Solidity custom errors.
*   **Scenario O: Complex Nested Structs**: Verifies encoding/decoding of nested tuples.

### 2. Real Network Verification (Sepolia)
You can run the smoke tests against the Sepolia testnet in read-only mode. This verifies connectivity and data retrieval without spending gas.

```bash
# Note: Sepolia tests are run by passing arguments to the SmokeApp via Gradle, not through this script directly.
# Example: ./gradlew :smoke-test:run --args='--sepolia'
# You can also override the RPC via the BRANE_SEPOLIA_RPC environment variable.
```

### 3. Canonical Examples (Learning & Debugging)
Run the examples to see the SDK in action or to debug specific features.

```bash
./scripts/test_integration.sh
```
**Prerequisite:** You must have `anvil` running in a separate terminal (or let the script start it).

**What it does:**
- Runs `CanonicalErc20Example` (High-level binding)
- Runs `CanonicalRawExample` (Low-level RPC)
- Runs `CanonicalDebugExample` (Error handling)
- Runs `CanonicalTxExample` (EIP-1559 & Access Lists)
- Runs `CanonicalAbiExample` (ABI encoding/decoding)

### 4. Integration Tests (Deep Testing)
Run the full JUnit integration test suite.

```bash
./scripts/test_integration.sh
```
**What it does:**
- Runs standard JUnit tests in `brane-rpc` and `brane-core`.
- Requires Anvil (started automatically by some tests, or checks for local instance).

## Prerequisites
- **Java 21**: Required for building and running.
- **Foundry (Anvil)**: Required for local blockchain simulation. Install via `foundryup`.
