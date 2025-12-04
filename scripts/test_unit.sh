#!/bin/bash
set -e

echo "🧪 [Level 1] Running Unit Tests..."

# Run standard tests, excluding integration tests
# We assume integration tests are tagged or in a separate source set, 
# but for now we'll run the standard 'test' task which usually excludes slow integration tests
# if configured correctly. 
# Based on current gradle setup, we might need to be specific.
# Let's run the fast sanity checks as part of unit tests too.

# Run Unit Tests for each module (excluding integration tests)
./gradlew \
    :brane-primitives:test \
    :brane-core:test \
    :brane-rpc:test \
    :brane-contract:test \
    -Pbrane.unit.tests=true \
    :brane-examples:run -PmainClass=io.brane.examples.CryptoSanityCheck \
    :brane-examples:run -PmainClass=io.brane.examples.TransactionSanityCheck \
    --parallel --no-daemon --console=plain

echo "✅ Unit Tests Passed"
