---
name: brane-abi-reference
description: Reference for Solidity ABI encoding and decoding rules. Use when implementing ABI encoder/decoder, debugging encoding issues, or mapping Solidity types to Java.
---

# Solidity ABI Encoding Reference

## Official Specification

- **ABI Spec**: https://docs.soliditylang.org/en/latest/abi-spec.html

---

## Type Categories

### Static Types (Fixed Size)

| Solidity Type | Size | Java Mapping |
|---------------|------|--------------|
| `bool` | 32 bytes (padded) | `boolean` / `Boolean` |
| `uint8` - `uint256` | 32 bytes | `BigInteger` |
| `int8` - `int256` | 32 bytes | `BigInteger` |
| `address` | 32 bytes (left-padded) | `Address` |
| `bytes1` - `bytes32` | 32 bytes (right-padded) | `byte[]` / `HexData` |
| `<type>[N]` | N * element size | `T[]` / `List<T>` |

### Dynamic Types (Variable Size)

| Solidity Type | Encoding | Java Mapping |
|---------------|----------|--------------|
| `bytes` | length + data | `byte[]` / `HexData` |
| `string` | length + UTF-8 data | `String` |
| `<type>[]` | length + elements | `T[]` / `List<T>` |
| `tuple` | concatenated fields | Record / Object |

---

## Encoding Rules

### Basic Principles

1. All values padded to 32-byte boundaries
2. Numbers are big-endian
3. Static types encoded in-place
4. Dynamic types use offset pointers

### Numeric Encoding

```
uint256(256)  -> 0x0000000000000000000000000000000000000000000000000000000000000100
int256(-1)    -> 0xffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffffff
uint8(255)    -> 0x00000000000000000000000000000000000000000000000000000000000000ff
```

