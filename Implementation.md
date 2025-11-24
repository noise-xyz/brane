# Phase 3.0: Configurable Gas Estimation (P0.13)

## Goal

Make Brane's **SmartGasStrategy** more transparent and configurable by:

* **Documenting** the existing intelligent gas estimation behavior
* **Extracting magic numbers** into named constants for clarity
* **Exposing a configuration path** for the gas limit buffer multiplier
* **(Optional)** Adding targeted retry logic for gas underestimation errors

This provides users with clear visibility into gas estimation while maintaining the "just works" default experience.

---

## Current State

From `SmartGasStrategy.java` (Phase 2.8 implementation):

**Gas Limit** (line 40-55):
```java
private TransactionRequest ensureGasLimit(final TransactionRequest request) {
    if (request.gasLimit() != null) {
        return request; // ✅ Respects user value
    }
    // Calls eth_estimateGas
    final BigInteger estimate = Numeric.decodeQuantity(estimateHex);
    final BigInteger buffered = estimate.multiply(BigInteger.valueOf(120))
                                       .divide(BigInteger.valueOf(100)); // Hardcoded 1.2x
    return copyWithGasFields(request, buffered.longValueExact(), ...);
}
```

**EIP-1559 Fees** (line 74-98):
```java
private TransactionRequest ensureEip1559Fees(final TransactionRequest request) {
    if (request.maxFeePerGas() != null && request.maxPriorityFeePerGas() != null) {
        return request; // ✅ Respects user values
    }
    // Derives fees from baseFeePerGas * 2 + priorityFee
    final Wei maxFee = new Wei(baseFee.value().multiply(BigInteger.valueOf(2))
                                      .add(priority.value())); // Hardcoded 2x
    ...
}
```

**What's Good:**
- ✅ Only fills `null` fields (never overwrites user values)
- ✅ Uses `RpcRetry` for `eth_estimateGas` and `eth_gasPrice`
- ✅ Falls back to legacy fees when `baseFeePerGas` is unavailable

**What Needs Improvement:**
- ❌ Magic numbers: `120/100` for gas limit buffer, `BigInteger.valueOf(2)` for base fee multiplier
- ❌ No way to configure the buffer multiplier
- ❌ No Javadoc explaining the heuristics

---

## Implementation Details

### 1. Extract Magic Numbers into Named Constants

**Current** (line 53):
```java
final BigInteger buffered = estimate.multiply(BigInteger.valueOf(120))
                                   .divide(BigInteger.valueOf(100));
```

**New**:
```java
// At class level
private static final BigInteger DEFAULT_GAS_LIMIT_BUFFER_NUMERATOR = BigInteger.valueOf(120);
private static final BigInteger DEFAULT_GAS_LIMIT_BUFFER_DENOMINATOR = BigInteger.valueOf(100);

// In method - use instance fields, not constants directly
final BigInteger buffered = estimate.multiply(gasLimitBufferNumerator)
                                   .divide(gasLimitBufferDenominator);
```

**Similarly** for base fee multiplier (line 89):
```java
private static final BigInteger BASE_FEE_MULTIPLIER = BigInteger.valueOf(2);

// In method
final Wei maxFee = new Wei(baseFee.value().multiply(BASE_FEE_MULTIPLIER)
                                  .add(priority.value()));
```

**Benefits:**
- Makes the 20% buffer and 2x multiplier explicit
- Easier to understand at a glance
- Sets up for future configurability

---

### 2. Add Configurable Gas Limit Buffer

Add a field and overloaded constructor:

```java
final class SmartGasStrategy {

    private static final BigInteger DEFAULT_GAS_LIMIT_BUFFER_NUMERATOR = BigInteger.valueOf(120);
    private static final BigInteger DEFAULT_GAS_LIMIT_BUFFER_DENOMINATOR = BigInteger.valueOf(100);
    
    private final PublicClient publicClient;
    private final BraneProvider provider;
    private final ChainProfile profile;
    private final BigInteger gasLimitBufferNumerator;
    private final BigInteger gasLimitBufferDenominator;

    // Existing constructor (default buffer)
    SmartGasStrategy(PublicClient publicClient, BraneProvider provider, ChainProfile profile) {
        this(publicClient, provider, profile, 
             DEFAULT_GAS_LIMIT_BUFFER_NUMERATOR, DEFAULT_GAS_LIMIT_BUFFER_DENOMINATOR);
    }

    // New constructor (custom buffer)
    SmartGasStrategy(PublicClient publicClient, BraneProvider provider, ChainProfile profile,
                     BigInteger gasLimitBufferNumerator, BigInteger gasLimitBufferDenominator) {
        this.publicClient = Objects.requireNonNull(publicClient);
        this.provider = Objects.requireNonNull(provider);
        this.profile = Objects.requireNonNull(profile);
        this.gasLimitBufferNumerator = gasLimitBufferNumerator;
        this.gasLimitBufferDenominator = gasLimitBufferDenominator;
    }
}
```

