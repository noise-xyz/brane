
# Phase 2.8: Smart Gas Defaults + Retry (P0.12)

## Goal

Provide a “just works” path for transaction sending by:

* Automatically filling **reasonable gas defaults** when the user doesn’t specify them.
* Applying a **small, controlled retry policy** for transient RPC/network issues (but never on reverts or user errors).

This should make Brane feel reliable and “smart” out of the box.

## Motivation

* **DX**: Most users don’t want to think about gas details on every tx.
* **Correctness**: Overwriting explicit gas fields is a bug; filling missing fields is a feature.
* **Resilience**: Some RPC errors are transient and benefit from simple retries; others must fail fast.

---

## Implementation Details

### 1. Placement

* Smart gas defaults → `brane-rpc`, inside `DefaultWalletClient` (or a small helper it uses).
* Retry logic → `brane-rpc`, shared between `PublicClient` and `DefaultWalletClient`.

---

### 2. Core Model Updates

#### 2.1. Chain Profile Extensions (`ChainProfile`)

Extend to include EIP-1559 support and default priority fee:

```java
public record ChainProfile(
    long chainId,
    String defaultRpcUrl,
    boolean supportsEip1559,
    Wei defaultPriorityFeePerGas
) {
    // ...
}
```

Notes:

* For EIP-1559 chains, `defaultPriorityFeePerGas` SHOULD be non-null. If it is null, SmartGasStrategy may either:

  * Throw a configuration exception, or
  * Fall back to a small hardcoded default (e.g. 1 gwei).

#### 2.2. Block Header Extensions (`BlockHeader`)

Add `baseFeePerGas` to support EIP-1559 calculations:

```java
public record BlockHeader(
    Hash hash,
    Long number,
    Hash parentHash,
    Long timestamp,
    Wei baseFeePerGas // [NEW]
) {}
```

* Update `DefaultPublicClient.getBlockByTag` / `getLatestBlock` to parse `baseFeePerGas` from RPC responses when present.

---

### 3. Smart Gas Strategy

Implement a helper to fill **only missing** gas fields:

```java
final class SmartGasStrategy {

    private final PublicClient publicClient;
    private final ChainProfile profile;

    SmartGasStrategy(PublicClient publicClient, ChainProfile profile) {
        this.publicClient = publicClient;
        this.profile = profile;
    }

    TransactionRequest applyDefaults(TransactionRequest tx) {
        // 1) Never overwrite user-provided gas fields.
        // 2) Fill gasLimit if null.
        // 3) Fill fee fields based on chain/profile and latest block.
        TransactionRequest withLimit = ensureGasLimit(tx);
        return ensureFees(withLimit);
    }

    // ...
}
```

**Design rule:**

* SmartGasStrategy MUST:

  * Only fill fields that are `null` on `TransactionRequest`.
  * Never overwrite user-provided values from `TxBuilder` (`gasLimit`, `gasPrice`, `maxFeePerGas`, `maxPriorityFeePerGas`).

#### 3.1. Gas Limit (`eth_estimateGas`)

If `tx.gasLimit() == null`:

1. Call `eth_estimateGas` (via `PublicClient`) with a tx object containing all user fields (from, to, value, data, etc.) but without any gas fields.
2. Let `estimated = estimateGas(...)`.
3. Set `gasLimit = estimated * 120 / 100` (20% buffer).
4. Return a new `TransactionRequest` with this `gasLimit`.

If `gasLimit` is already set → do not change it.

#### 3.2. EIP-1559 Fees

If `profile.supportsEip1559()` is `true`:

1. If **both** `maxFeePerGas` and `maxPriorityFeePerGas` are non-null:

   * Respect user values, don’t fetch anything.
2. Else:

   * Fetch latest block via `PublicClient.getLatestBlock()`.
   * Extract `baseFeePerGas`.
   * If `baseFeePerGas` is `null`, treat chain as legacy and **fall back to the gasPrice path in 3.3**.
   * Determine `priority`:

     * If `tx.maxPriorityFeePerGas()` is non-null, use that.
     * Else use `profile.defaultPriorityFeePerGas()`.
   * Compute:

     ```java
     Wei maxFee = baseFeePerGas.multiply(BigInteger.valueOf(2)).add(priority);
     ```
   * Fill missing fields:

     * If `maxPriorityFeePerGas` is null → set to `priority`.
     * If `maxFeePerGas` is null → set to `maxFee`.

