# Transaction Simulation Feature Specification

## Overview

This document outlines the design and implementation plan for adding transaction simulation to the Brane SDK. The feature will be accessible as a method on `PublicClient`, enabling developers to simulate Ethereum transactions and batches of calls without broadcasting them to the network.

The underlying RPC method is `eth_simulateV1`, introduced in [ethereum/execution-apis PR #484](https://github.com/ethereum/execution-apis/pull/484). This method allows simulating multiple transactions in sequence with state overrides, making it powerful for:

- **Gas estimation** for complex multi-call sequences
- **Dry-run validation** before submitting transactions
- **Debugging** contract interactions
- **Asset change tracing** to preview balance impacts

## Reference Implementations

| Library | Language | Reference |
|---------|----------|-----------|
| **viem** | TypeScript | [simulateCalls](https://viem.sh/docs/actions/public/simulateCalls) |
| **alloy** | Rust | [Provider::simulate](https://docs.rs/alloy/latest/alloy/providers/trait.Provider.html#method.simulate) |

The API design draws inspiration from both libraries while adhering to Brane's Java 21 idioms and type-safe patterns.

---

## API Design

### Primary Method on `PublicClient`

```java
/**
 * Simulates a batch of calls against the current blockchain state.
 *
 * @param request the simulation request containing calls and options
 * @return the simulation result with per-call outcomes
 * @throws RpcException if the RPC call fails
 * @throws SimulateNotSupportedException if the node does not support eth_simulateV1
 */
SimulateResult simulateCalls(SimulateRequest request);
```

### Request Builder Pattern

Following the existing `CallRequest` pattern in the codebase:

```java
SimulateRequest request = SimulateRequest.builder()
    .account(senderAddress)                              // optional: default from account
    .call(SimulateCall.builder()
        .to(contractAddress)
        .data(encodedFunctionCall)
        .build())
    .call(SimulateCall.builder()
        .to(anotherContract)
        .value(Wei.fromEther("1.0"))
        .build())
    .blockTag(BlockTag.LATEST)                           // optional: use BlockTag.LATEST, BlockTag.of(12345L), etc. (default: LATEST)
    .stateOverride(address, AccountOverride.builder()    // optional
        .balance(Wei.fromEther("1000"))
        .build())
    .traceAssetChanges(true)                             // optional: enable asset tracing
    .fetchTokenMetadata(false)                           // optional: fetch decimals/symbol (default: false)
    .validation(false)                                   // optional: skip validation mode
    .build();

SimulateResult result = publicClient.simulateCalls(request);
```

### Contract Call Convenience

Similar to viem, support ABI-aware calls for better ergonomics:

```java
SimulateRequest request = SimulateRequest.builder()
    .account(senderAddress)
    .call(SimulateCall.builder()
        .to(tokenAddress)
        .abi(erc20Abi)
        .function("transfer")
        .args(recipientAddress, BigInteger.valueOf(1000))
        .build())
    .call(SimulateCall.builder()
        .to(tokenAddress)
        .abi(erc20Abi)
        .function("balanceOf")
        .args(senderAddress)
        .build())
    .build();
```

---

## Data Models

### SimulateCall

Represents a single call in the simulation batch:

```java
public record SimulateCall(
    @Nullable Address from,          // Override account for this specific call
    Address to,                       // Target address (required)
    @Nullable HexData data,          // Raw calldata
    @Nullable Wei value,             // ETH value to send
    @Nullable BigInteger gas,        // Gas limit override
    @Nullable BigInteger gasPrice,   // Legacy gas price
    @Nullable BigInteger maxFeePerGas,
    @Nullable BigInteger maxPriorityFeePerGas
) {
    // Builder pattern for construction
}
```

### SimulateRequest

The full simulation request:

```java
public record SimulateRequest(
    @Nullable Address account,                         // Default from account
    List<SimulateCall> calls,                          // Calls to simulate (required, non-empty)
    @Nullable BlockTag blockTag,                       // Block reference: BlockTag.LATEST, BlockTag.of(number), etc. (default: LATEST)
    @Nullable Map<Address, AccountOverride> stateOverrides, // State modifications (map from address to override)
    boolean traceAssetChanges,                         // Enable asset change tracking
    boolean traceTransfers,                            // Enable transfer tracing
    boolean validation,                                // Enable validation mode
    boolean fetchTokenMetadata                         // Fetch decimals/symbol via eth_call (default: false)
) {
    // Builder pattern for construction
    // Note: blockTag defaults to BlockTag.LATEST if null
}
```

**Block Reference Behavior:**

The `blockTag` field uses the existing `BlockTag` type, which supports both named tags and specific block numbers:

- **Named tags**: `BlockTag.LATEST`, `BlockTag.PENDING`, `BlockTag.EARLIEST`, `BlockTag.SAFE`, `BlockTag.FINALIZED`
- **Specific block number**: `BlockTag.of(12345678L)`

When `blockTag` is `null` (not specified), it defaults to `BlockTag.LATEST`. This avoids ambiguity and matches the pattern used in `PublicClient.call()`.

**Examples:**
```java
// Use named tag
SimulateRequest.builder()
    .blockTag(BlockTag.LATEST)
    .build();

// Use specific block number
SimulateRequest.builder()
    .blockTag(BlockTag.of(12345678L))
    .build();

// Default to LATEST (omit blockTag)
SimulateRequest.builder()
    // blockTag defaults to BlockTag.LATEST
    .build();
```

### AccountOverride

For modifying account state during simulation. The address is specified as the map key in `SimulateRequest.stateOverrides`, not as a field in this record:

```java
public record AccountOverride(
    @Nullable Wei balance,                       // Override balance
    @Nullable Long nonce,                        // Override nonce
    @Nullable HexData code,                      // Override contract code
    @Nullable Map<Hash, Hash> stateDiff          // Storage slot overrides
) {
    // Builder pattern for construction
}
```

**Rationale:** This design aligns with the JSON-RPC format where `stateOverrides` is a map from address to override object (`{ "0x...": { "balance": "0x..." } }`). Using `Map<Address, AccountOverride>` prevents duplicate overrides for the same address and provides a cleaner, more type-safe API.

### SimulateResult

The simulation response:

```java
public record SimulateResult(
    List<CallResult> results,                    // Per-call results (same order as input)
    @Nullable List<AssetChange> assetChanges     // Only if traceAssetChanges=true
) {}
```

### CallResult

Individual call outcome:

```java
public sealed interface CallResult {
    BigInteger gasUsed();
    List<LogEntry> logs();
    
    record Success(
        BigInteger gasUsed,
        List<LogEntry> logs,
        @Nullable HexData returnData              // For view functions
    ) implements CallResult {}
    
    record Failure(
        BigInteger gasUsed,
        List<LogEntry> logs,
        String errorMessage,                      // From error.message in JSON response
        @Nullable HexData revertData              // From returnData in JSON response (revert data)
    ) implements CallResult {}
}
```

**Field Mapping from JSON-RPC Response:**
- `errorMessage`: Populated from the `error.message` field in the failed call result object
- `revertData`: Populated from the `returnData` field (contains revert data for failed calls, may be `null` or empty `"0x"` if no revert data)
- `gasUsed`: From `gasUsed` field (gas consumed before failure)
- `logs`: From `logs` array (any logs emitted before failure)

### AssetChange

Token balance changes (only when `traceAssetChanges=true`):

```java
public record AssetChange(
    AssetToken token,
    AssetValue value
) {}

public record AssetToken(
    Address address,                             // Token address (0xeee...eee for ETH)
    @Nullable Integer decimals,                  // null if fetchTokenMetadata=false or fetch failed
    @Nullable String symbol                       // null if fetchTokenMetadata=false or fetch failed
) {}

public record AssetValue(
    BigInteger pre,                              // Balance before
    BigInteger post,                             // Balance after
    BigInteger diff                              // Change amount (can be negative)
) {}
```

### Token Metadata Fetching

The `eth_simulateV1` RPC method returns asset changes with token addresses and balance deltas, but **does not include token metadata** such as `decimals` and `symbol`.

To populate the `AssetToken.decimals()` and `AssetToken.symbol()` fields, the client library must make additional `eth_call` requests:

- For ERC-20 tokens: Call `decimals()` and `symbol()` functions
- For native ETH: Use hardcoded values (18 decimals, "ETH" symbol, address `0xeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeeee`)

**Performance Considerations:**

- Each unique token address requires 2 additional `eth_call` requests (decimals + symbol)
- For simulations with many tokens, this can significantly increase latency
- **Recommendation**: Use `MulticallBatch` internally to batch all metadata requests into a single RPC call

**Design Decision:**

Token metadata fetching is **optional** and **defaults to `false`**:

```java
SimulateRequest request = SimulateRequest.builder()
    .calls(...)
    .traceAssetChanges(true)
    .fetchTokenMetadata(false)  // Default: false (no extra network calls)
    .build();
```

When `fetchTokenMetadata=false` (default):
- `AssetToken.decimals()` returns `null`
- `AssetToken.symbol()` returns `null`

When `fetchTokenMetadata=true`:
- The library makes additional `eth_call` requests (batched via `MulticallBatch` for efficiency)
- `decimals()` and `symbol()` are populated if the calls succeed
- If a call fails (e.g., non-ERC-20 contract), the field remains `null`

This allows users to opt-in to the performance cost when metadata is needed, or skip it when only addresses and balance changes are sufficient.

---

## Error Handling

### Method Not Supported

The `eth_simulateV1` method is relatively new and not supported by all RPC providers. When the node returns error code `-32601` ("Method not found"), throw a specific exception with a helpful message:

```java
public final class SimulateNotSupportedException extends RpcException {
    
    private static final int METHOD_NOT_FOUND = -32601;
    
    public SimulateNotSupportedException(String nodeMessage) {
        super(
            METHOD_NOT_FOUND,
            "eth_simulateV1 is not supported by this RPC node. " +
            "This method requires a node that supports the eth_simulateV1 JSON-RPC method " +
            "(e.g., recent versions of Geth, Erigon, or compatible RPC providers). " +
            "Original error: " + nodeMessage,
            null
        );
    }
}
```

### Detection Logic

The `sendWithRetry` method (via `RpcRetry.runRpc()`) returns a `JsonRpcResponse` for all RPC-level responses, including errors. It only throws exceptions for network/transport failures or when retries are exhausted. This design allows error handling to be centralized in a single `if (response.hasError())` block.

```java
private SimulateResult doSimulateCalls(SimulateRequest request) {
    JsonRpcResponse response = sendWithRetry("eth_simulateV1", buildParams(request));
    
    if (response.hasError()) {
        JsonRpcError err = response.error();
        if (err.code() == -32601) {
            throw new SimulateNotSupportedException(err.message());
        }
        // Handle other RPC errors normally
        throw new RpcException(err.code(), err.message(), extractErrorData(err.data()), null);
    }
    
    return parseResult(response.result());
}
```

**Note:** `sendWithRetry` may throw `RetryExhaustedException` or network exceptions (e.g., `IOException`), but these are distinct from RPC-level errors and represent transport failures rather than JSON-RPC method errors.

### Other Error Cases

| Error Code | Meaning | Handling |
|------------|---------|----------|
| `-32601` | Method not found | `SimulateNotSupportedException` with helpful message |
| `-32602` | Invalid params | `RpcException` (likely a bug in our serialization) |
| `-32000` | Execution error | `RpcException` or `RevertException` depending on context |

---

## JSON-RPC Mapping

### Request Format

```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "method": "eth_simulateV1",
  "params": [
    {
      "blockStateCalls": [
        {
          "stateOverrides": {
            "0x...": { "balance": "0x..." }
          },
          "calls": [
            {
              "from": "0x...",
              "to": "0x...",
              "value": "0x...",
              "data": "0x..."
            }
          ]
        }
      ],
      "traceTransfers": true,
      "validation": false
    },
    "latest"
  ]
}
```

### Response Format

**Successful Call:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": [
    {
      "calls": [
        {
          "status": "0x1",
          "gasUsed": "0x5208",
          "logs": [],
          "returnData": "0x"
        }
      ]
    }
  ]
}
```

**Failed Call:**
```json
{
  "jsonrpc": "2.0",
  "id": 1,
  "result": [
    {
      "calls": [
        {
          "status": "0x0",
          "gasUsed": "0x5208",
          "logs": [],
          "error": {
            "message": "execution reverted",
            "code": -32000
          },
          "returnData": "0x08c379a00000000000000000000000000000000000000000000000000000000000000020000000000000000000000000000000000000000000000000000000000000000e496e73756666696369656e742066756e64730000000000000000000000000000"
        }
      ]
    }
  ]
}
```

**Field Mapping:**
- `status`: `"0x1"` = success, `"0x0"` = failure
- `error.message`: Maps to `CallResult.Failure.errorMessage`
- `error.code`: Optional error code (may be null)
- `returnData`: For failures, contains revert data (may be empty `"0x"`). Maps to `CallResult.Failure.revertData`

---

## Usage Examples

### Simple Value Transfer Simulation

```java
try (var client = BranePublicClient.forChain(ChainProfiles.ETH_MAINNET).build()) {
    SimulateResult result = client.simulateCalls(SimulateRequest.builder()
        .account(Address.from("0x..."))
        .call(SimulateCall.builder()
            .to(Address.from("0x..."))
            .value(Wei.fromEther("1.0"))
            .build())
        .build());
    
    CallResult callResult = result.results().get(0);
    if (callResult instanceof CallResult.Success success) {
        System.out.println("Gas used: " + success.gasUsed());
    } else if (callResult instanceof CallResult.Failure failure) {
        System.out.println("Would revert: " + failure.errorMessage());
    }
}
```

### Contract Interaction with State Override

```java
// Simulate as if we had 1000 ETH
SimulateResult result = client.simulateCalls(SimulateRequest.builder()
    .account(myAddress)
    .call(SimulateCall.builder()
        .to(wethAddress)
        .abi(wethAbi)
        .function("deposit")
        .value(Wei.fromEther("100"))
        .build())
    .stateOverride(myAddress, AccountOverride.builder()
        .balance(Wei.fromEther("1000"))
        .build())
    .traceAssetChanges(true)
    .fetchTokenMetadata(true)  // Fetch decimals/symbol for better UX
    .build());

// Check asset changes
if (result.assetChanges() != null) {
    for (AssetChange change : result.assetChanges()) {
        String symbol = change.token().symbol() != null 
            ? change.token().symbol() 
            : change.token().address().value();  // Fallback to address if metadata not fetched
        System.out.printf("%s: %s -> %s (diff: %s)%n",
            symbol,
            change.value().pre(),
            change.value().post(),
            change.value().diff());
    }
}
```

### Multi-Call Sequence

```java
// Approve and transfer in sequence
SimulateResult result = client.simulateCalls(SimulateRequest.builder()
    .account(myAddress)
    .call(SimulateCall.builder()
        .to(tokenAddress)
        .abi(erc20Abi)
        .function("approve")
        .args(spenderAddress, BigInteger.valueOf(1000))
        .build())
    .call(SimulateCall.builder()
        .to(spenderAddress)
        .abi(spenderAbi)
        .function("transferFrom")
        .args(myAddress, recipientAddress, BigInteger.valueOf(500))
        .build())
    .build());

// All calls execute in sequence, second call sees state from first
for (int i = 0; i < result.results().size(); i++) {
    System.out.printf("Call %d: %s%n", i, 
        result.results().get(i) instanceof CallResult.Success ? "✓" : "✗");
}
```

---

## Implementation Checklist

### Phase 1: Core Implementation

- [ ] Add `SimulateCall` record with builder
- [ ] Add `SimulateRequest` record with builder
- [ ] Add `AccountOverride` record with builder
- [ ] Add `CallResult` sealed interface with Success/Failure records
- [ ] Add `SimulateResult` record
- [ ] Add `SimulateNotSupportedException` class
- [ ] Add `simulateCalls(SimulateRequest)` to `PublicClient` interface
- [ ] Implement in `DefaultPublicClient`
- [ ] Implement in `BranePublicClient` (delegate + ensureOpen)
- [ ] Add JSON serialization/deserialization logic in `RpcUtils`

### Phase 2: Testing

- [ ] Unit tests for request serialization
- [ ] Unit tests for response parsing
- [ ] Unit tests for error handling (-32601)
- [ ] Integration tests against Anvil (requires Anvil version with eth_simulateV1)
- [ ] Add to smoke tests

### Phase 3: Documentation

- [ ] Javadoc for all public classes
- [ ] Add example in `brane-examples`
- [ ] Update docs/public-client/api.mdx

---

## Future Improvements (Out of Scope)

The following enhancements are valuable but not included in the initial implementation:

### 1. Automatic Fallback Mechanism

If `eth_simulateV1` is not supported (returns `-32601`), the library could automatically fall back to:

- **Multiple `eth_call` requests**: Execute each call individually against the latest block
- **Multicall3 aggregation**: Batch calls using the existing `MulticallBatch` infrastructure
- **Legacy Multicall (MakerDAO)**: For chains without Multicall3

**Tradeoffs:**
- Fallback methods cannot simulate state changes between calls
- No state override support
- Performance degradation (multiple round-trips)

When a fallback is used, log a warning:

```java
log.warn("eth_simulateV1 not supported by RPC node, falling back to multiple eth_call requests. " +
         "Performance will be degraded and state changes between calls will not be reflected.");
```

### 2. Result Decoding Helpers

Add convenience methods to decode return values using ABI:

```java
CallResult.Success success = (CallResult.Success) result.results().get(0);
BigInteger balance = success.decode(erc20Abi, "balanceOf", BigInteger.class);
```

### 3. Transfer Tracing

When `traceTransfers=true`, include a list of all ETH and ERC-20 transfers:

```java
List<Transfer> transfers = result.transfers();
```

### 4. Block State Calls (Advanced)

Support multiple block state call groups for more complex simulations:

```java
SimulateRequest.builder()
    .blockStateCalls(List.of(
        BlockStateCall.builder()
            .stateOverrides(...)
            .calls(...)
            .build(),
        BlockStateCall.builder()
            .blockOverrides(...)  // timestamp, baseFee, etc.
            .calls(...)
            .build()
    ))
    .build();
```

---

## References

- [Viem simulateCalls Documentation](https://viem.sh/docs/actions/public/simulateCalls)
- [Alloy Provider::simulate](https://docs.rs/alloy/latest/alloy/providers/trait.Provider.html#method.simulate)
- [Ethereum Execution APIs](https://ethereum.github.io/execution-apis/api-documentation/)
- [ethereum/execution-apis PR #484](https://github.com/ethereum/execution-apis/pull/484) - Original eth_simulateV1 specification
- [JSON-RPC 2.0 Specification - Error Codes](https://www.jsonrpc.org/specification#error_object)

