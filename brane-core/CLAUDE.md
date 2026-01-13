# brane-core

The foundation module. Types, ABI encoding/decoding, crypto, transaction building, and data models.

## Package Structure

| Package | Purpose |
|---------|---------|
| `sh.brane.core.types` | Value objects: `Address`, `Wei`, `Hash`, `HexData`, `Blob`, `BlobSidecar`, `KzgCommitment`, `KzgProof` |
| `sh.brane.core.abi` | ABI encoding/decoding: `Abi`, `AbiEncoder`, `AbiDecoder` |
| `sh.brane.core.crypto` | ECDSA: `PrivateKey`, `Signer`, `Signature`, `Keccak256`; KZG: `Kzg` interface |
| `sh.brane.core.crypto.hd` | HD Wallet (BIP-39/BIP-44): `MnemonicWallet`, `DerivationPath` |
| `sh.brane.core.crypto.eip712` | EIP-712: `TypedData`, `Eip712Domain`, `TypedDataSigner`, `TypedDataJson` |
| `sh.brane.core.builder` | Transaction builders: `TxBuilder`, `Eip1559Builder`, `LegacyBuilder`, `Eip4844Builder` |
| `sh.brane.core.model` | Data models: `TransactionRequest`, `TransactionReceipt`, `BlockHeader`, `BlobTransactionRequest` |
| `sh.brane.core.tx` | Transaction types: `LegacyTransaction`, `Eip1559Transaction`, `Eip4844Transaction`; Blob utils: `SidecarBuilder`, `BlobDecoder` |
| `sh.brane.core.chain` | Chain profiles: `ChainProfiles.MAINNET`, etc. |
| `sh.brane.core.error` | Exception hierarchy: `BraneException`, `TxnException`, `RevertException` |
| `sh.brane.core.util` | Utilities: `Topics`, `MethodUtils` |

## Key Classes

### Types (`sh.brane.core.types`)
- **`Address`** - 20-byte Ethereum address with checksum validation
- **`Wei`** - Arbitrary precision wei amount with `fromEther()`, `toEther()`
- **`Hash`** - 32-byte hash (transaction hash, block hash)
- **`HexData`** - Arbitrary hex-encoded bytes (thread-safe lazy initialization)

### ABI (`sh.brane.core.abi`)
- **`Abi`** - Parse and use ABI definitions
- **`AbiEncoder`** - Encode function calls and constructor args
- **`AbiDecoder`** - Decode return values and event logs
- **`InternalAbi`** - Internal ABI codec (implementation detail)

### Crypto (`sh.brane.core.crypto`)
- **`PrivateKey`** - Secp256k1 key with `sign()`, `toAddress()`, implements `Destroyable`
- **`Signer`** - Interface for signing (allows remote signers)
- **`Signature`** - ECDSA signature with r, s, v components (defensive copies)
- **`Keccak256`** - Hash utility with ThreadLocal caching - **call `cleanup()` in pooled threads**

### HD Wallet (`sh.brane.core.crypto.hd`)
- **`MnemonicWallet`** - BIP-39/BIP-44 HD wallet with `fromPhrase()`, `generatePhrase()`, `derive()`
- **`DerivationPath`** - Custom derivation path for non-standard account indices
- **`Bip39`** - Internal BIP-39 mnemonic implementation
- **`Bip32`** - Internal BIP-32 key derivation implementation

### EIP-712 (`sh.brane.core.crypto.eip712`)
- **`TypedData<T>`** - Primary API for type-safe EIP-712 signing with records
- **`Eip712Domain`** - Domain separator fields with `separator()` method for computing domain hash
- **`TypedDataSigner`** - Static utility for dynamic/runtime EIP-712 signing
- **`TypedDataJson`** - JSON parsing for eth_signTypedData_v4 format (WalletConnect, MetaMask)
- **`TypeDefinition<T>`** - Type schema with field mappings and extractor
- **`TypedDataField`** - Single field definition (name + Solidity type)

### Builders (`sh.brane.core.builder`)
- **`TxBuilder`** - Sealed interface for transaction building
- **`Eip1559Builder`** - EIP-1559 transactions (recommended)
- **`LegacyBuilder`** - Legacy (type 0) transactions
- **`Eip4844Builder`** - EIP-4844 blob transactions with `blobData()` or `sidecar()` modes
- **`BuilderValidation`** - Internal validation utility

