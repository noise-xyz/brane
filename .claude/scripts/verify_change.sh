#!/bin/bash
#
# verify_change.sh - Analyze impact of code changes and suggest tests to run
#
# Usage:
#   ./scripts/verify_change.sh [file_or_class_name]
#   ./scripts/verify_change.sh                        # Analyzes git diff
#   ./scripts/verify_change.sh Address                # Analyzes a class by name
#   ./scripts/verify_change.sh brane-core/.../Address.java  # Analyzes a specific file
#
# Options:
#   --run    Actually run the suggested tests instead of just showing commands
#   --quick  Only run direct tests (fast feedback)
#   --full   Run full verification including integration tests
#

set -e

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_DIR="$(dirname "$SCRIPT_DIR")"
cd "$PROJECT_DIR"

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m' # No Color

# Parse arguments
RUN_TESTS=false
QUICK_MODE=false
FULL_MODE=false
TARGET=""

while [[ $# -gt 0 ]]; do
    case $1 in
        --run)
            RUN_TESTS=true
            shift
            ;;
        --quick)
            QUICK_MODE=true
            shift
            ;;
        --full)
            FULL_MODE=true
            shift
            ;;
        *)
            TARGET="$1"
            shift
            ;;
    esac
done

echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${CYAN}                    Brane Impact Analysis                       ${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo ""

# Determine what changed
if [ -z "$TARGET" ]; then
    echo -e "${BLUE}Analyzing recent git changes...${NC}"

    CHANGED_FILES=$(git diff --name-only HEAD~1 2>/dev/null | grep '\.java$' || true)

    if [ -z "$CHANGED_FILES" ]; then
        CHANGED_FILES=$(git diff --name-only 2>/dev/null | grep '\.java$' || true)
    fi

    if [ -z "$CHANGED_FILES" ]; then
        CHANGED_FILES=$(git diff --cached --name-only 2>/dev/null | grep '\.java$' || true)
    fi

    if [ -z "$CHANGED_FILES" ]; then
        echo -e "${YELLOW}No changed Java files found in git diff${NC}"
        echo "Usage: $0 [file_or_class_name]"
        exit 0
    fi

    echo -e "${GREEN}Changed files:${NC}"
    echo "$CHANGED_FILES" | while read -r file; do
        echo "  - $file"
    done
    echo ""
elif [[ "$TARGET" == *".java" ]] || [[ "$TARGET" == *"/"* ]]; then
    CHANGED_FILES="$TARGET"
    echo -e "${BLUE}Analyzing file: ${NC}$TARGET"
    echo ""
else
    echo -e "${BLUE}Searching for class: ${NC}$TARGET"
    FOUND_FILE=$(find . -name "${TARGET}.java" -path "*/src/main/*" | grep -v target | head -1)

    if [ -z "$FOUND_FILE" ]; then
        echo -e "${RED}Class not found: $TARGET${NC}"
        exit 1
    fi

    CHANGED_FILES="$FOUND_FILE"
    echo -e "${GREEN}Found: ${NC}$FOUND_FILE"
    echo ""
fi

# Collect all modules and classes
MODULES_AFFECTED=""
ALL_CLASSES=""

echo -e "${BLUE}═══ Step 1: Identifying Changed Modules ═══${NC}"
echo ""

for FILE in $CHANGED_FILES; do
    # Extract module using sed (macOS compatible)
    MODULE=$(echo "$FILE" | sed -n 's|.*\(brane-[a-z]*\).*|\1|p' | head -1)

    if [ -n "$MODULE" ]; then
        # Add to list if not already present
        if ! echo "$MODULES_AFFECTED" | grep -q "$MODULE"; then
            MODULES_AFFECTED="$MODULES_AFFECTED $MODULE"
            echo -e "  ${GREEN}•${NC} $MODULE"
        fi
    fi

    # Extract class name
    CLASS=$(basename "$FILE" .java)
    ALL_CLASSES="$ALL_CLASSES $CLASS"
done

echo ""

# Determine affected modules based on dependency graph
echo -e "${BLUE}═══ Step 2: Calculating Dependency Impact ═══${NC}"
echo ""

TRANSITIVELY_AFFECTED=""

for MODULE in $MODULES_AFFECTED; do
    TRANSITIVELY_AFFECTED="$TRANSITIVELY_AFFECTED $MODULE"

    case $MODULE in
        brane-primitives)
            echo -e "  ${YELLOW}⚠ brane-primitives changed - ALL modules affected${NC}"
            TRANSITIVELY_AFFECTED="$TRANSITIVELY_AFFECTED brane-core brane-rpc brane-contract brane-examples brane-smoke"
            ;;
        brane-core)
            echo -e "  ${CYAN}→ brane-core affects: rpc, contract, examples, brane-smoke${NC}"
            TRANSITIVELY_AFFECTED="$TRANSITIVELY_AFFECTED brane-rpc brane-contract brane-examples brane-smoke"
            ;;
        brane-rpc)
            echo -e "  ${CYAN}→ brane-rpc affects: contract, examples, brane-smoke${NC}"
            TRANSITIVELY_AFFECTED="$TRANSITIVELY_AFFECTED brane-contract brane-examples brane-smoke"
            ;;
        brane-contract)
            echo -e "  ${CYAN}→ brane-contract affects: examples, brane-smoke${NC}"
            TRANSITIVELY_AFFECTED="$TRANSITIVELY_AFFECTED brane-examples brane-smoke"
            ;;
        *)
            echo -e "  ${GREEN}• $MODULE (no downstream dependencies)${NC}"
            ;;
    esac
