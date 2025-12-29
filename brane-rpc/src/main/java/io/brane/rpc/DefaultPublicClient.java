package io.brane.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import io.brane.rpc.internal.RpcUtils;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class DefaultPublicClient implements PublicClient {

    private final BraneProvider provider;
    private final ObjectMapper mapper = new ObjectMapper();

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

        final var map = mapper.convertValue(result, new TypeReference<Map<String, Object>>() {});

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
                Optional.ofNullable(toHex).map(Address::new),
                inputHex != null ? new HexData(inputHex) : HexData.EMPTY,
                valueHex != null ? new Wei(RpcUtils.decodeHexBigInteger(valueHex)) : null,
                nonce,
                blockNumber);
    }

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
        if (result == null) {
            return List.of();
        }

        final List<Map<String, Object>> raw = mapper.convertValue(
            result,
            new TypeReference<List<Map<String, Object>>>() {}
        );        
        final List<LogEntry> logs = new ArrayList<>(raw.size());
        for (Map<String, Object> map : raw) {
            final String address = RpcUtils.stringValue(map.get("address"));
            final String data = RpcUtils.stringValue(map.get("data"));
            final String blockHash = RpcUtils.stringValue(map.get("blockHash"));
            final String txHash = RpcUtils.stringValue(map.get("transactionHash"));
            final Long logIndex = RpcUtils.decodeHexLong(map.get("logIndex"));
            final List<String> topicsHex = mapper.convertValue(
                map.get("topics"),
                new TypeReference<List<String>>() {}
            );
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
                            requireLogIndex(logIndex, map),
                            Boolean.TRUE.equals(map.get("removed"))));
        }
        return logs;
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

        final Map<String, Object> map = mapper.convertValue(result, new TypeReference<Map<String, Object>>() {
        });

        final String gasUsedHex = RpcUtils.stringValue(map.get("gasUsed"));
        final BigInteger gasUsed = gasUsedHex != null
                ? RpcUtils.decodeHexBigInteger(gasUsedHex)
                : BigInteger.ZERO;

        final List<Map<String, Object>> accessListRaw = mapper.convertValue(
                map.get("accessList"),
                new TypeReference<List<Map<String, Object>>>() {
                });
        final List<AccessListEntry> accessList = new ArrayList<>();
        if (accessListRaw != null) {
            for (Map<String, Object> entryMap : accessListRaw) {
                final String addressHex = RpcUtils.stringValue(entryMap.get("address"));
                final List<String> storageKeysHex = mapper.convertValue(
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
            tx.put("accessList", toJsonAccessList(request.accessList()));
        }
        return tx;
    }

    private List<Map<String, Object>> toJsonAccessList(final List<AccessListEntry> entries) {
        return entries.stream()
                .map(
                        entry -> {
                            final Map<String, Object> map = new LinkedHashMap<>();
                            map.put("address", entry.address().value());
                            map.put(
                                    "storageKeys",
                                    entry.storageKeys().stream().map(Hash::value).toList());
                            return map;
                        })
                .toList();
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
        filter.address().ifPresent(a -> req.put("address", a.value()));
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

    private BlockHeader getBlockByTag(final String tag) {
        final var response = sendWithRetry("eth_getBlockByNumber", List.of(tag, Boolean.FALSE));
        final Object result = response.result();
        if (result == null) {
            return null;
        }

        final Map<String, Object> map = mapper.convertValue(
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
            Map<String, Object> map = mapper.convertValue(
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
            Map<String, Object> map = mapper.convertValue(
                result, new TypeReference<Map<String, Object>>() {}
            );

            String address = RpcUtils.stringValue(map.get("address"));
            String data = RpcUtils.stringValue(map.get("data"));
            String blockHash = RpcUtils.stringValue(map.get("blockHash"));
            String txHash = RpcUtils.stringValue(map.get("transactionHash"));
            Long logIndex = RpcUtils.decodeHexLong(map.get("logIndex"));
            List<String> topicsHex = mapper.convertValue(
                map.get("topics"),
                new TypeReference<List<String>>() {}
            );
            List<Hash> topics = new ArrayList<>();
            if (topicsHex != null) {
                for (String t : topicsHex) {
                    topics.add(new Hash(t));
                }
            }

            LogEntry log = new LogEntry(
                    address != null ? new Address(address) : null,
                    data != null ? new HexData(data) : HexData.EMPTY,
                    topics,
                    blockHash != null ? new Hash(blockHash) : null,
                    txHash != null ? new Hash(txHash) : null,
                    requireLogIndex(logIndex, map),
                    Boolean.TRUE.equals(map.get("removed")));

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

    private Long requireLogIndex(Long logIndex, Map<String, Object> map) {
        if (logIndex == null) {
            throw new io.brane.core.error.RpcException(-32000, "Missing logIndex in log entry", map.toString(),
                    (Throwable) null);
        }
        return logIndex;
    }
}
