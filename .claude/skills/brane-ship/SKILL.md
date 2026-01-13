---
name: brane-ship
description: Complete workflow for verifying and committing Brane SDK changes. Run after implementing fixes/features to verify impact, test affected modules, and prepare for commit.
---

# Brane Ship - Verify & Commit Workflow

This skill orchestrates the complete verification-to-commit workflow for Brane SDK changes.

**Related Skills:**
- `/brane-impact` - Detailed impact analysis and test discovery (invoked automatically in Step 2)

## When to Use

After implementing a fix or feature, invoke `/brane-ship` to:
1. Analyze what was changed
2. Run impact analysis (uses `brane-impact` skill logic)
3. Test affected modules in dependency order
4. Run pre-commit checks
5. Commit and push (with approval)

---

## The Workflow

### Step 1: Detect Changes

First, identify what changed using git:

```bash
git status
git diff --name-only HEAD~1 | grep '\.java$'
```

Determine the primary module(s) affected and severity:

| Module | Impact | Downstream |
|--------|--------|------------|
| `brane-primitives` | HIGH | ALL modules |
| `brane-core` | HIGH | rpc, contract |
| `brane-rpc` | MEDIUM | contract |
| `brane-contract` | LOW | none |

### Step 2: Impact Analysis

Run the impact analysis script (leverages `brane-impact` skill knowledge):

```bash
./.claude/scripts/verify_change.sh
```

This shows:
- Which files changed
- Impact severity assessment
- Transitive dependencies
- Recommended test commands

### Step 3: Run ALL Test Layers (Mandatory)

**ALL THREE TEST LAYERS ARE MANDATORY. NEVER SKIP ANY.**

#### Layer 1: Unit Tests
Run tests in topological order based on the affected module:

| If Changed | Run Tests |
|------------|-----------|
| `brane-primitives` | `./gradlew :brane-primitives:test :brane-core:test :brane-rpc:test :brane-contract:test` |
| `brane-core` | `./gradlew :brane-core:test :brane-rpc:test :brane-contract:test` |
| `brane-rpc` | `./gradlew :brane-rpc:test :brane-contract:test` |
| `brane-contract` | `./gradlew :brane-contract:test` |

#### Layer 2: Integration Tests
Start Anvil (no permission needed) and run integration tests:

```bash
# Start Anvil in background (if not already running)
anvil &

# Run integration tests
./scripts/test_integration.sh
```

#### Layer 3: Smoke Tests
Run full E2E smoke tests:

```bash
./scripts/test_smoke.sh
```

**Rule**: If ANY test fails at ANY layer, STOP. Fix the issue before proceeding.

### Step 4: Pre-Commit Check

Run the pre-commit verification:

```bash
./.claude/scripts/pre_commit_check.sh
```

This ensures all affected modules compile and tests pass.

### Step 5: Commit & Push

If all checks pass, create the commit:

```bash
git add -A
git commit -m "type(scope): description [TICKET-ID]"
git push
```

Follow conventional commit format:
- `fix(rpc):` - Bug fix in rpc module
- `feat(core):` - New feature in core module
- `refactor(contract):` - Refactoring in contract module
- `docs(*)` - Documentation changes
- `test(*)` - Test additions/fixes

---

## Module Dependency Graph

```
brane-primitives (no deps)
       |
       v
   brane-core (BouncyCastle, Jackson)
       |
       v
    brane-rpc (Netty, Disruptor)
       |
       v
  brane-contract
       |
       v
  brane-examples / smoke-test
```

When module X changes, all modules below X in the graph must be tested.

---

## Quick Reference Commands

```bash
# Full workflow (analyze + test + check)
./.claude/scripts/verify_change.sh --run

# Quick (compile only, skip tests)
./.claude/scripts/pre_commit_check.sh --quick

# Single module test
./gradlew :brane-rpc:test

# Specific test class
./gradlew :brane-rpc:test --tests "sh.brane.rpc.BraneTest"

# Integration tests (requires Anvil)
./scripts/test_integration.sh
```

---

## Execution Instructions for Claude

When `/brane-ship` is invoked, follow these steps:

### Phase 1: Analysis
1. Run `git status` to see staged/unstaged changes
2. Run `git diff --name-only` to identify changed files
3. Determine which module(s) are affected
4. Report findings to user

### Phase 2: Impact Analysis
1. Run `./.claude/scripts/verify_change.sh`
2. Summarize the transitive dependencies affected

### Phase 3: Testing (ALL LAYERS MANDATORY)
1. Create a todo list with all test phases (unit, integration, smoke)
2. Start Anvil if not running: `anvil &`
3. **Layer 1 - Unit Tests**: Run `./gradlew :{module}:test` for affected + downstream modules
4. **Layer 2 - Integration Tests**: Run `./scripts/test_integration.sh`
5. **Layer 3 - Smoke Tests**: Run `./scripts/test_smoke.sh`
6. Mark each layer as completed in todo list
7. If ANY test fails at ANY layer, STOP and report the failure

**CRITICAL: Never skip integration or smoke tests. All three layers are equally important.**

### Phase 4: Pre-Commit Validation
1. Run `./.claude/scripts/pre_commit_check.sh`
2. Ensure all checks pass

### Phase 5: Commit Preparation
1. Show `git diff --stat` summary
2. Ask user for commit message (suggest based on changes)
3. Ask user for confirmation before committing
4. If approved: `git add -A && git commit -m "..."  && git push`

---

## Handling Failures

| Failure Type | Action |
|--------------|--------|
| Compilation error | Show error, suggest fix, do not proceed |
| Test failure | Show failed test, do not proceed to commit |
| Pre-commit check fails | Show which module failed, do not commit |

Never skip failing tests. Never use `--no-verify` unless explicitly requested.

---

## Example Invocations

**After implementing a fix:**
```
User: I fixed the logIndex null issue in LogParser
User: /brane-ship

Claude: [Runs full workflow, tests brane-rpc + brane-contract, commits]
```

**Quick verify without commit:**
```
User: /brane-ship --no-commit

Claude: [Runs analysis and tests only, skips commit step]
```

**Specify module explicitly:**
```
User: /brane-ship brane-core

Claude: [Tests brane-core and all downstream modules]
```
