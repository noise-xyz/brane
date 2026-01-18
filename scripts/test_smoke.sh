#!/bin/bash
set -e

echo "🔥 [Level 3] Running Smoke Tests..."

# 1. Publish to Maven Local (Required for SmokeApp to pick up latest changes)
echo "   📦 Publishing SDK to Maven Local..."
./gradlew publishToMavenLocal --quiet --no-daemon

# 2. Ensure Anvil is running
RPC_URL="http://127.0.0.1:8545"
if ! curl -s -X POST -H "Content-Type: application/json" --data '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' "$RPC_URL" > /dev/null; then
    echo "🔨 Starting Anvil..."
    anvil > anvil_smoke.log 2>&1 &
    ANVIL_PID=$!
    
    cleanup() {
        echo "🛑 Stopping Anvil..."
        kill $ANVIL_PID 2>/dev/null || true
        rm -f anvil_smoke.log
    }
    trap cleanup EXIT
    
    sleep 3
fi

# 3. Run SmokeApp
echo "   Running SmokeApp..."
./gradlew :brane-smoke:run --quiet --console=plain

echo "✅ Smoke Tests Passed"
