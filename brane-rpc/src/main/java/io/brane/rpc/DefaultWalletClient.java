package io.brane.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.brane.core.DebugLogger;
import io.brane.core.LogFormatter;
import io.brane.core.RevertDecoder;
import io.brane.core.chain.ChainProfile;
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
import io.brane.rpc.internal.RpcUtils;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Default implementation of {@link WalletClient} with automatic transaction
 * preparation.
 * 
 * <p>
 * This class implements the complete transaction lifecycle:
 * <ol>
 * <li>Fills missing gas parameters via {@link SmartGasStrategy}</li>
 * <li>Fetches nonce via {@code eth_getTransactionCount} if not provided</li>
 * <li>Signs the transaction using {@link TransactionSigner}</li>
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
 * @see WalletClient
 * @see SmartGasStrategy
 * @see TransactionSigner
 */
public final class DefaultWalletClient implements WalletClient {

    private final BraneProvider provider;

    private final TransactionSigner signer;
    private final Address senderAddress;
    private final long expectedChainId;

    private final SmartGasStrategy gasStrategy;
    private final ObjectMapper mapper = new ObjectMapper();
    private final AtomicReference<Long> cachedChainId = new AtomicReference<>();

    private DefaultWalletClient(
            final BraneProvider provider,
            final TransactionSigner signer,
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
            final PublicClient publicClient,
            final TransactionSigner signer,
            final Address senderAddress,
            final long expectedChainId,
            final ChainProfile chainProfile) {
        final SmartGasStrategy gasStrategy = new SmartGasStrategy(publicClient, provider, chainProfile);
        return new DefaultWalletClient(
                provider, signer, senderAddress, expectedChainId, gasStrategy);
    }

    public static DefaultWalletClient from(
            final BraneProvider provider,
            final PublicClient publicClient,
            final TransactionSigner signer,
            final Address senderAddress,
            final long expectedChainId,
            final ChainProfile chainProfile,
            final BigInteger gasLimitBufferNumerator,
            final BigInteger gasLimitBufferDenominator) {
        final SmartGasStrategy gasStrategy = new SmartGasStrategy(
                publicClient, provider, chainProfile, gasLimitBufferNumerator, gasLimitBufferDenominator);
        return new DefaultWalletClient(
                provider,
                signer,
                senderAddress,
                expectedChainId,
                gasStrategy);
    }

    public static DefaultWalletClient from(
            final BraneProvider provider,
            final PublicClient publicClient,
            final TransactionSigner signer,
            final Address senderAddress,
            final long expectedChainId) {
        final ChainProfile profile = ChainProfile.of(expectedChainId, null, true, Wei.of(1_000_000_000L));
        return from(provider, publicClient, signer, senderAddress, expectedChainId, profile);
    }

    public static DefaultWalletClient create(
            final BraneProvider provider,
            final PublicClient publicClient,
            final TransactionSigner signer,
            final Address senderAddress,
            final ChainProfile chainProfile) {
        final SmartGasStrategy gasStrategy = new SmartGasStrategy(publicClient, provider, chainProfile);
        return new DefaultWalletClient(
                provider, signer, senderAddress, 0L, gasStrategy);
    }

    public static DefaultWalletClient create(
            final BraneProvider provider,
            final PublicClient publicClient,
            final TransactionSigner signer,
            final Address senderAddress) {
        return from(provider, publicClient, signer, senderAddress, 0L);
    }

    public static DefaultWalletClient create(
            final BraneProvider provider,
            final PublicClient publicClient,
            final TransactionSigner signer,
            final Address senderAddress,
            final ChainProfile chainProfile,
            final BigInteger gasLimitBufferNumerator,
            final BigInteger gasLimitBufferDenominator) {
        return from(
                provider,
                publicClient,
                signer,
                senderAddress,
                0L,
                chainProfile,
                gasLimitBufferNumerator,
                gasLimitBufferDenominator);
    }