SmartGasStrategy must not set `gasPrice` on EIP-1559 paths.

#### 3.3. Legacy Fees (`gasPrice`)

If `profile.supportsEip1559() == false` **or** `baseFeePerGas` was `null`:

1. If `tx.gasPrice()` is non-null → respect that value.
2. Else:

   * Call `eth_gasPrice` via `PublicClient`.
   * Set `gasPrice` to the returned value.

SmartGasStrategy must never set both `gasPrice` **and** EIP-1559 fee fields at the same time.

---

### 4. WalletClient Integration

In `DefaultWalletClient`:

* Inject or construct `SmartGasStrategy` using the active `PublicClient` and `ChainProfile`.

* Before signing/sending a transaction:

  ```java
  TransactionRequest withDefaults = smartGasStrategy.applyDefaults(request);
  // proceed with withDefaults (signing, sending, receipt polling, etc.)
  ```

* Ensure explicit user-provided gas fields from `TxBuilder` are never overwritten.

---

### 5. Retry Policy (`RpcRetry`)

Introduce `RpcRetry` helper:

```java
final class RpcRetry {

    static <T> T callWithRetry(Supplier<T> rpcCall, int maxAttempts, Duration baseDelay) {
        int attempt = 0;
        while (true) {
            try {
                return rpcCall.get();
            } catch (RpcException e) {
                attempt++;
                if (!isRetryableRpcError(e) || attempt >= maxAttempts) {
                    throw e;
                }
                sleep(backoffMillis(attempt, baseDelay));
            } catch (IOException | RuntimeException e) {
                attempt++;
                if (!isRetryableNetworkError(e) || attempt >= maxAttempts) {
                    throw e;
                }
                sleep(backoffMillis(attempt, baseDelay));
            }
        }
    }

    // backoffMillis, isRetryableRpcError, isRetryableNetworkError, sleep...
}
```

#### 5.1. Classification

**Retryable:**

* `IOException`-like network issues:

  * Connection reset
  * Timeouts
  * Temporary DNS/connection failures
* Certain `RpcException` cases with clearly transient messages, e.g.:

  * Message contains `"header not found"`.
  * Other node/transient conditions if explicitly allowlisted.

**Important:** do **not** treat all `-32000` server errors as retryable; only those whose messages match a small, explicit allowlist of transient conditions.

**Non-Retryable:**

* Reverts (error data present, used by `RevertDecoder` / `RevertException`).
* User errors such as:

  * `"insufficient funds for gas * price + value"`.
  * `"nonce too low"` / `"nonce too high"`.
* Invalid params / JSON-RPC `-32602` and similar.

#### 5.2. Usage

* **PublicClient**:

  * `eth_call`
  * `eth_estimateGas`
  * `getLatestBlock` / `eth_getBlockByNumber`
  * `eth_gasPrice`
* **DefaultWalletClient**:

  * `eth_sendRawTransaction` / `eth_sendTransaction`
  * `eth_getTransactionCount` (nonce fetching, if desired)
  * (Optionally) receipt polling via `eth_getTransactionReceipt`

Use conservative defaults, e.g.:

* `maxAttempts = 3`
* `baseDelay = 200ms` with a simple linear or exponential backoff.

---

## Steps

1. [ ] Update `ChainProfile` (add `supportsEip1559` and `defaultPriorityFeePerGas`).
2. [ ] Update `BlockHeader` (add `baseFeePerGas`) and `DefaultPublicClient` (parse it).
3. [ ] Implement `SmartGasStrategy` in `brane-rpc`:

   * Fill `gasLimit` via `eth_estimateGas` + 20% margin.
   * For EIP-1559: use `baseFee * 2 + priority` heuristic.
   * For legacy: use `eth_gasPrice`.
   * Never overwrite user-specified gas fields.
4. [ ] Wire `SmartGasStrategy` into `DefaultWalletClient` before signing/sending.
5. [ ] Implement `RpcRetry` helper with:

   * Retryable vs non-retryable classification.
   * Bounded retry loop with backoff.
6. [ ] Use `RpcRetry` in `PublicClient` and `DefaultWalletClient` RPC paths.
7. [ ] Ensure explicit user-provided gas fields are never overwritten (add tests).

