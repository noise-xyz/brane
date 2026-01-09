# Documentation Outline

## 1. Getting Started
- **Installation**: Maven/Gradle dependencies.
- **Quickstart**: Connect, read block, send transaction.

## 2. Read Operations (Brane)
- **Overview**: Reading from the blockchain.
- **API**: `getBalance`, `getChainId`, `getBlock`, `call`.
- **Events**: `getLogs` (Filtering & Topics).
- **Simulation**: `simulateCalls` (`eth_simulateV1`).

## 3. Write Operations (Brane.Signer)
- **Overview**: Managing accounts and transactions.
- **Transactions**: `sendTransaction` vs `sendTransactionAndWait`.
- **Gas Management**: Automatic estimation & EIP-1559.

## 4. Smart Contracts
- **Interaction**: Reading (`call`) and Writing (`sendAndWait`).
- **Type-Safe Bindings**: Generating Java interfaces (`BraneContract.bind`).

## 5. Providers & Chains
- **Configuration**: `HttpBraneProvider` setup.
- **WebSocket**: Real-time subscriptions with `WebSocketProvider`.
- **Chains**: Using `ChainProfiles` (Mainnet, Sepolia, Anvil).

## 6. Advanced Signing (External Signers)
- **`Signer` Interface**: Implementing custom signers (e.g., Privy, AWS KMS, HSM).
- **Example**: How to inject a custom signer into `Brane.Signer`.

## 7. Utilities & Types
- **Primitives**: `Address`, `Wei`, `HexData`.
- **ABI Encoding**: `FastAbiEncoder` (Low-level usage).
- **Error Handling**: Decoding reverts and RPC errors.

## 8. Testing (Brane.Tester)
- **Overview**: Introduction to `Brane.Tester` for local test node interaction.
- **Setup**: `connectTest()` factory methods, `TestNodeMode`, `AnvilSigners`.
- **Account Manipulation**: `setBalance`, `setCode`, `setNonce`, `setStorageAt`.
- **Impersonation**: `impersonate()`, `ImpersonationSession`, whale testing patterns.
- **State Management**: `snapshot`/`revert`, `dumpState`/`loadState`, `reset()` for fork testing.
- **Mining & Time Control**: `mine()`, automine, interval mining, `increaseTime`, `setNextBlockTimestamp`.
