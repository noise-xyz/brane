#!/bin/bash
set -e

echo "🔄 Syncing Javadocs..."

# 1. Generate Javadocs
./gradlew allJavadoc

# 2. Clean destination to remove stale files
rm -rf website/public/javadoc
mkdir -p website/public/javadoc

# 3. Copy new Javadocs
cp -r build/docs/javadoc/* website/public/javadoc/

echo "✅ Javadocs synced to website/public/javadoc"
