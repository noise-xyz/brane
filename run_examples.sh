#!/bin/bash
set -e

# Configuration
RPC_URL="http://127.0.0.1:8545"
# Default Anvil Account #0 Private Key
PRIVATE_KEY="0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80"

echo "Checking for Anvil..."
if ! curl -s -X POST -H "Content-Type: application/json" --data '{"jsonrpc":"2.0","method":"eth_blockNumber","params":[],"id":1}' "$RPC_URL" > /dev/null; then
    echo "❌ Error: Anvil does not appear to be running at $RPC_URL"
    echo "Please run 'anvil' in a separate terminal window."
    exit 1
fi
echo "✓ Anvil is running."

echo "----------------------------------------------------------------"
echo "Running Canonical High-Level Example (ERC-20 Binding)..."
echo "----------------------------------------------------------------"
./gradlew :brane-examples:run \
  -PmainClass=io.brane.examples.CanonicalErc20Example \
  -Dbrane.examples.rpc="$RPC_URL" \
  -Dbrane.examples.pk="$PRIVATE_KEY" \
  --console=plain -q

echo ""
echo "----------------------------------------------------------------"
echo "Running Canonical Low-Level Example (Raw RPC & Tx)..."
echo "----------------------------------------------------------------"
./gradlew :brane-examples:run \
  -PmainClass=io.brane.examples.CanonicalRawExample \
  -Dbrane.examples.rpc="$RPC_URL" \
  -Dbrane.examples.pk="$PRIVATE_KEY" \
  --console=plain -q

echo ""
echo "----------------------------------------------------------------"
echo "Running Canonical Debug Example (Error Handling & Logs)..."
echo "----------------------------------------------------------------"
./gradlew :brane-examples:run \
  -PmainClass=io.brane.examples.CanonicalDebugExample \
  -Dbrane.examples.rpc="$RPC_URL" \
  --console=plain -q

echo ""
echo "✅ All examples completed successfully."