### EIP-4844 Blobs (`sh.brane.core.types`, `sh.brane.core.tx`, `sh.brane.core.crypto`)
- **`Blob`** - 128KB blob data (4096 field elements)
- **`BlobSidecar`** - Container for blobs, KZG commitments, and proofs
- **`KzgCommitment`** - 48-byte KZG commitment with `toVersionedHash()`
- **`KzgProof`** - 48-byte KZG proof
- **`SidecarBuilder`** - Encode raw bytes into blobs and build sidecars
- **`BlobDecoder`** - Decode original data back from blobs
- **`Kzg`** - Interface for KZG operations (implemented by `CKzg` in brane-kzg)
- **`BlobTransactionRequest`** - Request model containing transaction fields and sidecar

### Error Hierarchy (`sh.brane.core.error`)
```
BraneException (sealed)
├── AbiDecodingException - ABI decoding failures
├── AbiEncodingException - ABI encoding failures
├── KzgException - KZG proof/commitment failures
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

### HD Wallet (BIP-39/BIP-44)
```java
// Generate new wallet (save phrase securely!)
MnemonicWallet wallet = MnemonicWallet.generatePhrase();
String phrase = wallet.phrase();

// Restore wallet from existing phrase
MnemonicWallet restored = MnemonicWallet.fromPhrase(phrase);

// With passphrase for extra security
MnemonicWallet secure = MnemonicWallet.fromPhrase(phrase, "my-passphrase");

// Derive signers for different addresses (m/44'/60'/0'/0/N)
Signer account0 = wallet.derive(0);
Signer account1 = wallet.derive(1);

// Custom derivation path
Signer custom = wallet.derive(new DerivationPath(1, 5)); // m/44'/60'/1'/0/5
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

### EIP-4844 Blob Transactions
```java
// Build blob transaction from raw data (requires Kzg from brane-kzg)
BlobTransactionRequest request = Eip4844Builder.create()
    .to(recipient)
    .value(Wei.fromEther("0.001"))
    .blobData(rawBytes)  // Raw data to encode into blobs
    .maxFeePerBlobGas(Wei.gwei(10))
    .build(kzg);  // KZG instance for commitment/proof generation

// Or build with pre-constructed sidecar (for reuse/fee bumping)
BlobSidecar sidecar = SidecarBuilder.from(rawBytes).build(kzg);
BlobTransactionRequest request2 = Eip4844Builder.create()
    .to(recipient)
    .sidecar(sidecar)  // Pre-built sidecar
    .build();

// Decode data back from blobs (round-trip verification)
byte[] decoded = BlobDecoder.decode(request.sidecar().blobs());

// Validate KZG proofs
request.sidecar().validate(kzg);
```

## Gotchas

- **Keccak256 ThreadLocal**: Call `Keccak256.cleanup()` in pooled/web threads to prevent memory leaks
- **PrivateKey.fromBytes()**: Input array is **zeroed** after construction for security
- **PrivateKey implements Destroyable**: Call `destroy()` when done with key material
- **MnemonicWallet seed lifetime**: Keep wallet instances short-lived; the mnemonic phrase in memory provides access to all derived keys. Derived `Signer` instances are independent and can be held longer
- **Signature.r()/s()**: Return defensive copies - safe to expose
- **HexData.fromBytes()**: Creates defensive copy - safe to modify original after
- **Wei precision**: Use `BigInteger` or `BigDecimal` - never `double` for amounts
- **Address checksums**: `Address.from()` validates EIP-55 checksums when mixed case
- **TransactionReceipt.contractAddress**: Type is `Address` (not `HexData`)
- **Record validation**: All model records validate in compact constructors - invalid data throws early
- **BlobSidecar max blobs**: Maximum 6 blobs per transaction (EIP-4844 limit)
- **Blob data encoding**: Use `SidecarBuilder.from(bytes)` - each blob holds ~126KB usable data
- **Kzg instance**: Obtain from `brane-kzg` module via `CKzg.loadFromClasspath()`

## Dependencies

- BouncyCastle (crypto)
- Jackson (JSON parsing for ABI)
- brane-primitives (Hex/RLP)
