// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

import static io.brane.rpc.internal.RpcUtils.MAPPER;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

import com.fasterxml.jackson.core.type.TypeReference;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.brane.core.RevertDecoder;
import io.brane.core.chain.ChainProfile;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.model.AccessListEntry;
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
import io.brane.rpc.internal.LogParser;
import io.brane.rpc.internal.RpcInvoker;
import io.brane.rpc.internal.RpcUtils;

/**
 * Default implementation of {@link Brane.Reader} for read-only blockchain operations.
 *
 * <p>This implementation provides access to all query operations on the blockchain
 * without transaction signing capability. It uses the configured {@link BraneProvider}
 * for RPC communication and supports automatic retry with exponential backoff.
 *
 * @since 0.1.0
 */
non-sealed class DefaultReader implements Brane.Reader {

    private static final Logger log = LoggerFactory.getLogger(DefaultReader.class);

    private final BraneProvider provider;
    private final @Nullable ChainProfile chain;
    private final int maxRetries;
    private final RpcRetryConfig retryConfig;
    private final AtomicBoolean closed;
    private final RpcInvoker rpc;

    /**
     * Creates a new DefaultReader with the specified configuration.
     *
     * @param provider    the RPC provider for blockchain communication
     * @param chain       the chain profile for network-specific settings (may be null)
     * @param maxRetries  the maximum number of retry attempts for transient failures
     * @param retryConfig the retry configuration for backoff timing
     */
    DefaultReader(
            final BraneProvider provider,
            final @Nullable ChainProfile chain,
            final int maxRetries,
            final RpcRetryConfig retryConfig) {
        this.provider = provider;
        this.chain = chain;
        this.maxRetries = maxRetries;
        this.retryConfig = retryConfig;
        this.closed = new AtomicBoolean(false);
        this.rpc = new RpcInvoker(this::sendWithRetry, this::ensureOpen);
    }

    @Override
    public BigInteger chainId() {
        return rpc.call("eth_chainId", List.of(), RpcUtils::decodeHexBigInteger);
    }

    @Override
    public BigInteger getBalance(final Address address) {
        return rpc.call("eth_getBalance", List.of(address.value(), "latest"), RpcUtils::decodeHexBigInteger);
    }

    @Override
    public HexData getCode(final Address address) {
        return rpc.callWithDefault(
                "eth_getCode",
                List.of(address.value(), "latest"),
                hex -> "0x".equals(hex) ? HexData.EMPTY : new HexData(hex),
                HexData.EMPTY);
    }

    @Override
    public HexData getStorageAt(final Address address, final BigInteger slot) {
        final String slotHex = "0x" + slot.toString(16);
        return rpc.callWithDefault(
                "eth_getStorageAt",
                List.of(address.value(), slotHex, "latest"),
                HexData::new,
                HexData.EMPTY);
    }

    @Override
    public @Nullable BlockHeader getLatestBlock() {
        return getBlockByTag(BlockTag.LATEST.toRpcValue());
    }

    /**
     * Retrieves a block by its tag string.
     *
     * @param tag the block tag (e.g., "latest", "0x1234")
     * @return the block header, or null if not found
     */
    private @Nullable BlockHeader getBlockByTag(final String tag) {
        return rpc.callNullableObject(
                "eth_getBlockByNumber",
                List.of(tag, Boolean.FALSE),
                this::parseBlockHeader);
    }

    /**
     * Parses a block header from the raw JSON-RPC result.
     *
     * @param result the raw result object from JSON-RPC
     * @return the parsed block header
     */
    private BlockHeader parseBlockHeader(final Object result) {
        final Map<String, Object> map = MAPPER.convertValue(
                result, new TypeReference<Map<String, Object>>() {}
        );

        final String hash = RpcUtils.stringValue(map.get("hash"));
        final String parentHash = RpcUtils.stringValue(map.get("parentHash"));
        final Long number = RpcUtils.decodeHexLong(map.get("number"));
        final Long timestamp = RpcUtils.decodeHexLong(map.get("timestamp"));
        final String baseFeeHex = RpcUtils.stringValue(map.get("baseFeePerGas"));

        return new BlockHeader(
                hash != null ? new Hash(hash) : null,
                number,
                parentHash != null ? new Hash(parentHash) : null,
                timestamp,
                baseFeeHex != null ? new Wei(RpcUtils.decodeHexBigInteger(baseFeeHex)) : null);
    }

    @Override
    public @Nullable BlockHeader getBlockByNumber(final long blockNumber) {
        return getBlockByTag("0x" + Long.toHexString(blockNumber));
    }

    @Override
    public @Nullable Transaction getTransactionByHash(final Hash hash) {
        return rpc.callNullableObject(
                "eth_getTransactionByHash",
                List.of(hash.value()),
                this::parseTransaction);
    }

    /**
     * Parses a transaction from the raw JSON-RPC result.
     *
     * @param result the raw result object from JSON-RPC
     * @return the parsed transaction
     * @throws RpcException if required fields are missing
     */
    private Transaction parseTransaction(final Object result) {
        final Map<String, Object> map = MAPPER.convertValue(
                result, new TypeReference<Map<String, Object>>() {}
        );

        final String txHash = RpcUtils.stringValue(map.get("hash"));
        if (txHash == null) {
            throw new RpcException(
                    -32000, "eth_getTransactionByHash response missing 'hash' field", (String) null, (Throwable) null);
        }
        final String from = RpcUtils.stringValue(map.get("from"));
        if (from == null) {
            throw new RpcException(
                    -32000, "eth_getTransactionByHash response missing 'from' field", (String) null, (Throwable) null);
        }
        final String to = RpcUtils.stringValue(map.get("to"));
        final String input = RpcUtils.stringValue(map.get("input"));
        final String valueHex = RpcUtils.stringValue(map.get("value"));
        final long nonce = RpcUtils.decodeHexLong(map.get("nonce"));
        final Object blockNumberObj = map.get("blockNumber");
        final Long blockNumber = blockNumberObj != null ? RpcUtils.decodeHexLong(blockNumberObj) : null;

        return new Transaction(
                new Hash(txHash),
                new Address(from),
                to != null ? new Address(to) : null,
                input != null ? new HexData(input) : HexData.EMPTY,
                new Wei(RpcUtils.decodeHexBigInteger(valueHex)),
                nonce,
                blockNumber);
    }

    @Override
    public @Nullable TransactionReceipt getTransactionReceipt(final Hash hash) {
        return rpc.callNullableObject(
                "eth_getTransactionReceipt",
                List.of(hash.value()),
                this::parseTransactionReceipt);
    }

    /**
     * Parses a transaction receipt from the raw JSON-RPC result.
     *
     * @param result the raw result object from JSON-RPC
     * @return the parsed transaction receipt
     */
    private TransactionReceipt parseTransactionReceipt(final Object result) {
        final Map<String, Object> map = MAPPER.convertValue(
                result, new TypeReference<Map<String, Object>>() {}
        );

        final String txHash = RpcUtils.stringValue(map.get("transactionHash"));
        final String blockHash = RpcUtils.stringValue(map.get("blockHash"));
        final long blockNumber = RpcUtils.decodeHexLong(map.get("blockNumber"));
        final String from = RpcUtils.stringValue(map.get("from"));
        final String to = RpcUtils.stringValue(map.get("to"));
        final String contractAddress = RpcUtils.stringValue(map.get("contractAddress"));
        final String statusHex = RpcUtils.stringValue(map.get("status"));
        final boolean status = statusHex != null && !"0x0".equals(statusHex) && !"0x".equals(statusHex);
        final String cumulativeGasUsedHex = RpcUtils.stringValue(map.get("cumulativeGasUsed"));

        // Parse logs
        final List<LogEntry> logs = parseLogs(map.get("logs"));

        return new TransactionReceipt(
                new Hash(txHash),
                new Hash(blockHash),
                blockNumber,
                new Address(from),
                to != null ? new Address(to) : null,
                contractAddress != null ? new Address(contractAddress) : null,
                logs,
                status,
                new Wei(RpcUtils.decodeHexBigInteger(cumulativeGasUsedHex)));
    }

    /**
     * Parses a list of log entries from the RPC response.
     *
     * @param logsObject the raw logs array from the RPC response
     * @return a list of parsed log entries
     */
    @SuppressWarnings("unchecked")
    private List<LogEntry> parseLogs(final Object logsObject) {
        if (logsObject == null) {
            return List.of();
        }

        final List<Map<String, Object>> logsList = MAPPER.convertValue(
                logsObject, new TypeReference<List<Map<String, Object>>>() {}
        );

        return logsList.stream().map(this::parseLogEntry).toList();
    }

    /**
     * Parses a single log entry from a map.
     *
     * @param logMap the log entry map from the RPC response
     * @return the parsed log entry
     */
    @SuppressWarnings("unchecked")
    private LogEntry parseLogEntry(final Map<String, Object> logMap) {
        final String address = RpcUtils.stringValue(logMap.get("address"));
        final String data = RpcUtils.stringValue(logMap.get("data"));
        final String blockHash = RpcUtils.stringValue(logMap.get("blockHash"));
        final String transactionHash = RpcUtils.stringValue(logMap.get("transactionHash"));
        final long logIndex = RpcUtils.decodeHexLong(logMap.get("logIndex"));
        final Object removedObj = logMap.get("removed");
        final boolean removed = removedObj != null && Boolean.TRUE.equals(removedObj);

        // Parse topics
        final Object topicsObj = logMap.get("topics");
        final List<Hash> topics;
        if (topicsObj instanceof List<?> topicsList) {
            topics = topicsList.stream()
                    .map(t -> new Hash(RpcUtils.stringValue(t)))
                    .toList();
        } else {
            topics = List.of();
        }

        return new LogEntry(
                new Address(address),
                data != null ? new HexData(data) : HexData.EMPTY,
                topics,
                blockHash != null ? new Hash(blockHash) : null,
                new Hash(transactionHash),
                logIndex,
                removed);
    }

    @Override
    public HexData call(final CallRequest request) {
        return call(request, BlockTag.LATEST);
    }

    @Override
    public HexData call(final CallRequest request, final BlockTag blockTag) {
        ensureOpen();
        final JsonRpcResponse response = sendWithRetry(
                "eth_call",
                List.of(request.toMap(), blockTag.toRpcValue()));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            final String data = RpcUtils.extractErrorData(err.data());
            // Check if this is a revert error with data
            if (data != null && data.startsWith("0x") && data.length() > 10) {
                final RevertDecoder.Decoded decoded = RevertDecoder.decode(data);
                throw new RevertException(decoded.kind(), decoded.reason(), decoded.rawDataHex(), null);
            }
            throw new io.brane.core.error.RpcException(
                    err.code(), err.message(), data, (Long) null);
        }
        final Object result = response.result();
        if (result == null) {
            return HexData.EMPTY;
        }
        return new HexData(result.toString());
    }

    @Override
    public List<LogEntry> getLogs(final LogFilter filter) {
        final Map<String, Object> params = buildLogParams(filter);
        return rpc.callObjectWithDefault(
                "eth_getLogs",
                List.of(params),
                result -> LogParser.parseLogs(result, true),
                List.of());
    }

    /**
     * Builds the JSON-RPC parameters map for eth_getLogs.
     *
     * @param filter the log filter criteria
     * @return the parameters map
     */
    private Map<String, Object> buildLogParams(final LogFilter filter) {
        final Map<String, Object> params = new LinkedHashMap<>();
        filter.fromBlock().ifPresent(v -> {
            final String hex = RpcUtils.toHexBlock(v);
            if (hex != null) {
                params.put("fromBlock", hex);
            }
        });
        filter.toBlock().ifPresent(v -> {
            final String hex = RpcUtils.toHexBlock(v);
            if (hex != null) {
                params.put("toBlock", hex);
            }
        });
        // Serialize addresses: single address as string, multiple as array (per eth_getLogs spec)
        filter.addresses().ifPresent(addrs -> {
            if (addrs.size() == 1) {
                params.put("address", addrs.get(0).value());
            } else {
                params.put("address", addrs.stream().map(Address::value).toList());
            }
        });
        filter.topics().ifPresent(topics -> {
            final List<String> topicHex = new ArrayList<>();
            for (Hash h : topics) {
                if (h != null) {
                    topicHex.add(h.value());
                }
            }
            if (!topicHex.isEmpty()) {
                params.put("topics", topicHex);
            }
        });
        return params;
    }

    @Override
    public BigInteger estimateGas(final TransactionRequest request) {
        final Map<String, Object> params = buildEstimateGasParams(request);
        return rpc.call("eth_estimateGas", List.of(params), RpcUtils::decodeHexBigInteger);
    }

    @Override
    public Wei getBlobBaseFee() {
        return rpc.call("eth_blobBaseFee", List.of(), hex -> new Wei(RpcUtils.decodeHexBigInteger(hex)));
    }

    /**
     * Builds the JSON-RPC parameters map for eth_estimateGas.
     *
     * @param request the transaction request
     * @return the parameters map
     */
    private Map<String, Object> buildEstimateGasParams(final TransactionRequest request) {
        final Map<String, Object> params = new LinkedHashMap<>();
        if (request.from() != null) {
            params.put("from", request.from().value());
        }
        if (request.to() != null) {
            params.put("to", request.to().value());
        }
        if (request.value() != null) {
            params.put("value", "0x" + request.value().value().toString(16));
        }
        if (request.data() != null && request.data().byteLength() > 0) {
            params.put("data", request.data().value());
        }
        if (request.gasLimit() != null) {
            params.put("gas", "0x" + Long.toHexString(request.gasLimit()));
        }
        if (request.gasPrice() != null) {
            params.put("gasPrice", "0x" + request.gasPrice().value().toString(16));
        }
        if (request.maxFeePerGas() != null) {
            params.put("maxFeePerGas", "0x" + request.maxFeePerGas().value().toString(16));
        }
        if (request.maxPriorityFeePerGas() != null) {
            params.put("maxPriorityFeePerGas", "0x" + request.maxPriorityFeePerGas().value().toString(16));
        }
        return params;
    }

    @Override
    public AccessListWithGas createAccessList(final TransactionRequest request) {
        ensureOpen();
        final Map<String, Object> params = buildEstimateGasParams(request);
        final JsonRpcResponse response = sendWithRetry(
                "eth_createAccessList",
                List.of(params, BlockTag.LATEST.toRpcValue()));
        if (response.hasError()) {
            throw RpcUtils.toRpcException(response.error());
        }
        final Object result = response.result();
        if (result == null) {
            throw RpcException.fromNullResult("eth_createAccessList");
        }

        final Map<String, Object> map = MAPPER.convertValue(
                result, new TypeReference<Map<String, Object>>() {});

        final String gasUsedHex = RpcUtils.stringValue(map.get("gasUsed"));
        final BigInteger gasUsed = gasUsedHex != null
                ? RpcUtils.decodeHexBigInteger(gasUsedHex)
                : BigInteger.ZERO;

        final List<Map<String, Object>> accessListRaw = MAPPER.convertValue(
                map.get("accessList"),
                new TypeReference<List<Map<String, Object>>>() {});
        final List<AccessListEntry> accessList = new ArrayList<>();
        if (accessListRaw != null) {
            for (final Map<String, Object> entryMap : accessListRaw) {
                final String addressHex = RpcUtils.stringValue(entryMap.get("address"));
                final List<String> storageKeysHex = MAPPER.convertValue(
                        entryMap.get("storageKeys"),
                        new TypeReference<List<String>>() {});
                final List<Hash> storageKeys = (storageKeysHex == null)
                        ? List.of()
                        : storageKeysHex.stream()
                                .filter(Objects::nonNull)
                                .map(Hash::new)
                                .toList();
                if (addressHex != null) {
                    accessList.add(new AccessListEntry(new Address(addressHex), storageKeys));
                }
            }
        }

        return new AccessListWithGas(accessList, gasUsed);
    }

    @Override
    @SuppressWarnings("unchecked")
    public SimulateResult simulate(final SimulateRequest request) {
        ensureOpen();
        final BlockTag blockTag = Objects.requireNonNullElse(request.blockTag(), BlockTag.LATEST);
        final JsonRpcResponse response = sendWithRetry(
                "eth_simulateV1",
                List.of(request.toMap(), blockTag.toRpcValue()));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            // Error code -32601 means "Method not found" - eth_simulateV1 not supported
            if (err.code() == -32601) {
                throw new io.brane.rpc.exception.SimulateNotSupportedException(
                        "eth_simulateV1 is not supported by this node");
            }
            throw RpcUtils.toRpcException(err);
        }
        final Object result = response.result();
        if (result == null) {
            return new SimulateResult(List.of(), null);
        }

        // eth_simulateV1 returns an array of block results (one per simulated block)
        if (result instanceof List<?> listResult) {
            return SimulateResult.fromList((List<Map<String, Object>>) listResult);
        }
        // Fallback for implementations returning a single object
        return SimulateResult.fromMap(MAPPER.convertValue(
                result, new TypeReference<Map<String, Object>>() {}));
    }

    @Override
    public MulticallBatch batch() {
        ensureOpen();
        return MulticallBatch.create(this);
    }

    @Override
    public Subscription onNewHeads(final Consumer<BlockHeader> callback) {
        ensureOpen();
        if (!(provider instanceof WebSocketProvider)) {
            throw new UnsupportedOperationException(
                    "Subscriptions require a WebSocket provider. Use Brane.builder().wsUrl() or a WebSocketProvider.");
        }
        final String id = provider.subscribe("newHeads", List.of(), result -> {
            final Map<String, Object> map = MAPPER.convertValue(
                    result, new TypeReference<Map<String, Object>>() {}
            );

            final String hash = RpcUtils.stringValue(map.get("hash"));
            final String parentHash = RpcUtils.stringValue(map.get("parentHash"));
            final Long number = RpcUtils.decodeHexLong(map.get("number"));
            final Long timestamp = RpcUtils.decodeHexLong(map.get("timestamp"));
            final String baseFeeHex = RpcUtils.stringValue(map.get("baseFeePerGas"));

            final BlockHeader header = new BlockHeader(
                    hash != null ? new Hash(hash) : null,
                    number,
                    parentHash != null ? new Hash(parentHash) : null,
                    timestamp,
                    baseFeeHex != null ? new Wei(RpcUtils.decodeHexBigInteger(baseFeeHex)) : null);

            try {
                callback.accept(header);
            } catch (Exception e) {
                log.error("Exception in newHeads subscription callback (block {})", number, e);
            }
        });
        return new SubscriptionImpl(id, provider);
    }

    @Override
    public Subscription onLogs(final LogFilter filter, final Consumer<LogEntry> callback) {
        ensureOpen();
        if (!(provider instanceof WebSocketProvider)) {
            throw new UnsupportedOperationException(
                    "Subscriptions require a WebSocket provider. Use Brane.builder().wsUrl() or a WebSocketProvider.");
        }
        final Map<String, Object> params = buildLogParams(filter);
        final String id = provider.subscribe("logs", List.of(params), result -> {
            final Map<String, Object> map = MAPPER.convertValue(
                    result, new TypeReference<Map<String, Object>>() {}
            );
            final LogEntry logEntry = LogParser.parseLogStrict(map);
            try {
                callback.accept(logEntry);
            } catch (Exception e) {
                log.error("Exception in logs subscription callback (tx {})", logEntry.transactionHash(), e);
            }
        });
        return new SubscriptionImpl(id, provider);
    }

    @Override
    public Optional<ChainProfile> chain() {
        return Optional.ofNullable(chain);
    }

    @Override
    public boolean canSubscribe() {
        return provider instanceof WebSocketProvider;
    }

    @Override
    public void close() {
        if (closed.compareAndSet(false, true)) {
            provider.close();
        }
    }

    /**
     * Returns the provider used by this reader.
     *
     * @return the RPC provider
     */
    BraneProvider provider() {
        return provider;
    }

    /**
     * Returns the maximum number of retries.
     *
     * @return the max retries
     */
    int maxRetries() {
        return maxRetries;
    }

    /**
     * Returns the retry configuration.
     *
     * @return the retry config
     */
    RpcRetryConfig retryConfig() {
        return retryConfig;
    }

    /**
     * Returns whether this reader has been closed.
     *
     * @return true if closed
     */
    boolean isClosed() {
        return closed.get();
    }

    /**
     * Ensures this reader is not closed.
     *
     * @throws IllegalStateException if this reader has been closed
     */
    void ensureOpen() {
        if (closed.get()) {
            throw new IllegalStateException("This reader has been closed");
        }
    }

    /**
     * Sends an RPC request with automatic retry on transient failures.
     *
     * @param method the JSON-RPC method name
     * @param params the method parameters
     * @return the JSON-RPC response
     */
    JsonRpcResponse sendWithRetry(final String method, final List<?> params) {
        return RpcRetry.runRpc(() -> provider.send(method, params), maxRetries + 1, retryConfig);
    }

    /**
     * Subscription implementation that handles unsubscribe errors gracefully.
     *
     * <p><strong>Idempotency:</strong> Calling {@link #unsubscribe()} multiple times
     * is safe and has no additional effect after the first call.
     *
     * <p><strong>Error Handling:</strong> Unsubscribe failures are logged at WARN level
     * and do not throw exceptions. This prevents resource cleanup issues when the
     * subscription is already terminated (e.g., WebSocket disconnected).
     */
    private static final class SubscriptionImpl implements Subscription {
        private final String id;
        private final BraneProvider provider;
        private final AtomicBoolean unsubscribed = new AtomicBoolean(false);

        SubscriptionImpl(final String id, final BraneProvider provider) {
            this.id = id;
            this.provider = provider;
        }

        @Override
        public String id() {
            return id;
        }

        @Override
        public void unsubscribe() {
            // Idempotent: only unsubscribe once
            if (!unsubscribed.compareAndSet(false, true)) {
                log.debug("Subscription {} already unsubscribed, ignoring duplicate call", id);
                return;
            }

            try {
                provider.unsubscribe(id);
                log.debug("Successfully unsubscribed from {}", id);
            } catch (Exception e) {
                // Log and swallow - unsubscribe failures should not propagate
                // Common causes: connection already closed, subscription already terminated
                log.warn("Failed to unsubscribe from {}: {} ({})",
                        id, e.getMessage(), e.getClass().getSimpleName());
            }
        }
    }
}
