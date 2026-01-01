package io.brane.rpc;

import static io.brane.rpc.internal.RpcUtils.MAPPER;
import com.fasterxml.jackson.core.type.TypeReference;
import io.brane.core.DebugLogger;
import io.brane.core.LogFormatter;
import io.brane.core.model.AccessListEntry;
import io.brane.core.model.AccessListWithGas;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.LogEntry;
import io.brane.core.model.Transaction;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.rpc.internal.LogParser;
import io.brane.rpc.internal.RpcUtils;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.jspecify.annotations.Nullable;

/**
 * Default implementation of {@link PublicClient} for read-only blockchain operations.
 *
 * <p>This class provides implementations of all read operations defined in
 * {@link PublicClient}, translating method calls to JSON-RPC requests via
 * the underlying {@link BraneProvider}.
 *
 * <p><strong>Supported Operations:</strong>
 * <ul>
 *   <li>Block queries ({@code eth_getBlockByNumber})</li>
 *   <li>Transaction queries ({@code eth_getTransactionByHash})</li>
 *   <li>Balance queries ({@code eth_getBalance})</li>
 *   <li>Contract calls ({@code eth_call})</li>
 *   <li>Event log queries ({@code eth_getLogs})</li>
 *   <li>Access list creation ({@code eth_createAccessList})</li>
 *   <li>Chain ID queries ({@code eth_chainId})</li>
 *   <li>Subscription management (newHeads, logs)</li>
 * </ul>
 *
 * <p><strong>Retry Behavior:</strong> All RPC calls are automatically retried
 * on transient failures using {@link RpcRetry} with 3 attempts and exponential
 * backoff. Non-retryable errors (reverts, insufficient funds) fail immediately.
 *
 * <p><strong>Thread Safety:</strong> This class is thread-safe. All methods
 * can be called concurrently from multiple threads. Thread safety is provided
 * by the underlying {@link BraneProvider} implementation.
 *
 * <p><strong>JSON Deserialization:</strong> Response parsing uses Jackson's
 * ObjectMapper for type-safe conversion of RPC results to domain objects.
 *
 * @see PublicClient
 * @see BraneProvider
 * @see RpcRetry
 */
final class DefaultPublicClient implements PublicClient {

    private final BraneProvider provider;

    DefaultPublicClient(final BraneProvider provider) {
        this.provider = provider;
    }

    @Override
    public BlockHeader getLatestBlock() {
        return getBlockByTag("latest");
    }

    @Override
    public BlockHeader getBlockByNumber(final long blockNumber) {
        String tag = "0x" + Long.toHexString(blockNumber);
        return getBlockByTag(tag);
    }

    @Override
    public Transaction getTransactionByHash(final Hash hash) {
        final var response = sendWithRetry("eth_getTransactionByHash", List.of(hash.value()));
        final Object result = response.result();
        if (result == null) {
            return null;
        }

        final var map = MAPPER.convertValue(result, new TypeReference<Map<String, Object>>() {});

        final String hashHex = RpcUtils.stringValue(map.get("hash"));
        final String fromHex = RpcUtils.stringValue(map.get("from"));
        final String toHex = RpcUtils.stringValue(map.get("to"));
        final String inputHex = RpcUtils.stringValue(map.get("input"));
        final String valueHex = RpcUtils.stringValue(map.get("value"));
        final Long nonce = RpcUtils.decodeHexLong(map.get("nonce"));
        final Long blockNumber = RpcUtils.decodeHexLong(map.get("blockNumber"));

        return new Transaction(
                hashHex != null ? new Hash(hashHex) : null,
                fromHex != null ? new Address(fromHex) : null,
                toHex != null ? new Address(toHex) : null,
                inputHex != null ? new HexData(inputHex) : HexData.EMPTY,
                valueHex != null ? new Wei(RpcUtils.decodeHexBigInteger(valueHex)) : null,
                nonce,
                blockNumber);
    }

