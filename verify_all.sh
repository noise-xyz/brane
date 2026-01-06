#!/bin/bash
set -e

echo "🚀 Starting Full Verification Suite..."
echo "========================================"

# Level 0: Sanity
./scripts/test_sanity.sh
echo ""

# Level 1: Unit
./scripts/test_unit.sh
echo ""

# Level 2: Integration
./scripts/test_integration.sh
echo ""

# Level 3: Smoke
./scripts/test_smoke.sh
echo ""

# Level 4: Performance (Optional, run with --perf flag)
if [[ "$1" == "--perf" ]]; then
    ./scripts/test_perf.sh
else
    echo "⏩ Skipping Performance Tests (use --perf to run)"
fi

echo ""
echo "========================================"
echo "🎉 ALL CHECKS PASSED!"
echo "Ready to Merge."
