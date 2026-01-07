# docs

Documentation website powered by Mintlify (MDX format).

## Structure

```
docs/
├── mint.json              # Mintlify configuration
├── Introduction.mdx       # SDK overview
├── Quickstart.mdx         # Getting started
├── Performance.mdx        # Performance guide
├── Implementation.md      # Internal notes
│
├── utilities/             # Feature docs
│   ├── abi.mdx           # ABI system
│   ├── errors.mdx        # Error handling
│   ├── types.mdx         # Type system
│   ├── metrics.mdx       # Monitoring
│   └── threading.mdx     # Concurrency
│
├── brane-reader/          # Brane.Reader API
├── brane-signer/          # Brane.Signer API
├── contracts/             # Contract binding
├── providers/             # Provider setup
└── chains/                # Chain profiles
```

## Local Development

```bash
cd docs
npx mintlify dev
```

## MDX Format

Mintlify uses MDX (Markdown + JSX):

```mdx
---
title: "Page Title"
description: "Brief description"
---

# Heading

Regular markdown content.

<CodeGroup>
```java
// Java example
```
</CodeGroup>

<Note>
Important information here.
</Note>
```

## Key Files

- **mint.json**: Navigation, theme, API config
- **Introduction.mdx**: First page users see
- **Quickstart.mdx**: Installation and first steps

## Updating Docs

1. Edit `.mdx` files
2. Preview with `npx mintlify dev`
3. Commit changes
4. Deploys automatically via Mintlify

## When to Update

- New public API added
- Breaking changes
- New features in brane-contract/rpc/core
- After running `/brane-docs` skill

## Style Guide

- Use code examples liberally
- Keep explanations concise
- Link between related pages
- Include gotchas/common mistakes
