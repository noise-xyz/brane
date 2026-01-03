#!/bin/bash
#
# pre_commit_check.sh - Run affected tests before committing
#
# Usage:
#   ./scripts/pre_commit_check.sh          # Check staged changes
#   ./scripts/pre_commit_check.sh --all    # Run all tests
#   ./scripts/pre_commit_check.sh --quick  # Compile only, skip tests
#
# Exit codes:
#   0 - All checks passed
#   1 - Tests failed
#   2 - Compilation failed
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

# Parse arguments
RUN_ALL=false
QUICK_MODE=false

while [[ $# -gt 0 ]]; do
    case $1 in
        --all)
            RUN_ALL=true
            shift
            ;;
        --quick)
            QUICK_MODE=true
            shift
            ;;
        *)
            shift
            ;;
    esac
done

echo -e "${CYAN}═══ Pre-Commit Check ═══${NC}"
echo ""

# Get changed Java files (staged)
CHANGED_FILES=$(git diff --cached --name-only 2>/dev/null | grep '\.java$' || true)

if [ -z "$CHANGED_FILES" ] && [ "$RUN_ALL" = false ]; then
    echo -e "${GREEN}No Java files staged for commit${NC}"
    exit 0
fi

# Collect affected modules using simple string operations
MODULES_AFFECTED=""

if [ "$RUN_ALL" = true ]; then
    MODULES_AFFECTED="brane-primitives brane-core brane-rpc brane-contract"
else
    echo -e "${YELLOW}Changed files:${NC}"
    echo "$CHANGED_FILES" | while read -r file; do
        echo "  - $file"
    done
    echo ""

    # Extract modules from changed files
    for FILE in $CHANGED_FILES; do
        MODULE=$(echo "$FILE" | sed -n 's|.*\(brane-[a-z]*\).*|\1|p' | head -1)
        if [ -n "$MODULE" ]; then
            # Add to list if not already present
            if ! echo "$MODULES_AFFECTED" | grep -q "$MODULE"; then
                MODULES_AFFECTED="$MODULES_AFFECTED $MODULE"
            fi

            # Add transitive dependencies
            case $MODULE in
                brane-primitives)
                    for DEP in brane-core brane-rpc brane-contract; do
                        if ! echo "$MODULES_AFFECTED" | grep -q "$DEP"; then
                            MODULES_AFFECTED="$MODULES_AFFECTED $DEP"
                        fi
                    done
                    ;;
                brane-core)
                    for DEP in brane-rpc brane-contract; do
                        if ! echo "$MODULES_AFFECTED" | grep -q "$DEP"; then
                            MODULES_AFFECTED="$MODULES_AFFECTED $DEP"
                        fi
                    done
                    ;;
                brane-rpc)
                    if ! echo "$MODULES_AFFECTED" | grep -q "brane-contract"; then
                        MODULES_AFFECTED="$MODULES_AFFECTED brane-contract"
                    fi
                    ;;
            esac
        fi
    done
fi

# Report affected modules
echo -e "${YELLOW}Affected modules:${NC}"
for MODULE in $MODULES_AFFECTED; do
    echo "  - $MODULE"
done
echo ""

# Quick mode - compile only
if [ "$QUICK_MODE" = true ]; then
    echo -e "${CYAN}Running compilation check...${NC}"

    for MODULE in $MODULES_AFFECTED; do
        echo -e "  Compiling ${MODULE}..."
        if ! ./gradlew ":${MODULE}:compileJava" -q 2>&1; then
            echo -e "${RED}✗ Compilation failed for $MODULE${NC}"
            exit 2
        fi
    done

    echo ""
    echo -e "${GREEN}✓ Compilation check passed${NC}"
    exit 0
fi

# Full mode - run tests
echo -e "${CYAN}Running tests for affected modules...${NC}"
echo ""

FAILED_MODULES=""

for MODULE in brane-primitives brane-core brane-rpc brane-contract; do
    if echo "$MODULES_AFFECTED" | grep -q "$MODULE"; then
        echo -e "${YELLOW}Testing ${MODULE}...${NC}"

        if ./gradlew ":${MODULE}:test" -q 2>&1; then
            echo -e "${GREEN}✓ ${MODULE} tests passed${NC}"
        else
            echo -e "${RED}✗ ${MODULE} tests failed${NC}"
            FAILED_MODULES="$FAILED_MODULES $MODULE"
        fi
        echo ""
    fi
done

# Report results
if [ -n "$FAILED_MODULES" ]; then
    echo -e "${RED}═══════════════════════════════════════${NC}"
    echo -e "${RED}Pre-commit check FAILED${NC}"
    echo -e "${RED}Failed modules:$FAILED_MODULES${NC}"
    echo -e "${RED}═══════════════════════════════════════${NC}"
    echo ""
    echo "Fix the failing tests before committing, or use:"
    echo "  git commit --no-verify  # Skip pre-commit hook (not recommended)"
    exit 1
fi

echo -e "${GREEN}═══════════════════════════════════════${NC}"
echo -e "${GREEN}✓ Pre-commit check passed${NC}"
echo -e "${GREEN}═══════════════════════════════════════${NC}"
exit 0
