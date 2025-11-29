#!/bin/bash
set -e

# Set JAVA_HOME to the local JDK 21
if [ -d ".jdk/jdk-21.jdk/Contents/Home" ]; then
    export JAVA_HOME="$(pwd)/.jdk/jdk-21.jdk/Contents/Home"
elif command -v /usr/libexec/java_home &> /dev/null; then
    export JAVA_HOME=$(/usr/libexec/java_home -v 21)
fi

# Start anvil in background
echo "Starting Anvil..."
anvil > anvil.log 2>&1 &
ANVIL_PID=$!

# Function to cleanup anvil on exit
cleanup() {
    echo "Stopping Anvil..."
    if [ -n "$ANVIL_PID" ]; then
        kill $ANVIL_PID > /dev/null 2>&1 || true
    fi
}
trap cleanup EXIT

# Wait for anvil to start
sleep 3

# Default Anvil Private Key (Account 0)
PRIVATE_KEY="0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"
RPC_URL="http://127.0.0.1:8545"

echo "🚀 Running Fast Checks (Unit Tests + Sanity)..."

# 1. Run ALL Unit Tests & Sanity Checks in ONE Gradle invocation
# This replaces ~6 separate Gradle calls
./gradlew test \
    :brane-examples:run -PmainClass=io.brane.examples.CryptoSanityCheck \
    :brane-examples:run -PmainClass=io.brane.examples.TransactionSanityCheck \
    :brane-examples:run -PmainClass=io.brane.examples.SmartGasSanityCheck \
    :brane-examples:run -PmainClass=io.brane.examples.RequestIdSanityCheck \
    --parallel --no-daemon

if [ $? -ne 0 ]; then
    echo "❌ Unit Tests or Sanity Checks Failed!"
    exit 1
fi
echo "✅ Unit Tests & Sanity Checks Passed"

# 2. Deploy Contracts (Foundry)
echo "📦 Deploying Contracts..."
cd foundry/anvil-tests

# Deploy all contracts
REVERT_OUT=$(forge create src/RevertExample.sol:RevertExample --private-key $PRIVATE_KEY --rpc-url $RPC_URL --broadcast)
REVERT_ADDR=$(echo "$REVERT_OUT" | grep "Deployed to:" | awk '{print $3}')

STORAGE_OUT=$(forge create src/Storage.sol:Storage --private-key $PRIVATE_KEY --rpc-url $RPC_URL --broadcast)
STORAGE_ADDR=$(echo "$STORAGE_OUT" | grep "Deployed to:" | awk '{print $3}')

TOKEN_OUT=$(forge create src/BraneToken.sol:BraneToken --private-key $PRIVATE_KEY --rpc-url $RPC_URL --broadcast --constructor-args 1000000000000000000000)
TOKEN_ADDR=$(echo "$TOKEN_OUT" | grep "Deployed to:" | awk '{print $3}')

cd ../..

if [ -z "$REVERT_ADDR" ] || [ -z "$STORAGE_ADDR" ] || [ -z "$TOKEN_ADDR" ]; then
    echo "❌ Failed to deploy contracts"
    exit 1
fi

echo "✅ Contracts Deployed: Revert=$REVERT_ADDR, Storage=$STORAGE_ADDR, Token=$TOKEN_ADDR"

# 3. Run Integration Tests (Batched where possible)
echo "🔄 Running Integration Tests..."

# Anvil Addresses
HOLDER_ADDR="0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
RECIPIENT_ADDR="0x70997970C51812dc3A010C7d01b50e0d17dc79C8"

# We have to run these sequentially because they require different System Properties (-D)
# Gradle doesn't support passing different -D flags to different tasks in the same invocation easily.
# However, we can at least ensure we are efficient.

run_example() {
    local name=$1
    local class=$2
    shift 2
    printf "   %-40s " "Running $name..."
    OUT=$(./gradlew :brane-examples:run --quiet --no-daemon -PmainClass=$class "$@")
    if [ $? -ne 0 ]; then
        echo "❌ FAILED"
        echo "$OUT"
        exit 1
    fi
    echo "✅ PASS"
    # Optional: Return output for grep checks
    echo "$OUT"
}

# Main Example
OUT=$(run_example "Main Example" "io.brane.examples.Main" -Dbrane.examples.rpc=$RPC_URL -Dbrane.examples.contract=$REVERT_ADDR)
if ! echo "$OUT" | grep -q "echo(42) = 42"; then echo "❌ Main Example Output Mismatch"; exit 1; fi

