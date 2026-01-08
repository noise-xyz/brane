package io.brane.rpc;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.brane.core.chain.ChainProfile;
import io.brane.core.error.RpcException;
import io.brane.core.model.AccessListWithGas;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.LogEntry;
import io.brane.core.model.Transaction;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.internal.RpcUtils;

/**
 * Default implementation of {@link Brane.Tester} for test node operations.
 *
 * <p>This implementation provides access to all read operations via delegation to
 * an internal {@link DefaultSigner}, plus test-specific methods like snapshots,
 * impersonation, and time manipulation.
 *
 * <p>Supports Anvil, Hardhat, and Ganache test nodes through {@link TestNodeMode}.
 *
 * <h2>Implementation Notes</h2>
 *
 * <h3>Delegation Pattern</h3>
 * <p>This class uses composition-based delegation rather than inheritance. It wraps a
 * {@link DefaultSigner} instance internally and delegates all {@link Brane.Reader} and
 * {@link Brane.Signer} operations to it. This approach:
 * <ul>
 *   <li>Avoids diamond inheritance issues (Tester extends both Reader and Signer)</li>
 *   <li>Allows reuse of proven Reader/Signer implementations</li>
 *   <li>Keeps test-specific logic isolated in this class</li>
 * </ul>
 *
 * <h3>RPC Naming Convention</h3>
 * <p>Test node RPC methods follow different naming conventions across implementations:
 * <ul>
 *   <li><b>Anvil:</b> Uses {@code anvil_*} prefix (e.g., {@code anvil_mine}, {@code anvil_setBalance}),
 *       except for EVM methods which use {@code evm_*} (e.g., {@code evm_snapshot}, {@code evm_setAutomine})</li>
 *   <li><b>Hardhat:</b> Uses {@code hardhat_*} prefix (e.g., {@code hardhat_mine})</li>
 *   <li><b>Ganache:</b> Uses {@code evm_*} prefix (e.g., {@code evm_mine})</li>
 * </ul>
 * <p>The {@link TestNodeMode#prefix()} method returns the appropriate prefix for each mode.
 * Some methods (like {@code snapshot}, {@code setAutomine}) require special handling because
 * Anvil uses {@code evm_*} while other nodes use their standard prefix.
 *
 * <h3>Helper Methods</h3>
 * <p>Three internal helper methods reduce boilerplate for RPC calls:
 * <ul>
 *   <li>{@link #sendWithRetry(String, List)} - Core retry wrapper with exponential backoff</li>
 *   <li>{@link #sendVoid(String, List)} - For methods that don't return meaningful results</li>
 *   <li>{@link #sendBoolResult(String, List)} - For methods returning success/failure boolean</li>
 * </ul>
 *
 * @since 0.3.0
 */
final class DefaultTester implements Brane.Tester {

    private final DefaultSigner signer;
    private final BraneProvider provider;
    private final TestNodeMode mode;
    private final int maxRetries;
    private final RpcRetryConfig retryConfig;

    /**
     * Creates a new DefaultTester with the specified configuration.
     *
     * @param provider    the RPC provider for blockchain communication
     * @param signer      the signer for transaction signing
     * @param chain       the chain profile for network-specific settings (may be null)
     * @param maxRetries  the maximum number of retry attempts for transient failures
     * @param retryConfig the retry configuration for backoff timing
     * @param mode        the test node mode (Anvil, Hardhat, Ganache)
     */
    DefaultTester(
            final BraneProvider provider,
            final io.brane.core.crypto.Signer signer,
            final @Nullable ChainProfile chain,
            final int maxRetries,
            final RpcRetryConfig retryConfig,
            final TestNodeMode mode) {
        this.signer = new DefaultSigner(provider, signer, chain, maxRetries, retryConfig);
        this.provider = provider;
        this.mode = mode;
        this.maxRetries = maxRetries;
        this.retryConfig = retryConfig;
    }

    // ==================== Brane.Reader Interface Delegation ====================
    // All read operations are delegated to the internal DefaultSigner, which
    // itself delegates to DefaultReader. This ensures consistent behavior across
    // all client types (Reader, Signer, Tester).

    @Override
    public BigInteger chainId() {
        return signer.chainId();
    }

    @Override
    public BigInteger getBalance(final Address address) {
        return signer.getBalance(address);
    }

    @Override
    public @Nullable BlockHeader getLatestBlock() {
        return signer.getLatestBlock();
    }

    @Override
    public @Nullable BlockHeader getBlockByNumber(final long blockNumber) {
        return signer.getBlockByNumber(blockNumber);
    }

