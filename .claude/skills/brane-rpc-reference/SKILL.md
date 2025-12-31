---
name: brane-rpc-reference
description: Reference for Ethereum JSON-RPC methods, parameters, return types, and error codes. Use when implementing RPC client methods or debugging RPC issues.
---

# Ethereum JSON-RPC Reference

## Official Specification

- **Ethereum JSON-RPC Spec**: https://ethereum.org/en/developers/docs/apis/json-rpc/
- **Execution API Spec**: https://ethereum.github.io/execution-apis/api-documentation/

---

## Common Methods by Category

### Reading State

| Method | Parameters | Returns | Notes |
|--------|------------|---------|-------|
| `eth_chainId` | none | `QUANTITY` (hex) | Chain ID for EIP-155 signing |
| `eth_blockNumber` | none | `QUANTITY` | Latest block number |
| `eth_getBalance` | `address`, `block` | `QUANTITY` | Balance in wei |
| `eth_getCode` | `address`, `block` | `DATA` | Contract bytecode |
| `eth_getStorageAt` | `address`, `position`, `block` | `DATA` | 32-byte storage slot |
| `eth_getTransactionCount` | `address`, `block` | `QUANTITY` | Nonce for address |

### Blocks

| Method | Parameters | Returns | Notes |
|--------|------------|---------|-------|
| `eth_getBlockByNumber` | `block`, `fullTxs` | `Block` object | `fullTxs`: true for full tx objects |
| `eth_getBlockByHash` | `hash`, `fullTxs` | `Block` object | |
| `eth_getBlockReceipts` | `block` | `Receipt[]` | All receipts in block |

### Transactions

| Method | Parameters | Returns | Notes |
|--------|------------|---------|-------|
| `eth_getTransactionByHash` | `hash` | `Transaction` | null if not found |
| `eth_getTransactionReceipt` | `hash` | `Receipt` | null if not mined |
| `eth_sendRawTransaction` | `signedTxData` | `hash` | Returns tx hash |
| `eth_call` | `txObject`, `block` | `DATA` | Executes without sending |
| `eth_estimateGas` | `txObject`, `block`? | `QUANTITY` | Gas estimate |

### Logs/Events

| Method | Parameters | Returns | Notes |
|--------|------------|---------|-------|
| `eth_getLogs` | `filterObject` | `Log[]` | Historical logs |
| `eth_newFilter` | `filterObject` | `filterId` | Create filter |
| `eth_getFilterLogs` | `filterId` | `Log[]` | Get filter logs |
| `eth_getFilterChanges` | `filterId` | `Log[]` | Poll for new logs |
| `eth_uninstallFilter` | `filterId` | `bool` | Remove filter |

### Gas

| Method | Parameters | Returns | Notes |
|--------|------------|---------|-------|
| `eth_gasPrice` | none | `QUANTITY` | Legacy gas price |
| `eth_maxPriorityFeePerGas` | none | `QUANTITY` | EIP-1559 priority fee |
| `eth_feeHistory` | `blockCount`, `newestBlock`, `rewardPercentiles` | `FeeHistory` | Historical fee data |
| `eth_createAccessList` | `txObject`, `block`? | `AccessListResult` | EIP-2930 access list |

---

## Block Parameter

Many methods accept a block parameter:

| Value | Meaning |
|-------|---------|
| `"latest"` | Most recent mined block |
| `"pending"` | Pending state/transactions |
| `"earliest"` | Genesis block |
| `"safe"` | Latest safe head (PoS) |
| `"finalized"` | Latest finalized block (PoS) |
| `"0x..."` | Specific block number (hex) |

---

## Transaction Object (for eth_call, eth_estimateGas)

```json
{
  "from": "0x...",           // optional for eth_call
  "to": "0x...",             // null for contract creation
  "gas": "0x...",            // optional, node uses default
  "gasPrice": "0x...",       // legacy, optional
  "maxFeePerGas": "0x...",   // EIP-1559, optional
  "maxPriorityFeePerGas": "0x...", // EIP-1559, optional
  "value": "0x...",          // optional, default 0
  "data": "0x...",           // optional
  "nonce": "0x...",          // optional for eth_call
  "accessList": [...]        // EIP-2930, optional
}
```

---

## Filter Object (for eth_getLogs, eth_newFilter)

```json
{
  "fromBlock": "0x..." | "latest",  // optional
  "toBlock": "0x..." | "latest",    // optional
  "address": "0x..." | ["0x..."],   // optional, single or array
  "topics": [                        // optional
    "0x...",                         // topic[0] (event signature)
    null,                            // topic[1] (any value)
    ["0x...", "0x..."]              // topic[2] (OR match)
  ],
  "blockHash": "0x..."              // alternative to fromBlock/toBlock
}
```

