package io.brane.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.brane.core.DebugLogger;
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
import io.brane.internal.web3j.crypto.RawTransaction;
import io.brane.internal.web3j.utils.Numeric;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class DefaultWalletClient implements WalletClient {

    private final BraneProvider provider;
    private final PublicClient publicClient;
    private final TransactionSigner signer;
    private final Address senderAddress;
    private final long expectedChainId;
    private final ChainProfile chainProfile;
    private final SmartGasStrategy gasStrategy;
    private final ObjectMapper mapper = new ObjectMapper();
    private Long cachedChainId;

    private DefaultWalletClient(
            final BraneProvider provider,
            final PublicClient publicClient,
            final TransactionSigner signer,
            final Address senderAddress,
            final long expectedChainId,
            final ChainProfile chainProfile,
            final SmartGasStrategy gasStrategy) {
        this.provider = Objects.requireNonNull(provider, "provider");
        this.publicClient = Objects.requireNonNull(publicClient, "publicClient");
        this.signer = Objects.requireNonNull(signer, "signer");
        this.senderAddress = Objects.requireNonNull(senderAddress, "senderAddress");
        this.expectedChainId = expectedChainId;
        this.chainProfile = Objects.requireNonNull(chainProfile, "chainProfile");
        this.gasStrategy = Objects.requireNonNull(gasStrategy, "gasStrategy");
    }

    public static DefaultWalletClient from(
            final BraneProvider provider,
            final PublicClient publicClient,
            final TransactionSigner signer,
            final Address senderAddress,
            final long expectedChainId,
            final ChainProfile chainProfile) {
        final SmartGasStrategy gasStrategy =
                new SmartGasStrategy(publicClient, provider, chainProfile);
        return new DefaultWalletClient(
                provider, publicClient, signer, senderAddress, expectedChainId, chainProfile, gasStrategy);
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
        final SmartGasStrategy gasStrategy =
                new SmartGasStrategy(
                        publicClient, provider, chainProfile, gasLimitBufferNumerator, gasLimitBufferDenominator);
        return new DefaultWalletClient(
                provider,
                publicClient,
                signer,
                senderAddress,
                expectedChainId,
                chainProfile,
                gasStrategy);
    }

    public static DefaultWalletClient from(
            final BraneProvider provider,
            final PublicClient publicClient,
            final TransactionSigner signer,
            final Address senderAddress,
            final long expectedChainId) {
        final ChainProfile profile =
                ChainProfile.of(expectedChainId, null, true, Wei.of(1_000_000_000L));
        return from(provider, publicClient, signer, senderAddress, expectedChainId, profile);
    }

    public static DefaultWalletClient create(
            final BraneProvider provider,
            final PublicClient publicClient,
            final TransactionSigner signer,
            final Address senderAddress,
            final ChainProfile chainProfile) {
        final SmartGasStrategy gasStrategy =
                new SmartGasStrategy(publicClient, provider, chainProfile);
        return new DefaultWalletClient(
                provider, publicClient, signer, senderAddress, 0L, chainProfile, gasStrategy);
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

        final BigInteger nonce =
                withDefaults.nonceOpt().map(BigInteger::valueOf).orElseGet(() -> fetchNonce(from));

        final BigInteger gasLimit =
                withDefaults
                        .gasLimitOpt()
                        .map(BigInteger::valueOf)
                        .orElseGet(() -> estimateGas(withDefaults, from));

        final ValueParts valueParts = buildValueParts(withDefaults, from);

        final RawTransaction rawTx =
                valueParts.isEip1559
                        ? RawTransaction.createTransaction(
                                chainId,
                                nonce,
                                gasLimit,
                                valueParts.to,
                                valueParts.value,
                                valueParts.data,
                                valueParts.maxPriorityFeePerGas,
                                valueParts.maxFeePerGas)
                        : RawTransaction.createTransaction(
                                nonce,
                                valueParts.gasPrice,
                                gasLimit,
                                valueParts.to,
                                valueParts.value,
                                valueParts.data);

        DebugLogger.log(
                "[TX-SEND] from=%s to=%s nonce=%s gasLimit=%s value=%s",
                from,
                valueParts.to,
                nonce,
                gasLimit,
                valueParts.value);

        final String signedHex = signer.sign(rawTx);
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
        DebugLogger.log("[TX-HASH] hash=%s durationMicros=%s", txHash, durationMicros);
        return new Hash(txHash);
    }

    @Override
    public TransactionReceipt sendTransactionAndWait(
            final TransactionRequest request, final long timeoutMillis, final long pollIntervalMillis) {
        final Hash txHash = sendTransaction(request);
        DebugLogger.log("[TX-WAIT] hash=%s timeoutMillis=%s", txHash.value(), timeoutMillis);
        final Instant deadline = Instant.now().plus(Duration.ofMillis(timeoutMillis));

        while (Instant.now().isBefore(deadline)) {
            final TransactionReceipt receipt = fetchReceipt(txHash);
            if (receipt != null) {
                DebugLogger.log(
                        "[TX-RECEIPT] hash=%s block=%s status=%s",
                        txHash.value(),
                        receipt.blockNumber(),
                        receipt.status());
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
        final JsonRpcResponse response =
                provider.send("eth_getTransactionReceipt", List.of(hash.value()));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new RpcException(err.code(), err.message(), extractErrorData(err.data()), null, null);
        }
        final Object result = response.result();
        if (result == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        final Map<String, Object> map = mapper.convertValue(result, Map.class);
        final String statusHex = stringValue(map.get("status"));
        final boolean status =
                statusHex != null && !statusHex.isBlank() && !statusHex.equalsIgnoreCase("0x0");
        final String txHash = stringValue(map.get("transactionHash"));
        final String blockHash = stringValue(map.get("blockHash"));
        final Long blockNumber = decodeHexLong(map.get("blockNumber"));
        final String fromHex = stringValue(map.get("from"));
        final String toHex = stringValue(map.get("to"));
        final String contractAddress = stringValue(map.get("contractAddress"));
        final List<LogEntry> logs = parseLogs(map.get("logs"));
        final String cumulativeGasUsed = stringValue(map.get("cumulativeGasUsed"));

        return new TransactionReceipt(
                txHash != null ? new Hash(txHash) : null,
                blockHash != null ? new Hash(blockHash) : null,
                blockNumber != null ? blockNumber : 0L,
                fromHex != null ? new Address(fromHex) : null,
                toHex != null ? new Address(toHex) : null,
                contractAddress != null ? new HexData(contractAddress) : HexData.EMPTY,
                logs,
                status,
                cumulativeGasUsed != null ? new Wei(decodeHexBigInteger(cumulativeGasUsed)) : null);
    }

    private List<LogEntry> parseLogs(final Object value) {
        if (value == null) {
            return List.of();
        }
        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> rawLogs = mapper.convertValue(value, List.class);
        final List<LogEntry> logs = new ArrayList<>(rawLogs.size());
        for (Map<String, Object> log : rawLogs) {
            final String address = stringValue(log.get("address"));
            final String data = stringValue(log.get("data"));
            final String blockHash = stringValue(log.get("blockHash"));
            final String txHash = stringValue(log.get("transactionHash"));
            final Long logIndex = decodeHexLong(log.get("logIndex"));
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
            final BigInteger maxFee =
                    request.maxFeePerGas() != null ? request.maxFeePerGas().value() : fetchGasPrice();
            final BigInteger maxPriority =
                    request.maxPriorityFeePerGas() != null
                            ? request.maxPriorityFeePerGas().value()
                            : maxFee;
            return new ValueParts(
                    to, value, data, true, null, maxPriority, maxFee);
        }

        final BigInteger gasPrice =
                request.gasPrice() != null ? request.gasPrice().value() : fetchGasPrice();
        return new ValueParts(to, value, data, false, gasPrice, null, null);
    }

    private BigInteger fetchGasPrice() {
        final String gasPriceHex = callRpc("eth_gasPrice", List.of(), String.class, null);
        return Numeric.decodeQuantity(gasPriceHex);
    }

    private BigInteger fetchNonce(final Address from) {
        final String result =
                callRpc("eth_getTransactionCount", List.of(from.value(), "pending"), String.class, null);
        return Numeric.decodeQuantity(result);
    }

    private BigInteger estimateGas(final TransactionRequest request, final Address from) {
        final Map<String, Object> tx = new LinkedHashMap<>();
        tx.put("from", from.value());
        request.toOpt().ifPresent(address -> tx.put("to", address.value()));
        request.valueOpt().ifPresent(v -> tx.put("value", toQuantityHex(v.value())));
        if (request.data() != null) {
            tx.put("data", request.data().value());
        }
        DebugLogger.log("[ESTIMATE-GAS] from=%s to=%s data=%s", from, tx.get("to"), tx.get("data"));
        final long start = System.nanoTime();
        final String estimate;
        try {
            estimate = callRpc("eth_estimateGas", List.of(tx), String.class, null);
        } catch (RpcException e) {
            handlePotentialRevert(e, null);
            throw e;
        }
        final long durationMicros = (System.nanoTime() - start) / 1_000L;
        DebugLogger.log("[ESTIMATE-GAS-RESULT] durationMicros=%s gas=%s", durationMicros, estimate);
        return Numeric.decodeQuantity(estimate);
    }

    private long enforceChainId() {
        if (cachedChainId != null) {
            return cachedChainId;
        }
        final String chainIdHex = callRpc("eth_chainId", List.of(), String.class, null);
        final long actual = Numeric.decodeQuantity(chainIdHex).longValue();
        cachedChainId = actual;
        if (expectedChainId > 0 && expectedChainId != actual) {
            throw new ChainMismatchException(expectedChainId, actual);
        }
        return actual;
    }

    private <T> T callRpc(
            final String method, final List<?> params, final Class<T> responseType, final T defaultValue) {
        try {
            final JsonRpcResponse response =
                    RpcRetry.run(
                            () -> {
                                final JsonRpcResponse res = provider.send(method, params);
                                if (res.hasError()) {
                                    final JsonRpcError err = res.error();
                throw new RpcException(
                        err.code(),
                        err.message(),
                        extractErrorData(err.data()),
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

    private String extractErrorData(final Object dataValue) {
        return switch (dataValue) {
            case null -> null;
            case String s when s.trim().startsWith("0x") -> s;
            case Map<?, ?> map -> extractFromIterable(map.values(), dataValue);
            case Iterable<?> iterable -> extractFromIterable(iterable, dataValue);
            case Object array when dataValue.getClass().isArray() -> extractFromArray(array, dataValue);
            default -> dataValue.toString();
        };
    }

    private String extractFromIterable(final Iterable<?> iterable, final Object fallback) {
        for (Object value : iterable) {
            final String nested = extractErrorData(value);
            if (nested != null) {
                return nested;
            }
        }
        return fallback.toString();
    }

    private String extractFromArray(final Object array, final Object fallback) {
        final int length = Array.getLength(array);
        for (int i = 0; i < length; i++) {
            final String nested = extractErrorData(Array.get(array, i));
            if (nested != null) {
                return nested;
            }
        }
        return fallback.toString();
    }

    private String stringValue(final Object value) {
        return value != null ? value.toString() : null;
    }

    private Long decodeHexLong(final Object value) {
        final String hex = stringValue(value);
        if (hex == null || hex.isEmpty()) {
            return null;
        }
        final String normalized = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (normalized.isEmpty()) {
            return 0L;
        }
        return new BigInteger(normalized, 16).longValue();
    }

    private BigInteger decodeHexBigInteger(final String hex) {
        if (hex == null || hex.isEmpty()) {
            return BigInteger.ZERO;
        }
        final String normalized = hex.startsWith("0x") ? hex.substring(2) : hex;
        if (normalized.isEmpty()) {
            return BigInteger.ZERO;
        }
        return new BigInteger(normalized, 16);
    }

    private String toQuantityHex(final BigInteger value) {
        return "0x" + value.toString(16);
    }

    private record ValueParts(
            String to,
            BigInteger value,
            String data,
            boolean isEip1559,
            BigInteger gasPrice,
            BigInteger maxPriorityFeePerGas,
            BigInteger maxFeePerGas) {}

    private static void handlePotentialRevert(final RpcException e, final Hash hash)
            throws RevertException {
        final String raw = e.data();
        if (raw != null && raw.startsWith("0x") && raw.length() > 10) {
            final var decoded = RevertDecoder.decode(raw);
            // Always throw RevertException for revert data, even if kind is UNKNOWN
            // (UNKNOWN just means we couldn't decode it, but it's still a revert)
            DebugLogger.log(
                    "[TX-REVERT] hash=%s kind=%s reason=%s", hash != null ? hash.value() : "unknown", decoded.kind(), decoded.reason());
            throw new RevertException(decoded.kind(), decoded.reason(), decoded.rawDataHex(), e);
        }
    }
}
