#!/bin/bash
# Setup script for Maven Central publishing environment variables
# Run this script to configure your shell environment for publishing

set -e

KEYS_DIR="$HOME/.brane-keys"
GPG_KEY_ID="BE398C4E143D3170"

echo "=== Brane SDK Publishing Environment Setup ==="
echo ""

# Check if keys directory exists
if [ ! -d "$KEYS_DIR" ]; then
    echo "Creating keys directory: $KEYS_DIR"
    mkdir -p "$KEYS_DIR"
fi

# Check public key
if [ ! -f "$KEYS_DIR/public.asc" ]; then
    echo "Exporting public key..."
    gpg --armor --export "$GPG_KEY_ID" > "$KEYS_DIR/public.asc"
fi

# Check private key
if [ ! -f "$KEYS_DIR/private.asc" ]; then
    echo "Exporting private key (you may be prompted for passphrase)..."
    gpg --armor --export-secret-keys "$GPG_KEY_ID" > "$KEYS_DIR/private.asc"
fi

# Verify keys exist
if [ ! -s "$KEYS_DIR/public.asc" ] || [ ! -s "$KEYS_DIR/private.asc" ]; then
    echo "ERROR: Key export failed. Please run manually:"
    echo "  gpg --armor --export $GPG_KEY_ID > $KEYS_DIR/public.asc"
    echo "  gpg --armor --export-secret-keys $GPG_KEY_ID > $KEYS_DIR/private.asc"
    exit 1
fi

echo ""
echo "Keys exported successfully to $KEYS_DIR"
echo ""

# Generate environment variable exports
cat << 'EXPORTS'
=== Add these to your shell profile (~/.zshrc or ~/.bashrc) ===

# GPG Signing for Maven Central
export JRELEASER_GPG_PUBLIC_KEY="$(cat ~/.brane-keys/public.asc)"
export JRELEASER_GPG_SECRET_KEY="$(cat ~/.brane-keys/private.asc)"
export JRELEASER_GPG_PASSPHRASE=""  # Set this to your GPG key passphrase

# Sonatype Central credentials (get from central.sonatype.com > Settings > Generate User Token)
export JRELEASER_MAVENCENTRAL_CENTRAL_USERNAME="your-token-username"
export JRELEASER_MAVENCENTRAL_CENTRAL_PASSWORD="your-token-password"

EXPORTS

echo ""
echo "=== Next Steps ==="
echo "1. Add a passphrase to your GPG key (recommended):"
echo "   gpg --edit-key $GPG_KEY_ID passwd"
echo ""
echo "2. Get Sonatype credentials:"
echo "   - Go to https://central.sonatype.com"
echo "   - Sign in and verify namespace 'io.brane'"
echo "   - Go to Settings > Generate User Token"
echo "   - Copy the username and password"
echo ""
echo "3. Add the environment variables to your shell profile"
echo ""
echo "4. Test publishing locally:"
echo "   ./gradlew stageRelease"
echo ""