    @Override
    public @Nullable Transaction getTransactionByHash(final Hash hash) {
        return signer.getTransactionByHash(hash);
    }

    @Override
    public @Nullable TransactionReceipt getTransactionReceipt(final Hash hash) {
        return signer.getTransactionReceipt(hash);
    }

    @Override
    public HexData call(final CallRequest request) {
        return signer.call(request);
    }

    @Override
    public HexData call(final CallRequest request, final BlockTag blockTag) {
        return signer.call(request, blockTag);
    }

    @Override
    public List<LogEntry> getLogs(final LogFilter filter) {
        return signer.getLogs(filter);
    }

    @Override
    public BigInteger estimateGas(final TransactionRequest request) {
        return signer.estimateGas(request);
    }

    @Override
    public AccessListWithGas createAccessList(final TransactionRequest request) {
        return signer.createAccessList(request);
    }

    @Override
    public SimulateResult simulate(final SimulateRequest request) {
        return signer.simulate(request);
    }

    @Override
    public MulticallBatch batch() {
        return signer.batch();
    }

    @Override
    public Subscription onNewHeads(final Consumer<BlockHeader> callback) {
        return signer.onNewHeads(callback);
    }

    @Override
    public Subscription onLogs(final LogFilter filter, final Consumer<LogEntry> callback) {
        return signer.onLogs(filter, callback);
    }

    @Override
    public Optional<ChainProfile> chain() {
        return signer.chain();
    }

    @Override
    public boolean canSubscribe() {
        return signer.canSubscribe();
    }

    @Override
    public void close() {
        signer.close();
    }

    // ==================== Tester Interface Implementation ====================
    // Tester-specific methods that are not part of Reader or Signer interfaces.

    @Override
    public Brane.Signer asSigner() {
        return signer;
    }

    // ==================== Brane.Signer Method Delegation ====================
    // Transaction signing operations delegated to internal DefaultSigner.

    @Override
    public Hash sendTransaction(final TransactionRequest request) {
        return signer.sendTransaction(request);
    }

    @Override
    public TransactionReceipt sendTransactionAndWait(
            final TransactionRequest request,
            final long timeoutMillis,
            final long pollIntervalMillis) {
        return signer.sendTransactionAndWait(request, timeoutMillis, pollIntervalMillis);
    }

    @Override
    public io.brane.core.crypto.Signer signer() {
        return signer.signer();
    }

    // ==================== Snapshot Methods ====================
    // State snapshot and revert capabilities for test isolation.
    // Note: Anvil uses evm_snapshot/evm_revert, others use their prefix.

    @Override
    public SnapshotId snapshot() {
        final String method = mode == TestNodeMode.ANVIL ? "evm_snapshot" : mode.prefix() + "snapshot";
        final JsonRpcResponse response = sendWithRetry(method, List.of());
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
        final Object result = response.result();
        if (result == null) {
            throw new RpcException(-32000, method + " returned null", (String) null, (Throwable) null);
        }
        return SnapshotId.from(result.toString());
    }

    @Override
    public boolean revert(final SnapshotId snapshotId) {
        final String method = mode == TestNodeMode.ANVIL ? "evm_revert" : mode.prefix() + "revert";
        return sendBoolResult(method, List.of(snapshotId.value()));
    }

    @Override
    public ImpersonationSession impersonate(final Address address) {
        final String method = mode.prefix() + "impersonateAccount";
        final JsonRpcResponse response = sendWithRetry(method, List.of(address.value()));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
        return new DefaultImpersonationSession(this, address);
    }

    @Override
    public void stopImpersonating(final Address address) {
        sendVoid(mode.prefix() + "stopImpersonatingAccount", List.of(address.value()));
    }

    @Override
    public void enableAutoImpersonate() {
        if (mode != TestNodeMode.ANVIL) {
            throw new UnsupportedOperationException("Auto-impersonate is only supported by Anvil");
        }
        sendVoid("anvil_autoImpersonateAccount", List.of(true));
    }

    @Override
    public void disableAutoImpersonate() {
        if (mode != TestNodeMode.ANVIL) {
            throw new UnsupportedOperationException("Auto-impersonate is only supported by Anvil");
        }
        sendVoid("anvil_autoImpersonateAccount", List.of(false));
    }

    @Override
    public void setBalance(final Address address, final Wei balance) {
        java.util.Objects.requireNonNull(address, "address must not be null");
        java.util.Objects.requireNonNull(balance, "balance must not be null");
        final String balanceHex = "0x" + balance.value().toString(16);
        sendVoid(mode.prefix() + "setBalance", List.of(address.value(), balanceHex));
    }

