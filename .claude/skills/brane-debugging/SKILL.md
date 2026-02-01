---
name: brane-debugging
description: Systematic approach to debugging Brane SDK issues. Use when diagnosing transaction failures, RPC errors, encoding mismatches, or unexpected behavior.
---

# Brane SDK Debugging Guide

## Debugging Philosophy

1. **Reproduce first** - Get a minimal reproduction
2. **Isolate the layer** - RPC? Encoding? Signing? Contract?
3. **Compare with known-good** - Use Anvil, compare with viem/cast
4. **Read the actual bytes** - Hex doesn't lie

---

## Quick Diagnosis Flowchart

```
Transaction Failed
       │
       ▼
┌─────────────────┐
│ What error?     │
└────────┬────────┘
         │
    ┌────┴────┬────────────┬─────────────┐
    ▼         ▼            ▼             ▼
"reverted"  "nonce"    "gas"      "insufficient
    │         │          │         funds"
    ▼         ▼          ▼             │
 Decode     Check      Estimate       ▼
 revert    pending      gas        Check
 data       txs                   balance
```

---

## Layer-by-Layer Debugging

### Layer 1: RPC Communication

**Symptoms**: Connection refused, timeout, unexpected null

**Checks**:
```bash
# Is the node reachable?
curl -X POST http://localhost:8545 \
  -H "Content-Type: application/json" \
  -d '{"jsonrpc":"2.0","method":"eth_chainId","params":[],"id":1}'

# Expected: {"jsonrpc":"2.0","id":1,"result":"0x1"}
```

**Common Issues**:

| Symptom | Cause | Fix |
|---------|-------|-----|
| Connection refused | Node not running | Start Anvil/node |
| 403 Forbidden | Rate limited or auth required | Check API key, rate limits |
| Timeout | Node overloaded or network issue | Retry, use different endpoint |
| Empty result | Method not supported | Check node type (full vs light) |

### Layer 2: Request Encoding

**Symptoms**: Invalid params error, unexpected behavior

**Debug Approach**:
```java
// Enable Brane debug logging
System.setProperty("BRANE_DEBUG", "true");

// This will print:
// [BRANE] → eth_call {"to":"0x...", "data":"0x..."}
// [BRANE] ← "0x..."
```

**Verify calldata manually**:
```bash
# Using cast (foundry)
cast calldata "balanceOf(address)" 0xYourAddress

# Compare with what Brane generates
```

### Layer 3: Transaction Signing

**Symptoms**: Invalid sender, signature error

**Checks**:
1. Is the signer address what you expect?
   ```java
   System.out.println("Signer address: " + signer.address());
   ```

2. Is chain ID correct?
   ```java
   // Chain ID mismatch causes "invalid sender"
   var chainId = publicClient.getChainId();
   System.out.println("Chain ID: " + chainId);
   ```

3. Verify signature recovery:
   ```bash
   # Using cast
   cast wallet sign --private-key 0x... "message"
   ```

### Layer 4: Contract Execution

**Symptoms**: Revert, out of gas, unexpected return value

**Debug with eth_call first**:
```java
// Test without sending transaction
var result = publicClient.call(TransactionRequest.builder()
    .to(contractAddress)
    .data(encodedCalldata)
    .build());
```

**Get revert reason**:
```java
try {
    walletClient.sendTransaction(request);
} catch (RevertException e) {
    System.out.println("Revert kind: " + e.kind());
    System.out.println("Reason: " + e.reason());
    System.out.println("Raw data: " + e.rawDataHex());
}
```

---

## Decoding Revert Data

### Standard Error (0x08c379a0)

```
0x08c379a0
0000000000000000000000000000000000000000000000000000000000000020  // offset
0000000000000000000000000000000000000000000000000000000000000011  // length (17)
496e73756666696369656e742066756e647300000000000000000000000000  // "Insufficient funds"
```

**Decode**:
```bash
cast 4byte-decode 0x08c379a0...
# Or
cast --to-ascii 0x496e73756666696369656e742066756e6473
```

### Panic Code (0x4e487b71)

```
0x4e487b71
0000000000000000000000000000000000000000000000000000000000000011  // panic code
```

| Code | Meaning |
|------|---------|
| 0x01 | assert() failed |
| 0x11 | Arithmetic overflow |
| 0x12 | Division by zero |
| 0x21 | Invalid enum |
| 0x32 | Array out of bounds |

### Custom Error

```
0x<4-byte-selector><encoded-params>
```

**Find selector**:
```bash
# If you have the ABI
cast sig "InsufficientBalance(uint256,uint256)"
# Returns: 0x...

# Or search online
# https://openchain.xyz/signatures
```

---

## Common Error Messages

### Transaction Errors

| Error | Cause | Solution |
|-------|-------|----------|
| "nonce too low" | Nonce already used | Fetch fresh nonce with "pending" |
| "nonce too high" | Gap in nonce sequence | Use correct sequential nonce |
| "replacement transaction underpriced" | Replacing tx needs +10% gas | Increase gas price |
| "intrinsic gas too low" | Gas limit < base cost | Increase gas limit |
| "insufficient funds" | Can't afford gas + value | Check balance |
| "invalid sender" | Signature/chainId mismatch | Check signer, chain ID |
| "already known" | Duplicate transaction | Transaction already pending |

