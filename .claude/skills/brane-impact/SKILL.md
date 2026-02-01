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
  [Consumer modules: examples, benchmark, brane-smoke]
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
./.claude/scripts/verify_change.sh

# Analyze specific file or class
./.claude/scripts/verify_change.sh Address
./.claude/scripts/verify_change.sh brane-core/src/main/java/sh/brane/core/types/Address.java

# Run with tests
./.claude/scripts/verify_change.sh --run
```

---

## LSP-Enhanced Impact Analysis (Preferred)

**Use cclsp MCP tools for precise, semantic impact analysis.**

### Why LSP > Grep

| Aspect | Grep (text-based) | LSP (semantic) |
|--------|-------------------|----------------|
| False positives | Yes (comments, strings) | No |
| Renamed imports | Misses them | Finds them |
| Qualified refs | May miss | Finds all |
| Accuracy | ~80% | ~99% |

### LSP Commands for Impact Analysis

```
# Find all usages of a symbol (MOST USEFUL)
mcp__cclsp__find_references(file_path, symbol_name)

# Find where a symbol is defined
mcp__cclsp__find_definition(file_path, symbol_name)

# Check for compile errors after changes
mcp__cclsp__get_diagnostics(file_path)
```

### Example: Analyzing Impact of Changing `Address.from()`

```
# Step 1: Find all references to the method
mcp__cclsp__find_references(
  file_path="brane-core/src/main/java/sh/brane/core/types/Address.java",
  symbol_name="from"
)
→ Returns 47 locations across 12 files

# Step 2: Check if changes broke anything
mcp__cclsp__get_diagnostics(
  file_path="brane-core/src/main/java/sh/brane/core/types/Address.java"
)
→ Returns compile errors if signature is incompatible
```

### When to Use LSP vs Grep

| Scenario | Use |
|----------|-----|
| Public API changes | **LSP** (precise reference count) |
| Method signature changes | **LSP** (find all callers) |
| Class renames | **LSP** (safe refactoring) |
| Finding test files by name | Grep (pattern matching) |
| Quick text search | Grep (faster for simple cases) |

---

## Test Discovery Commands (Fallback)

### Find Tests for a Changed Class

```bash
CLASS_NAME="Address"

# Direct unit test (same name)
find . -path "*/test/*" -name "*${CLASS_NAME}*Test.java" | grep -v target

# All tests that reference this class (less precise than LSP)
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
| Smoke | `SmokeApp.java` | `brane-smoke/src/main/java/` | Yes | **NO** |

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
3. Identify changed class/method names

### Phase 2: LSP-Based Impact Analysis (PREFERRED)

**For each changed public symbol, use LSP to find precise references:**

```
mcp__cclsp__find_references(file_path, symbol_name)
```

This is MORE ACCURATE than grep because:
- No false positives from comments/strings
- Finds renamed imports and qualified references
- Shows exact line numbers

**Example workflow:**
```
# Changed file: Address.java, changed method: from()
mcp__cclsp__find_references(
  "brane-core/.../Address.java",
  "from"
)
→ 47 references in 12 files

# Check for compile errors
mcp__cclsp__get_diagnostics("brane-core/.../Address.java")
→ No errors (or shows what broke)
```

### Phase 3: Determine Module Impact
1. Use the Impact Propagation Matrix to find affected modules
2. Categorize severity (HIGH/MEDIUM/LOW)
3. List transitive dependencies

### Phase 4: Discover Tests
1. From LSP references, filter for `*Test.java` files
2. Find direct tests: `find . -name "*ClassName*Test.java"`
3. Cross-reference with module dependency graph

### Phase 5: Generate Report
Produce a report with:
- Changed files summary
- **LSP reference count** (e.g., "47 usages across 12 files")
- Impact severity assessment
- Affected tests (from LSP + file patterns)
- Recommended test commands (minimal → full)

### Phase 6: Optionally Execute
If `--run` is specified:
```bash
./.claude/scripts/verify_change.sh --run
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