    @Override
    public Hash sendTransaction(final TransactionRequest request) {
        final long chainId = enforceChainId();

        final Address from = request.from() != null ? request.from() : senderAddress;
        final TransactionRequest withDefaults = gasStrategy.applyDefaults(request, from);

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

        final String signedHex = signer.sign(unsignedTx, chainId);
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

    @Override
    public TransactionReceipt sendTransactionAndWait(
            final TransactionRequest request, final long timeoutMillis, final long pollIntervalMillis) {
        final Hash txHash = sendTransaction(request);
        DebugLogger.logTx(LogFormatter.formatTxWait(txHash.value(), timeoutMillis));
        final Instant deadline = Instant.now().plus(Duration.ofMillis(timeoutMillis));

        while (Instant.now().isBefore(deadline)) {
            final TransactionReceipt receipt = fetchReceipt(txHash);
            if (receipt != null) {
                DebugLogger.logTx(
                        LogFormatter.formatTxReceipt(txHash.value(), receipt.blockNumber(), receipt.status()));
                return receipt;
            }
            try {
                Thread.sleep(pollIntervalMillis);
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
        final Map<String, Object> map = mapper.convertValue(result, Map.class);
        final String statusHex = RpcUtils.stringValue(map.get("status"));
        final boolean status = statusHex != null && !statusHex.isBlank() && !statusHex.equalsIgnoreCase("0x0");
        final String txHash = RpcUtils.stringValue(map.get("transactionHash"));
        final String blockHash = RpcUtils.stringValue(map.get("blockHash"));
        final Long blockNumber = RpcUtils.decodeHexLong(map.get("blockNumber"));
        final String fromHex = RpcUtils.stringValue(map.get("from"));
        final String toHex = RpcUtils.stringValue(map.get("to"));
        final String contractAddress = RpcUtils.stringValue(map.get("contractAddress"));
        final List<LogEntry> logs = parseLogs(map.get("logs"));
        final String cumulativeGasUsed = RpcUtils.stringValue(map.get("cumulativeGasUsed"));

        return new TransactionReceipt(
                txHash != null ? new Hash(txHash) : null,
                blockHash != null ? new Hash(blockHash) : null,
                blockNumber != null ? blockNumber : 0L,
                fromHex != null ? new Address(fromHex) : null,
                toHex != null ? new Address(toHex) : null,
                contractAddress != null ? new HexData(contractAddress) : HexData.EMPTY,
                logs,
                status,
                cumulativeGasUsed != null ? new Wei(RpcUtils.decodeHexBigInteger(cumulativeGasUsed)) : null);
    }

    private List<LogEntry> parseLogs(final Object value) {
        if (value == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> rawLogs = mapper.convertValue(value, List.class);
        final List<LogEntry> logs = new ArrayList<>(rawLogs.size());
        for (Map<String, Object> log : rawLogs) {
            final String address = RpcUtils.stringValue(log.get("address"));
            final String data = RpcUtils.stringValue(log.get("data"));
            final String blockHash = RpcUtils.stringValue(log.get("blockHash"));
            final String txHash = RpcUtils.stringValue(log.get("transactionHash"));
            final Long logIndex = RpcUtils.decodeHexLong(log.get("logIndex"));
            @SuppressWarnings("unchecked")
            final List<String> topicsHex = mapper.convertValue(log.get("topics"), List.class);
            final List<Hash> topics = new ArrayList<>();
            if (topicsHex != null) {
                for (String t : topicsHex) {
                    topics.add(new Hash(t));
                }
            }
            logs.add(
                    new LogEntry(
                            address != null ? new Address(address) : null,
                            data != null ? new HexData(data) : HexData.EMPTY,
                            topics,
                            blockHash != null ? new Hash(blockHash) : null,
                            txHash != null ? new Hash(txHash) : null,
                            logIndex != null ? logIndex : 0L,
                            Boolean.TRUE.equals(log.get("removed"))));
        }
        return logs;
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
        final Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("from", from.value());
        request.toOpt().ifPresent(address -> tx.put("to", address.value()));
        request.valueOpt().ifPresent(v -> tx.put("value", RpcUtils.toQuantityHex(v.value())));
        if (request.data() != null) {
            tx.put("data", request.data().value());
        }
        if (request.accessList() != null && !request.accessList().isEmpty()) {
            tx.put("accessList", toJsonAccessList(request.accessList()));
        }
        DebugLogger.logTx(LogFormatter.formatEstimateGas(
                String.valueOf(from), String.valueOf(tx.get("to")), String.valueOf(tx.get("data"))));
        final long start = System.nanoTime();
        final String estimate;
        try {
            estimate = callRpc("eth_estimateGas", List.of(tx), String.class, null);
        } catch (RpcException e) {
            handlePotentialRevert(e, null);
            throw e;
        }
        final long durationMicros = (System.nanoTime() - start) / 1_000L;
        DebugLogger.logTx(LogFormatter.formatEstimateGasResult(durationMicros, estimate));
        return RpcUtils.decodeHexBigInteger(estimate);
    }

    private long enforceChainId() {
        final Long cached = cachedChainId.get();
        if (cached != null) {
            return cached;
        }
        final String chainIdHex = callRpc("eth_chainId", List.of(), String.class, null);
        final long actual = RpcUtils.decodeHexBigInteger(chainIdHex).longValue();
        cachedChainId.set(actual);
        if (expectedChainId > 0 && expectedChainId != actual) {
            throw new ChainMismatchException(expectedChainId, actual);
        }
        return actual;
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
            return mapper.convertValue(result, responseType);
        } catch (RpcException e) {
            handlePotentialRevert(e, null);
            throw e;
        }
    }

    private List<Map<String, Object>> toJsonAccessList(final List<io.brane.core.model.AccessListEntry> entries) {
        final List<Map<String, Object>> list = new ArrayList<>(entries.size());
        for (var entry : entries) {
            final Map<String, Object> map = new LinkedHashMap<>();
            map.put("address", entry.address().value());
            map.put("storageKeys", entry.storageKeys().stream().map(Hash::value).toList());
            list.add(map);
        }
        return list;
    }

    private record ValueParts(
            String to,
            BigInteger value,
            String data,
            boolean isEip1559,
            BigInteger gasPrice,
            BigInteger maxPriorityFeePerGas,
            BigInteger maxFeePerGas) {
    }

    private static void handlePotentialRevert(final RpcException e, final Hash hash)
            throws RevertException {
        final String raw = e.data();
        if (raw != null && raw.startsWith("0x") && raw.length() > 10) {
            final var decoded = RevertDecoder.decode(raw);
            // Always throw RevertException for revert data, even if kind is UNKNOWN
            // (UNKNOWN just means we couldn't decode it, but it's still a revert)
            DebugLogger.logTx(
                    LogFormatter.formatTxRevert(hash != null ? hash.value() : null, decoded.kind().toString(),
                            decoded.reason()));
            throw new RevertException(decoded.kind(), decoded.reason(), decoded.rawDataHex(), e);
        }
    }
}
