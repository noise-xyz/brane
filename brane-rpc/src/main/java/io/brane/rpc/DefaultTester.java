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

    // ==================== Brane Interface Delegation ====================

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

    @Override
    public Brane.Signer asSigner() {
        return signer;
    }

    // ==================== Signer Method Delegation ====================

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
        final JsonRpcResponse response = sendWithRetry(method, List.of(snapshotId.value()));
        if (response.hasError()) {
            return false;
        }
        final Object result = response.result();
        return result != null && Boolean.TRUE.equals(result);
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
        final String method = mode.prefix() + "stopImpersonatingAccount";
        final JsonRpcResponse response = sendWithRetry(method, List.of(address.value()));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void enableAutoImpersonate() {
        if (mode != TestNodeMode.ANVIL) {
            throw new UnsupportedOperationException("Auto-impersonate is only supported by Anvil");
        }
        final JsonRpcResponse response = sendWithRetry("anvil_autoImpersonateAccount", List.of(true));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void disableAutoImpersonate() {
        if (mode != TestNodeMode.ANVIL) {
            throw new UnsupportedOperationException("Auto-impersonate is only supported by Anvil");
        }
        final JsonRpcResponse response = sendWithRetry("anvil_autoImpersonateAccount", List.of(false));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void setBalance(final Address address, final Wei balance) {
        java.util.Objects.requireNonNull(address, "address must not be null");
        java.util.Objects.requireNonNull(balance, "balance must not be null");
        final String method = mode.prefix() + "setBalance";
        final String balanceHex = "0x" + balance.value().toString(16);
        final JsonRpcResponse response = sendWithRetry(method, List.of(address.value(), balanceHex));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void setCode(final Address address, final HexData code) {
        java.util.Objects.requireNonNull(address, "address must not be null");
        java.util.Objects.requireNonNull(code, "code must not be null");
        final String method = mode.prefix() + "setCode";
        final JsonRpcResponse response = sendWithRetry(method, List.of(address.value(), code.value()));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void setNonce(final Address address, final long nonce) {
        java.util.Objects.requireNonNull(address, "address must not be null");
        final String method = mode.prefix() + "setNonce";
        final String nonceHex = "0x" + Long.toHexString(nonce);
        final JsonRpcResponse response = sendWithRetry(method, List.of(address.value(), nonceHex));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void setStorageAt(final Address address, final Hash slot, final Hash value) {
        java.util.Objects.requireNonNull(address, "address must not be null");
        java.util.Objects.requireNonNull(slot, "slot must not be null");
        java.util.Objects.requireNonNull(value, "value must not be null");
        final String method = mode.prefix() + "setStorageAt";
        final JsonRpcResponse response = sendWithRetry(method, List.of(address.value(), slot.value(), value.value()));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void mine() {
        mine(1);
    }

    @Override
    public void mine(final long blocks) {
        final String method = mode.prefix() + "mine";
        final String blocksHex = "0x" + Long.toHexString(blocks);
        final JsonRpcResponse response = sendWithRetry(method, List.of(blocksHex));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void mine(final long blocks, final long intervalSeconds) {
        final String method = mode.prefix() + "mine";
        final String blocksHex = "0x" + Long.toHexString(blocks);
        final String intervalHex = "0x" + Long.toHexString(intervalSeconds);
        final JsonRpcResponse response = sendWithRetry(method, List.of(blocksHex, intervalHex));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void mineAt(final long timestamp) {
        final String method = mode.prefix() + "mine";
        final String timestampHex = "0x" + Long.toHexString(timestamp);
        // Anvil's anvil_mine accepts (blocks, timestamp) or just timestamp via mine(1, timestamp)
        final JsonRpcResponse response = sendWithRetry(method, List.of("0x1", timestampHex));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
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
        final JsonRpcResponse response = sendWithRetry(method, List.of(enabled));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void setIntervalMining(final long intervalMs) {
        final String method = mode == TestNodeMode.ANVIL
                ? "evm_setIntervalMining"
                : mode.prefix() + "setIntervalMining";
        final JsonRpcResponse response = sendWithRetry(method, List.of(intervalMs));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void setNextBlockTimestamp(final long timestamp) {
        final String method = mode == TestNodeMode.ANVIL
                ? "evm_setNextBlockTimestamp"
                : mode.prefix() + "setNextBlockTimestamp";
        final String timestampHex = "0x" + Long.toHexString(timestamp);
        final JsonRpcResponse response = sendWithRetry(method, List.of(timestampHex));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void increaseTime(final long seconds) {
        final String method = mode == TestNodeMode.ANVIL
                ? "evm_increaseTime"
                : mode.prefix() + "increaseTime";
        final String secondsHex = "0x" + Long.toHexString(seconds);
        final JsonRpcResponse response = sendWithRetry(method, List.of(secondsHex));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void setNextBlockBaseFee(final Wei baseFee) {
        final String method = mode.prefix() + "setNextBlockBaseFeePerGas";
        final String baseFeeHex = "0x" + baseFee.value().toString(16);
        final JsonRpcResponse response = sendWithRetry(method, List.of(baseFeeHex));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void setBlockGasLimit(final java.math.BigInteger gasLimit) {
        java.util.Objects.requireNonNull(gasLimit, "gasLimit must not be null");
        final String method = mode.prefix() + "setBlockGasLimit";
        final String gasLimitHex = "0x" + gasLimit.toString(16);
        final JsonRpcResponse response = sendWithRetry(method, List.of(gasLimitHex));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void setCoinbase(final Address coinbase) {
        final String method = mode.prefix() + "setCoinbase";
        final JsonRpcResponse response = sendWithRetry(method, List.of(coinbase.value()));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void reset() {
        final String method = mode.prefix() + "reset";
        final JsonRpcResponse response = sendWithRetry(method, List.of());
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    @Override
    public void reset(final String forkUrl, final long blockNumber) {
        final String method = mode.prefix() + "reset";
        final var params = java.util.Map.of(
                "forking", java.util.Map.of(
                        "jsonRpcUrl", forkUrl,
                        "blockNumber", blockNumber));
        final JsonRpcResponse response = sendWithRetry(method, List.of(params));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
    }

    // ==================== State Management Methods ====================

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
        final JsonRpcResponse response = sendWithRetry("anvil_loadState", List.of(state.value()));
        if (response.hasError()) {
            return false;
        }
        final Object result = response.result();
        return result != null && Boolean.TRUE.equals(result);
    }

    // ==================== Internal Helpers ====================

    /**
     * Sends an RPC request with automatic retry on transient failures.
     */
    private JsonRpcResponse sendWithRetry(final String method, final List<?> params) {
        return RpcRetry.runRpc(() -> provider.send(method, params), maxRetries + 1, retryConfig);
    }

    /**
     * Returns the underlying provider (for ImpersonationSession).
     */
    BraneProvider provider() {
        return provider;
    }

    /**
     * Returns the max retries (for ImpersonationSession).
     */
    int maxRetries() {
        return maxRetries;
    }

    /**
     * Returns the retry config (for ImpersonationSession).
     */
    RpcRetryConfig retryConfig() {
        return retryConfig;
    }

    /**
     * Returns the underlying signer (for ImpersonationSession to send transactions).
     */
    DefaultSigner delegateSigner() {
        return signer;
    }

    // ==================== Receipt Waiting ====================

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
}