    @Override
    public void setCode(final Address address, final HexData code) {
        java.util.Objects.requireNonNull(address, "address must not be null");
        java.util.Objects.requireNonNull(code, "code must not be null");
        sendVoid(mode.prefix() + "setCode", List.of(address.value(), code.value()));
    }

    @Override
    public void setNonce(final Address address, final long nonce) {
        java.util.Objects.requireNonNull(address, "address must not be null");
        final String nonceHex = "0x" + Long.toHexString(nonce);
        sendVoid(mode.prefix() + "setNonce", List.of(address.value(), nonceHex));
    }

    @Override
    public void setStorageAt(final Address address, final Hash slot, final Hash value) {
        java.util.Objects.requireNonNull(address, "address must not be null");
        java.util.Objects.requireNonNull(slot, "slot must not be null");
        java.util.Objects.requireNonNull(value, "value must not be null");
        sendVoid(mode.prefix() + "setStorageAt", List.of(address.value(), slot.value(), value.value()));
    }

    @Override
    public void mine() {
        mine(1);
    }

    @Override
    public void mine(final long blocks) {
        final String blocksHex = "0x" + Long.toHexString(blocks);
        sendVoid(mode.prefix() + "mine", List.of(blocksHex));
    }

    @Override
    public void mine(final long blocks, final long intervalSeconds) {
        final String blocksHex = "0x" + Long.toHexString(blocks);
        final String intervalHex = "0x" + Long.toHexString(intervalSeconds);
        sendVoid(mode.prefix() + "mine", List.of(blocksHex, intervalHex));
    }

    @Override
    public void mineAt(final long timestamp) {
        final String timestampHex = "0x" + Long.toHexString(timestamp);
        // Anvil's anvil_mine accepts (blocks, timestamp) or just timestamp via mine(1, timestamp)
        sendVoid(mode.prefix() + "mine", List.of("0x1", timestampHex));
    }

    @Override
    public boolean getAutomine() {
        final String method = mode == TestNodeMode.ANVIL
                ? "anvil_getAutomine"
                : mode.prefix() + "getAutomine";
        final JsonRpcResponse response = sendWithRetry(method, List.of());
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
        final Object result = response.result();
        if (result == null) {
            throw new RpcException(-32000, method + " returned null", (String) null, (Throwable) null);
        }
        return Boolean.TRUE.equals(result);
    }

    @Override
    public void setAutomine(final boolean enabled) {
        final String method = mode == TestNodeMode.ANVIL
                ? "evm_setAutomine"
                : mode.prefix() + "setAutomine";
        sendVoid(method, List.of(enabled));
    }

    @Override
    public void setIntervalMining(final long intervalMs) {
        final String method = mode == TestNodeMode.ANVIL
                ? "evm_setIntervalMining"
                : mode.prefix() + "setIntervalMining";
        sendVoid(method, List.of(intervalMs));
    }

    @Override
    public void setNextBlockTimestamp(final long timestamp) {
        final String method = mode == TestNodeMode.ANVIL
                ? "evm_setNextBlockTimestamp"
                : mode.prefix() + "setNextBlockTimestamp";
        final String timestampHex = "0x" + Long.toHexString(timestamp);
        sendVoid(method, List.of(timestampHex));
    }

    @Override
    public void increaseTime(final long seconds) {
        final String method = mode == TestNodeMode.ANVIL
                ? "evm_increaseTime"
                : mode.prefix() + "increaseTime";
        final String secondsHex = "0x" + Long.toHexString(seconds);
        sendVoid(method, List.of(secondsHex));
    }

    @Override
    public void setNextBlockBaseFee(final Wei baseFee) {
        java.util.Objects.requireNonNull(baseFee, "baseFee");
        final String baseFeeHex = "0x" + baseFee.value().toString(16);
        sendVoid(mode.prefix() + "setNextBlockBaseFeePerGas", List.of(baseFeeHex));
    }

    @Override
    public void setBlockGasLimit(final java.math.BigInteger gasLimit) {
        java.util.Objects.requireNonNull(gasLimit, "gasLimit");
        final String gasLimitHex = "0x" + gasLimit.toString(16);
        sendVoid(mode.prefix() + "setBlockGasLimit", List.of(gasLimitHex));
    }

    @Override
    public void setCoinbase(final Address coinbase) {
        java.util.Objects.requireNonNull(coinbase, "coinbase");
        sendVoid(mode.prefix() + "setCoinbase", List.of(coinbase.value()));
    }

    @Override
    public void reset() {
        sendVoid(mode.prefix() + "reset", List.of());
    }

