#!/bin/bash
set -e

echo "🚀 Starting Smoke Test Verification..."

# 0. Cleanup existing Anvil
pkill anvil || true

# 1. Start Anvil
echo "🔨 Starting Anvil..."
LOG_FILE="$PWD/anvil_smoke_temp.log"
echo "   (Logging to $LOG_FILE)"

anvil > "$LOG_FILE" 2>&1 &
ANVIL_PID=$!

cleanup() {
    echo "🛑 Stopping Anvil..."
    kill $ANVIL_PID 2>/dev/null || true
    rm -f "$LOG_FILE"
}
trap cleanup EXIT

# Wait for Anvil to be ready by polling
echo "   Waiting for Anvil to be ready..."
MAX_RETRIES=30
count=0
while ! curl -s -X POST --data '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}' -H "Content-Type: application/json" http://127.0.0.1:8545 &> /dev/null; do
    echo -n "."
    sleep 1
    count=$((count+1))
    if [ $count -ge $MAX_RETRIES ]; then
        echo "❌ Timed out waiting for Anvil to start!"
        cat "$LOG_FILE"
        exit 1
    fi
done
echo " Anvil is ready."

# Check if Anvil is still running
if ! ps -p $ANVIL_PID > /dev/null; then
    echo "❌ Anvil failed to start!"
    cat "$LOG_FILE"
    exit 1
fi

# Check for port conflict in logs
if grep -q "Address already in use" "$LOG_FILE"; then
    echo "❌ Anvil failed to bind port (Address already in use)!"
    cat "$LOG_FILE"
    exit 1
fi

# 2. Publish Main SDK to Maven Local
echo "📦 Publishing SDK to Maven Local..."
./gradlew publishToMavenLocal --quiet --no-daemon

# 3. Run Smoke Test Consumer
echo "🔥 Running Smoke Test Consumer..."
cd smoke-test
if [ -z "$*" ]; then
    CMD="../gradlew run"
else
    CMD="../gradlew run --args='$*'"
fi

if eval $CMD; then
    echo "✅ Smoke Test Verification Successful!"
else
    echo "❌ Smoke Test Failed!"
    echo "--- Anvil Logs ---"
    cat "$LOG_FILE"
    echo "------------------"
    exit 1
fi
