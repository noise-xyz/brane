---
name: brane-docs
description: Update Javadoc and public documentation after implementing features. Use when finishing implementation to ensure both internal (Javadoc) and external (docs/) documentation are accurate and complete.
---

# Brane Documentation (Post-Implementation)

## The Golden Rule: Accuracy Over Speed

**Documentation that misleads is worse than no documentation.**

Developers will lose trust if docs don't match reality. Every claim must be verifiable against actual code behavior.

---

## When to Use This Skill

Invoke `/brane-docs` after completing implementation work:
- New public classes, methods, or types
- Changes to existing public API behavior
- New features that users need to know about
- Bug fixes that change documented behavior

---

## Documentation Workflow

### Phase 1: Identify What Changed

```bash
# See what files were modified
git diff --name-only HEAD~1

# See staged changes
git diff --cached --name-only

# See changes in specific module
git diff HEAD~1 -- brane-core/
```

Focus on:
- **New public classes** - Need Javadoc + possibly new docs page
- **New public methods** - Need Javadoc + update existing docs
- **Changed behavior** - Update both Javadoc and public docs
- **New types** - Document in utilities/types.mdx

### Phase 2: Update Javadoc

Update source files in `src/main/java/`. See Javadoc conventions below.

### Phase 3: Update Public Docs

Update MDX files in `docs/`. See Public Docs conventions below.

### Phase 4: Verify Accuracy

**Critical step - never skip this.**

1. **Code matches Javadoc** - Re-read the implementation
2. **Examples compile** - Test code snippets mentally or actually
3. **Types are correct** - Verify parameter and return types
4. **Exceptions documented** - All thrown exceptions listed
5. **No stale content** - Remove outdated information

---

## Javadoc Conventions

### Location

All public API Javadoc lives in source files:
```
brane-core/src/main/java/io/brane/core/...
brane-rpc/src/main/java/io/brane/rpc/...
brane-contract/src/main/java/io/brane/contract/...
```

### Structure Template

```java
/**
 * <summary sentence in imperative mood - what it does>.
 *
 * <p>
 * <detailed description - behavior, constraints, thread-safety, performance notes>
 * </p>
 *
 * <p>
 * <strong>Validation:</strong>
 * <ul>
 * <li>Constraint 1 - what happens if violated</li>
 * <li>Constraint 2 - what happens if violated</li>
 * </ul>
 * </p>
 *
 * <p>
 * <strong>Example:</strong>
 * <pre>{@code
 * // Actual working code example
 * var result = MyClass.method(param);
 * assertEquals(expected, result);
 * }</pre>
 * </p>
 *
 * @param paramName description (never null, must be positive, etc.)
 * @return what is returned (never null, empty list if none, etc.)
 * @throws ExceptionType when this exception is thrown
 * @throws AnotherException when this other exception is thrown
 * @see RelatedClass
 * @see OtherMethod
 */
```

### Required Elements

| Element | When Required |
|---------|---------------|
| Summary sentence | Always |
| `<p>` description | For non-trivial methods |
| `<strong>Validation:</strong>` | When input constraints exist |
| `<strong>Example:</strong>` | For public API entry points |
| `@param` | Every parameter |
| `@return` | Non-void methods |
| `@throws` | Every checked exception + significant unchecked |
| `@see` | When related classes/methods exist |

### Style Rules

1. **Summary sentence** - Imperative mood ("Binds", "Sends", "Creates")
2. **Parameters** - State nullability ("never null", "may be null")
3. **Returns** - State what empty/null means ("empty list if none found")
4. **Exceptions** - State the condition ("if address is invalid")
5. **Examples** - Must be actual working code, not pseudocode

### Reference Examples

Study these for the expected style:
- `brane-contract/.../BraneContract.java` - Comprehensive class Javadoc
- `brane-core/.../types/Address.java` - Record with validation
- `brane-core/.../abi/Abi.java` - Interface with static factories
- `brane-rpc/.../Brane.java` - Client interface methods (see Brane.Reader and Brane.Signer)

---

## Public Docs Conventions

### Location

All public docs live in `docs/` as MDX files:
```
docs/
├── mint.json              # Navigation structure
├── quickstart.mdx
├── public-client/
│   ├── api.mdx
│   └── subscriptions.mdx
├── wallet-client/
│   └── ...
├── contracts/
│   └── ...
├── providers/
│   └── ...
├── utilities/
│   └── ...
└── chains/
    └── ...
```

### MDX File Structure

```mdx
---
title: Page Title
description: 'One-line description for SEO and navigation.'
---

Brief intro paragraph explaining what this page covers.

## Section Heading

Explanation of the concept.

```java
// Code example with imports
import io.brane.core.types.Address;

var address = new Address("0x...");
```

## Another Section

More content...

| Column 1 | Column 2 | Column 3 |
|----------|----------|----------|
| Data     | Data     | Data     |
```

### Navigation (mint.json)

When adding a new page, update `docs/mint.json`:

```json
{
  "navigation": [
    {
      "group": "Group Name",
      "pages": [
        "folder/existing-page",
        "folder/new-page"  // Add new page here
      ]
    }
  ]
}
```

### Content Guidelines

