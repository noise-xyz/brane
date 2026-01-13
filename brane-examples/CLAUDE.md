# brane-examples

Comprehensive examples demonstrating SDK features. Also serves as integration tests.

## Running Examples

```bash
# Run specific example
./gradlew :brane-examples:run -PmainClass=sh.brane.examples.CanonicalAbiExample

# Requires Anvil for most examples
anvil  # In separate terminal

# EIP-4844 blob examples require Cancun hardfork
anvil --hardfork cancun
./gradlew :brane-examples:run -PmainClass=sh.brane.examples.BlobTransactionExample
```

## Canonical Examples (Start Here)

| Example | Description |
|---------|-------------|
| `CanonicalAbiExample` | Contract binding via `BraneContract.bind()` |
| `CanonicalErc20Example` | ERC-20 token interactions |
| `CanonicalTxExample` | Transaction building and submission |
| `CanonicalRawExample` | Low-level RPC calls |
| `CanonicalDebugExample` | Debug logging setup |
| `CanonicalCustomSignerExample` | Custom/remote signer implementation |

## Feature Examples

| Example | Feature |
|---------|---------|
| `AccessListExample` | EIP-2930 access list optimization |
| `BlobTransactionExample` | EIP-4844 blob transactions with KZG |
| `GasEstimationDemo` | Gas estimation strategies |
| `HighPerformanceExample` | Virtual thread performance |
| `MnemonicWalletExample` | BIP-39/BIP-44 HD wallet derivation |
| `TxBuilderExample` | Transaction builder patterns |
| `Erc20TransferLogExample` | Event log decoding |
| `MultiChainLatestBlockExample` | Multi-chain support |

## Sanity Checks

| Check | Purpose |
|-------|---------|
| `TransactionSanityCheck` | Transaction validation |
| `CryptoSanityCheck` | Cryptography verification |
| `SmartGasSanityCheck` | Gas strategy testing |
| `RequestIdSanityCheck` | Request ID tracking |

## Integration Tests

| Test | Coverage |
|------|----------|
| `WalletRevertTest` | Revert handling |
| `RevertIntegrationTest` | Revert decoding |
| `TxBuilderIntegrationTest` | Transaction builder E2E |
| `DebugIntegrationTest` | Debug logging |
| `InfuraWebSocketTest` | WebSocket with Infura |

## Writing New Examples

1. Create class in `sh.brane.examples`
2. Add `public static void main(String[] args)`
3. Use standard Anvil setup:
   ```java
   var signer = new PrivateKeySigner("0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80");
   var client = Brane.connect("http://127.0.0.1:8545", signer);
   ```
4. Print results clearly
5. Throw on failure (no silent failures)

## Dependencies

- brane-core
- brane-rpc
- brane-contract
- brane-kzg (for EIP-4844 blob examples)
- Requires Anvil for most examples
