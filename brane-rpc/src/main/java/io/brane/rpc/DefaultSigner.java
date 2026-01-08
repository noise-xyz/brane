package io.brane.rpc;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import org.jspecify.annotations.Nullable;

import io.brane.core.DebugLogger;
import io.brane.core.LogFormatter;
import io.brane.core.RevertDecoder;
import io.brane.core.chain.ChainProfile;
import io.brane.core.error.ChainMismatchException;
import io.brane.core.error.InvalidSenderException;
import io.brane.core.error.RevertException;
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
 * Default implementation of {@link Brane.Signer} for full blockchain operations.
 *
 * <p>This implementation provides access to all read operations via delegation to
 * an internal {@link DefaultReader}, plus transaction signing and sending capabilities
 * using the configured {@link Signer} and {@link SmartGasStrategy}.
 *
 * @since 0.1.0
 */
final class DefaultSigner implements Brane.Signer {

    private final DefaultReader reader;
    private final BraneProvider provider;
    private final io.brane.core.crypto.Signer signer;
    private final SmartGasStrategy gasStrategy;
    private final AtomicReference<Long> cachedChainId = new AtomicReference<>();

    /**
     * Creates a new DefaultSigner with the specified configuration.
     *
     * @param provider    the RPC provider for blockchain communication
     * @param signer      the signer for transaction signing
     * @param chain       the chain profile for network-specific settings (may be null)
     * @param maxRetries  the maximum number of retry attempts for transient failures
     * @param retryConfig the retry configuration for backoff timing
     */
    DefaultSigner(
            final BraneProvider provider,
            final io.brane.core.crypto.Signer signer,
            final @Nullable ChainProfile chain,
            final int maxRetries,
            final RpcRetryConfig retryConfig) {
        this.reader = new DefaultReader(provider, chain, maxRetries, retryConfig);
        this.provider = provider;
        this.signer = signer;
        final ChainProfile resolvedChain = chain != null ? chain : defaultChainProfile();
        this.gasStrategy = new SmartGasStrategy(reader, provider, resolvedChain);
    }

    /**
     * Creates a default chain profile for when none is provided.
     * Defaults to EIP-1559 support with 1 gwei priority fee.
     */
    private static ChainProfile defaultChainProfile() {
        // Use chain ID 1 as a placeholder; actual chain ID will be fetched lazily
        return ChainProfile.of(1L, null, true, Wei.of(1_000_000_000L));
    }

    @Override
    public BigInteger chainId() {
        return reader.chainId();
    }

    @Override
    public BigInteger getBalance(final Address address) {
        return reader.getBalance(address);
    }

    @Override
    public HexData getCode(final Address address) {
        return reader.getCode(address);
    }

    @Override
    public HexData getStorageAt(final Address address, final BigInteger slot) {
        return reader.getStorageAt(address, slot);
    }

    @Override
    public @Nullable BlockHeader getLatestBlock() {
        return reader.getLatestBlock();
    }

    @Override
    public @Nullable BlockHeader getBlockByNumber(final long blockNumber) {
        return reader.getBlockByNumber(blockNumber);
    }

    @Override
    public @Nullable Transaction getTransactionByHash(final Hash hash) {
        return reader.getTransactionByHash(hash);
    }

    @Override
    public @Nullable TransactionReceipt getTransactionReceipt(final Hash hash) {
        return reader.getTransactionReceipt(hash);
    }

    @Override
    public HexData call(final CallRequest request) {
        return reader.call(request);
    }

    @Override
    public HexData call(final CallRequest request, final BlockTag blockTag) {
        return reader.call(request, blockTag);
    }

    @Override
    public List<LogEntry> getLogs(final LogFilter filter) {
        return reader.getLogs(filter);
    }

    @Override
    public BigInteger estimateGas(final TransactionRequest request) {
        return reader.estimateGas(request);
    }

