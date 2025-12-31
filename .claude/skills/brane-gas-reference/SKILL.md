---
name: brane-gas-reference
description: Reference for Ethereum gas mechanics, estimation strategies, and optimization. Use when implementing gas estimation, debugging transaction failures, or optimizing gas usage.
---

# Ethereum Gas Reference

## Gas Fundamentals

### What Gas Represents

- **Computational cost** of executing operations
- **Storage cost** for state changes
- **Transaction overhead** for inclusion in block

### Key Terms

| Term | Definition |
|------|------------|
| Gas Limit | Maximum gas units transaction can consume |
| Gas Used | Actual gas consumed by execution |
| Gas Price | Price per gas unit (legacy) |
| Base Fee | Minimum price per gas (EIP-1559, burned) |
| Priority Fee | Tip to validator (EIP-1559) |
| Max Fee | Maximum total per gas (EIP-1559) |

---

## Transaction Types & Gas

### Legacy (Type 0)

```
Total Cost = gasUsed * gasPrice
```

### EIP-1559 (Type 2)

```
Effective Gas Price = min(maxFeePerGas, baseFee + maxPriorityFeePerGas)
Total Cost = gasUsed * effectiveGasPrice
```

**User pays**: `effectiveGasPrice`
**Validator gets**: `effectiveGasPrice - baseFee` (priority fee)
**Burned**: `baseFee * gasUsed`

---

## Intrinsic Gas Costs

Minimum gas before execution begins:

| Component | Gas Cost |
|-----------|----------|
| Base transaction | 21,000 |
| Per zero byte in calldata | 4 |
| Per non-zero byte in calldata | 16 |
| Contract creation (no `to`) | +32,000 |
| Access list address | 2,400 per address |
| Access list storage key | 1,900 per key |

### Calculation

```
intrinsicGas = 21000
             + (zeroBytes * 4)
             + (nonZeroBytes * 16)
             + (isContractCreation ? 32000 : 0)
             + (accessListAddresses * 2400)
             + (accessListKeys * 1900)
```

---

## Opcode Gas Costs (Selected)

### Cheap Operations (3 gas)

- `ADD`, `SUB`, `NOT`, `LT`, `GT`, `EQ`, `ISZERO`
- `AND`, `OR`, `XOR`
- `PUSH`, `POP`, `DUP`, `SWAP`

### Medium Operations

| Opcode | Gas | Notes |
|--------|-----|-------|
| `MUL`, `DIV` | 5 | |
| `ADDMOD`, `MULMOD` | 8 | |
| `EXP` | 10 + 50*bytes | Exponentiation |
| `SHA3` | 30 + 6*words | Keccak256 |
| `BALANCE` | 100 (warm) / 2600 (cold) | |
| `CALL` | 100 (warm) / 2600 (cold) | Plus memory & value costs |

### Storage Operations

| Operation | Gas (Warm) | Gas (Cold) |
|-----------|------------|------------|
| `SLOAD` | 100 | 2,100 |
| `SSTORE` (0→non-0) | 20,000 | 22,100 |
| `SSTORE` (non-0→non-0) | 2,900 | 5,000 |
| `SSTORE` (non-0→0) | 2,900 + 4,800 refund | 5,000 + 4,800 refund |

### External Calls

| Type | Base Cost |
|------|-----------|
| `CALL` | 100 (warm) / 2,600 (cold) |
| `CALL` with value | +9,000 |
| `CALL` creating account | +25,000 |
| `DELEGATECALL` | Same as CALL (no value transfer) |
| `STATICCALL` | Same as CALL (no value transfer) |

---

## Warm vs Cold Access (EIP-2929)

First access to address/slot in transaction = **cold** (expensive)
Subsequent access = **warm** (cheap)

| Access Type | Cold | Warm |
|-------------|------|------|
| Address (BALANCE, CALL, etc.) | 2,600 | 100 |
| Storage slot (SLOAD) | 2,100 | 100 |

**Access Lists** (EIP-2930) pre-warm addresses/slots for gas savings.

---

## Gas Estimation

### `eth_estimateGas` Behavior

1. Executes transaction against current state
2. Returns gas that *would be* used
3. Does NOT account for state changes between estimate and execution
4. May fail if transaction would revert

### Estimation Strategies

#### Simple Buffer