done

# Remove duplicates
TRANSITIVELY_AFFECTED=$(echo "$TRANSITIVELY_AFFECTED" | tr ' ' '\n' | sort -u | tr '\n' ' ')

echo ""

# Find direct dependents
echo -e "${BLUE}═══ Step 3: Finding Direct Dependents ═══${NC}"
echo ""

for CLASS in $ALL_CLASSES; do
    echo -e "${CYAN}Class: $CLASS${NC}"

    DEPS=$(grep -rl "import.*${CLASS}\|${CLASS}\." --include="*.java" . 2>/dev/null | grep -v target | grep -v "/build/" | grep -v "/${CLASS}.java$" | head -10 || true)

    if [ -n "$DEPS" ]; then
        echo "$DEPS" | while read -r dep; do
            echo -e "  ${GREEN}←${NC} $dep"
        done
    else
        echo -e "  ${YELLOW}(no direct dependents found)${NC}"
    fi
    echo ""
done

# Find relevant tests
echo -e "${BLUE}═══ Step 4: Discovering Relevant Tests ═══${NC}"
echo ""

DIRECT_TESTS=""
DEPENDENT_TESTS=""
INTEGRATION_TESTS=""
EXAMPLE_TESTS=""

for CLASS in $ALL_CLASSES; do
    # Direct test for this class
    DIRECT=$(find . -path "*/test/*" -name "*${CLASS}*Test.java" 2>/dev/null | grep -v target | grep -v build || true)
    if [ -n "$DIRECT" ]; then
        DIRECT_TESTS="$DIRECT_TESTS $DIRECT"
    fi

    # Tests that reference this class
    REFS=$(grep -rl "$CLASS" --include="*Test.java" . 2>/dev/null | grep -v target | grep -v build | grep -v "${CLASS}Test.java" || true)
    if [ -n "$REFS" ]; then
        DEPENDENT_TESTS="$DEPENDENT_TESTS $REFS"
    fi

    # Integration tests
    INTEG=$(grep -rl "$CLASS" --include="*IntegrationTest.java" . 2>/dev/null | grep -v target | grep -v build || true)
    if [ -n "$INTEG" ]; then
        INTEGRATION_TESTS="$INTEGRATION_TESTS $INTEG"
    fi

    # Examples
    if [ -d "brane-examples/src/main/java" ]; then
        EXAMPLES=$(grep -rl "$CLASS" brane-examples/src/main/java --include="*.java" 2>/dev/null | grep -v target || true)
        if [ -n "$EXAMPLES" ]; then
            EXAMPLE_TESTS="$EXAMPLE_TESTS $EXAMPLES"
        fi
    fi
done

