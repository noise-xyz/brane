# Feature: Implement `eth_createAccessList` Support

## Goal

Add `createAccessList(TransactionRequest request)` to `PublicClient` that auto-generates an EIP-2930 access list by calling `eth_createAccessList`.

## Why This Feature?

Access lists (EIP-2930) pre-declare storage slots a transaction will touch, reducing gas costs by ~10-20% for complex contract interactions. All modern Ethereum SDKs support this:

**viem (TypeScript):**
```typescript
const result = await publicClient.createAccessList({
  data: '0xdeadbeef',
  to: '0x70997970c51812dc3a010c7d01b50e0d17dc79c8'
});
// Returns: { accessList: AccessList, gasUsed: bigint }
```

**alloy-rs (Rust):**
```rust
let result = provider.create_access_list(&tx_request).await?;
// Returns: AccessListWithGasUsed { access_list, gas_used }
```

Brane should have parity with these libraries. Currently users must manually construct access lists or skip the optimization entirely.

## Context

- `TransactionRequest` and `AccessListEntry` models already exist in `brane-core`
- `DefaultPublicClient` implements `PublicClient` with methods like `getBalance`, `getLogs`
- Look at existing patterns before implementing

## API Contract

```java
// New model in brane-core/src/main/java/io/brane/core/model/
public record AccessListWithGas(
    List<AccessListEntry> accessList,
    BigInteger gasUsed
) {}

// New method in PublicClient interface
AccessListWithGas createAccessList(TransactionRequest request);
```

## RPC Specification

**Request:**
```json
["eth_createAccessList", [{"from": "0x...", "to": "0x...", "data": "0x..."}, "latest"]]
```

**Response:**
```json
{
  "accessList": [{"address": "0x...", "storageKeys": ["0x..."]}],
  "gasUsed": "0x5208"
}
```

## Files to Modify

| File | Action |
|------|--------|
| `brane-core/.../model/AccessListWithGas.java` | **NEW** |
| `brane-rpc/.../rpc/PublicClient.java` | Add method |
| `brane-rpc/.../rpc/DefaultPublicClient.java` | Implement |
| `brane-rpc/.../rpc/DefaultPublicClientTest.java` | Add tests |

## Acceptance Criteria

1. `createAccessList` returns correct `AccessListWithGas` for valid request
2. Empty access list response is handled correctly
3. All existing tests still pass: `./gradlew test`

## Verification

```bash
# Run your new tests
./gradlew :brane-rpc:test --tests "*DefaultPublicClientTest*"

# Run full suite
./gradlew test
```

## Time Limit

**90 minutes**
