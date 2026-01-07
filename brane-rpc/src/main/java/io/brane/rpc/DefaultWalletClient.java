package io.brane.rpc;

import static io.brane.rpc.internal.RpcUtils.MAPPER;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

import io.brane.core.DebugLogger;
import io.brane.core.LogFormatter;
import io.brane.core.RevertDecoder;
import io.brane.core.chain.ChainProfile;
import io.brane.core.crypto.Signer;
import io.brane.core.error.ChainMismatchException;
import io.brane.core.error.InvalidSenderException;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.model.LogEntry;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.internal.LogParser;
import io.brane.rpc.internal.RpcUtils;

/**
 * Default wallet client with automatic transaction preparation.
 *
 * <p>
 * This class implements the complete transaction lifecycle:
 * <ol>
 * <li>Fills missing gas parameters via {@link SmartGasStrategy}</li>
 * <li>Fetches nonce via {@code eth_getTransactionCount} if not provided</li>
 * <li>Signs the transaction using {@link io.brane.core.crypto.Signer}</li>
 * <li>Broadcasts via {@code eth_sendRawTransaction}</li>
 * <li>Optionally polls for receipt via {@code eth_getTransactionReceipt}</li>
 * </ol>
 *
 * <p>
 * <strong>Chain ID Enforcement:</strong> Validates that the configured chain ID
 * matches the connected node's chain ID on first transaction. Caches the result
 * using {@link AtomicReference} for thread-safe lazy initialization.
 *
 * <p>
 * <strong>Thread Safety:</strong> This class is thread-safe. Multiple threads
 * can submit transactions concurrently.
 *
 * <p>
 * <strong>Usage:</strong> Typically created via a builder pattern, not
 * instantiated directly.
 *
 * @see SmartGasStrategy
 * @see io.brane.core.crypto.Signer
 * @deprecated Use {@link Brane.Signer} via {@link Brane#connect(String, io.brane.core.crypto.Signer)} instead.
 *             This class will be removed in a future version.
 */
@Deprecated(forRemoval = true)
public final class DefaultWalletClient {

    private final BraneProvider provider;

    private final Signer signer;
    private final Address senderAddress;
    private final long expectedChainId;

    private final SmartGasStrategy gasStrategy;
    private final AtomicReference<Long> cachedChainId = new AtomicReference<>();

    private DefaultWalletClient(
            final BraneProvider provider,
            final io.brane.core.crypto.Signer signer,
            final Address senderAddress,
            final long expectedChainId,
            final SmartGasStrategy gasStrategy) {
        this.provider = Objects.requireNonNull(provider, "provider");

        this.signer = Objects.requireNonNull(signer, "signer");
        this.senderAddress = Objects.requireNonNull(senderAddress, "senderAddress");
        this.expectedChainId = expectedChainId;

        this.gasStrategy = Objects.requireNonNull(gasStrategy, "gasStrategy");
    }

    public static DefaultWalletClient from(
            final BraneProvider provider,
            final Brane brane,
            final io.brane.core.crypto.Signer signer,
            final Address senderAddress,
            final long expectedChainId,
            final ChainProfile chainProfile) {
        final SmartGasStrategy gasStrategy = new SmartGasStrategy(brane, provider, chainProfile);
        return new DefaultWalletClient(
                provider, signer, senderAddress, expectedChainId, gasStrategy);
    }

    public static DefaultWalletClient from(
            final BraneProvider provider,
            final Brane brane,
            final io.brane.core.crypto.Signer signer,
            final Address senderAddress,
            final long expectedChainId,
            final ChainProfile chainProfile,
            final BigInteger gasLimitBufferNumerator,
            final BigInteger gasLimitBufferDenominator) {
        final SmartGasStrategy gasStrategy = new SmartGasStrategy(
                brane, provider, chainProfile, gasLimitBufferNumerator, gasLimitBufferDenominator);
        return new DefaultWalletClient(
                provider,
                signer,
                senderAddress,
                expectedChainId,
                gasStrategy);
    }