1. **Imports always shown** - Don't assume readers know where classes come from
2. **Working examples** - Code must compile and run
3. **Progressive complexity** - Simple example first, then advanced
4. **Tables for options** - Use tables for configuration options
5. **Link to Javadoc** - Reference detailed Javadoc for edge cases

### Which Page to Update?

| Feature Type | Update These Pages |
|--------------|-------------------|
| New RPC method | `public-client/api.mdx` or `wallet-client/api.mdx` |
| New type | `utilities/types.mdx` |
| Contract feature | `contracts/interaction.mdx` or `contracts/bindings.mdx` |
| Provider changes | `providers/http.mdx` or `providers/websocket.mdx` |
| Error handling | `utilities/errors.mdx` |
| New major feature | May need new page + mint.json update |

---

## Accuracy Checklist

Before finishing documentation, verify each point:

### Javadoc Accuracy

- [ ] Summary accurately describes what the code does (re-read implementation)
- [ ] All parameters documented with correct types
- [ ] Return type and semantics match actual behavior
- [ ] All thrown exceptions listed (check `throw` statements in code)
- [ ] Example code actually works (trace through mentally or compile)
- [ ] No copy-paste errors from similar methods
- [ ] Thread-safety claims are accurate

### Public Docs Accuracy

- [ ] Code examples use correct class names and methods
- [ ] Import statements are complete
- [ ] Method signatures match actual API
- [ ] Defaults in tables match code constants
- [ ] No stale examples from previous versions
- [ ] Links to other pages are valid

### Cross-Reference Check

- [ ] Javadoc and public docs say the same thing
- [ ] No contradictions between different doc sources
- [ ] Version-specific behavior clearly marked

---

## Common Documentation Mistakes

### Mistake: Generic Description

```java
// BAD - doesn't say what it actually does
/**
 * Processes the transaction.
 */

// GOOD - specific behavior
/**
 * Sends a signed transaction and waits for confirmation.
 *
 * <p>Blocks until the transaction is mined or timeout is reached.
 * Polls the node at the specified interval to check for receipt.</p>
 */
```

### Mistake: Missing Exception Conditions

```java
// BAD - no context
/**
 * @throws IllegalArgumentException if invalid
 */

// GOOD - specific condition
/**
 * @throws IllegalArgumentException if address is null or not 42 characters
 */
```

### Mistake: Outdated Example

```java
// BAD - method signature changed but example wasn't updated
/**
 * <pre>{@code
 * contract.call("balanceOf", owner);  // Missing return type parameter!
 * }</pre>
 */

// GOOD - matches current API
/**
 * <pre>{@code
 * BigInteger balance = contract.call("balanceOf", BigInteger.class, owner);
 * }</pre>
 */
```

### Mistake: Missing Imports in Public Docs

```mdx
// BAD - where does Address come from?
```java
var addr = new Address("0x...");
```

// GOOD - clear imports
```java
import io.brane.core.types.Address;

var addr = new Address("0x...");
```
```

---

## Documentation Templates

### New Public Method Javadoc

```java
/**
 * <verb> <what it does>.
 *
 * <p>
 * <how it works, any side effects, thread-safety>
 * </p>
 *
 * <p>
 * <strong>Example:</strong>
 * <pre>{@code
 * // working example
 * }</pre>
 * </p>
 *
 * @param param1 description (constraints)
 * @param param2 description (constraints)
 * @return description (never null / may be null / empty semantics)
 * @throws SomeException when condition
 */
```

### New Public Class Javadoc

```java
/**
 * <one-line description of the class purpose>.
 *
 * <p>
 * <detailed description of responsibilities, lifecycle, thread-safety>
 * </p>
 *
 * <p>
 * <strong>Usage:</strong>
 * <pre>{@code
 * // typical usage pattern
 * var instance = MyClass.create(...);
 * var result = instance.doSomething();
 * }</pre>
 * </p>
 *
 * <p>
 * <strong>Thread Safety:</strong>
 * <statement about concurrent use>
 * </p>
 *
 * @see RelatedClass
 */
```

### New Docs Page

```mdx
---
title: Feature Name
description: 'What this feature enables users to do.'
---

Brief intro explaining the feature and when to use it.

## Basic Usage

The simplest way to use this feature:

```java
import io.brane.xxx.FeatureClass;

// Simple example
var feature = FeatureClass.create(...);
var result = feature.method();
```

## Configuration

Customize behavior with options:

```java
var options = FeatureOptions.builder()
    .option1(value)
    .option2(value)
    .build();
```

| Option | Default | Description |
|--------|---------|-------------|
| `option1` | `X` | What it controls |
| `option2` | `Y` | What it controls |

## Advanced Usage

More complex scenarios...

## Error Handling

What can go wrong and how to handle it...
```

---

## Workflow Summary

1. **Identify changes** - `git diff` to see what was implemented
2. **Update Javadoc** - Add/update in source files
3. **Update public docs** - Add/update in `docs/` MDX files
4. **Verify accuracy** - Cross-check docs against actual code
5. **Update mint.json** - If new pages were added

**Remember**: Accuracy is paramount. Take the time to verify every claim against the actual implementation.