### Topics Array Logic

- Position = topic index (0-3)
- `null` = match any value
- `"0x..."` = exact match
- `["0x...", "0x..."]` = OR (match any in array)

---

## Response Objects

### Transaction Receipt

```json
{
  "transactionHash": "0x...",
  "transactionIndex": "0x...",
  "blockHash": "0x...",
  "blockNumber": "0x...",
  "from": "0x...",
  "to": "0x...",               // null for contract creation
  "contractAddress": "0x...",  // null unless contract creation
  "cumulativeGasUsed": "0x...",
  "gasUsed": "0x...",
  "effectiveGasPrice": "0x...",
  "logs": [...],
  "logsBloom": "0x...",
  "status": "0x1" | "0x0",     // 1 = success, 0 = revert
  "type": "0x0" | "0x2"        // 0 = legacy, 2 = EIP-1559
}
```

### Log Entry

```json
{
  "address": "0x...",
  "topics": ["0x...", ...],
  "data": "0x...",
  "blockNumber": "0x...",
  "blockHash": "0x...",
  "transactionHash": "0x...",
  "transactionIndex": "0x...",
  "logIndex": "0x...",
  "removed": false
}
```

---

## Error Codes

### Standard JSON-RPC Errors

| Code | Message | Meaning |
|------|---------|---------|
| -32700 | Parse error | Invalid JSON |
| -32600 | Invalid request | Not valid JSON-RPC |
| -32601 | Method not found | Method doesn't exist |
| -32602 | Invalid params | Wrong parameters |
| -32603 | Internal error | Server error |

### Ethereum-Specific Errors

| Code | Message | Meaning |
|------|---------|---------|
| -32000 | Server error | Generic (check message) |
| -32001 | Resource not found | Block/tx not found |
| -32002 | Resource unavailable | Node syncing |
| -32003 | Transaction rejected | Rejected by node |
| -32004 | Method not supported | Not implemented |
| -32005 | Limit exceeded | Rate limit or block range |

### Common -32000 Messages

| Message Contains | Meaning |
|------------------|---------|
| "insufficient funds" | Not enough ETH for gas + value |
| "nonce too low" | Nonce already used |
| "nonce too high" | Gap in nonce sequence |
| "gas too low" | Gas limit below intrinsic gas |
| "intrinsic gas too low" | Same as above |
| "replacement transaction underpriced" | Replacement needs higher gas |
| "already known" | Duplicate transaction |
| "execution reverted" | Contract reverted |
| "block range" | eth_getLogs range too large |

---

## Revert Data

When `eth_call` or `eth_estimateGas` reverts, error data contains:

```json
{
  "code": -32000,
  "message": "execution reverted",
  "data": "0x08c379a0..."  // ABI-encoded revert reason
}
```

### Revert Selectors

| Selector | Type | Decoding |
|----------|------|----------|
| `0x08c379a0` | `Error(string)` | Standard revert with message |
| `0x4e487b71` | `Panic(uint256)` | Solidity panic code |
| Other | Custom error | Application-specific |

---

## Quantity Encoding

All numeric values are hex-encoded with:
- `0x` prefix
- No leading zeros (except `0x0`)
- Lowercase hex digits

```
0        -> "0x0"
255      -> "0xff"
256      -> "0x100"
1000000  -> "0xf4240"
```

---

## Node-Specific Methods

### Geth/Erigon Debug Methods

| Method | Purpose |
|--------|---------|
| `debug_traceTransaction` | Trace transaction execution |
| `debug_traceCall` | Trace eth_call |
| `debug_getBadBlocks` | List invalid blocks |

### Anvil-Specific (Testing)

| Method | Purpose |
|--------|---------|
| `anvil_mine` | Mine blocks |
| `anvil_setBalance` | Set account balance |
| `anvil_impersonateAccount` | Impersonate address |
| `evm_snapshot` | Create state snapshot |
| `evm_revert` | Revert to snapshot |
| `evm_increaseTime` | Advance block time |

---

## Brane Method Mapping

| Brane Method | RPC Method |
|--------------|------------|
| `publicClient.getBalance()` | `eth_getBalance` |
| `publicClient.getBlockNumber()` | `eth_blockNumber` |
| `publicClient.getBlock()` | `eth_getBlockByNumber` |
| `publicClient.call()` | `eth_call` |
| `publicClient.estimateGas()` | `eth_estimateGas` |
| `publicClient.getLogs()` | `eth_getLogs` |
| `walletClient.sendTransaction()` | `eth_sendRawTransaction` |
| `walletClient.sendTransactionAndWait()` | `eth_sendRawTransaction` + `eth_getTransactionReceipt` |