    public static DefaultWalletClient from(
            final BraneProvider provider,
            final Brane brane,
            final io.brane.core.crypto.Signer signer,
            final Address senderAddress,
            final long expectedChainId) {
        final ChainProfile profile = ChainProfile.of(expectedChainId, null, true, Wei.of(1_000_000_000L));
        return from(provider, brane, signer, senderAddress, expectedChainId, profile);
    }

    public static DefaultWalletClient create(
            final BraneProvider provider,
            final Brane brane,
            final io.brane.core.crypto.Signer signer,
            final Address senderAddress,
            final ChainProfile chainProfile) {
        final SmartGasStrategy gasStrategy = new SmartGasStrategy(brane, provider, chainProfile);
        return new DefaultWalletClient(
                provider, signer, senderAddress, 0L, gasStrategy);
    }

    public static DefaultWalletClient create(
            final BraneProvider provider,
            final Brane brane,
            final io.brane.core.crypto.Signer signer,
            final ChainProfile chainProfile) {
        return create(provider, brane, signer, signer.address(), chainProfile);
    }

    /**
     * Creates a wallet client that auto-detects the chain ID from the connected network.
     *
     * <p>This method queries {@code eth_chainId} to determine the network's chain ID
     * and creates an appropriate ChainProfile. Use the overloads that accept a
     * {@link ChainProfile} parameter for better performance (avoids the RPC call).
     */
    public static DefaultWalletClient create(
            final BraneProvider provider,
            final Brane brane,
            final io.brane.core.crypto.Signer signer,
            final Address senderAddress) {
        final long chainId = brane.chainId().longValue();
        final ChainProfile profile = ChainProfile.of(chainId, null, true, Wei.of(1_000_000_000L));
        return create(provider, brane, signer, senderAddress, profile);
    }

    /**
     * Creates a wallet client that auto-detects the chain ID from the connected network.
     *
     * <p>This method queries {@code eth_chainId} to determine the network's chain ID
     * and creates an appropriate ChainProfile. Use the overloads that accept a
     * {@link ChainProfile} parameter for better performance (avoids the RPC call).
     */
    public static DefaultWalletClient create(
            final BraneProvider provider,
            final Brane brane,
            final io.brane.core.crypto.Signer signer) {
        return create(provider, brane, signer, signer.address());
    }

    public static DefaultWalletClient create(
            final BraneProvider provider,
            final Brane brane,
            final io.brane.core.crypto.Signer signer,
            final Address senderAddress,
            final ChainProfile chainProfile,
            final BigInteger gasLimitBufferNumerator,
            final BigInteger gasLimitBufferDenominator) {
        return from(
                provider,
                brane,
                signer,
                senderAddress,
                0L,
                chainProfile,
                gasLimitBufferNumerator,
                gasLimitBufferDenominator);
    }

