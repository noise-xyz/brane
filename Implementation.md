# Code Quality Improvements - Unused Imports & Variables

## Goal
Clean up unused imports and unused fields/variables identified by IDE to improve code quality without touching vendored web3j code.

## Scope

### In Scope ✅
1. **Remove unused imports** (~20 files)
   - Test files: `AbiWrapperIntegrationTest`, `AbiWrapperExample`, `DebugExample`, etc.
   - Main code: `RpcRetry`, `SmartGasStrategy`, etc.
2. **Remove unused variables** (where safe)
   - Test helper fields that are write-only
   - Local variables that serve no purpose

### Out of Scope ❌
1. **Raw type warnings in `io.brane.internal.web3j.*`** - Vendored web3j, do not modify
2. **Build path cycles** - Complex Gradle restructuring, separate effort
3. **Deprecated API usage in web3j** - Vendored code
4. **Unused fields in production code** - May be used via reflection or future use

## Implementation Plan

### Phase 1: Remove Unused Imports

#### brane-contract module
- `InternalAbi.java` - Remove `Abi.FunctionMetadata`
- `AbiWrapperIntegrationTest.java` - Remove `BraneContract`, `URI`
- `ReadOnlyContractTest.java` - Remove `LinkedHashMap`

#### brane-rpc module
- `RpcRetry.java` - Remove `RevertDecoder`
- `SmartGasStrategy.java` - Remove `JsonRpcError`, `JsonRpcResponse`
- `DefaultWalletClientTest.java` - Remove `LinkedHashMap`

#### brane-examples module
- `AbiWrapperExample.java` - Remove `Abi`, `TxBuilder`, `HexData`, `Wei`, `URI`
- `DebugExample.java` - Remove `BigInteger`
- `RevertIntegrationTest.java` - Remove `BigInteger`
- `SmartGasSanityCheck.java` - Remove `BigInteger`, `URI`, `Duration`
- `TxBuilderIntegrationTest.java` - Remove `Hash`, `BigInteger`
- `WalletRevertTest.java` - Remove `Wei`

### Phase 2: Remove Unused Test Variables

Safe removals (test-only, no production impact):
- `ContractInvocationHandlerTest.java` - Unused local `abi` variables
- `Erc20TransferLogExample.java` - Unused local `t` variable
- `ReadOnlyContractTest.FakePublicClient.txHash` - Write-only field
- `DefaultWalletClientDebugTest.FakeSigner.last` - Write-only field

**DO NOT REMOVE** (may have hidden usage):
- `DefaultWalletClient.publicClient` - May be used in future
- `DefaultWalletClient.chainProfile` - May be used in future
- `InternalAbi.Call.function` - Internal state

### Phase 3: Fix Raw Types
- `InternalAbi.java`
    - Parameterize `List<Type>` to `List<Type<?>>` in `decodeEvents`, `Call.decode`, `mapArray`.
    - Parameterize `TypeReference<Type>` to `TypeReference<Type<?>>` in `castNonIndexed`.
    - Parameterize `Type` to `Type<?>` in `mapUnknown`.
- `RevertDecoder.java`
    - Parameterize `List<Type>` to `List<Type<?>>` in `formatCustomReason`.
- `RevertDecoderTest.java` - Parameterize `List<Type>` and `TypeReference`
- `AbiWrapperIntegrationTest.java` - Fix raw `Type` references
- `ContractReadTest.java` - Fix raw `Type` references
- `ReadOnlyContractTest.java` - Fix raw `Type` references

### Phase 4: Build Cycles (Verification & Fix)
- **Problem:** User reported persistent build cycle warnings.
- **Action:**
    - Double-check `brane-primitives/build.gradle` for any remaining dependencies on `brane-core`.
    - Verify `brane-core/build.gradle` dependencies.
    - Run `./gradlew dependencies` to inspect the graph if needed.
    - Ensure `brane-contract` and `brane-rpc` do not introduce hidden cycles.

### Phase 4: Build Cycles (Resolution)
- **Problem:** `brane-primitives` depends on `brane-core` for tests (`RlpCompatibilityTest`), while `brane-core` depends on `brane-primitives`.
- **Solution:** Move `RlpCompatibilityTest.java` to `brane-core` module.
    - Move `brane-primitives/src/test/java/io/brane/primitives/rlp/RlpCompatibilityTest.java` to `brane-core/src/test/java/io/brane/core/rlp/RlpCompatibilityTest.java`.
    - Update package declaration to `io.brane.core.rlp`.
    - Remove `testImplementation project(':brane-core')` from `brane-primitives/build.gradle`.

## Verification

1. Remove imports/variables
2. Fix raw types
3. Resolve build cycles
4. Run `./gradlew compileJava compileTestJava`
5. Run `./gradlew test`
6. Run `./gradlew javadoc`
7. Verify all tests pass

## Success Criteria

- ✅ All unused import warnings resolved
- ✅ Safe unused variable warnings resolved  
- ✅ Raw type warnings resolved in non-web3j code
- ✅ Build cycle warnings understood/resolved
- ✅ All tests pass
- ✅ Javadoc generates without errors
- ✅ No functionality broken
