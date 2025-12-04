# Documentation Outline

## 1. Getting Started
- **Installation**: Maven/Gradle dependencies.
- **Quickstart**: Connect, read block, send transaction.

## 2. Public Client (Read)
- **Overview**: Reading from the blockchain.
- **API**: `getBalance`, `getChainId`, `getBlock`, `call`.
- **Events**: `getLogs` (Filtering & Topics).

## 3. Wallet Client (Write)
- **Overview**: Managing accounts and transactions.
- **Transactions**: `sendTransaction` vs `sendTransactionAndWait`.
- **Gas Management**: Automatic estimation & EIP-1559.

## 4. Smart Contracts
- **Interaction**: Reading (`call`) and Writing (`sendAndWait`).
- **Type-Safe Bindings**: Generating Java interfaces (`BraneContract.bind`).

## 5. Providers & Chains
- **Configuration**: `HttpBraneProvider` setup.
- **Chains**: Using `ChainProfiles` (Mainnet, Sepolia, Anvil).

## 6. Advanced Signing (External Signers)
- **`Signer` Interface**: Implementing custom signers (e.g., Privy, AWS KMS, HSM).
- **Example**: How to inject a custom signer into `WalletClient`.

## 7. Utilities & Types
- **Primitives**: `Address`, `Wei`, `HexData`.
- **ABI Encoding**: `FastAbiEncoder` (Low-level usage).
- **Error Handling**: Decoding reverts and RPC errors.