    public Hash sendTransaction(final TransactionRequest request) {
        final long chainId = enforceChainId();

        final Address from = request.from() != null ? request.from() : senderAddress;
        final SmartGasStrategy.GasFilledRequest gasResult = gasStrategy.applyDefaults(request, from);
        final TransactionRequest withDefaults = gasResult.request();

        // Log if EIP-1559 fell back to legacy (useful for debugging multi-chain apps)
        if (gasResult.fellBackToLegacy()) {
            DebugLogger.log("EIP-1559 requested but fell back to legacy gas pricing");
        }

        final BigInteger nonce = withDefaults.nonceOpt().map(BigInteger::valueOf).orElseGet(() -> fetchNonce(from));

        final BigInteger gasLimit = withDefaults
                .gasLimitOpt()
                .map(BigInteger::valueOf)
                .orElseGet(() -> estimateGas(withDefaults, from));

        final ValueParts valueParts = buildValueParts(withDefaults, from);

        // Build UnsignedTransaction directly with computed nonce and gasLimit
        final io.brane.core.tx.UnsignedTransaction unsignedTx;
        final Wei valueOrZero = withDefaults.value() != null ? withDefaults.value() : Wei.of(0);
        final HexData dataOrEmpty = withDefaults.data() != null ? withDefaults.data() : HexData.EMPTY;

        if (withDefaults.isEip1559()) {
            unsignedTx = new io.brane.core.tx.Eip1559Transaction(
                    chainId,
                    nonce.longValue(),
                    Wei.of(valueParts.maxPriorityFeePerGas),
                    Wei.of(valueParts.maxFeePerGas),
                    gasLimit.longValue(),
                    withDefaults.to(),
                    valueOrZero,
                    dataOrEmpty,
                    withDefaults.accessListOrEmpty());
        } else {
            unsignedTx = new io.brane.core.tx.LegacyTransaction(
                    nonce.longValue(),
                    Wei.of(valueParts.gasPrice),
                    gasLimit.longValue(),
                    withDefaults.to(),
                    valueOrZero,
                    dataOrEmpty);
        }

        DebugLogger.logTx(
                LogFormatter.formatTxSend(from.value(), valueParts.to, nonce, gasLimit, valueParts.value));

        final io.brane.core.crypto.Signature baseSig = signer.signTransaction(unsignedTx, chainId);

        // Adjust V value and encode
        final io.brane.core.crypto.Signature signature;
        if (unsignedTx instanceof io.brane.core.tx.LegacyTransaction) {
            // For legacy transactions, use EIP-155 encoding: v = chainId * 2 + 35 + yParity
            final int v = (int) (chainId * 2 + 35 + baseSig.v());
            signature = new io.brane.core.crypto.Signature(baseSig.r(), baseSig.s(), v);
        } else {
            // For EIP-1559, v is just yParity (0 or 1)
            signature = baseSig;
        }

        final byte[] envelope = unsignedTx.encodeAsEnvelope(signature);
        final String signedHex = io.brane.primitives.Hex.encode(envelope);

        final String txHash;
        final long start = System.nanoTime();
        try {
            txHash = callRpc("eth_sendRawTransaction", List.of(signedHex), String.class, null);
        } catch (RpcException e) {
            handlePotentialRevert(e, null);
            if (e.getMessage() != null
                    && e.getMessage().toLowerCase().contains("invalid sender")) {
                throw new InvalidSenderException(e.getMessage(), e);
            }
            throw e;
        }
        final long durationMicros = (System.nanoTime() - start) / 1_000L;
        DebugLogger.logTx(LogFormatter.formatTxHash(txHash, durationMicros));
        return new Hash(txHash);
    }

    /** Maximum poll interval for exponential backoff (10 seconds). */
    private static final long MAX_POLL_INTERVAL_MILLIS = 10_000L;

    public TransactionReceipt sendTransactionAndWait(
            final TransactionRequest request, final long timeoutMillis, final long pollIntervalMillis) {
        final Hash txHash = sendTransaction(request);
        DebugLogger.logTx(LogFormatter.formatTxWait(txHash.value(), timeoutMillis));
        // Use monotonic clock (System.nanoTime) instead of wall clock (Instant.now)
        // to avoid issues with NTP adjustments or VM clock skew.
        // The comparison (now - deadline < 0) is wraparound-safe because signed subtraction
        // correctly handles the rare case where nanoTime() wraps around Long.MAX_VALUE.
        // See: https://docs.oracle.com/javase/8/docs/api/java/lang/System.html#nanoTime--
        final long deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMillis);

        // Exponential backoff: start with user-provided interval, double each time, cap at MAX
        long currentInterval = pollIntervalMillis;

