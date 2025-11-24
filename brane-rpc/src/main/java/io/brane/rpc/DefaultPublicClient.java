package io.brane.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.brane.core.DebugLogger;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.Transaction;
import io.brane.core.model.LogEntry;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
        final JsonRpcResponse response =
                sendWithRetry("eth_getTransactionByHash", List.of(hash.value()));
        final Object result = response.result();
        if (result == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        final Map<String, Object> map = mapper.convertValue(result, Map.class);

        final String hashHex = stringValue(map.get("hash"));
        final String fromHex = stringValue(map.get("from"));
        final String toHex = stringValue(map.get("to"));
        final String inputHex = stringValue(map.get("input"));
        final String valueHex = stringValue(map.get("value"));
        final Long nonce = decodeHexLong(map.get("nonce"));
        final Long blockNumber = decodeHexLong(map.get("blockNumber"));

        return new Transaction(
                hashHex != null ? new Hash(hashHex) : null,
                fromHex != null ? new Address(fromHex) : null,
                toHex != null ? new Address(toHex) : null,
                inputHex != null ? new HexData(inputHex) : HexData.EMPTY,
                valueHex != null ? new Wei(decodeHexBigInteger(valueHex)) : null,
                nonce,
                blockNumber);
    }

    @Override
    public String call(final Map<String, Object> callObject, final String blockTag) {
        final long start = System.nanoTime();
        DebugLogger.log("[CALL] tag=%s request=%s", blockTag, callObject);
        final JsonRpcResponse response = sendWithRetry("eth_call", List.of(callObject, blockTag));
        final Object result = response.result();
        final String output = result != null ? result.toString() : null;
        final long durationMicros = (System.nanoTime() - start) / 1_000L;
        DebugLogger.log("[CALL-RESULT] tag=%s durationMicros=%s result=%s", blockTag, durationMicros, output);
        return output;
    }

    @Override
    public List<LogEntry> getLogs(final LogFilter filter) {
        final Map<String, Object> req = buildLogParams(filter);

        final JsonRpcResponse response = sendWithRetry("eth_getLogs", List.of(req));
        if (response.hasError()) {
            final JsonRpcError err = response.error();
            throw new io.brane.core.error.RpcException(
                    err.code(), err.message(), extractErrorData(err.data()), null);
        }
        final Object result = response.result();
        if (result == null) {
            return List.of();
        }

        @SuppressWarnings("unchecked")
        final List<Map<String, Object>> raw = mapper.convertValue(result, List.class);
        final List<LogEntry> logs = new ArrayList<>(raw.size());
        for (Map<String, Object> map : raw) {
            final String address = stringValue(map.get("address"));
            final String data = stringValue(map.get("data"));
            final String blockHash = stringValue(map.get("blockHash"));
            final String txHash = stringValue(map.get("transactionHash"));
            final Long logIndex = decodeHexLong(map.get("logIndex"));
            @SuppressWarnings("unchecked")
            final List<String> topicsHex = mapper.convertValue(map.get("topics"), List.class);
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
                            Boolean.TRUE.equals(map.get("removed"))));
        }
        return logs;
    }

    private String extractErrorData(final Object dataValue) {
        return switch (dataValue) {
            case null -> null;
            case String s -> s;
            case Map<?, ?> map -> map.values().stream()
                    .map(this::extractErrorData)
                    .filter(java.util.Objects::nonNull)
                    .findFirst()
                    .orElseGet(dataValue::toString);
            case Object array when dataValue.getClass().isArray() -> extractFromArray(array, dataValue);
            case Iterable<?> iterable -> extractFromIterable(iterable, dataValue);
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

    private String toHexBlock(final Long block) {
        if (block == null) {
            return null;
        }
        return "0x" + Long.toHexString(block).toLowerCase();
    }

    private Map<String, Object> buildLogParams(final LogFilter filter) {
        final Map<String, Object> req = new java.util.LinkedHashMap<>();
        filter.fromBlock()
                .ifPresent(
                        v -> {
                            final String hex = toHexBlock(v);
                            if (hex != null) {
                                req.put("fromBlock", hex);
                            }
                        });
        filter.toBlock()
                .ifPresent(
                        v -> {
                            final String hex = toHexBlock(v);
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
        final JsonRpcResponse response =
                sendWithRetry("eth_getBlockByNumber", List.of(tag, Boolean.FALSE));
        final Object result = response.result();
        if (result == null) {
            return null;
        }

        @SuppressWarnings("unchecked")
        final Map<String, Object> map = mapper.convertValue(result, Map.class);
        final String hash = stringValue(map.get("hash"));
        final String parentHash = stringValue(map.get("parentHash"));
        final Long number = decodeHexLong(map.get("number"));
        final Long timestamp = decodeHexLong(map.get("timestamp"));
        final String baseFeeHex = stringValue(map.get("baseFeePerGas"));

        return new BlockHeader(
                hash != null ? new Hash(hash) : null,
                number,
                parentHash != null ? new Hash(parentHash) : null,
                timestamp,
                baseFeeHex != null ? new Wei(decodeHexBigInteger(baseFeeHex)) : null);
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
        return new BigInteger(normalized, 16).longValueExact();
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

    private String stringValue(final Object value) {
        return value != null ? value.toString() : null;
    }

    private JsonRpcResponse sendWithRetry(final String method, final List<?> params) {
        return RpcRetry.run(() -> provider.send(method, params), 3);
    }
}