# ERC20 Examples
run_example "Erc20Example" "io.brane.examples.Erc20Example" \
    -Dbrane.examples.erc20.rpc=$RPC_URL \
    -Dbrane.examples.erc20.contract=$TOKEN_ADDR \
    -Dbrane.examples.erc20.holder=$HOLDER_ADDR

run_example "Erc20TransferExample" "io.brane.examples.Erc20TransferExample" \
    -Dbrane.examples.erc20.rpc=$RPC_URL \
    -Dbrane.examples.erc20.contract=$TOKEN_ADDR \
    -Dbrane.examples.erc20.recipient=$RECIPIENT_ADDR \
    -Dbrane.examples.erc20.pk=$PRIVATE_KEY \
    -Dbrane.examples.erc20.amount=100

run_example "Erc20TransferLogExample" "io.brane.examples.Erc20TransferLogExample" \
    -Dbrane.examples.erc20.rpc=$RPC_URL \
    -Dbrane.examples.erc20.contract=$TOKEN_ADDR \
    -Dbrane.examples.erc20.fromBlock=0 \
    -Dbrane.examples.erc20.toBlock=latest

# Chain Profiles
run_example "ChainProfilesDumpExample" "io.brane.examples.ChainProfilesDumpExample"

# Error Diagnostics
run_example "ErrorDiagnostics (Helpers)" "io.brane.examples.ErrorDiagnosticsExample" -Pbrane.examples.mode=helpers
run_example "ErrorDiagnostics (RPC Error)" "io.brane.examples.ErrorDiagnosticsExample" -Pbrane.examples.mode=rpc-error

# MultiChain
run_example "MultiChainLatestBlockExample" "io.brane.examples.MultiChainLatestBlockExample" -Dbrane.examples.rpc.base-sepolia=https://sepolia.base.org

# TxBuilder
run_example "TxBuilderIntegrationTest" "io.brane.examples.TxBuilderIntegrationTest" -Dbrane.examples.rpc=$RPC_URL -Dbrane.examples.pk=$PRIVATE_KEY

# Revert Integration
run_example "RevertIntegrationTest" "io.brane.examples.RevertIntegrationTest" -Dbrane.examples.rpc="$RPC_URL" -Dbrane.examples.contract="$REVERT_ADDR"

# Debug Integration
run_example "DebugIntegrationTest" "io.brane.examples.DebugIntegrationTest" -Dbrane.examples.rpc="$RPC_URL" -Dorg.slf4j.simpleLogger.defaultLogLevel=debug

# Access List
run_example "AccessListExample" "io.brane.examples.AccessListExample" -Dbrane.examples.rpc="$RPC_URL" -Dbrane.examples.pk="$PRIVATE_KEY"

# AbiWrapper
run_example "AbiWrapperExample" "io.brane.examples.AbiWrapperExample" -Dbrane.examples.rpc="$RPC_URL" -Dbrane.examples.pk="$PRIVATE_KEY" -Dbrane.examples.contract="$TOKEN_ADDR"

# 4. Run JUnit Integration Tests (Batched)
echo "🧪 Running JUnit Integration Tests..."
./gradlew :brane-contract:test --tests "io.brane.contract.AbiWrapperIntegrationTest" \
    :brane-rpc:test --tests "io.brane.rpc.DefaultWalletClientTest.sendsTransactionWithCustomGasBuffer" \
    :brane-rpc:test --tests "io.brane.rpc.DefaultWalletClientTest.sendsEip1559TransactionWithAccessList" \
    :brane-rpc:test --tests "io.brane.rpc.DefaultWalletClientTest.includesAccessListInEstimation" \
    -Dbrane.integration.tests=true \
    -Dbrane.examples.rpc="$RPC_URL" \
    -Dbrane.anvil.rpc=$RPC_URL \
    -Dbrane.anvil.revertExample.address=$REVERT_ADDR \
    -Dbrane.anvil.storage.address=$STORAGE_ADDR \
    -Dbrane.anvil.signer.privateKey=$PRIVATE_KEY \
    --no-daemon

if [ $? -ne 0 ]; then
    echo "❌ JUnit Integration Tests Failed!"
    exit 1
fi

echo "🎉 All Tests Completed Successfully!"
