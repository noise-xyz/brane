#!/bin/bash
#
# suggest_tests.sh - Suggest tests to run after editing a file
#
# Usage (typically called by Claude Code hooks):
#   ./scripts/suggest_tests.sh <file_path>
#
# This script outputs a JSON system message with test suggestions
# that Claude can use to inform the user about relevant tests.
#

FILE_PATH="$1"

if [ -z "$FILE_PATH" ]; then
    exit 0
fi

# Only process Java source files (not tests)
if ! echo "$FILE_PATH" | grep -qE '\.java$'; then
    exit 0
fi

# Skip test files
if echo "$FILE_PATH" | grep -qE '/test/|Test\.java$'; then
    exit 0
fi

# Extract information using sed (macOS compatible)
CLASS_NAME=$(basename "$FILE_PATH" .java)
MODULE=$(echo "$FILE_PATH" | sed -n 's|.*\(brane-[a-z]*\).*|\1|p' | head -1)

if [ -z "$MODULE" ]; then
    exit 0
fi

# Build suggestions
SUGGESTIONS=""

# Find direct test
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"

DIRECT_TEST=$(find "$PROJECT_DIR" -path "*/test/*" -name "*${CLASS_NAME}*Test.java" 2>/dev/null | grep -v target | head -1)
if [ -n "$DIRECT_TEST" ]; then
    TEST_CLASS=$(basename "$DIRECT_TEST" .java)
    SUGGESTIONS="Direct test: ./gradlew :${MODULE}:test --tests '*${TEST_CLASS}*'"
fi

# Determine affected modules
case $MODULE in
    brane-primitives)
        AFFECTED="ALL modules (primitives is foundational)"
        ;;
    brane-core)
        AFFECTED="rpc, contract, examples"
        ;;
    brane-rpc)
        AFFECTED="contract, examples"
        ;;
    brane-contract)
        AFFECTED="examples"
        ;;
    *)
        AFFECTED="none"
        ;;
esac

# Build system message
if [ -n "$SUGGESTIONS" ]; then
    cat << EOF
{"systemMessage": "Test suggestion for ${CLASS_NAME}: ${SUGGESTIONS}. Affected modules: ${AFFECTED}. Run ./scripts/verify_change.sh ${CLASS_NAME} for full analysis."}
EOF
fi

exit 0
