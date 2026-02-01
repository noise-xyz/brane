---
name: brane-docs
description: Update Javadoc and public documentation after implementing features. Use when finishing implementation to ensure both internal (Javadoc) and external (website docs) documentation are accurate and complete.
---

# Brane Documentation (Post-Implementation)

**Golden Rule: Documentation that misleads is worse than no documentation.**

## When to Use

Invoke `/brane-docs` after completing implementation that affects:
- New public classes, methods, or types
- Changes to existing public API behavior
- New features users need to know about

---

## Workflow

### 1. Identify What Changed

```bash
git diff --name-only HEAD~1 | grep '\.java$'
```

Focus on public API changes in:
- `brane-core/src/main/java/sh/brane/core/...`
- `brane-rpc/src/main/java/sh/brane/rpc/...`
- `brane-contract/src/main/java/sh/brane/contract/...`

### 2. Update Javadoc

Update source files directly. Required elements:
- Summary sentence (imperative: "Sends", "Creates", "Binds")
- `@param` for every parameter (state nullability)
- `@return` for non-void (state empty/null semantics)
- `@throws` for all exceptions (state condition)
- Working `{@code}` examples for public entry points

### 3. Update Website Docs

Docs location: `website/docs/pages/docs/`

```
website/docs/pages/docs/
├── quickstart.mdx
├── architecture.mdx
├── performance.mdx
├── reader/           # Brane.Reader methods
│   ├── api.mdx
│   ├── simulate.mdx
│   └── subscriptions.mdx
├── signer/           # Brane.Signer methods
│   ├── api.mdx
│   ├── signers.mdx
│   ├── hd-wallets.mdx
│   ├── eip712.mdx
│   ├── blobs.mdx
│   └── custom-signers.mdx
├── contracts/
│   ├── bindings.mdx
│   ├── interaction.mdx
│   └── multicall.mdx
├── testing/          # Brane.Tester methods
│   ├── overview.mdx
│   ├── setup.mdx
│   ├── state.mdx
│   ├── impersonation.mdx
│   ├── mining-time.mdx
│   └── accounts.mdx
├── providers/
│   ├── http.mdx
│   └── websocket.mdx
├── utilities/
│   ├── types.mdx
│   ├── abi.mdx
│   ├── errors.mdx
│   ├── threading.mdx
│   └── metrics.mdx
└── chains/
    └── profiles.mdx
```

### 4. Which Page to Update?

| Feature Type | Update These Pages |
|--------------|-------------------|
| New Reader method | `reader/api.mdx` |
| New Signer method | `signer/api.mdx` |
| New type (Address, Wei, etc.) | `utilities/types.mdx` |
| Contract feature | `contracts/bindings.mdx` or `contracts/interaction.mdx` |
| Provider changes | `providers/http.mdx` or `providers/websocket.mdx` |
| Error handling | `utilities/errors.mdx` |
| Testing feature | `testing/` appropriate file |
| New major feature | May need new page |

### 5. Evaluate CLAUDE.md Updates

After updating Javadoc and website docs, decide whether CLAUDE.md files need updates.

**Update CLAUDE.md when:**
- New public class added that users will interact with → Add to "Key Classes" section
- New usage pattern that differs from existing examples → Add to "Patterns" section
- Discovered a gotcha or pitfall → Add to "Gotchas" section
- Significant behavioral change that affects documented patterns

**Skip CLAUDE.md when:**
- Minor method additions that follow existing patterns
- Internal implementation changes
- Bug fixes that don't affect documented behavior
- Changes already well-represented by existing documentation

**Which CLAUDE.md to update:**

| Change Location | CLAUDE.md File |
|-----------------|----------------|
| brane-primitives | `brane-primitives/CLAUDE.md` |
| brane-core types, ABI, crypto | `brane-core/CLAUDE.md` |
| brane-kzg | `brane-kzg/CLAUDE.md` |
| brane-rpc clients, providers | `brane-rpc/CLAUDE.md` |
| brane-contract | `brane-contract/CLAUDE.md` |
| Cross-cutting patterns | Root `CLAUDE.md` |

**When updating, follow existing structure:**
- **Key Classes**: Brief description, key methods/purpose
- **Patterns**: Working code examples with realistic scenarios
- **Gotchas**: Specific class/method, clear issue, resolution

---

## Accuracy Checklist

Before finishing, verify:

**Javadoc:**
- [ ] Summary matches actual behavior (re-read implementation)
- [ ] All parameters documented with correct types
- [ ] All thrown exceptions listed
- [ ] Example code actually works

**Website Docs:**
- [ ] Code examples include imports
- [ ] Method signatures match actual API
- [ ] No stale examples from previous versions

**Cross-Reference:**
- [ ] Javadoc and website docs say the same thing
- [ ] No contradictions between sources

**CLAUDE.md (if applicable):**
- [ ] Key classes table includes new public classes
- [ ] Patterns section has working examples for new patterns
- [ ] Gotchas are specific and actionable
- [ ] Module-level and root CLAUDE.md are consistent