    @Override
    public AccessListWithGas createAccessList(final TransactionRequest request) {
        return reader.createAccessList(request);
    }

    @Override
    public SimulateResult simulate(final SimulateRequest request) {
        return reader.simulate(request);
    }

    @Override
    public MulticallBatch batch() {
        return reader.batch();
    }

    @Override
    public Subscription onNewHeads(final Consumer<BlockHeader> callback) {
        return reader.onNewHeads(callback);
    }

    @Override
    public Subscription onLogs(final LogFilter filter, final Consumer<LogEntry> callback) {
        return reader.onLogs(filter, callback);
    }

    @Override
    public Optional<ChainProfile> chain() {
        return reader.chain();
    }

    @Override
    public boolean canSubscribe() {
        return reader.canSubscribe();
    }

    @Override
    public void close() {
        reader.close();
    }

    @Override
    public Hash sendTransaction(final TransactionRequest request) {
        final long chainId = fetchAndCacheChainId();
        final Address from = request.from() != null ? request.from() : signer.address();

        // Apply gas defaults via SmartGasStrategy
        final SmartGasStrategy.GasFilledRequest gasResult = gasStrategy.applyDefaults(request, from);
        final TransactionRequest withDefaults = gasResult.request();

        // Log if EIP-1559 fell back to legacy
        if (gasResult.fellBackToLegacy()) {
            DebugLogger.log("EIP-1559 requested but fell back to legacy gas pricing");
        }

        // Fetch nonce if not provided
        final BigInteger nonce = withDefaults.nonceOpt()
                .map(BigInteger::valueOf)
                .orElseGet(() -> fetchNonce(from));

        // Get gas limit (should already be filled by SmartGasStrategy)
        final BigInteger gasLimit = withDefaults.gasLimitOpt()
                .map(BigInteger::valueOf)
                .orElseGet(() -> reader.estimateGas(withDefaults));

        // Prepare value and data
        final Wei valueOrZero = withDefaults.value() != null ? withDefaults.value() : Wei.of(0);
        final HexData dataOrEmpty = withDefaults.data() != null ? withDefaults.data() : HexData.EMPTY;

        // Build unsigned transaction
        final io.brane.core.tx.UnsignedTransaction unsignedTx;
        if (withDefaults.isEip1559()) {
            final Wei maxPriority = withDefaults.maxPriorityFeePerGas() != null
                    ? withDefaults.maxPriorityFeePerGas()
                    : Wei.of(1_000_000_000L);
            final Wei maxFee = withDefaults.maxFeePerGas() != null
                    ? withDefaults.maxFeePerGas()
                    : maxPriority;

            unsignedTx = new io.brane.core.tx.Eip1559Transaction(
                    chainId,
                    nonce.longValue(),
                    maxPriority,
                    maxFee,
                    gasLimit.longValue(),
                    withDefaults.to(),
                    valueOrZero,
                    dataOrEmpty,
                    withDefaults.accessListOrEmpty());
        } else {
            final Wei gasPrice = withDefaults.gasPrice() != null
                    ? withDefaults.gasPrice()
                    : Wei.of(fetchGasPrice());

            unsignedTx = new io.brane.core.tx.LegacyTransaction(
                    nonce.longValue(),
                    gasPrice,
                    gasLimit.longValue(),
                    withDefaults.to(),
                    valueOrZero,
                    dataOrEmpty);
        }

        DebugLogger.logTx(LogFormatter.formatTxSend(
                from.value(),
                withDefaults.to() != null ? withDefaults.to().value() : null,
                nonce,
                gasLimit,
                valueOrZero.value()));

        // Sign the transaction
        final io.brane.core.crypto.Signature baseSig = signer.signTransaction(unsignedTx, chainId);

        // Adjust V value for legacy transactions (EIP-155)
        final io.brane.core.crypto.Signature signature;
        if (unsignedTx instanceof io.brane.core.tx.LegacyTransaction) {
            // For legacy transactions, use EIP-155 encoding: v = chainId * 2 + 35 + yParity
            final int v = (int) (chainId * 2 + 35 + baseSig.v());
            signature = new io.brane.core.crypto.Signature(baseSig.r(), baseSig.s(), v);
        } else {
            // For EIP-1559, v is just yParity (0 or 1)
            signature = baseSig;
        }

        // Encode and send
        final byte[] envelope = unsignedTx.encodeAsEnvelope(signature);
        final String signedHex = io.brane.primitives.Hex.encode(envelope);

        final String txHash;
        final long start = System.nanoTime();
        try {
            final JsonRpcResponse response = reader.sendWithRetry(
                    "eth_sendRawTransaction", List.of(signedHex));
            if (response.hasError()) {
                final JsonRpcError err = response.error();
                final String data = RpcUtils.extractErrorData(err.data());
                handlePotentialRevert(data, err.message());
                if (err.message() != null
                        && err.message().toLowerCase().contains("invalid sender")) {
                    throw new InvalidSenderException(err.message(), null);
                }
                throw new RpcException(err.code(), err.message(), data, (Long) null);
            }
            final Object result = response.result();
            if (result == null) {
                throw new RpcException(-32000, "eth_sendRawTransaction returned null", (String) null, (Throwable) null);
            }
            txHash = result.toString();
        } catch (RpcException e) {
            handlePotentialRevert(e.data(), e.getMessage());
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("invalid sender")) {
                throw new InvalidSenderException(e.getMessage(), e);
            }
            throw e;
        }