**Usage in `ensureGasLimit`**:
```java
final BigInteger buffered = estimate.multiply(gasLimitBufferNumerator)
                                   .divide(gasLimitBufferDenominator);
```

**Migration:**
- All existing code uses the 3-arg constructor (default buffer)
- Advanced users can use the 5-arg constructor to customize
- Future: Could expose via `WalletClientBuilder` or similar

---

### 3. Add Comprehensive Javadoc

Add class-level and method-level documentation:

```java
/**
 * Automatically fills missing gas fields in transaction requests using intelligent defaults.
 *
 * <h3>Gas Limit</h3>
 * If {@code gasLimit} is null:
 * <ul>
 *   <li>Calls {@code eth_estimateGas} to get the estimated gas usage</li>
 *   <li>Applies a safety buffer (default: 20% → estimate * 120/100)</li>
 *   <li>Returns the buffered value as {@code gasLimit}</li>
 * </ul>
 * If {@code gasLimit} is already set, it is never modified.
 *
 * <h3>EIP-1559 Fees</h3>
 * For EIP-1559 chains, if {@code maxFeePerGas} or {@code maxPriorityFeePerGas} are null:
 * <ul>
 *   <li>Fetches the latest {@code baseFeePerGas} from the chain</li>
 *   <li>Derives {@code maxFeePerGas = baseFeePerGas * 2 + priorityFee}</li>
 *   <li>Uses {@code ChainProfile.defaultPriorityFeePerGas} when the user has not provided {@code maxPriorityFeePerGas}</li>
 * </ul>
 * Explicit user values are never overwritten.
 *
 * <h3>Legacy Fees</h3>
 * For non-EIP-1559 chains or when {@code baseFeePerGas} is unavailable:
 * <ul>
 *   <li>Calls {@code eth_gasPrice} if {@code gasPrice} is null</li>
 *   <li>Returns the network's suggested gas price</li>
 * </ul>
 *
 * @see RpcRetry for retry behavior on transient errors
 */
final class SmartGasStrategy {
    // ...
}
```

---

### 4. Optional: Gas Underestimation Retry

**Current behavior**: If `eth_estimateGas` fails, `RpcRetry` retries transient network errors but not gas estimation errors.

**Proposed**: Add a targeted retry for clear underestimation errors in `ensureGasLimit`:

If `eth_estimateGas` fails with a clearly underestimation-style error (e.g., message contains "intrinsic gas too low", "gas too low", "out of gas"), we can optionally:

* Log `[ESTIMATE-GAS-RETRY] reason=... oldBuffer=120/100 newBuffer=150/100`
* Retry once with a higher buffer (e.g., 150/100) when computing `gasLimit` from the estimate
* Never retry on reverts, insufficient funds, or nonce errors

**High-level approach**:
```java
private TransactionRequest ensureGasLimit(final TransactionRequest request) {
    if (request.gasLimit() != null) {
        return request;
    }
    
    try {
        // First attempt with default buffer
        final String estimateHex = RpcRetry.run(() -> callEstimateGas(tx), 3);
        final BigInteger estimate = Numeric.decodeQuantity(estimateHex);
        final BigInteger buffered = estimate.multiply(gasLimitBufferNumerator)
                                           .divide(gasLimitBufferDenominator);
        return copyWithGasFields(request, buffered.longValueExact(), ...);
    } catch (RpcException e) {
        if (isGasUnderestimationError(e)) {
            DebugLogger.log("[ESTIMATE-GAS-RETRY] reason=%s oldBuffer=%s/%s newBuffer=150/100"
                .formatted(e.getMessage(), gasLimitBufferNumerator, gasLimitBufferDenominator));
            
            // Retry with higher buffer (150/100 = 50%)
            final String estimateHex = RpcRetry.run(() -> callEstimateGas(tx), 3);
            final BigInteger estimate = Numeric.decodeQuantity(estimateHex);
            final BigInteger buffered = estimate.multiply(BigInteger.valueOf(150))
                                               .divide(BigInteger.valueOf(100));
            return copyWithGasFields(request, buffered.longValueExact(), ...);
        }
        throw e; // Non-underestimation errors propagate immediately
    }
}

private boolean isGasUnderestimationError(RpcException e) {
    final String msg = e.getMessage().toLowerCase();
    return msg.contains("intrinsic gas too low") 
        || msg.contains("out of gas")
        || msg.contains("gas too low");
}
```

