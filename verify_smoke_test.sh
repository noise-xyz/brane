#!/bin/bash
set -e

echo "🚀 Starting Smoke Test Verification..."

# 1. Publish Main SDK to Maven Local
echo "📦 Publishing SDK to Maven Local..."
./gradlew publishToMavenLocal --quiet --no-daemon

# 2. Run Smoke Test Consumer
echo "🔥 Running Smoke Test Consumer..."
cd smoke-test
../gradlew run --quiet --no-daemon

if [ $? -eq 0 ]; then
    echo "✅ Smoke Test Verification Successful!"
else
    echo "❌ Smoke Test Failed!"
    exit 1
fi