---

## Testing

### A. Smart Gas Defaults (Unit)

Add tests (either for `SmartGasStrategy` directly or via `DefaultWalletClientTest` with mocks):

1. **EIP-1559, all gas fields null**

   * `ChainProfile`: `supportsEip1559 = true`, `defaultPriorityFeePerGas = 2 gwei`.
   * Mock `eth_getBlockByNumber` / `getLatestBlock` → `baseFeePerGas = 20 gwei`.
   * Mock `eth_estimateGas` → `100_000`.
   * After `applyDefaults`:

     * `gasLimit > 100_000` (e.g., 120_000).
     * `maxPriorityFeePerGas = 2 gwei`.
     * `maxFeePerGas = 42 gwei` (2 * 20 + 2).

2. **EIP-1559, user-provided priority**

   * Same setup, but `maxPriorityFeePerGas = 5 gwei`, `maxFeePerGas == null`.
   * After defaults:

     * `maxPriorityFeePerGas = 5 gwei` (unchanged).
     * `maxFeePerGas = baseFee * 2 + 5 gwei`.

3. **EIP-1559, fully user-specified fees**

   * Both `maxFeePerGas` and `maxPriorityFeePerGas` non-null.
   * Ensure no call to `getLatestBlock` / `eth_getBlockByNumber` and values unchanged.

4. **Legacy chain, no gasPrice**

   * `ChainProfile`: `supportsEip1559 = false`.
   * Mock `eth_gasPrice = 30 gwei`.
   * tx: `gasPrice == null`.
   * After defaults: `gasPrice = 30 gwei`.

5. **Legacy chain, user gasPrice**

   * tx: `gasPrice = 40 gwei`.
   * Ensure no `eth_gasPrice` call and `gasPrice` unchanged.

6. **GasLimit via estimateGas**

   * Mock `eth_estimateGas = 80_000`.
   * tx: `gasLimit == null`.
   * After defaults: `gasLimit > 80_000`.

7. **EIP-1559 profile but baseFee missing**

   * `supportsEip1559 = true`, but `baseFeePerGas = null` from latest block.
   * SmartGasStrategy should fall back to `eth_gasPrice` path and set `gasPrice` if missing.

---

### B. Retry Policy (Unit)

Add tests for `RpcRetry`:

1. **Retry on “header not found”**

   * First call: throw `RpcException("header not found")`.
   * Second call: return `"ok"`.
   * `maxAttempts = 3`.
   * Assert:

     * Result = `"ok"`.
     * Supplier called exactly 2 times.

2. **Retry on transient network error**

   * First call: throw `IOException("connection reset")`.
   * Second call: success.
   * Assert 2 invocations, success returned.

3. **No retry on revert error**

   * First call: `RpcException` that clearly represents an EVM revert (e.g., has `data` with revert payload).
   * `isRetryableRpcError` → false.
   * Assert:

     * Exception thrown on first attempt.
     * Supplier called exactly once.

4. **No retry on user error**

   * First call: `RpcException("insufficient funds for gas * price + value")`.
   * Assert no retry, only one invocation.

5. **Exhaust retries**

   * All attempts throw a retryable error (e.g. `IOException` or `"header not found"`).
   * With `maxAttempts = 3`, assert:

     * Supplier called 3 times.
     * Exception propagated on final attempt.

---

### C. WalletClient Integration

In `DefaultWalletClientTest`:

1. **End-to-end smart defaults**

   * Build tx with missing gas fields.
   * Mock RPC:

     * `eth_estimateGas`, `eth_getBlockByNumber` / `getLatestBlock`, `eth_gasPrice`.
   * Send via `WalletClient`.
   * Assert that the final `eth_sendRawTransaction` uses:

     * A non-null `gasLimit`.
     * Correct `gasPrice` or EIP-1559 `maxFeePerGas` / `maxPriorityFeePerGas` per heuristic.
   * Assert that explicit user-specified gas fields remain unchanged.

2. **Retry on send**

   * Fake provider:

     * First `eth_sendRawTransaction` call → `RpcException("header not found")`.
     * Second call → success.
   * Assert:

     * 2 calls made.
     * No unhandled error.

3. **No retry on revert**

   * Fake provider returns revert error with data on first send.
   * Assert:

     * `RevertException` thrown.
     * Send called exactly once.