        final long durationMicros = (System.nanoTime() - start) / 1_000L;
        DebugLogger.logTx(LogFormatter.formatTxHash(txHash, durationMicros));
        return new Hash(txHash);
    }

    /**
     * Fetches and caches the chain ID, validating it against the configured chain profile.
     *
     * @throws ChainMismatchException if the actual chain ID doesn't match the expected one
     */
    private long fetchAndCacheChainId() {
        final Long cached = cachedChainId.get();
        if (cached != null) {
            return cached;
        }
        final long actual = reader.chainId().longValue();

        // Validate chain ID against configured chain profile (if present)
        reader.chain().ifPresent(profile -> {
            final long expected = profile.chainId();
            if (expected != actual) {
                throw new ChainMismatchException(expected, actual);
            }
        });

        final Long witness = cachedChainId.compareAndExchange(null, actual);
        return witness != null ? witness : actual;
    }

    /**
     * Fetches the nonce for an address.
     */
    private BigInteger fetchNonce(final Address from) {
        final JsonRpcResponse response = reader.sendWithRetry(
                "eth_getTransactionCount", List.of(from.value(), "pending"));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
        final Object result = response.result();
        if (result == null) {
            throw new RpcException(-32000, "eth_getTransactionCount returned null", (String) null, (Throwable) null);
        }
        return RpcUtils.decodeHexBigInteger(result.toString());
    }

    /**
     * Fetches the current gas price.
     */
    private BigInteger fetchGasPrice() {
        final JsonRpcResponse response = reader.sendWithRetry("eth_gasPrice", List.of());
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
        final Object result = response.result();
        if (result == null) {
            throw new RpcException(-32000, "eth_gasPrice returned null", (String) null, (Throwable) null);
        }
        return RpcUtils.decodeHexBigInteger(result.toString());
    }

    /**
     * Handles potential revert data in error responses.
     */
    private static void handlePotentialRevert(final @Nullable String raw, final @Nullable String message) {
        if (raw != null && raw.startsWith("0x") && raw.length() > 10) {
            final RevertDecoder.Decoded decoded = RevertDecoder.decode(raw);
            DebugLogger.logTx(LogFormatter.formatTxRevert(null, decoded.kind().toString(), decoded.reason()));
            throw new io.brane.core.error.RevertException(decoded.kind(), decoded.reason(), decoded.rawDataHex(), null);
        }
    }

    /** Maximum poll interval for exponential backoff (10 seconds). */
    private static final long MAX_POLL_INTERVAL_MILLIS = 10_000L;

    @Override
    public TransactionReceipt sendTransactionAndWait(
            final TransactionRequest request, final long timeoutMillis, final long pollIntervalMillis) {
        final Hash txHash = sendTransaction(request);
        DebugLogger.logTx(LogFormatter.formatTxWait(txHash.value(), timeoutMillis));

        // Use monotonic clock (System.nanoTime) instead of wall clock (Instant.now)
        // to avoid issues with NTP adjustments or VM clock skew.
        final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        // Exponential backoff: start with user-provided interval, double each time, cap at MAX
        long currentInterval = pollIntervalMillis;

        while (System.nanoTime() - deadlineNanos < 0) {
            final TransactionReceipt receipt = getTransactionReceipt(txHash);
            if (receipt != null) {
                DebugLogger.logTx(
                        LogFormatter.formatTxReceipt(txHash.value(), receipt.blockNumber(), receipt.status()));
                if (!receipt.status()) {
                    // Transaction was mined but reverted - replay via eth_call to get revert reason
                    throwRevertException(request, txHash, receipt);
                }
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

    /**
     * Replays a reverted transaction via eth_call to extract the revert reason.
     */
    private void throwRevertException(
            final TransactionRequest request, final Hash txHash, final TransactionReceipt receipt) {
        // Try to replay the transaction via eth_call to get the revert reason
        final Map<String, Object> tx = new LinkedHashMap<>();
        final Address from = receipt.from() != null ? receipt.from() : signer.address();
        tx.put("from", from.value());
        if (request.to() != null) {
            tx.put("to", request.to().value());
        }
        if (request.value() != null) {
            tx.put("value", RpcUtils.toQuantityHex(request.value().value()));
        }
        if (request.data() != null) {
            tx.put("data", request.data().value());
        }

        // Replay at the block where the transaction was mined
        final String blockNumber = RpcUtils.toQuantityHex(BigInteger.valueOf(receipt.blockNumber()));

        try {
            final JsonRpcResponse response = provider.send("eth_call", List.of(tx, blockNumber));
            if (response.hasError()) {
                final JsonRpcError err = response.error();
                final String data = RpcUtils.extractErrorData(err.data());
                if (data != null && data.startsWith("0x") && data.length() > 10) {
                    final RevertDecoder.Decoded decoded = RevertDecoder.decode(data);
                    DebugLogger.logTx(
                            LogFormatter.formatTxRevert(txHash.value(), decoded.kind().toString(), decoded.reason()));
                    throw new RevertException(decoded.kind(), decoded.reason(), decoded.rawDataHex(), null);
                }
                throw new RevertException(
                        RevertDecoder.RevertKind.UNKNOWN,
                        err.message() != null ? err.message() : "Transaction reverted",
                        null,
                        null);
            }
            // eth_call succeeded but transaction was marked as reverted in receipt - unexpected state
            throw new RevertException(
                    RevertDecoder.RevertKind.UNKNOWN,
                    "Transaction reverted but eth_call replay succeeded (txHash: " + txHash.value() + ")",
                    null,
                    null);
        } catch (RevertException e) {
            throw e;
        } catch (Exception e) {
            // If eth_call fails for any other reason, throw a generic revert exception
            // but preserve the original exception as the cause for debugging
            DebugLogger.logTx(LogFormatter.formatTxRevert(txHash.value(), "UNKNOWN",
                    "Transaction reverted (eth_call replay failed: " + e.getMessage() + ")"));
            throw new RevertException(
                    RevertDecoder.RevertKind.UNKNOWN,
                    "Transaction reverted (eth_call replay failed: " + e.getMessage() + ")",
                    null,
                    e);
        }
    }

    @Override
    public io.brane.core.crypto.Signer signer() {
        return signer;
    }
}
