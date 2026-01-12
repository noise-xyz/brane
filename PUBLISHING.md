# Publishing to Maven Central

This document describes how to publish Brane SDK to Maven Central using JReleaser.

## Prerequisites

### 1. Sonatype Central Account

1. Create an account at [central.sonatype.com](https://central.sonatype.com)
2. Verify namespace ownership for `sh.brane`
3. Generate a user token (Settings > Generate User Token)
4. Save the username and password - these are your API credentials

### 2. GPG Key Setup

Generate a GPG key for signing artifacts:

```bash
# Generate key (use RSA 4096-bit, no expiration for simplicity)
gpg --full-generate-key

# List keys to find your key ID
gpg --list-secret-keys --keyid-format LONG

# Export public key (replace KEY_ID with yours)
gpg --armor --export KEY_ID > public.asc

# Export private key
gpg --armor --export-secret-keys KEY_ID > private.asc

# Upload public key to keyserver
gpg --keyserver keyserver.ubuntu.com --send-keys KEY_ID
```

### 3. Environment Variables

Set these environment variables (in CI or locally):

```bash
# GPG signing (armored key content, not file paths)
export JRELEASER_GPG_PUBLIC_KEY="$(cat public.asc)"
export JRELEASER_GPG_SECRET_KEY="$(cat private.asc)"
export JRELEASER_GPG_PASSPHRASE=""  # Set securely; never hardcode or commit this value

# Sonatype credentials (from token generation)
export JRELEASER_MAVENCENTRAL_CENTRAL_USERNAME="sonatype-token-username"
export JRELEASER_MAVENCENTRAL_CENTRAL_PASSWORD="sonatype-token-password"
```

## Publishing Workflow

### Local Development / Testing

```bash
# Stage artifacts locally (no credentials needed)
./gradlew stageRelease

# Verify staged artifacts
ls -la brane-*/build/staging-deploy/
```

### Publishing Snapshots

Snapshots go directly to Sonatype without JReleaser:

```bash
# Set version to snapshot in build.gradle
# version = '0.3.0-SNAPSHOT'

./gradlew publishSnapshot
```

### Publishing Releases

Releases use JReleaser for GPG signing and Maven Central deployment:

```bash
# 1. Ensure version is NOT a snapshot
# version = '0.3.0'

# 2. Stage and deploy all modules
./gradlew jreleaserDeploy

# Or for a single module:
./gradlew :brane-core:jreleaserDeploy
```

JReleaser will:
1. Stage artifacts to `build/staging-deploy/`
2. Sign all artifacts with GPG
3. Upload to Maven Central staging
4. Automatically release after validation passes

## Published Modules

| Module | Artifact ID | Description |
|--------|-------------|-------------|
| brane-primitives | `sh.brane:brane-primitives` | Hex/RLP utilities |
| brane-core | `sh.brane:brane-core` | Types, ABI, crypto |
| brane-kzg | `sh.brane:brane-kzg` | KZG commitments |
| brane-rpc | `sh.brane:brane-rpc` | JSON-RPC client |
| brane-contract | `sh.brane:brane-contract` | Contract binding |

Non-published modules: `brane-examples`, `brane-benchmark`, `smoke-test`

## CI/CD Integration

### GitHub Actions

```yaml
name: Release

on:
  push:
    tags:
      - 'v*'

jobs:
  release:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - uses: actions/setup-java@v4
        with:
          java-version: '21'
          distribution: 'temurin'

      - name: Publish to Maven Central
        env:
          JRELEASER_GPG_PUBLIC_KEY: ${{ secrets.GPG_PUBLIC_KEY }}
          JRELEASER_GPG_SECRET_KEY: ${{ secrets.GPG_SECRET_KEY }}
          JRELEASER_GPG_PASSPHRASE: ${{ secrets.GPG_PASSPHRASE }}
          JRELEASER_MAVENCENTRAL_CENTRAL_USERNAME: ${{ secrets.SONATYPE_USERNAME }}
          JRELEASER_MAVENCENTRAL_CENTRAL_PASSWORD: ${{ secrets.SONATYPE_PASSWORD }}
        run: ./gradlew jreleaserDeploy
```

### Required Secrets

Add these to your GitHub repository secrets:
- `GPG_PUBLIC_KEY` - Armored GPG public key
- `GPG_SECRET_KEY` - Armored GPG private key
- `GPG_PASSPHRASE` - GPG key passphrase
- `SONATYPE_USERNAME` - Sonatype token username
- `SONATYPE_PASSWORD` - Sonatype token password

## Troubleshooting

### "Could not find signing key"

Ensure GPG keys are properly exported as armored ASCII:
```bash
gpg --armor --export-secret-keys KEY_ID
```

### "401 Unauthorized" from Sonatype

1. Verify credentials are from a generated token, not your login password
2. Check the username/password match what Sonatype generated
3. Ensure namespace `sh.brane` is verified

### "Invalid signature"

1. Ensure public key is uploaded to a keyserver
2. Wait a few minutes for keyserver propagation
3. Try alternate keyserver: `keys.openpgp.org`

### Validation Failures

Check the JReleaser output for specific validation errors. Common issues:
- Missing POM metadata (description, URL, SCM)
- Missing Javadoc or sources JAR
- Invalid coordinates

## Version Management

Update version in `build.gradle`:

```groovy
allprojects {
    group = 'sh.brane'
    version = '0.4.0'  // Update this
}
```

## Dry Run

Test the full release process without actually publishing:

```bash
./gradlew jreleaserDeploy -Djreleaser.dry.run=true
```
