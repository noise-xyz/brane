#!/bin/bash
set -e

# Set JAVA_HOME to the local JDK 21
export JAVA_HOME="/Users/atlantropa/Documents/Noise/brane/.jdk/jdk-21.jdk/Contents/Home"

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

echo "Running Sanity Check (All Tests)..."
./gradlew test --no-daemon

echo "Running Pure Unit Tests..."
./gradlew :brane-core:test
./gradlew :brane-rpc:test
./gradlew :brane-contract:test

cd foundry/anvil-tests

echo "Deploying RevertExample..."
REVERT_OUT=$(forge create src/RevertExample.sol:RevertExample --private-key $PRIVATE_KEY --rpc-url $RPC_URL --broadcast)
REVERT_ADDR=$(echo "$REVERT_OUT" | grep "Deployed to:" | awk '{print $3}')
if [ -z "$REVERT_ADDR" ]; then echo "Error: Failed to deploy RevertExample"; exit 1; fi
echo "RevertExample deployed at: $REVERT_ADDR"

echo "Deploying Storage..."
STORAGE_OUT=$(forge create src/Storage.sol:Storage --private-key $PRIVATE_KEY --rpc-url $RPC_URL --broadcast)
STORAGE_ADDR=$(echo "$STORAGE_OUT" | grep "Deployed to:" | awk '{print $3}')
if [ -z "$STORAGE_ADDR" ]; then echo "Error: Failed to deploy Storage"; exit 1; fi
echo "Storage deployed at: $STORAGE_ADDR"

echo "Deploying BraneToken..."
# Move --broadcast before --constructor-args to avoid parsing issues
TOKEN_OUT=$(forge create src/BraneToken.sol:BraneToken --private-key $PRIVATE_KEY --rpc-url $RPC_URL --broadcast --constructor-args 1000000000000000000000)
TOKEN_ADDR=$(echo "$TOKEN_OUT" | grep "Deployed to:" | awk '{print $3}')
if [ -z "$TOKEN_ADDR" ]; then echo "Error: Failed to deploy BraneToken"; echo "$TOKEN_OUT"; exit 1; fi
echo "BraneToken deployed at: $TOKEN_ADDR"

cd ../..

echo "Running Integration Tests..."
./gradlew :brane-contract:test \
  -Dbrane.anvil.rpc=$RPC_URL \
  -Dbrane.anvil.revertExample.address=$REVERT_ADDR \
  -Dbrane.anvil.storage.address=$STORAGE_ADDR \
  -Dbrane.anvil.signer.privateKey=$PRIVATE_KEY

echo "Running Examples..."

# Anvil Account 0 Address (Holder)
HOLDER_ADDR="0xf39Fd6e51aad88F6F4ce6aB8827279cffFb92266"
# Anvil Account 1 Address (Recipient)
RECIPIENT_ADDR="0x70997970C51812dc3A010C7d01b50e0d17dc79C8"

echo "Running Main Example (Echo)..."
OUT=$(./gradlew :brane-examples:run --no-daemon \
  -PmainClass=io.brane.examples.Main \
  -Dbrane.examples.rpc=$RPC_URL \
  -Dbrane.examples.contract=$REVERT_ADDR)
echo "$OUT"
if ! echo "$OUT" | grep -q "echo(42) = 42"; then echo "FAILED: Main Example"; exit 1; fi

echo "Running Erc20Example (Read)..."
OUT=$(./gradlew :brane-examples:run --no-daemon \
  -PmainClass=io.brane.examples.Erc20Example \
  -Dbrane.examples.erc20.rpc=$RPC_URL \
  -Dbrane.examples.erc20.contract=$TOKEN_ADDR \
  -Dbrane.examples.erc20.holder=$HOLDER_ADDR)
echo "$OUT"
if ! echo "$OUT" | grep -q "balanceOf = 1000000000000000000000"; then echo "FAILED: Erc20Example"; exit 1; fi

echo "Running Erc20TransferExample (Write)..."
OUT=$(./gradlew :brane-examples:run --no-daemon \
  -PmainClass=io.brane.examples.Erc20TransferExample \
  -Dbrane.examples.erc20.rpc=$RPC_URL \
  -Dbrane.examples.erc20.contract=$TOKEN_ADDR \
  -Dbrane.examples.erc20.recipient=$RECIPIENT_ADDR \
  -Dbrane.examples.erc20.pk=$PRIVATE_KEY \
  -Dbrane.examples.erc20.amount=100)
