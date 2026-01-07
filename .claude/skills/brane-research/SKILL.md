---
name: brane-research
description: Research EVM SDK patterns from viem (TypeScript) and alloy (Rust) when designing Brane features. Use when implementing new functionality, designing APIs, or solving problems that other SDKs have already addressed.
---

# Brane SDK Research

## Purpose

When implementing Brane features, reference how mature EVM SDKs solve the same problems:

- **viem** (TypeScript) - Modern, type-safe, excellent documentation
- **alloy** (Rust) - Performance-focused, similar design philosophy to Brane

These SDKs have solved many of the same problems. Learn from their API design, naming conventions, and implementation patterns.

---

## Reference Sources

### viem (TypeScript)
- **Documentation**: https://viem.sh
- **GitHub**: https://github.com/wevm/viem
- **Key strengths**: Excellent docs, modern API, comprehensive examples

### alloy (Rust)
- **Documentation**: https://alloy.rs
- **GitHub**: https://github.com/alloy-rs/alloy
- **Key strengths**: Type safety, performance, similar goals to Brane

---

## When to Research

Use this skill when:

1. **Designing new APIs** - How do viem/alloy name this? What parameters do they use?
2. **Implementing features** - What edge cases did they handle?
3. **Solving problems** - Has this been solved before? How?
4. **Naming things** - What's the conventional name for this concept?

---

## Research Workflow

### Step 1: Identify the Feature Area

Map your Brane feature to the equivalent concept:

| Brane Area | viem Equivalent | alloy Equivalent |
|------------|-----------------|------------------|
| `Brane.Reader` | `publicClient` / `PublicActions` | `Provider` |
| `Brane.Signer` | `walletClient` / `WalletActions` | `Signer` + `Provider` |
| `BraneContract.bind()` | `getContract()` | `ContractInstance` |
| `TransactionRequest` | `TransactionRequest` | `TransactionRequest` |
| `Address`, `Hash`, `HexData` | Branded types | `Address`, `B256`, `Bytes` |
| ABI encoding | `encodeFunctionData` | `SolCall::abi_encode` |
| ABI decoding | `decodeFunctionResult` | `SolCall::abi_decode` |
| Gas estimation | `estimateGas` | `estimate_gas` |
| Transaction receipt | `waitForTransactionReceipt` | `get_transaction_receipt` |

### Step 2: Fetch Documentation

Use WebFetch to read the relevant documentation:

```
# viem examples
https://viem.sh/docs/actions/public/getBalance
https://viem.sh/docs/contract/readContract
https://viem.sh/docs/actions/wallet/sendTransaction

# alloy examples
https://alloy.rs/examples/providers/builder.html
https://alloy.rs/examples/contracts/deploy.html
```

### Step 3: Compare and Adapt

When adapting patterns to Java/Brane:

| viem/alloy Pattern | Brane Adaptation |
|-------------------|------------------|
| Async/await | Blocking calls (Java's virtual threads handle concurrency) |
| Generic type parameters | Java generics or method overloads |
| Builder pattern | Same (Java builders work well) |
| Result/Option types | Exceptions + Optional |
| Branded types | Record types with validation |

---

## Common Research Scenarios

### Scenario: Implementing a new RPC method

1. Check viem docs for the action: `https://viem.sh/docs/actions/public/{methodName}`
2. Note the parameters, return type, and edge cases
3. Check if alloy has additional insights in their examples

### Scenario: Designing a new type

1. Look at viem's type definitions in their TypeScript source
2. Look at alloy's type definitions (usually in `alloy-primitives` or `alloy-rpc-types`)
3. Adapt to Java records with validation

### Scenario: Handling errors

1. Check viem's error types: https://viem.sh/docs/glossary/errors
2. See how they categorize and present errors
3. Map to Brane's exception hierarchy

### Scenario: Gas estimation strategy

1. viem: https://viem.sh/docs/actions/public/estimateGas
2. alloy: Check their provider examples
3. Note buffer strategies, EIP-1559 handling

---

## API Naming Conventions

Learn from viem/alloy naming patterns:

| Concept | viem | alloy | Brane (adopt) |
|---------|------|-------|---------------|
| Read contract | `readContract` | `call` | `call` or `read` |
| Write contract | `writeContract` | `send_transaction` | `sendTransaction` |
| Get balance | `getBalance` | `get_balance` | `getBalance` |
| Get block | `getBlock` | `get_block` | `getBlock` |
| Wait for receipt | `waitForTransactionReceipt` | `watch_pending_transaction` | `sendTransactionAndWait` |

---

## Key Documentation Pages

### viem - Bookmark These

- **Public Actions**: https://viem.sh/docs/actions/public/introduction
- **Wallet Actions**: https://viem.sh/docs/actions/wallet/introduction
- **Contract**: https://viem.sh/docs/contract/introduction
- **ABI**: https://viem.sh/docs/abi/introduction
- **Utilities**: https://viem.sh/docs/utilities/introduction
- **Errors**: https://viem.sh/docs/glossary/errors

### alloy - Bookmark These

- **Book/Guide**: https://alloy.rs
- **Examples**: https://alloy.rs/examples/
- **API Docs**: https://docs.rs/alloy/latest/alloy/

---

## Example Research Session

**Task**: Implement `eth_getLogs` with filter support

**Research steps**:

1. **viem docs**: Fetch https://viem.sh/docs/actions/public/getLogs
   - Note: filter parameters, block range handling, topics array format
   - Note: How they handle "block range too large" errors

2. **alloy examples**: Check their filtering examples
   - Note: Type definitions for LogFilter

3. **Adapt to Brane**:
   - Create `LogFilter` record with builder
   - Return `List<LogEntry>`
   - Handle block range errors with `RpcException.isBlockRangeTooLarge()`

---

## Things viem/alloy Do Well (Learn From)

1. **Type safety** - Both have strong typing; Brane should too
2. **Ergonomic APIs** - Easy common cases, flexible advanced cases
3. **Good defaults** - Sensible defaults, explicit overrides
4. **Error messages** - Clear, actionable error messages
5. **Documentation** - Examples for every feature

---

## Things to Adapt (Not Copy Directly)

| Pattern | Why Adapt |
|---------|-----------|
| Async everywhere | Java uses blocking + virtual threads |
| Function overloading via types | Java uses method overloading |
| Optional chaining | Java uses Optional or null checks |
| Destructuring | Java uses record accessors |