```java
estimatedGas = eth_estimateGas(tx)
gasLimit = estimatedGas * 120 / 100  // 20% buffer
```

#### Dynamic Buffer Based on Type

```java
if (isContractDeployment) {
    buffer = 1.3;  // 30% - deployments vary more
} else if (hasAccessList) {
    buffer = 1.1;  // 10% - access list reduces variance
} else {
    buffer = 1.2;  // 20% - standard
}
gasLimit = estimatedGas * buffer;
```

#### Cap at Block Gas Limit

```java
gasLimit = min(calculatedLimit, blockGasLimit * 0.9)
```

---

## Fee Estimation (EIP-1559)

### Base Fee Prediction

Base fee adjusts each block:
- Block > 50% full: base fee increases (up to 12.5%)
- Block < 50% full: base fee decreases (up to 12.5%)

### Priority Fee Selection

| Speed | Strategy |
|-------|----------|
| Slow | Historical 25th percentile |
| Medium | Historical 50th percentile |
| Fast | Historical 75th percentile or higher |

### Using `eth_feeHistory`

```json
{
  "method": "eth_feeHistory",
  "params": [
    "0x4",           // 4 blocks
    "latest",        // ending block
    [25, 50, 75]     // percentiles for priority fees
  ]
}
```

Response:
```json
{
  "baseFeePerGas": ["0x...", ...],    // base fees (N+1 values)
  "gasUsedRatio": [0.5, 0.3, ...],    // how full each block was
  "reward": [                         // priority fees at percentiles
    ["0x...", "0x...", "0x..."],      // block 1: [p25, p50, p75]
    ...
  ]
}
```

### Recommended Max Fee

```
nextBaseFee = currentBaseFee * 1.125  // worst case increase
maxFeePerGas = nextBaseFee * 2 + maxPriorityFeePerGas
```

This covers ~6 blocks of maximum increases.

---

## Common Gas Errors

### "intrinsic gas too low"

**Cause**: Gas limit below intrinsic minimum

**Fix**: Ensure gas limit ≥ intrinsic gas calculation

### "out of gas"

**Cause**: Execution exceeded gas limit

**Fixes**:
- Increase gas limit
- Optimize contract code
- Use access list to reduce cold costs

### "insufficient funds"

**Cause**: `balance < (gasLimit * gasPrice) + value`

**Fix**: Ensure sender has enough ETH

### "replacement transaction underpriced"

**Cause**: Replacement tx needs ≥10% higher gas price

**Fix**: Increase gas price by at least 10%

---

## Gas Optimization Tips

### For SDK Users

1. **Use access lists** for repeated storage access
2. **Batch calls** with Multicall3
3. **Estimate accurately** to avoid overpaying
4. **Monitor base fee** to time transactions

### For Contract Interactions

1. **Pre-approve** tokens to avoid approval + transfer
2. **Use Permit** for gasless approvals (EIP-2612)
3. **Batch operations** when possible

---

## Brane Gas Implementation

### SmartGasStrategy

Location: `brane-rpc/.../SmartGasStrategy.java`

Features:
- Automatic EIP-1559 vs legacy detection
- Configurable buffer (numerator/denominator)
- Uses chain profile defaults

### Gas-Related Methods

| Method | Purpose |
|--------|---------|
| `publicClient.estimateGas()` | Get gas estimate |
| `publicClient.getGasPrice()` | Legacy gas price |
| `publicClient.getMaxPriorityFeePerGas()` | EIP-1559 priority fee |
| `publicClient.getFeeHistory()` | Historical fee data |
| `publicClient.createAccessList()` | Generate access list |

### Chain Profiles

Different chains have different:
- Minimum gas prices
- Block gas limits
- EIP-1559 support

See: `brane-core/.../chain/ChainProfiles.java`

---

## Gas Refunds

### Storage Clearing

Clearing storage (non-zero → zero) provides refund:
- Refund: 4,800 gas
- Capped at: 20% of total gas used (post-London)

### Self-Destruct

`SELFDESTRUCT` refund removed in EIP-3529 (London).

---

## Block Gas Limits

| Chain | Block Gas Limit |
|-------|-----------------|
| Ethereum Mainnet | ~30M |
| Arbitrum | 32M (L2 gas) |
| Optimism | 30M |
| Polygon | 30M |
| Base | 30M |

Single transaction typically limited to 90% of block gas limit.
