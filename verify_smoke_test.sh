#!/bin/bash
set -e

echo "🚀 Starting Smoke Test Verification..."

# 1. Start Anvil
echo "🔨 Starting Anvil..."
anvil > anvil_smoke.log 2>&1 &
ANVIL_PID=$!

cleanup() {
    echo "🛑 Stopping Anvil..."
    kill $ANVIL_PID 2>/dev/null || true
}
trap cleanup EXIT

sleep 3 # Wait for Anvil to be ready

# 2. Publish Main SDK to Maven Local
echo "📦 Publishing SDK to Maven Local..."
./gradlew publishToMavenLocal --quiet --no-daemon

# 3. Run Smoke Test Consumer
echo "🔥 Running Smoke Test Consumer..."
cd smoke-test
../gradlew run --quiet --no-daemon

if [ $? -eq 0 ]; then
    echo "✅ Smoke Test Verification Successful!"
else
    echo "❌ Smoke Test Failed! Check anvil_smoke.log for details."
    exit 1
fi
