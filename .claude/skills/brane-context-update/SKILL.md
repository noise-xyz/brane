---
name: brane-context-update
description: Update CLAUDE.md files after significant changes. Use after implementing features, refactoring, or making architectural decisions to keep AI context fresh.
---

# Brane Context Update

Update CLAUDE.md documentation to reflect recent codebase changes.

## When to Use This Skill

Invoke `/brane-context-update` after:
- Completing a feature implementation
- Significant refactoring
- Adding new public classes or patterns
- Making architectural decisions
- Discovering gotchas or traps
- Breaking API changes

---

## Workflow

### Phase 1: Identify What Changed

```bash
# Recent commits
git log --oneline -5

# Files changed in last commit
git diff HEAD~1 --name-only

# Files changed in last 3 commits
git diff HEAD~3 --name-only --stat

# See actual changes
git diff HEAD~1
```

Categorize changes:
- **Architectural**: New modules, changed dependencies, new patterns
- **API**: New public classes, changed method signatures
- **Patterns**: New conventions or idioms introduced
- **Gotchas**: Bug fixes that reveal traps or pitfalls

### Phase 2: Determine Which CLAUDE.md Files Need Updates

| Change Type | Update |
|-------------|--------|
| Cross-module architectural change | Root `CLAUDE.md` |
| New pattern used across modules | Root `CLAUDE.md` |
| Module-specific API change | `<module>/CLAUDE.md` |
| New key class in module | `<module>/CLAUDE.md` |
| New gotcha discovered | Relevant module `CLAUDE.md` |
| Dependency change | Root + affected module |

### Phase 3: Update Files

For each file that needs updating:

1. **Read current CLAUDE.md** to understand existing structure
2. **Identify the right section** to update:
   - Key Classes
   - Patterns
   - Gotchas
   - Package Structure
3. **Make minimal, focused updates** - one-liners preferred
4. **Keep it concise** - CLAUDE.md is for quick context, not comprehensive docs

### Phase 4: Report Changes

After updating, summarize:
- Which files were updated
- What sections changed
- What was added/modified

---

## What to Document in CLAUDE.md

### DO Document

- New public classes/interfaces that are key entry points
- New patterns or conventions established
- Gotchas that caused bugs or confusion
- Breaking API changes
- New dependencies or module relationships
- Changed package structure

### DON'T Document

- Every new class (only key ones)
- Implementation details
- Temporary workarounds
- Obvious things already clear from code
- Detailed API docs (that's for Javadoc)

---

## Section Guidelines

### Key Classes Section

Add new class only if it's:
- A main entry point users will use directly
- A new abstraction that changes how the module works
- Something developers need to know about when working in this module

Format: `**ClassName** - one-line description`

### Patterns Section

Add new pattern if:
- It's used in multiple places
- It's different from what someone might expect
- It establishes a convention for future code

Include a minimal code example.

### Gotchas Section

Add gotcha if:
- It caused a bug that took time to diagnose
- It's counterintuitive behavior
- It's a trap other developers might fall into

Format: `- **Short name**: Brief explanation of the trap and how to avoid it`

### Package Structure Section

Update if:
- New package added
- Package responsibility changed significantly
- Package renamed or moved

---

## Templates

### Adding a New Key Class

```markdown
### Key Classes

...existing classes...
- **NewClass** - brief description of what it does
```

### Adding a New Pattern

```markdown
## Patterns

### Pattern Name
```java
// Minimal example showing the pattern
var x = NewPattern.use(args);
```
```

### Adding a New Gotcha

```markdown
## Gotchas

...existing gotchas...
- **Short Name**: Brief description of the trap. How to avoid it.
```

### Adding an Architectural Decision

In root CLAUDE.md:

```markdown
## Recent Decisions

- **YYYY-MM-DD: Decision name** - Brief explanation of what changed and why
```

---

## Example Update Session

```
User: /brane-context-update

AI: Let me check recent changes...

[Runs git commands to see changes]

Found:
- New `SmartBatcher` class in brane-rpc
- Changed `WalletClient` to use virtual threads internally
- Fixed ThreadLocal leak in Keccak256 (added cleanup)

Updating:

1. `brane-rpc/CLAUDE.md`:
   - Added `SmartBatcher` to Key Classes
   - Added note about virtual thread usage in WalletClient

2. `brane-core/CLAUDE.md`:
   - Added gotcha about Keccak256 ThreadLocal cleanup

Summary:
- Updated 2 CLAUDE.md files
- Added 1 new class, 1 pattern note, 1 gotcha
```

---

## Verification

After updating, quickly verify:
- [ ] Updates are concise (CLAUDE.md should stay readable)
- [ ] No duplication with AGENT.md (CLAUDE.md is quick reference)
- [ ] Class names and patterns match actual code
- [ ] Gotchas describe real traps with solutions