    @Override
    public void reset(final String forkUrl, final long blockNumber) {
        final var params = java.util.Map.of(
                "forking", java.util.Map.of(
                        "jsonRpcUrl", forkUrl,
                        "blockNumber", blockNumber));
        sendVoid(mode.prefix() + "reset", List.of(params));
    }

    // ==================== State Management Methods ====================
    // Full state dump/load for Anvil only. Useful for sharing test state.

    @Override
    public HexData dumpState() {
        if (mode != TestNodeMode.ANVIL) {
            throw new UnsupportedOperationException("dumpState is only supported by Anvil");
        }
        final JsonRpcResponse response = sendWithRetry("anvil_dumpState", List.of());
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
        final Object result = response.result();
        if (result == null) {
            throw new RpcException(-32000, "anvil_dumpState returned null", (String) null, (Throwable) null);
        }
        return new HexData(result.toString());
    }

    @Override
    public boolean loadState(final HexData state) {
        java.util.Objects.requireNonNull(state, "state must not be null");
        if (mode != TestNodeMode.ANVIL) {
            throw new UnsupportedOperationException("loadState is only supported by Anvil");
        }
        return sendBoolResult("anvil_loadState", List.of(state.value()));
    }

    // ==================== Transaction Pool Methods ====================
    // Pending transaction manipulation for Anvil only.

    @Override
    public boolean dropTransaction(final Hash txHash) {
        java.util.Objects.requireNonNull(txHash, "txHash must not be null");
        if (mode != TestNodeMode.ANVIL) {
            throw new UnsupportedOperationException("dropTransaction is only supported by Anvil");
        }
        return sendBoolResult("anvil_dropTransaction", List.of(txHash.value()));
    }

    // ==================== Internal Helpers ====================
    // Private utility methods for RPC communication. These reduce boilerplate and
    // centralize retry logic, error handling, and response parsing.

    /**
     * Returns the test node mode used by this tester.
     *
     * @return the test node mode
     */
    TestNodeMode testMode() {
        return mode;
    }

    /**
     * Sends an RPC request with automatic retry on transient failures.
     *
     * @param method the RPC method name
     * @param params the method parameters
     * @return the JSON-RPC response
     */
    JsonRpcResponse sendWithRetry(final String method, final List<?> params) {
        return RpcRetry.runRpc(() -> provider.send(method, params), maxRetries + 1, retryConfig);
    }