**Note**: The buffer is applied **client-side** after receiving the estimate from `eth_estimateGas`. We never "pass a buffer" to the RPC call itself. This is **optional** and can be added in a follow-up if needed.

---

## Steps

1. [ ] Extract magic numbers into named constants:
   - `DEFAULT_GAS_LIMIT_BUFFER_NUMERATOR = 120`
   - `DEFAULT_GAS_LIMIT_BUFFER_DENOMINATOR = 100`
   - `BASE_FEE_MULTIPLIER = 2`

2. [ ] Add configurable buffer fields and 5-arg constructor to `SmartGasStrategy`

3. [ ] Update `ensureGasLimit` to use the configurable buffer fields

4. [ ] Add comprehensive Javadoc to `SmartGasStrategy` class and key methods

5. [ ] (Optional) Implement targeted retry for gas underestimation errors

6. [ ] Update `DefaultWalletClient` if needed to expose configuration

7. [ ] Update existing tests to use the new constants

---

## Testing

### A. Unit Tests (SmartGasStrategy)

**Extend**: `brane-rpc/src/test/java/io/brane/rpc/SmartGasStrategyTest.java`

1. **Default buffer constant verification**
   - Verify the default buffer results in `estimate * 120 / 100` (20% buffer)
   - Given `estimate = 100_000`, assert resulting `gasLimit = 120_000`
   - Ensure `BASE_FEE_MULTIPLIER` equals `2`

2. **Custom buffer multiplier**
   - Create `SmartGasStrategy` with custom buffer (e.g., 150/100 for 50%)
   - Mock `eth_estimateGas` to return `100_000`
   - Assert resulting `gasLimit` equals `150_000` (not `120_000`)

3. **Explicit gasLimit bypasses estimation**
   - Create `TransactionRequest` with explicit `gasLimit = 90_000`
   - Assert `eth_estimateGas` is never called
   - Assert `gasLimit` remains `90_000`

4. **Optional: underestimation retry**
   - Mock first `eth_estimateGas` to throw "intrinsic gas too low"
   - Mock second call to succeed
   - Assert `eth_estimateGas` called twice
   - Assert `[ESTIMATE-GAS-RETRY]` log exists
   - **Non-underestimation errors**: mock error with "insufficient funds for gas * price + value"
     - Assert **no retry** occurs and error is propagated immediately

---

### B. Integration Tests

**Extend**: `brane-rpc/src/test/java/io/brane_rpc/DefaultWalletClientTest.java`

1. **End-to-end with custom buffer**
   - Create `DefaultWalletClient` with custom `SmartGasStrategy` (or similar fake provider)
   - Send transaction without `gasLimit`
   - Verify final `gasLimit` uses custom buffer

2. **Existing tests still pass**
   - Run all existing `SmartGasStrategyTest` tests
   - Verify they pass with the new constant-based implementation

---

### C. Documentation / Examples

1. **Update README** with gas estimation configuration:
   ```java
   // Default 20% buffer
   WalletClient wallet = DefaultWalletClient.create(provider, publicClient, signer, address);
   
   // Custom 50% buffer (if/when exposed via builder/config)
   SmartGasStrategy strategy = new SmartGasStrategy(
       publicClient, provider, profile,
       BigInteger.valueOf(150), BigInteger.valueOf(100)
   );
   // Wire this into a custom WalletClient factory, or keep as internal wiring for now.
   ```

2. **Add example to `brane-examples`**:
   - `GasEstimationDemo.java` that shows default vs custom buffers
   - Prints estimated vs actual gas used

---

## Out of Scope (Future Work)

- Configurable base fee multiplier (currently hardcoded to `2x`)
- Dynamic buffer adjustment based on contract complexity
- `GasStrategy` interface abstraction (not needed yet)
- Per-transaction buffer override (can be added later via builder)

---

## Files to Modify

- `brane-rpc/src/main/java/io/brane/rpc/SmartGasStrategy.java` - Add constants, fields, constructor, Javadoc
- `brane-rpc/src/test/java/io/brane/rpc/SmartGasStrategyTest.java` - Add tests for custom buffer
- (Optional) `brane-rpc/src/main/java/io/brane/rpc/DefaultWalletClient.java` - Expose configuration
- `README.md` - Document gas estimation behavior