// SPDX-License-Identifier: MIT OR Apache-2.0
package sh.brane.rpc;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.jspecify.annotations.Nullable;

import sh.brane.core.DebugLogger;
import sh.brane.core.LogFormatter;
import sh.brane.core.RevertDecoder;
import sh.brane.core.chain.ChainProfile;
import sh.brane.core.error.ChainMismatchException;
import sh.brane.core.error.InvalidSenderException;
import sh.brane.core.error.RevertException;
import sh.brane.core.error.RpcException;
import sh.brane.core.model.BlobTransactionRequest;
import sh.brane.core.model.TransactionReceipt;
import sh.brane.core.model.TransactionRequest;
import sh.brane.core.types.Address;
import sh.brane.core.types.Hash;
import sh.brane.core.types.HexData;
import sh.brane.core.types.Wei;
import sh.brane.rpc.internal.RpcUtils;

/**
 * Default implementation of {@link Brane.Signer} for full blockchain operations.
 *
 * <p>This implementation extends {@link DefaultReader} to inherit all read operations,
 * and adds transaction signing and sending capabilities using the configured
 * {@link Signer} and {@link SmartGasStrategy}.
 *
 * @since 0.1.0
 */
non-sealed class DefaultSigner extends DefaultReader implements Brane.Signer {

    private final sh.brane.core.crypto.Signer signer;
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
            final sh.brane.core.crypto.Signer signer,
            final @Nullable ChainProfile chain,
            final int maxRetries,
            final RpcRetryConfig retryConfig) {
        super(provider, chain, maxRetries, retryConfig);
        this.signer = signer;
        final ChainProfile resolvedChain = chain != null ? chain : defaultChainProfile();
        this.gasStrategy = new SmartGasStrategy(this, provider, resolvedChain);
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
                .orElseGet(() -> estimateGas(withDefaults));

        // Prepare value and data
        final Wei valueOrZero = Objects.requireNonNullElse(withDefaults.value(), Wei.ZERO);
        final HexData dataOrEmpty = Objects.requireNonNullElse(withDefaults.data(), HexData.EMPTY);

        // Build unsigned transaction
        final sh.brane.core.tx.UnsignedTransaction unsignedTx;
        if (withDefaults.isEip1559()) {
            final Wei maxPriority = withDefaults.maxPriorityFeePerGas() != null
                    ? withDefaults.maxPriorityFeePerGas()
                    : Wei.of(1_000_000_000L);
            final Wei maxFee = withDefaults.maxFeePerGas() != null
                    ? withDefaults.maxFeePerGas()
                    : maxPriority;

            unsignedTx = new sh.brane.core.tx.Eip1559Transaction(
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

            unsignedTx = new sh.brane.core.tx.LegacyTransaction(
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
        final sh.brane.core.crypto.Signature baseSig = signer.signTransaction(unsignedTx, chainId);

        // Adjust V value for legacy transactions (EIP-155)
        final sh.brane.core.crypto.Signature signature;
        if (unsignedTx instanceof sh.brane.core.tx.LegacyTransaction) {
            // For legacy transactions, use EIP-155 encoding: v = chainId * 2 + 35 + yParity
            final int v = (int) (chainId * 2 + 35 + baseSig.v());
            signature = new sh.brane.core.crypto.Signature(baseSig.r(), baseSig.s(), v);
        } else {
            // For EIP-1559, v is just yParity (0 or 1)
            signature = baseSig;
        }

        // Encode and send
        final byte[] envelope = unsignedTx.encodeAsEnvelope(signature);
        final String signedHex = sh.brane.primitives.Hex.encode(envelope);

        final String txHash;
        final long start = System.nanoTime();
        try {
            final JsonRpcResponse response = sendWithRetry(
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
                throw RpcException.fromNullResult("eth_sendRawTransaction");
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
        final long actual = chainId().longValue();

        // Validate chain ID against configured chain profile (if present)
        chain().ifPresent(profile -> {
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
        final JsonRpcResponse response = sendWithRetry(
                "eth_getTransactionCount", List.of(from.value(), "pending"));
        if (response.hasError()) {
            throw RpcUtils.toRpcException(response.error());
        }
        final Object result = response.result();
        if (result == null) {
            throw RpcException.fromNullResult("eth_getTransactionCount");
        }
        return RpcUtils.decodeHexBigInteger(result.toString());
    }

    /**
     * Fetches the current gas price.
     */
    private BigInteger fetchGasPrice() {
        final JsonRpcResponse response = sendWithRetry("eth_gasPrice", List.of());
        if (response.hasError()) {
            throw RpcUtils.toRpcException(response.error());
        }
        final Object result = response.result();
        if (result == null) {
            throw RpcException.fromNullResult("eth_gasPrice");
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
            throw new sh.brane.core.error.RevertException(decoded.kind(), decoded.reason(), decoded.rawDataHex(), null);
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
            final JsonRpcResponse response = provider().send("eth_call", List.of(tx, blockNumber));
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
    public Hash sendBlobTransaction(final BlobTransactionRequest request) {
        final long chainId = fetchAndCacheChainId();
        final Address from = request.from() != null ? request.from() : signer.address();

        // Fetch nonce if not provided
        final long nonce = request.nonceOpt()
                .orElseGet(() -> fetchNonce(from).longValue());

        // Use provided gas limit or estimate
        final long gasLimit = request.gasLimitOpt()
                .orElseGet(() -> {
                    // Build a TransactionRequest for gas estimation
                    final TransactionRequest estRequest = new TransactionRequest(
                            from,
                            request.to(),
                            request.value(),
                            null, // gasLimit (to be estimated)
                            null, // gasPrice
                            null, // maxPriorityFeePerGas
                            null, // maxFeePerGas
                            null, // nonce
                            request.data(),
                            true, // isEip1559 (EIP-4844 uses EIP-1559 fees)
                            request.accessList());
                    return estimateGas(estRequest).longValue();
                });

        // Get fee parameters - use SmartGasStrategy for EIP-1559 fee estimation
        final Wei maxPriorityFeePerGas;
        final Wei maxFeePerGas;
        if (request.maxPriorityFeePerGas() != null && request.maxFeePerGas() != null) {
            maxPriorityFeePerGas = request.maxPriorityFeePerGas();
            maxFeePerGas = request.maxFeePerGas();
        } else {
            // Create a dummy TransactionRequest to use SmartGasStrategy for fee estimation
            final TransactionRequest dummyRequest = new TransactionRequest(
                    from,
                    request.to(),
                    request.value(),
                    gasLimit,
                    null, // gasPrice
                    request.maxPriorityFeePerGas(),
                    request.maxFeePerGas(),
                    nonce,
                    request.data(),
                    true, // isEip1559
                    request.accessList());
            final SmartGasStrategy.GasFilledRequest filled = gasStrategy.applyDefaults(dummyRequest, from);
            maxPriorityFeePerGas = filled.request().maxPriorityFeePerGas();
            maxFeePerGas = filled.request().maxFeePerGas();
        }

        final Wei maxFeePerBlobGas = request.maxFeePerBlobGasOpt()
                .orElseGet(() -> {
                    // Get current blob base fee and add buffer (2x)
                    final Wei blobBaseFee = getBlobBaseFee();
                    return Wei.of(blobBaseFee.value().multiply(BigInteger.TWO));
                });

        // Build the unsigned EIP-4844 transaction
        final sh.brane.core.tx.Eip4844Transaction unsignedTx = new sh.brane.core.tx.Eip4844Transaction(
                chainId,
                nonce,
                maxPriorityFeePerGas,
                maxFeePerGas,
                gasLimit,
                request.to(),
                request.valueOpt().orElse(Wei.ZERO),
                Objects.requireNonNullElse(request.data(), HexData.EMPTY),
                request.accessListOrEmpty(),
                maxFeePerBlobGas,
                request.blobVersionedHashes());

        final Wei valueOrZero = request.valueOpt().orElse(Wei.ZERO);
        DebugLogger.logTx(LogFormatter.formatTxSend(
                from.value(),
                request.to().value(),
                BigInteger.valueOf(nonce),
                BigInteger.valueOf(gasLimit),
                valueOrZero.value()));

        // Sign the transaction
        final sh.brane.core.crypto.Signature signature = signer.signTransaction(unsignedTx, chainId);

        // Encode with blob sidecar for network transmission
        final byte[] envelope = unsignedTx.encodeAsNetworkWrapper(signature, request.sidecar());
        final String signedHex = sh.brane.primitives.Hex.encode(envelope);

        final String txHash;
        final long start = System.nanoTime();
        try {
            final JsonRpcResponse response = sendWithRetry(
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
                throw RpcException.fromNullResult("eth_sendRawTransaction");
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

    @Override
    public sh.brane.core.crypto.Signer signer() {
        return signer;
    }
}
