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

# 2. Deploy Contracts (Foundry)
echo "📦 Deploying Contracts..."
if [ -d "foundry/anvil-tests" ]; then
    cd foundry/anvil-tests
    
    # Deploy RevertExample
    REVERT_OUT=$(forge create src/RevertExample.sol:RevertExample --private-key $PRIVATE_KEY --rpc-url $RPC_URL --broadcast)
    echo "DEBUG: REVERT_OUT=$REVERT_OUT"
    REVERT_ADDR=$(echo "$REVERT_OUT" | grep "Deployed to:" | awk '{print $3}')
    echo "DEBUG: REVERT_ADDR=$REVERT_ADDR"
    
    # Deploy Storage
    STORAGE_OUT=$(forge create src/Storage.sol:Storage --private-key $PRIVATE_KEY --rpc-url $RPC_URL --broadcast)
    STORAGE_ADDR=$(echo "$STORAGE_OUT" | grep "Deployed to:" | awk '{print $3}')
    
    # Deploy BraneToken
    TOKEN_OUT=$(forge create src/BraneToken.sol:BraneToken --private-key $PRIVATE_KEY --rpc-url $RPC_URL --broadcast --constructor-args 1000000000000000000000)
    TOKEN_ADDR=$(echo "$TOKEN_OUT" | grep "Deployed to:" | awk '{print $3}')
    
    cd ../..
    
    echo "   Contracts Deployed:"
    echo "   - Revert: $REVERT_ADDR"
    echo "   - Storage: $STORAGE_ADDR"
    echo "   - Token: $TOKEN_ADDR"
else
    echo "⚠️ foundry/anvil-tests directory not found. Skipping deployment."
    REVERT_ADDR="0x0000000000000000000000000000000000000000"
    STORAGE_ADDR="0x0000000000000000000000000000000000000000"
    TOKEN_ADDR="0x0000000000000000000000000000000000000000"
fi

# 3. Run All Examples & Integration Tests (brane-examples)
echo "   Running All Examples & Integration Tests..."

run_example() {
    local class=$1
    echo "   > $class"
    ./gradlew :brane-examples:run -PmainClass=$class \
        -Dbrane.examples.rpc="$RPC_URL" \
        -Dbrane.examples.pk="$PRIVATE_KEY" \
        -Dbrane.examples.contract="$TOKEN_ADDR" \
        -Dbrane.examples.erc20.contract="$TOKEN_ADDR" \
        -Dbrane.anvil.storage.address="$STORAGE_ADDR" \
        -Dbrane.anvil.revertExample.address="$REVERT_ADDR" \
        --console=plain -q
}

# Find all *Example.java and *IntegrationTest.java files
# We use find to get the paths, then sed to convert to package.ClassName
CLASSES=$(find brane-examples/src/main/java/io/brane/examples -name "*Example.java" -o -name "*IntegrationTest.java" | \
    sed 's|brane-examples/src/main/java/||' | \
    sed 's|/|.|g' | \
    sed 's|.java||')

for class in $CLASSES; do
    # Skip RequestIdSanityCheck here as we run it separately/explicitly if needed, 
    # but actually it's fine to run it in the loop too if it works.
    # However, we want to be explicit about Sanity checks.
    if [[ "$class" == *"RequestIdSanityCheck" ]]; then continue; fi
    
    run_example "$class"
done

# 4. Run Sanity Checks with I/O
echo "   Running Sanity Checks with I/O..."
run_example "io.brane.examples.RequestIdSanityCheck"

# 5. Run JUnit Integration Tests (brane-contract, brane-rpc, etc.)
echo "   Running JUnit Integration Tests..."
# We run any test ending in IntegrationTest or AnvilTest (configured in build.gradle)
./gradlew test \
    -Pbrane.integration.tests=true \
    -Dbrane.examples.rpc="$RPC_URL" \
    -Dbrane.anvil.rpc="$RPC_URL" \
    -Dbrane.anvil.signer.privateKey="$PRIVATE_KEY" \
    -Dbrane.anvil.storage.address="$STORAGE_ADDR" \
    -Dbrane.anvil.revertExample.address="$REVERT_ADDR" \
    --no-daemon --console=plain

echo "✅ Integration Tests Passed"
