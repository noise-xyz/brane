#!/bin/bash
set -e

echo "🔄 [Level 2] Running Integration Tests..."

# Configuration
RPC_URL="http://127.0.0.1:8545"
PRIVATE_KEY="0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"

# 1. Ensure Anvil is running
if ! curl -s -X POST -H "Content-Type: application/json" --data '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' "$RPC_URL" > /dev/null; then
    echo "🔨 Starting Anvil..."
    anvil > anvil_integration.log 2>&1 &
    ANVIL_PID=$!
    
    # Cleanup on exit
    cleanup() {
        echo "🛑 Stopping Anvil..."
        kill $ANVIL_PID 2>/dev/null || true
        rm -f anvil_integration.log
    }
    trap cleanup EXIT
    
    # Wait for Anvil
    sleep 3
else
    echo "✓ Anvil is already running."
fi

# 2. Deploy Contracts (Required for some tests)
# We reuse the logic from the old script, but streamlined.
# For now, we assume the tests that need contracts deploy them (like CanonicalErc20Example).
# If we need pre-deployed contracts, we should add a deployment step here.

# 3. Run Canonical Examples (The Core Integration Suite)
echo "   Running Canonical Examples..."

run_example() {
    local class=$1
    echo "   > $class"
    ./gradlew :brane-examples:run -PmainClass=$class \
        -Dbrane.examples.rpc="$RPC_URL" \
        -Dbrane.examples.pk="$PRIVATE_KEY" \
        --console=plain -q
}

run_example "io.brane.examples.CanonicalErc20Example"
run_example "io.brane.examples.CanonicalRawExample"
run_example "io.brane.examples.CanonicalDebugExample"
run_example "io.brane.examples.CanonicalTxExample"
run_example "io.brane.examples.CanonicalAbiExample"
run_example "io.brane.examples.CanonicalCustomSignerExample"
run_example "io.brane.examples.CanonicalSafeMultisigExample"

# 4. Run Sanity Checks with I/O
echo "   Running Sanity Checks with I/O..."
run_example "io.brane.examples.RequestIdSanityCheck"

# 5. Run JUnit Integration Tests
echo "   Running JUnit Integration Tests..."
./gradlew :brane-contract:test --tests "io.brane.contract.AbiWrapperIntegrationTest" \
    -Dbrane.integration.tests=true \
    -Dbrane.examples.rpc="$RPC_URL" \
    -Dbrane.anvil.rpc="$RPC_URL" \
    -Dbrane.anvil.signer.privateKey="$PRIVATE_KEY" \
    --no-daemon --console=plain

echo "✅ Integration Tests Passed"