        while (System.nanoTime() - deadlineNanos < 0) {
            final TransactionReceipt receipt = fetchReceipt(txHash);
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

    private void throwRevertException(
            final TransactionRequest request, final Hash txHash, final TransactionReceipt receipt) {
        // Try to replay the transaction via eth_call to get the revert reason
        final Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("from", receipt.from() != null ? receipt.from().value() : senderAddress.value());
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
                    "Transaction reverted (txHash: " + txHash.value() + ")",
                    null,
                    e);
        }
    }

    private TransactionReceipt fetchReceipt(final Hash hash) {
        final JsonRpcResponse response = provider.send("eth_getTransactionReceipt", List.of(hash.value()));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), RpcUtils.extractErrorData(err.data()), null, null);
        }
        final Object result = response.result();
        if (result == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        final Map<String, Object> map = MAPPER.convertValue(result, Map.class);
        final String statusHex = RpcUtils.stringValue(map.get("status"));
        final boolean status = statusHex != null && !statusHex.isBlank() && !statusHex.equalsIgnoreCase("0x0");
        final String txHash = RpcUtils.stringValue(map.get("transactionHash"));
        final String blockHash = RpcUtils.stringValue(map.get("blockHash"));
        final Long blockNumber = RpcUtils.decodeHexLong(map.get("blockNumber"));
        final String fromHex = RpcUtils.stringValue(map.get("from"));
        final String toHex = RpcUtils.stringValue(map.get("to"));
        final String contractAddress = RpcUtils.stringValue(map.get("contractAddress"));
        final List<LogEntry> logs = LogParser.parseLogs(map.get("logs"));
        final String cumulativeGasUsed = RpcUtils.stringValue(map.get("cumulativeGasUsed"));

        return new TransactionReceipt(
                txHash != null ? new Hash(txHash) : null,
                blockHash != null ? new Hash(blockHash) : null,
                blockNumber != null ? blockNumber : 0L,
                fromHex != null ? new Address(fromHex) : null,
                toHex != null ? new Address(toHex) : null,
                contractAddress != null ? new Address(contractAddress) : null,
                logs,
                status,
                cumulativeGasUsed != null ? new Wei(RpcUtils.decodeHexBigInteger(cumulativeGasUsed)) : null);
    }

    private ValueParts buildValueParts(final TransactionRequest request, final Address from) {
        final String to = request.to() != null ? request.to().value() : null;
        final BigInteger value = request.value() != null ? request.value().value() : BigInteger.ZERO;
        final String data = request.data() != null ? request.data().value() : "0x";

        final boolean has1559 = request.isEip1559();

        if (has1559) {
            final BigInteger maxFee = request.maxFeePerGas() != null ? request.maxFeePerGas().value() : fetchGasPrice();
            final BigInteger maxPriority = request.maxPriorityFeePerGas() != null
                    ? request.maxPriorityFeePerGas().value()
                    : maxFee;
            return new ValueParts(
                    to, value, data, true, null, maxPriority, maxFee);
        }

        final BigInteger gasPrice = request.gasPrice() != null ? request.gasPrice().value() : fetchGasPrice();
        return new ValueParts(to, value, data, false, gasPrice, null, null);
    }

    private BigInteger fetchGasPrice() {
        final String gasPriceHex = callRpc("eth_gasPrice", List.of(), String.class, null);
        return RpcUtils.decodeHexBigInteger(gasPriceHex);
    }

    private BigInteger fetchNonce(final Address from) {
        final String result = callRpc("eth_getTransactionCount", List.of(from.value(), "pending"), String.class, null);
        return RpcUtils.decodeHexBigInteger(result);
    }

    private BigInteger estimateGas(final TransactionRequest request, final Address from) {
        final Map<String, Object> tx = RpcUtils.buildTxObject(request, from);
        final String estimate = RpcUtils.timedEstimateGas(tx, () -> {
            try {
                return callRpc("eth_estimateGas", List.of(tx), String.class, null);
            } catch (RpcException e) {
                handlePotentialRevert(e, null);
                throw e;
            }
        });
        return RpcUtils.decodeHexBigInteger(estimate);
    }

    /**
     * Fetches and caches the chain ID with thread-safe atomic caching.
     *
     * <p>
     * Uses {@link AtomicReference#compareAndExchange} to ensure exactly one
     * thread's value is cached, and all threads return the same cached value.
     * This eliminates the TOCTOU race condition that could occur with separate
     * compareAndSet + get operations.
     *
     * @return the chain ID (guaranteed consistent across all concurrent callers)
     * @throws ChainMismatchException if expectedChainId is set and doesn't match
     */
    private long enforceChainId() {
        // Fast path - already cached
        final Long cached = cachedChainId.get();
        if (cached != null) {
            return cached;
        }

        // Slow path - fetch from network
        final String chainIdHex = callRpc("eth_chainId", List.of(), String.class, null);
        final long actual = RpcUtils.decodeHexBigInteger(chainIdHex).longValue();

        // Validate BEFORE caching to prevent caching an invalid chain ID
        if (expectedChainId > 0 && expectedChainId != actual) {
            throw new ChainMismatchException(expectedChainId, actual);
        }

        // Atomically set if still null, using compareAndExchange to get the witness value.
        // If another thread already cached a value, witness will be non-null and we use it.
        // If we win the race, witness will be null and we return our fetched value.
        final Long witness = cachedChainId.compareAndExchange(null, actual);
        return witness != null ? witness : actual;
    }

    private <T> T callRpc(
            final String method, final List<?> params, final Class<T> responseType, final T defaultValue) {
        try {
            final JsonRpcResponse response = RpcRetry.run(
                    () -> {
                        final JsonRpcResponse res = provider.send(method, params);
                        if (res.hasError()) {
                            final JsonRpcError err = res.error();
                            throw new RpcException(
                                    err.code(),
                                    err.message(),
                                    RpcUtils.extractErrorData(err.data()),
                                    null,
                                    null);
                        }
                        return res;
                    },
                    3);

            final Object result = response.result();
            if (result == null) {
                return defaultValue;
            }
            return MAPPER.convertValue(result, responseType);
        } catch (RpcException e) {
            handlePotentialRevert(e, null);
            throw e;
        }
    }

    /**
     * Extracted transaction values for building and logging.
     *
     * <p>This record normalizes transaction fields from {@link TransactionRequest},
     * converting domain types to raw values and applying defaults where needed.
     * Used internally to avoid repeated null-checks and type conversions.
     *
     * @param to recipient address as hex string, or null for contract creation
     * @param value transfer amount in wei
     * @param data call data as hex string (defaults to "0x" if empty)
     * @param isEip1559 true for EIP-1559 transactions, false for legacy
     * @param gasPrice gas price for legacy transactions (null if EIP-1559)
     * @param maxPriorityFeePerGas priority fee for EIP-1559 transactions (null if legacy)
     * @param maxFeePerGas max fee for EIP-1559 transactions (null if legacy)
     */
    private record ValueParts(
            @Nullable String to,
            BigInteger value,
            String data,
            boolean isEip1559,
            @Nullable BigInteger gasPrice,
            @Nullable BigInteger maxPriorityFeePerGas,
            @Nullable BigInteger maxFeePerGas) {
    }

    private static void handlePotentialRevert(final RpcException e, final Hash hash)
            throws RevertException {
        final String raw = e.data();
        if (raw != null && raw.startsWith("0x") && raw.length() > 10) {
            final RevertDecoder.Decoded decoded = RevertDecoder.decode(raw);
            // Always throw RevertException for revert data, even if kind is UNKNOWN
            // (UNKNOWN just means we couldn't decode it, but it's still a revert)
            DebugLogger.logTx(
                    LogFormatter.formatTxRevert(hash != null ? hash.value() : null, decoded.kind().toString(),
                            decoded.reason()));
            throw new RevertException(decoded.kind(), decoded.reason(), decoded.rawDataHex(), e);
        }
    }
}
