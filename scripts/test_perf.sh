#!/bin/bash
set -e

echo "🏎️ [Level 4] Running Performance Tests..."

# Run JMH benchmarks
# We use the 'jmh' task from the brane-benchmark project
./gradlew :brane-benchmark:jmh --console=plain

echo "✅ Performance Tests Passed"
