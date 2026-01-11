#!/bin/bash
#
# patch-cclsp-timeout.sh - Patch cclsp to increase jdtls initialization timeout
#
# This directly modifies your installed cclsp to use longer timeouts for Java/jdtls.
# Run this once after installing/updating cclsp.
#
# Usage:
#   ./.claude/scripts/patch-cclsp-timeout.sh
#   ./.claude/scripts/patch-cclsp-timeout.sh --restore  # Restore from backup
#

set -e

CCLSP_PATH="/opt/homebrew/lib/node_modules/cclsp/dist/index.js"

if [ ! -f "$CCLSP_PATH" ]; then
    CCLSP_PATH="$(npm root -g)/cclsp/dist/index.js"
fi

if [ ! -f "$CCLSP_PATH" ]; then
    echo "Error: Cannot find cclsp installation"
    exit 1
fi

if [ "$1" = "--restore" ]; then
    if [ -f "${CCLSP_PATH}.backup" ]; then
        cp "${CCLSP_PATH}.backup" "$CCLSP_PATH"
        echo "Restored from backup"
    else
        echo "No backup found"
    fi
    exit 0
fi

# Check if already patched
if grep -q "JdtlsAdapter" "$CCLSP_PATH"; then
    echo "cclsp is already patched with JdtlsAdapter"
    exit 0
fi

echo "Patching cclsp at: $CCLSP_PATH"

# Backup
cp "$CCLSP_PATH" "${CCLSP_PATH}.backup"
echo "Backup created"

# Use node to patch the file (more reliable than sed for complex JS)
node << 'PATCHSCRIPT'
const fs = require('fs');
const path = process.argv[2] || '/opt/homebrew/lib/node_modules/cclsp/dist/index.js';

let content = fs.readFileSync(path, 'utf8');

// 1. Increase default timeout from 30000 to 120000
content = content.replace(/timeout = 30000/g, 'timeout = 120000');

// 2. Add JdtlsAdapter class before AdapterRegistry
const jdtlsAdapter = `
class JdtlsAdapter {
  name = "jdtls";
  matches(config) {
    return config.command.some((c) => c.includes("jdtls") || c.includes("redhat.java") || c.includes("eclipse.jdt.ls"));
  }
  getTimeout(method) {
    const t = { "initialize": 180000, "textDocument/definition": 60000, "textDocument/references": 120000, "textDocument/rename": 90000, "textDocument/documentSymbol": 60000 };
    return t[method];
  }
}
`;
content = content.replace(/class AdapterRegistry/, jdtlsAdapter + '\nclass AdapterRegistry');

// 3. Add JdtlsAdapter to the registry array
content = content.replace(
  /this\.adapters = \[\s*\n\s*new VueLanguageServerAdapter,?\s*\n\s*new PyrightAdapter\s*\n?\s*\]/,
  'this.adapters = [\n      new JdtlsAdapter,\n      new VueLanguageServerAdapter,\n      new PyrightAdapter\n    ]'
);

// 4. Make initialize request use adapter timeout
content = content.replace(
  /const initResult = await this\.sendRequest\(childProcess, "initialize", initializeParams\);/,
  'const initTimeout = adapter?.getTimeout?.("initialize") ?? 30000; const initResult = await this.sendRequest(childProcess, "initialize", initializeParams, initTimeout);'
);

fs.writeFileSync(path, content);
console.log('Patch applied via node');
PATCHSCRIPT

echo ""
echo "Patch complete! Changes:"
echo "  - Default timeout: 30s -> 120s"
echo "  - JdtlsAdapter: 180s initialize timeout"
echo "  - Initialize request uses adapter timeout"
echo ""
echo "Restore: $0 --restore"
