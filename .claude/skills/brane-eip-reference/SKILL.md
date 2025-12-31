---
name: brane-eip-reference
description: Reference for Ethereum Improvement Proposals (EIPs) relevant to SDK implementation. Use when implementing transaction types, signing, or protocol features.
---

# EIP Implementation Reference

## EIP Sources

- **Official EIPs**: https://eips.ethereum.org
- **EIP GitHub**: https://github.com/ethereum/EIPs

---

## Transaction Types

### EIP-2718: Typed Transaction Envelope

**Status**: Final | **Brane Support**: Yes

Defines the envelope format for typed transactions:

```
TransactionType || TransactionPayload
```

| Type | Name | EIP |
|------|------|-----|
| `0x00` | Legacy | Pre-EIP-2718 |
| `0x01` | Access List | EIP-2930 |
| `0x02` | EIP-1559 | EIP-1559 |
| `0x03` | Blob | EIP-4844 |

**Brane Implementation**: `UnsignedTransaction.encodeAsEnvelope()`

---

### EIP-155: Replay Protection

**Status**: Final | **Brane Support**: Yes

Prevents transaction replay across chains by including chainId in signature.

**Legacy Transaction Signing**:
```
v = chainId * 2 + 35 + yParity
```

Where `yParity` is 0 or 1 from the ECDSA signature.

**Brane Implementation**: `DefaultWalletClient.sendTransaction()` line ~234

---

### EIP-1559: Fee Market

**Status**: Final | **Brane Support**: Yes

Replaces `gasPrice` with:
- `maxFeePerGas`: Maximum total fee per gas
- `maxPriorityFeePerGas`: Maximum tip to validator

**Actual fee**: `min(maxFeePerGas, baseFee + maxPriorityFeePerGas)`

**Transaction Format (Type 0x02)**:
```
0x02 || RLP([chainId, nonce, maxPriorityFeePerGas, maxFeePerGas, gasLimit, to, value, data, accessList, signatureYParity, signatureR, signatureS])
```

**RPC Methods**:
- `eth_maxPriorityFeePerGas` - Suggested priority fee
- `eth_feeHistory` - Historical base fees and rewards

**Brane Implementation**:
- `Eip1559Transaction`
- `SmartGasStrategy`

---

### EIP-2930: Access Lists

**Status**: Final | **Brane Support**: Yes

Optional access list to pre-declare storage slots, reducing gas costs.

**Format**:
```json
"accessList": [
  {
    "address": "0x...",
    "storageKeys": ["0x...", "0x..."]
  }
]
```

**Gas Savings**:
- Cold address access: 2600 → 2400 gas (with access list)
- Cold storage access: 2100 → 1900 gas (with access list)

**RPC Method**: `eth_createAccessList`

**Brane Implementation**: `AccessListEntry`, `TxBuilder.accessList()`

---

### EIP-4844: Blob Transactions (Proto-Danksharding)

**Status**: Final | **Brane Support**: Not yet

For rollup data availability. Adds:
- `maxFeePerBlobGas`
- `blobVersionedHashes`

**Transaction Type**: `0x03`

---

## Signing Standards

### EIP-712: Typed Structured Data Signing

**Status**: Final | **Brane Support**: Partial

Standard for signing typed data (used by Permit, Permit2, etc.)

**Domain Separator**:
```solidity
struct EIP712Domain {
    string name;
    string version;
    uint256 chainId;
    address verifyingContract;
}
```

**Signing Process**:
1. Hash the domain separator
2. Hash the struct data
3. Sign: `keccak256("\x19\x01" || domainSeparator || structHash)`

**Common Uses**:
- ERC-20 Permit (gasless approvals)
- Permit2 (Uniswap)
- Safe transactions
- NFT marketplace signatures

---

### EIP-191: Signed Data Standard

**Status**: Final | **Brane Support**: Yes

Prefix for signed messages to prevent transaction replay:

```
"\x19Ethereum Signed Message:\n" + len(message) + message
```

**Brane Implementation**: Personal sign in `Signer`

---

## Contract Standards

### EIP-1967: Proxy Storage Slots

**Status**: Final

Standard storage slots for proxy contracts:

| Slot | Purpose | Value |
|------|---------|-------|
| `0x360894a13ba1a3210667c828492db98dca3e2076cc3735a920a3ca505d382bbc` | Implementation | `bytes32(uint256(keccak256('eip1967.proxy.implementation')) - 1)` |
| `0xb53127684a568b3173ae13b9f8a6016e243e63b6e8ee1178d6a717850b5d6103` | Admin | `bytes32(uint256(keccak256('eip1967.proxy.admin')) - 1)` |
| `0xa3f0ad74e5423aebfd80d3ef4346578335a9a72aeaee59ff6cb3582b35133d50` | Beacon | `bytes32(uint256(keccak256('eip1967.proxy.beacon')) - 1)` |

---

### ERC-20: Token Standard

**Status**: Final | **Brane Support**: Via contract binding

```solidity
function name() view returns (string)
function symbol() view returns (string)
function decimals() view returns (uint8)
function totalSupply() view returns (uint256)
function balanceOf(address owner) view returns (uint256)
function transfer(address to, uint256 amount) returns (bool)
function transferFrom(address from, address to, uint256 amount) returns (bool)
function approve(address spender, uint256 amount) returns (bool)
function allowance(address owner, address spender) view returns (uint256)

event Transfer(address indexed from, address indexed to, uint256 value)
event Approval(address indexed owner, address indexed spender, uint256 value)
```

---

### ERC-721: NFT Standard

**Status**: Final | **Brane Support**: Via contract binding

Key functions:
```solidity
function balanceOf(address owner) view returns (uint256)
function ownerOf(uint256 tokenId) view returns (address)
function safeTransferFrom(address from, address to, uint256 tokenId)
function transferFrom(address from, address to, uint256 tokenId)
function approve(address to, uint256 tokenId)
function setApprovalForAll(address operator, bool approved)
function getApproved(uint256 tokenId) view returns (address)
function isApprovedForAll(address owner, address operator) view returns (bool)
```

---

### ERC-1155: Multi-Token Standard

**Status**: Final | **Brane Support**: Via contract binding

Key functions:
```solidity
function balanceOf(address account, uint256 id) view returns (uint256)
function balanceOfBatch(address[] accounts, uint256[] ids) view returns (uint256[])
function safeTransferFrom(address from, address to, uint256 id, uint256 amount, bytes data)
function safeBatchTransferFrom(address from, address to, uint256[] ids, uint256[] amounts, bytes data)
function setApprovalForAll(address operator, bool approved)
function isApprovedForAll(address account, address operator) view returns (bool)
```

---

## Error Standards

### EIP-838: ABI-Encoded Revert Reasons

Defines how revert reasons are encoded:

```solidity
// Standard revert
revert("message");
// Encodes as: 0x08c379a0 + ABI-encoded string

// Custom error
error InsufficientBalance(uint256 available, uint256 required);
revert InsufficientBalance(100, 200);
// Encodes as: selector + ABI-encoded parameters
```

**Panic Codes** (selector `0x4e487b71`):

| Code | Meaning |
|------|---------|
| 0x00 | Generic compiler panic |
| 0x01 | Assert failed |
| 0x11 | Arithmetic overflow/underflow |
| 0x12 | Division by zero |
| 0x21 | Invalid enum value |
| 0x22 | Storage byte array encoding error |
| 0x31 | pop() on empty array |
| 0x32 | Array index out of bounds |
| 0x41 | Too much memory allocated |
| 0x51 | Zero internal function pointer |

**Brane Implementation**: `RevertDecoder`, `RevertException`

---

## Multicall Standards

### Multicall3

**Address**: `0xcA11bde05977b3631167028862bE2a173976CA11` (same on all chains)

```solidity
struct Call3 {
    address target;
    bool allowFailure;
    bytes callData;
}

struct Result {
    bool success;
    bytes returnData;
}

function aggregate3(Call3[] calls) returns (Result[])
```

**Brane Implementation**: `MulticallBatch`

---

## Relevant EIPs by Feature

### When Implementing Transactions
- EIP-155 (replay protection)
- EIP-1559 (fee market)
- EIP-2718 (typed transactions)
- EIP-2930 (access lists)

### When Implementing Signing
- EIP-191 (personal sign)
- EIP-712 (typed data)

### When Implementing Contract Interaction
- EIP-838 (revert reasons)
- ERC-20/721/1155 (token standards)

### When Implementing Multicall
- Multicall3 spec
