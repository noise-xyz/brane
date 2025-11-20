package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import io.brane.core.error.RpcException;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.Transaction;
import io.brane.core.types.Hash;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class DefaultPublicClientTest {

    @Test
    void getLatestBlockMapsJsonToBlockHeader() throws Exception {
        String hash = "0x" + "a".repeat(64);
        String parentHash = "0x" + "b".repeat(64);
        Map<String, Object> block =
                Map.of(
                        "hash", hash,
                        "parentHash", parentHash,
                        "number", "0x10",
                        "timestamp", "0x5");
        JsonRpcResponse response =
                new JsonRpcResponse("2.0", block, null, "1");

        PublicClient client =
                PublicClient.from(new FakeProvider(Map.of("eth_getBlockByNumber", response)));

        BlockHeader header = client.getLatestBlock();
        assertNotNull(header);
        assertEquals(hash, header.hash().value());
        assertEquals(parentHash, header.parentHash().value());
        assertEquals(16L, header.number());
        assertEquals(5L, header.timestamp());
    }

    @Test
    void getBlockByNumberUsesHexAndMaps() throws Exception {
        String hash = "0x" + "1".repeat(64);
        Map<String, Object> block =
                Map.of(
                        "hash", hash,
                        "parentHash", "0x" + "2".repeat(64),
                        "number", "0x1",
                        "timestamp", "0xa");
        JsonRpcResponse response =
                new JsonRpcResponse("2.0", block, null, "1");

        PublicClient client =
                PublicClient.from(new FakeProvider(Map.of("eth_getBlockByNumber", response)));

        BlockHeader header = client.getBlockByNumber(1);
        assertNotNull(header);
        assertEquals(hash, header.hash().value());
        assertEquals(1L, header.number());
    }

    @Test
    void getTransactionByHashReturnsTransaction() throws Exception {
        String hash = "0x" + "a".repeat(64);
        String from = "0x" + "1".repeat(40);
        String to = "0x" + "2".repeat(40);
        Map<String, Object> tx =
                Map.of(
                        "hash", hash,
                        "from", from,
                        "to", to,
                        "input", "0x",
                        "value", "0x2a",
                        "nonce", "0x1",
                        "gas", "0x5208",
                        "maxFeePerGas", "0x1",
                        "maxPriorityFeePerGas", "0x1",
                        "blockNumber", "0x10");
        JsonRpcResponse response =
                new JsonRpcResponse("2.0", tx, null, "1");

        PublicClient client =
                PublicClient.from(
                        new FakeProvider(Map.of("eth_getTransactionByHash", response)));

        Transaction transaction = client.getTransactionByHash(new Hash(hash));
        assertNotNull(transaction);
        assertEquals(hash, transaction.hash().value());
        assertEquals(from, transaction.from().value());
    }

    @Test
    void callReturnsRawHex() throws Exception {
        JsonRpcResponse response =
                new JsonRpcResponse("2.0", "0x2a", null, "1");
        PublicClient client =
                PublicClient.from(new FakeProvider(Map.of("eth_call", response)));

        String result =
                client.call(Map.of("to", "0xabc", "data", "0x1234"), "latest");
        assertEquals("0x2a", result);
    }

    private static final class FakeProvider implements BraneProvider {
        private final Map<String, JsonRpcResponse> responses;

        private FakeProvider(final Map<String, JsonRpcResponse> responses) {
            this.responses = responses;
        }

        @Override
        public JsonRpcResponse send(final String method, final List<?> params)
                throws RpcException {
            final JsonRpcResponse resp = responses.get(method);
            if (resp == null) {
                throw new RpcException(-32601, "Method not mocked: " + method, null, null);
            }
            if (resp.error() != null) {
                throw new RpcException(
                        resp.error().code(),
                        resp.error().message(),
                        resp.error().data() != null ? resp.error().data().toString() : null,
                        null);
            }
            return resp;
        }
    }
}
