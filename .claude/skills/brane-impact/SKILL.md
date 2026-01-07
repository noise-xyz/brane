---
name: brane-impact
description: Analyze change impact and discover affected tests. Use after making changes to understand what depends on the modified code, find relevant tests, and determine what to run.
---

# Brane Impact & Test Discovery

Comprehensive impact analysis and test discovery for Brane SDK changes.

---

## Module Dependency Graph

```
brane-primitives (no deps)
       |
       v
   brane-core (depends on: primitives)
       |
       v
    brane-rpc (depends on: core, primitives)
       |
       v
  brane-contract (depends on: rpc, core, primitives)
       |
       v
  [Consumer modules: examples, benchmark, smoke-test]
```

### Impact Propagation Matrix

| Changed Module | Must Test | Severity |
|----------------|-----------|----------|
| `brane-primitives` | ALL modules | HIGH |
| `brane-core` | core, rpc, contract | HIGH |
| `brane-rpc` | rpc, contract | MEDIUM |
| `brane-contract` | contract only | LOW |
| `brane-examples` | examples only | LOW |

---

## Quick Impact Analysis

### Step 1: Identify Changed Files

```bash
# Recent commit
git diff --name-only HEAD~1 | grep '\.java$'

# Staged changes
git diff --cached --name-only | grep '\.java$'

# Working changes
git diff --name-only | grep '\.java$'
```

### Step 2: Run Automated Analysis

```bash
# Full analysis with recommendations
./scripts/verify_change.sh

# Analyze specific file or class
./scripts/verify_change.sh Address
./scripts/verify_change.sh brane-core/src/main/java/io/brane/core/types/Address.java

# Run with tests
./scripts/verify_change.sh --run
```

---

## Test Discovery Commands

### Find Tests for a Changed Class

```bash
CLASS_NAME="Address"

# Direct unit test (same name)
find . -path "*/test/*" -name "*${CLASS_NAME}*Test.java" | grep -v target

# All tests that reference this class
grep -rl "$CLASS_NAME" --include="*Test.java" . | grep -v target

# Integration tests
grep -rl "$CLASS_NAME" --include="*IntegrationTest.java" . | grep -v target

# Examples (integration tests in examples module)
grep -rl "$CLASS_NAME" brane-examples/src/main/java --include="*.java" | grep -v target
```

### Module-Specific Test Commands

| Module Changed | Test Command |
|----------------|--------------|
| `brane-primitives` | `./gradlew test` (all) |
| `brane-core` | `./gradlew :brane-core:test :brane-rpc:test :brane-contract:test` |
| `brane-rpc` | `./gradlew :brane-rpc:test :brane-contract:test` |
| `brane-contract` | `./gradlew :brane-contract:test` |

---

## Test Type Reference

| Test Type | Pattern | Location | Requires Anvil | Skip? |
|-----------|---------|----------|----------------|-------|
| Unit | `*Test.java` | `*/src/test/java/` | No | **NO** |
| Integration | `*IntegrationTest.java` | `*/src/test/java/` | Yes | **NO** |
| Examples | `*Example.java` | `brane-examples/src/main/java/` | Yes | **NO** |
| Smoke | `SmokeApp.java` | `smoke-test/src/main/java/` | Yes | **NO** |

---

## Mandatory Test Layers

**ALL THREE TEST LAYERS ARE MANDATORY. NEVER SKIP ANY.**

| Layer | Purpose | Command |
|-------|---------|---------|
| **Unit** | Logic correctness, fast feedback | `./gradlew :module:test` |
| **Integration** | Real RPC calls, real transactions | `./scripts/test_integration.sh` |
| **Smoke** | Full E2E, consumer perspective | `./scripts/test_smoke.sh` |

Starting Anvil requires no special permission - just run `anvil &` in background.

---

## Test Execution Order

Follow this order for every change:

### Step 1: Unit Tests (Fast Feedback)
```bash
./gradlew :MODULE:test --tests "*ClassName*"
./gradlew :MODULE:test
```

### Step 2: Downstream Unit Tests
```bash
./gradlew :downstream-module:test
```

### Step 3: Integration Tests (Start Anvil First)
```bash
anvil &  # No permission needed
./scripts/test_integration.sh
```

### Step 4: Smoke Tests
```bash
./scripts/test_smoke.sh
```

### Full Verification (All at Once)
```bash
./verify_all.sh
```

---

## Change Severity Categories

**Note: Regardless of severity, ALL test layers (unit, integration, smoke) are MANDATORY.**

### HIGH Impact

- Changes to `brane-primitives` (foundation)
- Public API changes in `brane-core` (types, exceptions)
- Changes to sealed exception hierarchy
- Type signature changes (`Address`, `Hash`, `Wei`, `HexData`)

**Action**:
1. Unit: `./gradlew test` (all modules)
2. Integration: `./scripts/test_integration.sh`
3. Smoke: `./scripts/test_smoke.sh`

### MEDIUM Impact

- RPC client interface changes
- Contract binding logic changes
- Transaction builder changes
- Internal utility changes in core

**Action**:
1. Unit: Affected module + downstream modules
2. Integration: `./scripts/test_integration.sh`
3. Smoke: `./scripts/test_smoke.sh`

### LOW Impact

- Private method changes
- Internal implementation details
- Test-only changes
- Documentation changes

**Action**:
1. Unit: Direct tests + module tests
2. Integration: `./scripts/test_integration.sh`
3. Smoke: `./scripts/test_smoke.sh`

---

## Cross-Module Test Examples

### When `Address` (brane-core) Changes
```
Direct:     brane-core/.../types/AddressTest.java
Dependent:  brane-core/.../abi/AbiEncoderTest.java
            brane-rpc/.../BraneTest.java
            brane-contract/.../BraneContractTest.java
Integration: brane-examples/.../TransferExample.java
```

### When `Brane` (brane-rpc) Changes
```
Direct:     brane-rpc/.../BraneTest.java
Dependent:  brane-contract/.../BraneContractTest.java
Integration: brane-examples/.../*Example.java
```

### When `BraneContract` (brane-contract) Changes
```
Direct:     brane-contract/.../BraneContractTest.java
Integration: brane-examples/.../ContractExample.java
```

---

## Execution Instructions for Claude

When asked to analyze impact or find affected tests:

### Phase 1: Identify Changes
1. Run `git diff --name-only` to find changed files
2. Extract module names from paths
3. Identify changed class names

### Phase 2: Determine Impact
1. Use the Impact Propagation Matrix to find affected modules
2. Categorize severity (HIGH/MEDIUM/LOW)
3. List transitive dependencies

### Phase 3: Discover Tests
1. Find direct tests: `find . -name "*ClassName*Test.java"`
2. Find dependent tests: `grep -rl "ClassName" --include="*Test.java"`
3. Find integration tests: `grep -rl "ClassName" --include="*IntegrationTest.java"`

### Phase 4: Generate Report
Produce a report with:
- Changed files summary
- Impact severity assessment
- Direct and dependent tests
- Recommended test commands (minimal → full)

### Phase 5: Optionally Execute
If `--run` is specified:
```bash
./scripts/verify_change.sh --run
```

---

## CI/CD Integration

For automated pipelines (all three layers mandatory):

```bash
# Determine changed modules
CHANGED_MODULES=$(git diff --name-only origin/main | grep -oE 'brane-[a-z]+' | sort -u)

# Layer 1: Unit tests for affected modules
for MODULE in $CHANGED_MODULES; do
    ./gradlew ":${MODULE}:test"
done

# Layer 2: Integration tests (always run)
./scripts/test_integration.sh

# Layer 3: Smoke tests (always run)
./scripts/test_smoke.sh
```

**All three layers must pass for CI to be green.**