# Remove duplicates and format
DIRECT_TESTS=$(echo "$DIRECT_TESTS" | tr ' ' '\n' | sort -u | grep -v '^$')
DEPENDENT_TESTS=$(echo "$DEPENDENT_TESTS" | tr ' ' '\n' | sort -u | grep -v '^$')
INTEGRATION_TESTS=$(echo "$INTEGRATION_TESTS" | tr ' ' '\n' | sort -u | grep -v '^$')
EXAMPLE_TESTS=$(echo "$EXAMPLE_TESTS" | tr ' ' '\n' | sort -u | grep -v '^$')

echo -e "${GREEN}Direct Tests (same class):${NC}"
if [ -n "$DIRECT_TESTS" ]; then
    echo "$DIRECT_TESTS" | while read -r test; do
        [ -n "$test" ] && echo "  • $test"
    done
else
    echo "  (none found)"
fi
echo ""

echo -e "${GREEN}Dependent Tests (reference the class):${NC}"
if [ -n "$DEPENDENT_TESTS" ]; then
    echo "$DEPENDENT_TESTS" | head -10 | while read -r test; do
        [ -n "$test" ] && echo "  • $test"
    done
else
    echo "  (none found)"
fi
echo ""

echo -e "${GREEN}Integration Tests:${NC}"
if [ -n "$INTEGRATION_TESTS" ]; then
    echo "$INTEGRATION_TESTS" | while read -r test; do
        [ -n "$test" ] && echo "  • $test"
    done
else
    echo "  (none found)"
fi
echo ""

echo -e "${GREEN}Example Tests (brane-examples):${NC}"
if [ -n "$EXAMPLE_TESTS" ]; then
    echo "$EXAMPLE_TESTS" | while read -r test; do
        [ -n "$test" ] && echo "  • $test"
    done
else
    echo "  (none found)"
fi
echo ""

# Generate test commands
echo -e "${BLUE}═══ Step 5: Recommended Test Commands ═══${NC}"
echo ""

# Build module test command
SDK_MODULES=""
for MODULE in brane-primitives brane-core brane-rpc brane-contract; do
    if echo "$TRANSITIVELY_AFFECTED" | grep -q "$MODULE"; then
        SDK_MODULES="$SDK_MODULES :${MODULE}:test"
    fi
done

echo -e "${YELLOW}Quick (direct tests only):${NC}"
if [ -n "$DIRECT_TESTS" ]; then
    echo "$DIRECT_TESTS" | head -3 | while read -r test; do
        if [ -n "$test" ]; then
            TEST_MODULE=$(echo "$test" | sed -n 's|.*\(brane-[a-z]*\).*|\1|p' | head -1)
            TEST_CLASS=$(basename "$test" .java)
            echo "  ./gradlew :${TEST_MODULE}:test --tests \"*${TEST_CLASS}*\""
        fi
    done
else
    for MODULE in $MODULES_AFFECTED; do
        echo "  ./gradlew :${MODULE}:test"
    done
fi
echo ""

echo -e "${YELLOW}Thorough (affected modules):${NC}"
if [ -n "$SDK_MODULES" ]; then
    echo "  ./gradlew$SDK_MODULES"
else
    echo "  ./gradlew test"
fi
echo ""

echo -e "${YELLOW}Integration (requires Anvil):${NC}"
echo "  ./scripts/test_integration.sh"
echo ""

echo -e "${YELLOW}Full verification:${NC}"
echo "  ./verify_all.sh"
echo ""

# Execute tests if requested
if [ "$RUN_TESTS" = true ]; then
    echo -e "${CYAN}═══ Running Tests ═══${NC}"
    echo ""

    if [ "$QUICK_MODE" = true ]; then
        echo -e "${YELLOW}Running quick tests...${NC}"
        for MODULE in $MODULES_AFFECTED; do
            ./gradlew ":${MODULE}:test" || exit 1
        done
    elif [ "$FULL_MODE" = true ]; then
        echo -e "${YELLOW}Running full verification...${NC}"
        ./verify_all.sh
    else
        echo -e "${YELLOW}Running affected module tests...${NC}"
        if [ -n "$SDK_MODULES" ]; then
            ./gradlew $SDK_MODULES || exit 1
        else
            ./gradlew test || exit 1
        fi
    fi

    echo ""
    echo -e "${GREEN}✓ All tests passed${NC}"
fi

echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
