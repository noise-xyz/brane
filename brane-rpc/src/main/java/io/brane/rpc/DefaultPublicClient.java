package io.brane.rpc;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.Transaction;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import java.math.BigInteger;
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
                provider.send("eth_getTransactionByHash", List.of(hash.value()));
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
        final JsonRpcResponse response =
                provider.send("eth_call", List.of(callObject, blockTag));
        final Object result = response.result();
        return result != null ? result.toString() : null;
    }

    private BlockHeader getBlockByTag(final String tag) {
        final JsonRpcResponse response =
                provider.send("eth_getBlockByNumber", List.of(tag, Boolean.FALSE));
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

        return new BlockHeader(
                hash != null ? new Hash(hash) : null,
                number,
                parentHash != null ? new Hash(parentHash) : null,
                timestamp);
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
}
