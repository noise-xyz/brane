package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import io.brane.core.model.BlockHeader;
import io.brane.core.model.Transaction;
import io.brane.core.types.Hash;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class DefaultPublicClientTest {

        @Mock
        private BraneProvider provider;

        private PublicClient client;

        @BeforeEach
        void setUp() {
                client = PublicClient.from(provider);
        }

        @Test
        void getLatestBlockMapsJsonToBlockHeader() throws Exception {
                String hash = "0x" + "a".repeat(64);
                String parentHash = "0x" + "b".repeat(64);
                Map<String, Object> block = Map.of(
                                "hash", hash,
                                "parentHash", parentHash,
                                "number", "0x10",
                                "timestamp", "0x5");
                JsonRpcResponse response = new JsonRpcResponse("2.0", block, null, "1");

                when(provider.send(eq("eth_getBlockByNumber"), any())).thenReturn(response);

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
                Map<String, Object> block = Map.of(
                                "hash", hash,
                                "parentHash", "0x" + "2".repeat(64),
                                "number", "0x1",
                                "timestamp", "0xa");
                JsonRpcResponse response = new JsonRpcResponse("2.0", block, null, "1");

                when(provider.send(eq("eth_getBlockByNumber"), any())).thenReturn(response);

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
                Map<String, Object> tx = Map.of(
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
                JsonRpcResponse response = new JsonRpcResponse("2.0", tx, null, "1");

                when(provider.send(eq("eth_getTransactionByHash"), any())).thenReturn(response);

                Transaction transaction = client.getTransactionByHash(new Hash(hash));
                assertNotNull(transaction);
                assertEquals(hash, transaction.hash().value());
                assertEquals(from, transaction.from().value());
        }

        @Test
        void callReturnsRawHex() throws Exception {
                JsonRpcResponse response = new JsonRpcResponse("2.0", "0x2a", null, "1");
                when(provider.send(eq("eth_call"), any())).thenReturn(response);

                String result = client.call(Map.of("to", "0xabc", "data", "0x1234"), "latest");
                assertEquals("0x2a", result);
        }
}