echo "$OUT"
if ! echo "$OUT" | grep -q "Mined tx in block"; then echo "FAILED: Erc20TransferExample"; exit 1; fi

echo "Running Erc20TransferLogExample (Logs)..."
OUT=$(./gradlew :brane-examples:run --no-daemon \
  -PmainClass=io.brane.examples.Erc20TransferLogExample \
  -Dbrane.examples.erc20.rpc=$RPC_URL \
  -Dbrane.examples.erc20.contract=$TOKEN_ADDR \
  -Dbrane.examples.erc20.fromBlock=0 \
  -Dbrane.examples.erc20.toBlock=latest)
echo "$OUT"
# We expect at least one transfer (minting) and maybe the one we just did
if ! echo "$OUT" | grep -q "Transfer event size"; then echo "FAILED: Erc20TransferLogExample"; exit 1; fi

echo "Running ChainProfilesDumpExample..."
OUT=$(./gradlew :brane-examples:run --no-daemon \
  -PmainClass=io.brane.examples.ChainProfilesDumpExample)
echo "$OUT"
if ! echo "$OUT" | grep -q "chainId=1"; then echo "FAILED: ChainProfilesDumpExample"; exit 1; fi

echo "Running ErrorDiagnosticsExample (Helpers)..."
OUT=$(./gradlew :brane-examples:run --no-daemon \
  -PmainClass=io.brane.examples.ErrorDiagnosticsExample \
  -Pbrane.examples.mode=helpers)
echo "$OUT"
if ! echo "$OUT" | grep -q "isBlockRangeTooLarge = true"; then echo "FAILED: ErrorDiagnosticsExample (Helpers)"; exit 1; fi

echo "Running ErrorDiagnosticsExample (RPC Error)..."
OUT=$(./gradlew :brane-examples:run --no-daemon \
  -PmainClass=io.brane.examples.ErrorDiagnosticsExample \
  -Pbrane.examples.mode=rpc-error)
echo "$OUT"
if ! echo "$OUT" | grep -q "Caught RpcException as expected"; then echo "FAILED: ErrorDiagnosticsExample (RPC Error)"; exit 1; fi

echo "Running MultiChainLatestBlockExample..."
OUT=$(./gradlew :brane-examples:run --no-daemon \
  -PmainClass=io.brane.examples.MultiChainLatestBlockExample \
  -Dbrane.examples.rpc.base-sepolia=https://sepolia.base.org)
echo "$OUT"
if ! echo "$OUT" | grep -q "Anvil latest block"; then echo "FAILED: MultiChainLatestBlockExample (Anvil)"; exit 1; fi
if ! echo "$OUT" | grep -q "Base Sepolia latest block"; then echo "FAILED: MultiChainLatestBlockExample (Base Sepolia)"; exit 1; fi

echo "Running TxBuilderIntegrationTest..."
OUT=$(./gradlew :brane-examples:run --no-daemon \
  -PmainClass=io.brane.examples.TxBuilderIntegrationTest \
  -Dbrane.examples.rpc=$RPC_URL \
  -Dbrane.examples.pk=$PRIVATE_KEY)
echo "$OUT"
if ! echo "$OUT" | grep -q "TxBuilder Integration Tests Passed!"; then echo "FAILED: TxBuilderIntegrationTest"; exit 1; fi

echo "Running Revert Integration Tests..."
./gradlew :brane-examples:run --no-daemon -PmainClass=io.brane.examples.RevertIntegrationTest \
  -Dbrane.examples.rpc="$RPC_URL" \
  -Dbrane.examples.contract="$REVERT_ADDR"
if [ $? -ne 0 ]; then
    echo "Revert Integration Tests Failed!"
    exit 1
fi
echo "Revert Integration Tests Passed!"

echo "Running Debug Integration Tests..."
./gradlew :brane-examples:run --no-daemon -PmainClass=io.brane.examples.DebugIntegrationTest \
  -Dbrane.examples.rpc="$RPC_URL" \
  -Dorg.slf4j.simpleLogger.defaultLogLevel=debug
if [ $? -ne 0 ]; then
    echo "Debug Integration Tests Failed!"
    exit 1
fi
echo "Debug Integration Tests Passed!"

echo "Running Smart Gas Sanity Check..."
./gradlew :brane-examples:run --no-daemon -PmainClass=io.brane.examples.SmartGasSanityCheck
if [ $? -ne 0 ]; then
    echo "Smart Gas Sanity Check Failed!"
    exit 1
fi
echo "Smart Gas Sanity Check Passed!"

echo "All Tests and Examples Completed Successfully."