    @Override
    public HexData call(final CallRequest request, final BlockTag blockTag) {
        final Map<String, Object> callObject = request.toMap();
        final String blockTagStr = blockTag.toRpcValue();
        final String result = call(callObject, blockTagStr);
        if (result == null || result.isEmpty() || "0x".equals(result)) {
            return HexData.EMPTY;
        }
        return new HexData(result);
    }

    @SuppressWarnings("deprecation")
    @Override
    public String call(final Map<String, Object> callObject, final String blockTag) {
        final long start = System.nanoTime();
        DebugLogger.log(LogFormatter.formatCall(blockTag, callObject));
        final JsonRpcResponse response = sendWithRetry("eth_call", List.of(callObject, blockTag));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new io.brane.core.error.RpcException(
                    err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
        final Object result = response.result();
        final String output = result != null ? result.toString() : null;
        final long durationMicros = (System.nanoTime() - start) / 1_000L;
        DebugLogger.log(LogFormatter.formatCallResult(blockTag, durationMicros, output));
        return output;
    }

    @Override
    public List<LogEntry> getLogs(final LogFilter filter) {
        final var req = buildLogParams(filter);

        final var response = sendWithRetry("eth_getLogs", List.of(req));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new io.brane.core.error.RpcException(
                    err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
        final Object result = response.result();
        return LogParser.parseLogs(result, true);
    }

    @Override
    public java.math.BigInteger getChainId() {
        final var response = sendWithRetry("eth_chainId", List.of());
        final Object result = response.result();
        if (result == null) {
            throw new io.brane.core.error.RpcException(0, "eth_chainId returned null", (String) null, (Throwable) null);
        }
        return RpcUtils.decodeHexBigInteger(result.toString());
    }

    @Override
    public java.math.BigInteger getBalance(final Address address) {
        final var response = sendWithRetry("eth_getBalance", List.of(address.value(), "latest"));
        final Object result = response.result();
        if (result == null) {
            throw new io.brane.core.error.RpcException(0, "eth_getBalance returned null", (String) null,
                    (Throwable) null);
        }
        return RpcUtils.decodeHexBigInteger(result.toString());
    }

    @Override
    public AccessListWithGas createAccessList(final TransactionRequest request) {
        final Map<String, Object> txObject = buildTxObject(request);
        final var response = sendWithRetry("eth_createAccessList", List.of(txObject, "latest"));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new io.brane.core.error.RpcException(
                    err.code(), err.message(), RpcUtils.extractErrorData(err.data()), (Long) null);
        }
        final Object result = response.result();
        if (result == null) {
            throw new io.brane.core.error.RpcException(0, "eth_createAccessList returned null", (String) null,
                    (Throwable) null);
        }

        final Map<String, Object> map = MAPPER.convertValue(result, new TypeReference<Map<String, Object>>() {
        });

        final String gasUsedHex = RpcUtils.stringValue(map.get("gasUsed"));
        final BigInteger gasUsed = gasUsedHex != null
                ? RpcUtils.decodeHexBigInteger(gasUsedHex)
                : BigInteger.ZERO;

        final List<Map<String, Object>> accessListRaw = MAPPER.convertValue(
                map.get("accessList"),
                new TypeReference<List<Map<String, Object>>>() {
                });
        final List<AccessListEntry> accessList = new ArrayList<>();
        if (accessListRaw != null) {
            for (Map<String, Object> entryMap : accessListRaw) {
                final String addressHex = RpcUtils.stringValue(entryMap.get("address"));
                final List<String> storageKeysHex = MAPPER.convertValue(
                        entryMap.get("storageKeys"),
                        new TypeReference<List<String>>() {
                        });
                final List<Hash> storageKeys = (storageKeysHex == null)
                        ? List.of()
                        : storageKeysHex.stream()
                                .filter(java.util.Objects::nonNull)
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
    public MulticallBatch createBatch() {
        return MulticallBatch.create(this);
    }

    private Map<String, Object> buildTxObject(final TransactionRequest request) {
        final Map<String, Object> tx = new LinkedHashMap<>();
        if (request.from() != null) {
            tx.put("from", request.from().value());
        }
        request.toOpt().ifPresent(address -> tx.put("to", address.value()));
        request.valueOpt().ifPresent(v -> tx.put("value", RpcUtils.toQuantityHex(v.value())));
        if (request.data() != null) {
            tx.put("data", request.data().value());
        }
        if (request.accessList() != null && !request.accessList().isEmpty()) {
            tx.put("accessList", RpcUtils.toJsonAccessList(request.accessList()));
        }
        return tx;
    }

    private Map<String, Object> buildLogParams(final LogFilter filter) {
        final Map<String, Object> req = new java.util.LinkedHashMap<>();
        filter.fromBlock()
                .ifPresent(
                        v -> {
                            final String hex = RpcUtils.toHexBlock(v);
                            if (hex != null) {
                                req.put("fromBlock", hex);
                            }
                        });
        filter.toBlock()
                .ifPresent(
                        v -> {
                            final String hex = RpcUtils.toHexBlock(v);
                            if (hex != null) {
                                req.put("toBlock", hex);
                            }
                        });
        // Serialize addresses: single address as string, multiple as array (per eth_getLogs spec)
        filter.addresses().ifPresent(addrs -> {
            if (addrs.size() == 1) {
                req.put("address", addrs.get(0).value());
            } else {
                req.put("address", addrs.stream().map(Address::value).toList());
            }
        });
        filter.topics()
                .ifPresent(
                        topics -> {
                            final List<String> topicHex = new ArrayList<>();
                            for (Hash h : topics) {
                                if (h != null) {
                                    topicHex.add(h.value());
                                }
                            }
                            if (!topicHex.isEmpty()) {
                                req.put("topics", topicHex);
                            }
                        });
        return req;
    }

    private @Nullable BlockHeader getBlockByTag(final String tag) {
        final var response = sendWithRetry("eth_getBlockByNumber", List.of(tag, Boolean.FALSE));
        final Object result = response.result();
        if (result == null) {
            return null;
        }

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
    public Subscription subscribeToNewHeads(java.util.function.Consumer<BlockHeader> callback) {
        String id = provider.subscribe("newHeads", List.of(), result -> {
            Map<String, Object> map = MAPPER.convertValue(
                result, new TypeReference<Map<String, Object>>() {}
            );

            String hash = RpcUtils.stringValue(map.get("hash"));
            String parentHash = RpcUtils.stringValue(map.get("parentHash"));
            Long number = RpcUtils.decodeHexLong(map.get("number"));
            Long timestamp = RpcUtils.decodeHexLong(map.get("timestamp"));
            String baseFeeHex = RpcUtils.stringValue(map.get("baseFeePerGas"));

            BlockHeader header = new BlockHeader(
                    hash != null ? new Hash(hash) : null,
                    number,
                    parentHash != null ? new Hash(parentHash) : null,
                    timestamp,
                    baseFeeHex != null ? new Wei(RpcUtils.decodeHexBigInteger(baseFeeHex)) : null);

            callback.accept(header);
        });
        return new SubscriptionImpl(id, provider);
    }

    @Override
    public Subscription subscribeToLogs(LogFilter filter, java.util.function.Consumer<LogEntry> callback) {
        Map<String, Object> params = buildLogParams(filter);
        String id = provider.subscribe("logs", List.of(params), result -> {
            @SuppressWarnings("unchecked")
            Map<String, Object> map = MAPPER.convertValue(
                result, new TypeReference<Map<String, Object>>() {}
            );
            LogEntry log = LogParser.parseLogStrict(map);
            callback.accept(log);
        });
        return new SubscriptionImpl(id, provider);
    }

    private record SubscriptionImpl(String id, BraneProvider provider) implements Subscription {
        @Override
        public void unsubscribe() {
            provider.unsubscribe(id);
        }
    }

    private JsonRpcResponse sendWithRetry(final String method, final List<?> params) {
        return RpcRetry.run(() -> provider.send(method, params), 3);
    }
}