### RPC Errors

| Code | Message | Cause |
|------|---------|-------|
| -32000 | (various) | Server-specific, read message |
| -32601 | Method not found | Node doesn't support method |
| -32602 | Invalid params | Wrong parameter format |
| -32005 | Limit exceeded | Rate limit or block range |

### eth_call Errors

| Symptom | Cause | Debug |
|---------|-------|-------|
| Returns `0x` | Function doesn't exist or wrong address | Verify contract address, function selector |
| Returns weird data | Wrong ABI, wrong return type | Compare selector, check ABI |
| Reverts | Contract logic revert | Decode revert data |

---

## Debugging ABI Encoding

### Verify Function Selector

```java
// What Brane computes
var selector = Abi.computeSelector("transfer(address,uint256)");
System.out.println("Selector: " + Hex.encode(selector));
// Should be: 0xa9059cbb
```

```bash
# Verify with cast
cast sig "transfer(address,uint256)"
```

### Verify Encoded Parameters

```java
// Print full calldata
var calldata = abi.encodeFunction("transfer", recipient, amount);
System.out.println("Calldata: " + calldata.value());
```

```bash
# Decode to verify
cast calldata-decode "transfer(address,uint256)" 0xa9059cbb...
```

### Common Encoding Mistakes

| Symptom | Cause | Fix |
|---------|-------|-----|
| Wrong selector | Signature has spaces or wrong types | Use canonical signature |
| Wrong address | Not left-padded | Check Address encoding |
| Wrong number | Not proper hex | Use BigInteger, not long |

---

## Debugging with Anvil

### Start Anvil with Logging

```bash
# Verbose mode shows all RPC calls
anvil -vvvv

# Fork mainnet for testing against real contracts
anvil --fork-url https://eth.llamarpc.com
```

### Useful Anvil Commands

```bash
# Mine a block
cast rpc anvil_mine

# Set balance
cast rpc anvil_setBalance 0xAddress 0x1000000000000000000

# Impersonate account
cast rpc anvil_impersonateAccount 0xWhale

# Get transaction trace
cast run <txhash> --trace
```

### Compare with Cast

```bash
# Send same transaction with cast, compare results
cast send --private-key 0x... \
  0xContractAddress \
  "transfer(address,uint256)" \
  0xRecipient \
  1000000
```

---

## Debugging Contract Binding

### Method Not Found

```
IllegalArgumentException: No ABI function named 'transfer'
```

**Checks**:
1. Method name matches ABI exactly (case-sensitive)
2. ABI JSON is valid and contains the function
3. Interface method matches ABI signature

### Parameter Type Mismatch

```
IllegalArgumentException: Unsupported parameter type for transfer
```

**Checks**:
1. Java type matches Solidity type
2. For arrays: use `List<T>` or `T[]`
3. For uint256: use `BigInteger`, not `long`

### Return Type Mismatch

```
IllegalArgumentException: Unsupported return type for view function
```

**Checks**:
1. View functions return the decoded type
2. State-changing functions return `TransactionReceipt` or `void`

---

## Debug Logging

### Enable Brane Debug Output

```java
// Set before any Brane calls
System.setProperty("BRANE_DEBUG", "true");
```

**Output includes**:
- RPC method and parameters
- Response data
- Transaction encoding details
- Gas estimation

### Custom Logging Points

```java
// Log transaction request
System.out.println("To: " + request.to());
System.out.println("Data: " + request.data());
System.out.println("Value: " + request.value());

// Log raw signed transaction
System.out.println("Signed: " + Hex.encode(signedTx));
```

---

## Network-Specific Issues

### Mainnet

- Rate limits from public RPCs
- High gas prices during congestion
- MEV/frontrunning effects

### Testnets

- Faucet rate limits
- Occasional reorgs
- Different behavior than mainnet

### L2s (Arbitrum, Optimism, Base)

- Different gas model
- Sequencer delays
- L1 data costs in gas

---

## Checklist: Transaction Won't Send

1. [ ] Node reachable? (`eth_chainId` works?)
2. [ ] Correct chain ID?
3. [ ] Sender has balance for gas + value?
4. [ ] Nonce is correct? (fetch with "pending")
5. [ ] Gas limit sufficient? (try eth_estimateGas)
6. [ ] Gas price acceptable? (not below minimum)
7. [ ] Contract address correct?
8. [ ] Function selector correct?
9. [ ] Parameters encoded correctly?
10. [ ] Signer address matches expected?

---

## Checklist: eth_call Returns Wrong Data

1. [ ] Contract address correct?
2. [ ] Function exists in contract?
3. [ ] Selector matches? (compare with cast sig)
4. [ ] Parameters encoded correctly?
5. [ ] Return type matches ABI?
6. [ ] Block parameter correct? (latest vs specific)
7. [ ] Contract not proxy? (might need implementation address)
