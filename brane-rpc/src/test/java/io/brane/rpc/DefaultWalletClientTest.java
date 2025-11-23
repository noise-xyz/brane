package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.brane.core.builder.TxBuilder;
import io.brane.core.error.ChainMismatchException;
import io.brane.core.error.InvalidSenderException;
import io.brane.core.error.RpcException;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.internal.web3j.crypto.RawTransaction;
import io.brane.internal.web3j.crypto.transaction.type.TransactionType;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashMap;
import org.junit.jupiter.api.Test;

class DefaultWalletClientTest {

    @Test
    void sendsLegacyTransactionWithAutoFields() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient = new FakePublicClient();

        final FakeBraneProvider provider =
                new FakeBraneProvider()
                        .respond("eth_chainId", "0x1")
                        .respond("eth_getTransactionCount", "0x5")
                        .respond("eth_estimateGas", "0x5208")
                        .respond("eth_gasPrice", "0x3b9aca00")
                        .respond("eth_sendRawTransaction", "0x" + "a".repeat(64));

        DefaultWalletClient wallet =
                DefaultWalletClient.from(
                        provider, publicClient, signer.asSigner(), signer.address(), 1L);

        TransactionRequest request =
                TxBuilder.legacy()
                        .from(signer.address())
                        .to(new Address("0x" + "2".repeat(40)))
                        .value(Wei.of(0))
                        .gasPrice(Wei.of(1_000_000_000L))
                        .data(new HexData("0x"))
                        .build();

        Hash hash = wallet.sendTransaction(request);

        assertEquals("0x" + "a".repeat(64), hash.value());
        assertEquals(TransactionType.LEGACY, signer.lastTransactionType());
        assertEquals(5L, signer.lastNonce());
        assertEquals(0x5208L, signer.lastGasLimit());
    }

    @Test
    void enforcesChainId() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient = new FakePublicClient();

        final FakeBraneProvider provider =
                new FakeBraneProvider()
                        .respond("eth_chainId", "0x2");

        DefaultWalletClient wallet =
                DefaultWalletClient.from(
                        provider, publicClient, signer.asSigner(), signer.address(), 1L);

        TransactionRequest request =
                TxBuilder.legacy()
                        .from(signer.address())
                        .to(new Address("0x" + "2".repeat(40)))
                        .value(Wei.of(0))
                        .gasPrice(Wei.of(1_000_000_000L))
                        .data(new HexData("0x"))
                        .build();

        assertThrows(ChainMismatchException.class, () -> wallet.sendTransaction(request));
    }

    @Test
    void invalidSenderRaisesInvalidSenderException() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient = new FakePublicClient();

        final FakeBraneProvider provider =
                new FakeBraneProvider()
                        .respond("eth_chainId", "0x1")
                        .respond("eth_getTransactionCount", "0x0")
                        .respond("eth_estimateGas", "0x5208")
                        .respond("eth_gasPrice", "0x3b9aca00")
                        .respondError("eth_sendRawTransaction", -32000, "invalid sender");

        DefaultWalletClient wallet =
                DefaultWalletClient.from(
                        provider, publicClient, signer.asSigner(), signer.address(), 1L);

        TransactionRequest request =
                TxBuilder.legacy()
                        .from(signer.address())
                        .to(new Address("0x" + "2".repeat(40)))
                        .value(Wei.of(0))
                        .gasPrice(Wei.of(1_000_000_000L))
                        .data(new HexData("0x"))
                        .build();

        assertThrows(InvalidSenderException.class, () -> wallet.sendTransaction(request));
    }

    @Test
    void sendAndWaitPollsUntilReceipt() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient = new FakePublicClient();

        final FakeBraneProvider provider =
                new FakeBraneProvider()
                        .respond("eth_chainId", "0x1")
                        .respond("eth_getTransactionCount", "0x0")
                        .respond("eth_estimateGas", "0x5208")
                        .respond("eth_gasPrice", "0x3b9aca00")
                        .respond("eth_sendRawTransaction", "0x" + "a".repeat(64))
                        .respond("eth_getTransactionReceipt", null)
                        .respond(
                                "eth_getTransactionReceipt",
                                new LinkedHashMapBuilder()
                                        .put("transactionHash", "0x" + "a".repeat(64))
                                        .put("blockNumber", "0x1")
                                        .put("blockHash", "0x" + "3".repeat(64))
                                        .put("from", signer.address().value())
                                        .put("to", "0x" + "2".repeat(40))
                                        .put("status", "0x1")
                                        .put("cumulativeGasUsed", "0x5208")
                                        .put("logs", List.of())
                                        .build());

        DefaultWalletClient wallet =
                DefaultWalletClient.from(
                        provider, publicClient, signer.asSigner(), signer.address(), 1L);

        TransactionRequest request =
                TxBuilder.legacy()
                        .from(signer.address())
                        .to(new Address("0x" + "2".repeat(40)))
                        .value(Wei.of(0))
                        .gasPrice(Wei.of(1_000_000_000L))
                        .data(new HexData("0x"))
                        .build();

        TransactionReceipt receipt = wallet.sendTransactionAndWait(request, 2000, 10);

        assertEquals("0x" + "a".repeat(64), receipt.transactionHash().value());
        assertTrue(provider.recordedMethods().contains("eth_getTransactionReceipt"));
    }

    private static final class FakeSigner {
        private final Address address;
        private RawTransaction last;

        private FakeSigner(final Address address) {
            this.address = address;
        }

        Address address() {
            return address;
        }

        TransactionSigner asSigner() {
            return tx -> {
                this.last = tx;
                return "0xsigned";
            };
        }

        long lastNonce() {
            return last.getNonce().longValue();
        }

        long lastGasLimit() {
            return last.getGasLimit().longValue();
        }

        TransactionType lastTransactionType() {
            return last.getType();
        }
    }

    private static final class FakePublicClient implements PublicClient {
        @Override
        public io.brane.core.model.BlockHeader getLatestBlock() {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.brane.core.model.BlockHeader getBlockByNumber(long blockNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.brane.core.model.Transaction getTransactionByHash(Hash hash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String call(java.util.Map<String, Object> callObject, String blockTag) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.util.List<io.brane.core.model.LogEntry> getLogs(final LogFilter filter) {
            return java.util.Collections.emptyList();
        }
    }

    private static final class FakeBraneProvider implements BraneProvider {
        private final List<JsonRpcResponse> responses = new ArrayList<>();
        private final List<String> methods = new ArrayList<>();

        FakeBraneProvider respond(final String method, final Object result) {
            responses.add(new JsonRpcResponse("2.0", result, null, "1"));
            methods.add(method);
            return this;
        }

        FakeBraneProvider respondError(final String method, final int code, final String message) {
            responses.add(new JsonRpcResponse("2.0", null, new JsonRpcError(code, message, null), "1"));
            methods.add(method);
            return this;
        }

        @Override
        public JsonRpcResponse send(final String method, final List<?> params) throws RpcException {
            if (responses.isEmpty()) {
                throw new RpcException(-1, "No response queued for " + method, null, null);
            }
            methods.add(method);
            return responses.remove(0);
        }

        List<String> recordedMethods() {
            return methods;
        }
    }

    private static final class LinkedHashMapBuilder {
        private final java.util.LinkedHashMap<String, Object> map = new java.util.LinkedHashMap<>();

        LinkedHashMapBuilder put(final String key, final Object value) {
            map.put(key, value);
            return this;
        }

        java.util.LinkedHashMap<String, Object> build() {
            return map;
        }
    }
}
