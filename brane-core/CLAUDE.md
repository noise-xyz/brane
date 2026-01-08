# brane-core

The foundation module. Types, ABI encoding/decoding, crypto, transaction building, and data models.

## Package Structure

| Package | Purpose |
|---------|---------|
| `io.brane.core.types` | Value objects: `Address`, `Wei`, `Hash`, `HexData` |
| `io.brane.core.abi` | ABI encoding/decoding: `Abi`, `AbiEncoder`, `AbiDecoder` |
| `io.brane.core.crypto` | ECDSA: `PrivateKey`, `Signer`, `Signature`, `Keccak256` |
| `io.brane.core.crypto.eip712` | EIP-712: `TypedData`, `Eip712Domain`, `TypedDataSigner`, `TypedDataJson` |
| `io.brane.core.builder` | Transaction builders: `TxBuilder`, `Eip1559Builder`, `LegacyBuilder` |
| `io.brane.core.model` | Data models: `TransactionRequest`, `TransactionReceipt`, `BlockHeader` |
| `io.brane.core.tx` | Transaction types: `LegacyTransaction`, `Eip1559Transaction` |
| `io.brane.core.chain` | Chain profiles: `ChainProfiles.MAINNET`, etc. |
| `io.brane.core.error` | Exception hierarchy: `BraneException`, `TxnException`, `RevertException` |
| `io.brane.core.util` | Utilities: `Topics`, `MethodUtils` |

## Key Classes

### Types (`io.brane.core.types`)
- **`Address`** - 20-byte Ethereum address with checksum validation
- **`Wei`** - Arbitrary precision wei amount with `fromEther()`, `toEther()`
- **`Hash`** - 32-byte hash (transaction hash, block hash)
- **`HexData`** - Arbitrary hex-encoded bytes (thread-safe lazy initialization)

### ABI (`io.brane.core.abi`)
- **`Abi`** - Parse and use ABI definitions
- **`AbiEncoder`** - Encode function calls and constructor args
- **`AbiDecoder`** - Decode return values and event logs
- **`InternalAbi`** - Internal ABI codec (implementation detail)

### Crypto (`io.brane.core.crypto`)
- **`PrivateKey`** - Secp256k1 key with `sign()`, `toAddress()`, implements `Destroyable`
- **`Signer`** - Interface for signing (allows remote signers)
- **`Signature`** - ECDSA signature with r, s, v components (defensive copies)
- **`Keccak256`** - Hash utility with ThreadLocal caching - **call `cleanup()` in pooled threads**

### EIP-712 (`io.brane.core.crypto.eip712`)
- **`TypedData<T>`** - Primary API for type-safe EIP-712 signing with records
- **`Eip712Domain`** - Domain separator fields (name, version, chainId, verifyingContract, salt)
- **`TypedDataSigner`** - Static utility for dynamic/runtime EIP-712 signing
- **`TypedDataJson`** - JSON parsing for eth_signTypedData_v4 format (WalletConnect, MetaMask)
- **`TypeDefinition<T>`** - Type schema with field mappings and extractor
- **`TypedDataField`** - Single field definition (name + Solidity type)

### Builders (`io.brane.core.builder`)
- **`TxBuilder`** - Sealed interface for transaction building
- **`Eip1559Builder`** - EIP-1559 transactions (recommended)
- **`LegacyBuilder`** - Legacy (type 0) transactions
- **`BuilderValidation`** - Internal validation utility

### Error Hierarchy (`io.brane.core.error`)
```
BraneException (sealed)
├── AbiDecodingException - ABI decoding failures
├── AbiEncodingException - ABI encoding failures
├── RevertException - EVM execution reverts
├── RpcException - JSON-RPC communication failures
└── TxnException - Transaction-specific failures (non-sealed)
    ├── BraneTxBuilderException - Transaction building failures
    ├── ChainMismatchException - Chain ID mismatch errors
    └── InvalidSenderException - Invalid sender address errors
```

## Patterns

### Creating Types
```java
Address addr = Address.from("0x742d35Cc6634C0532925a3b844Bc9e7595f...");
Wei amount = Wei.fromEther("1.5");
Wei wei = Wei.of(1_000_000_000_000_000_000L);
HexData data = HexData.from("0xabcd");
```

### ABI Encoding
```java
Abi abi = Abi.from(jsonAbiString);
HexData calldata = abi.encodeFunction("transfer", toAddress, amount);
List<Object> result = abi.decodeFunction("balanceOf", returnData);
```

### Signing with Cleanup
```java
PrivateKey key = PrivateKey.from("0xac0974...");
try {
    Address myAddr = key.toAddress();
    Signature sig = key.sign(messageHash);
} finally {
    key.destroy();  // Clear sensitive data
}
```

### EIP-712 Typed Data Signing
```java
// Build domain
var domain = Eip712Domain.builder()
    .name("MyDapp")
    .version("1")
    .chainId(1L)
    .verifyingContract(contractAddress)
    .build();

// Type-safe API with records (compile-time safety)
var typedData = TypedData.create(domain, Permit.DEFINITION, permit);
Signature sig = typedData.sign(signer);

// Dynamic API for runtime types (JSON from dapps)
TypedData<?> fromJson = TypedDataJson.parseAndValidate(jsonString);
Signature sig2 = fromJson.sign(signer);
```

### ThreadLocal Cleanup in Pooled Threads
```java
executor.submit(() -> {
    try {
        byte[] hash = Keccak256.hash(data);
        // ... use hash ...
    } finally {
        Keccak256.cleanup();  // Prevent memory leak
    }
});
```

## Gotchas

- **Keccak256 ThreadLocal**: Call `Keccak256.cleanup()` in pooled/web threads to prevent memory leaks
- **PrivateKey.fromBytes()**: Input array is **zeroed** after construction for security
- **PrivateKey implements Destroyable**: Call `destroy()` when done with key material
- **Signature.r()/s()**: Return defensive copies - safe to expose
- **HexData.fromBytes()**: Creates defensive copy - safe to modify original after
- **Wei precision**: Use `BigInteger` or `BigDecimal` - never `double` for amounts
- **Address checksums**: `Address.from()` validates EIP-55 checksums when mixed case
- **TransactionReceipt.contractAddress**: Type is `Address` (not `HexData`)
- **Record validation**: All model records validate in compact constructors - invalid data throws early

## Dependencies

- BouncyCastle (crypto)
- Jackson (JSON parsing for ABI)
- brane-primitives (Hex/RLP)