**Rules**:
- Unsigned: left-pad with zeros
- Signed negative: left-pad with `0xff` (two's complement)

### Address Encoding

```
address(0xdead...beef) -> 0x000000000000000000000000deaddeaddeaddeaddeaddeaddeaddeaddeadbeef
```

**Rule**: Left-pad to 32 bytes with zeros

### Boolean Encoding

```
bool(true)  -> 0x0000000000000000000000000000000000000000000000000000000000000001
bool(false) -> 0x0000000000000000000000000000000000000000000000000000000000000000
```

### Fixed Bytes Encoding

```
bytes4(0xdeadbeef)  -> 0xdeadbeef00000000000000000000000000000000000000000000000000000000
bytes32(0xdead...)  -> 0xdead...0000 (right-padded)
```

**Rule**: Right-pad with zeros

---

## Dynamic Type Encoding

### String / bytes

```
string("hello") encodes as:
  offset:  0x0000...0020 (32 = location of data)
  length:  0x0000...0005 (5 bytes)
  data:    0x68656c6c6f000000000000000000000000000000000000000000000000000000
           ("hello" right-padded)
```

### Dynamic Array

```
uint256[](1, 2, 3) encodes as:
  offset:  0x0000...0020 (location)
  length:  0x0000...0003 (3 elements)
  elem[0]: 0x0000...0001
  elem[1]: 0x0000...0002
  elem[2]: 0x0000...0003
```

---

## Function Calls

### Function Selector

First 4 bytes of `keccak256(signature)`:

```
transfer(address,uint256)
keccak256("transfer(address,uint256)") = 0xa9059cbb...
selector = 0xa9059cbb
```

**Rules for signature**:
- No spaces
- No parameter names
- `uint` → `uint256`, `int` → `int256`
- Arrays: `uint256[]`, `address[3]`
- Tuples: `(uint256,address)`

### Full Calldata

```
transfer(0xdead..., 100)

0xa9059cbb                                                       // selector (4 bytes)
000000000000000000000000deaddeaddeaddeaddeaddeaddeaddeaddeadbeef  // address (32 bytes)
0000000000000000000000000000000000000000000000000000000000000064  // amount (32 bytes)
```

---

## Tuple Encoding

### Static Tuple

```solidity
function foo(uint256 a, address b)
```

Encodes as concatenation:
```
[a: 32 bytes][b: 32 bytes]
```

### Tuple with Dynamic Fields

```solidity
function foo(uint256 a, string memory b, uint256 c)
```

Encodes as:
```
[a: value     ] // 32 bytes - static
[b: offset    ] // 32 bytes - pointer to string data
[c: value     ] // 32 bytes - static
[b.length     ] // 32 bytes - string length
[b.data       ] // ceil(len/32)*32 bytes - string content
```

---

## Nested Dynamic Types

### Array of Strings

```solidity
function foo(string[] memory strs)
```

```
[offset to array     ] // 0x20
[array length        ] // N
[offset to strs[0]   ] // relative to array start
[offset to strs[1]   ]
...
[strs[0].length      ]
[strs[0].data        ]
[strs[1].length      ]
[strs[1].data        ]
...
```

---

## Event Encoding

### Topic[0]: Event Signature

```solidity
event Transfer(address indexed from, address indexed to, uint256 value);
```

```
topic[0] = keccak256("Transfer(address,address,uint256)")
         = 0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef
```

### Indexed Parameters

- Up to 3 indexed parameters (topics 1-3)
- Indexed dynamic types store hash, not value
- Non-indexed go in `data` field

```
Transfer(0xfrom, 0xto, 100):
  topic[0]: event signature
  topic[1]: 0x000...from (address padded)
  topic[2]: 0x000...to
  data:     0x000...0064 (value = 100)
```

### Anonymous Events

No topic[0] (no signature), allows 4 indexed params.

---

## Decoding Return Values

### Single Return Value

```solidity
function balanceOf(address) returns (uint256)
```

Return data is just the encoded value:
```
0x0000000000000000000000000000000000000000000000000000000000000064
```

### Multiple Return Values

```solidity
function getInfo() returns (uint256, address, bool)
```

Encoded as tuple:
```
[uint256: 32 bytes]
[address: 32 bytes]
[bool: 32 bytes]
```

### Dynamic Return Values

```solidity
function getName() returns (string memory)
```

```
[offset: 0x20]
[length: N]
[data: padded string]
```

---

## Common Encoding Mistakes

### Wrong Padding Direction

```
// address - LEFT pad
WRONG: 0xdead...0000000000000000000000000000
RIGHT: 0x000000000000000000000000dead...

// bytes4 - RIGHT pad
WRONG: 0x00000000000000000000000000000000000000000000000000000000deadbeef
RIGHT: 0xdeadbeef00000000000000000000000000000000000000000000000000000000
```

### Missing/Wrong Offsets

Dynamic types need correct offset calculation from the start of the encoding, not from current position.

### Signature Normalization

```
// WRONG signatures
"transfer(address, uint256)"     // has space
"transfer(address,uint)"         // uint not uint256
"transfer(address to,uint256)"   // has param names

// CORRECT
"transfer(address,uint256)"
```

---

## Brane Type Mappings

| Solidity | Brane Type | Notes |
|----------|------------|-------|
| `uint8`-`uint256` | `BigInteger` | Use `BigInteger` for all sizes |
| `int8`-`int256` | `BigInteger` | Signed handled in encoding |
| `address` | `Address` | Validates format |
| `bool` | `boolean`/`Boolean` | |
| `string` | `String` | UTF-8 |
| `bytes` | `byte[]`/`HexData` | Dynamic |
| `bytes1`-`bytes32` | `byte[]`/`HexData` | Fixed |
| `T[]` | `List<T>`/`T[]` | Dynamic array |
| `T[N]` | `List<T>`/`T[]` | Fixed array (N elements) |
| `tuple` | Record/Object | Map to Java record |

---

## Reference Implementation Files

- **Encoder**: `brane-core/.../abi/AbiEncoder.java`
- **Decoder**: `brane-core/.../abi/AbiDecoder.java`
- **Types**: `brane-core/.../abi/AbiType.java`
- **Tests**: `brane-core/.../abi/AbiEncoderTest.java`, `AbiDecoderTest.java`
