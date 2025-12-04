#!/bin/bash
set -e

echo "🔍 [Level 0] Running Sanity Checks..."

# 1. Check Java
if ! command -v java &> /dev/null; then
    echo "❌ Java not found!"
    exit 1
fi
JAVA_VER=$(java -version 2>&1 | head -n 1)
echo "  ✓ Java: $JAVA_VER"

# 2. Check Anvil (Optional but recommended for full suite)
if command -v anvil &> /dev/null; then
    ANVIL_VER=$(anvil --version)
    echo "  ✓ Anvil: $ANVIL_VER"
else
    echo "  ⚠️ Anvil not found in PATH (Integration tests will fail)"
fi

# 3. Check RPC Connection
RPC_URL="http://127.0.0.1:8545"
if curl -s -X POST -H "Content-Type: application/json" --data '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}' "$RPC_URL" &> /dev/null; then
    echo "  ✓ RPC Connection: OK ($RPC_URL)"
else
    echo "  ⚠️ RPC Connection: Failed (Is Anvil running?)"
    echo "     (Unit tests will pass, but Integration/Smoke tests will fail)"
fi

echo "✅ Sanity Checks Passed"