    /**
     * Sends an RPC request that returns void on success, throwing on error.
     *
     * <p>This helper reduces boilerplate for methods that don't need the response result.
     *
     * @param method the RPC method name
     * @param params the method parameters
     * @throws RpcException if the RPC call fails
     */
    void sendVoid(final String method, final List<?> params) {
        final JsonRpcResponse response = sendWithRetry(method, params);
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    /**
     * Sends an RPC request that returns a boolean result.
     *
     * <p>Returns false on RPC error or null result, returns the boolean result otherwise.
     * This is used for operations like revert() and loadState() that return success status.
     *
     * @param method the RPC method name
     * @param params the method parameters
     * @return true if the operation succeeded, false otherwise
     */
    boolean sendBoolResult(final String method, final List<?> params) {
        final JsonRpcResponse response = sendWithRetry(method, params);
        if (response.hasError()) {
            return false;
        }
        final Object result = response.result();
        return result != null && Boolean.TRUE.equals(result);
    }

    // ==================== Receipt Waiting ====================
    // Transaction receipt polling with exponential backoff.

    /** Maximum poll interval for exponential backoff (10 seconds). */
    private static final long MAX_POLL_INTERVAL_MILLIS = 10_000L;

    @Override
    public TransactionReceipt waitForReceipt(
            final Hash txHash, final long timeoutMillis, final long pollIntervalMillis) {
        // Use monotonic clock (System.nanoTime) instead of wall clock
        // to avoid issues with NTP adjustments or VM clock skew.
        final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        // Exponential backoff: start with user-provided interval, double each time, cap at MAX
        long currentInterval = pollIntervalMillis;

        while (System.nanoTime() - deadlineNanos < 0) {
            final TransactionReceipt receipt = getTransactionReceipt(txHash);
            if (receipt != null) {
                return receipt;
            }
            try {
                Thread.sleep(currentInterval);
                // Double the interval for next iteration, capped at MAX_POLL_INTERVAL_MILLIS
                currentInterval = Math.min(currentInterval * 2, MAX_POLL_INTERVAL_MILLIS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RpcException(-32000, "Interrupted while waiting for receipt", null, e);
            }
        }

        throw new RpcException(
                -32000,
                "Timed out waiting for transaction receipt for " + txHash.value(),
                null,
                null,
                null);
    }

    // ==================== Inner Classes ====================
    // Nested classes that require access to DefaultTester's internal state.

    /**
     * Default implementation of {@link ImpersonationSession} for test nodes.
     *
     * <p>Allows sending transactions from an impersonated address without possessing
     * its private key. The session automatically stops impersonation when closed.
     *
     * <p>This inner class has direct access to {@link DefaultTester}'s private members.
     *
     * @since 0.3.0
     */
    static final class DefaultImpersonationSession implements ImpersonationSession {

        private static final org.slf4j.Logger log =
                org.slf4j.LoggerFactory.getLogger(DefaultImpersonationSession.class);

        private final DefaultTester tester;
        private final Address address;
        private final java.util.concurrent.atomic.AtomicBoolean closed =
                new java.util.concurrent.atomic.AtomicBoolean(false);

        DefaultImpersonationSession(final DefaultTester tester, final Address address) {
            this.tester = tester;
            this.address = address;
        }

        @Override
        public Address address() {
            return address;
        }

        @Override
        public Hash sendTransaction(final TransactionRequest request) {
            ensureOpen();
            // Build transaction params with the impersonated address as from
            final java.util.Map<String, Object> tx = new java.util.LinkedHashMap<>();
            tx.put("from", address.value());
            if (request.to() != null) {
                tx.put("to", request.to().value());
            }
            if (request.value() != null) {
                tx.put("value", RpcUtils.toQuantityHex(request.value().value()));
            }
            if (request.data() != null && request.data().byteLength() > 0) {
                tx.put("data", request.data().value());
            }
            if (request.gasLimit() != null) {
                tx.put("gas", RpcUtils.toQuantityHex(BigInteger.valueOf(request.gasLimit())));
            }
            if (request.gasPrice() != null) {
                tx.put("gasPrice", RpcUtils.toQuantityHex(request.gasPrice().value()));
            }
            if (request.maxFeePerGas() != null) {
                tx.put("maxFeePerGas", RpcUtils.toQuantityHex(request.maxFeePerGas().value()));
            }
            if (request.maxPriorityFeePerGas() != null) {
                tx.put("maxPriorityFeePerGas", RpcUtils.toQuantityHex(request.maxPriorityFeePerGas().value()));
            }
            if (request.nonce() != null) {
                tx.put("nonce", RpcUtils.toQuantityHex(BigInteger.valueOf(request.nonce())));
            }

            final JsonRpcResponse response = tester.sendWithRetry("eth_sendTransaction", List.of(tx));
            if (response.hasError()) {
                final JsonRpcError err = response.error();
                throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
            }
            final Object result = response.result();
            if (result == null) {
                throw new RpcException(-32000, "eth_sendTransaction returned null", (String) null, (Throwable) null);
            }
            return new Hash(result.toString());
        }

        @Override
        public TransactionReceipt sendTransactionAndWait(final TransactionRequest request) {
            return sendTransactionAndWait(
                    request, Brane.Signer.DEFAULT_TIMEOUT_MILLIS, Brane.Signer.DEFAULT_POLL_INTERVAL_MILLIS);
        }

        @Override
        public TransactionReceipt sendTransactionAndWait(
                final TransactionRequest request, final long timeoutMillis, final long pollIntervalMillis) {
            final Hash txHash = sendTransaction(request);

            final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
            long currentInterval = pollIntervalMillis;
            final long maxPollInterval = 10_000L;

            while (System.nanoTime() - deadlineNanos < 0) {
                final TransactionReceipt receipt = tester.getTransactionReceipt(txHash);
                if (receipt != null) {
                    if (!receipt.status()) {
                        throw new io.brane.core.error.RevertException(
                                io.brane.core.RevertDecoder.RevertKind.UNKNOWN,
                                "Transaction reverted (txHash: " + txHash.value() + ")",
                                null,
                                null);
                    }
                    return receipt;
                }
                try {
                    Thread.sleep(currentInterval);
                    currentInterval = Math.min(currentInterval * 2, maxPollInterval);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    throw new RpcException(-32000, "Interrupted while waiting for receipt", null, e);
                }
            }

            throw new RpcException(
                    -32000,
                    "Timed out waiting for transaction receipt for " + txHash.value(),
                    null,
                    null,
                    null);
        }

        @Override
        public void close() {
            if (closed.compareAndSet(false, true)) {
                try {
                    tester.stopImpersonating(address);
                } catch (Exception e) {
                    log.warn("Failed to stop impersonating {}: {}", address.value(), e.getMessage());
                }
            }
        }

        private void ensureOpen() {
            if (closed.get()) {
                throw new IllegalStateException("ImpersonationSession has been closed");
            }
        }
    }
}
