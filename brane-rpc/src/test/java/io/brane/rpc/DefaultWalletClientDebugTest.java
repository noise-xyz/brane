package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.brane.core.BraneDebug;
import io.brane.core.builder.TxBuilder;
import io.brane.core.error.RevertException;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class DefaultWalletClientDebugTest {

    private final Logger debugLogger = (Logger) LoggerFactory.getLogger("io.brane.debug");

    @AfterEach
    void tearDown() {
        BraneDebug.setEnabled(false);
        debugLogger.detachAndStopAllAppenders();
    }

    @org.junit.jupiter.api.Disabled("TODO: Fix mock RPC response ordering")
    @Test
    void emitsLifecycleLogsForSendAndWait() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient = new FakePublicClient();
        final FakeBraneProvider provider = new FakeBraneProvider()
                .respond("eth_chainId", "0x1")
                .respond("eth_getTransactionCount", "0x0")
                .respond("eth_estimateGas", "0x5208")
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

        DefaultWalletClient wallet = DefaultWalletClient.from(
                provider,
                publicClient,
                signer.asSigner(),
                signer.address(),
                1L,
                io.brane.core.chain.ChainProfiles.ANVIL_LOCAL);

        TransactionRequest request = TxBuilder.legacy()
                .from(signer.address())
                .to(new Address("0x" + "2".repeat(40)))
                .value(Wei.of(0))
                .gasPrice(Wei.of(1_000_000_000L))
                .data(new HexData("0x"))
                .build();

        BraneDebug.setEnabled(true);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        debugLogger.addAppender(appender);

        TransactionReceipt receipt = wallet.sendTransactionAndWait(request, 100, 1);

        assertEquals("0x" + "a".repeat(64), receipt.transactionHash().value());
        final String combinedLogs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(combinedLogs.contains("[TX-SEND]"));
        assertTrue(combinedLogs.contains("[TX-HASH]"));
        assertTrue(combinedLogs.contains("[TX-WAIT]"));
        assertTrue(combinedLogs.contains("[TX-RECEIPT]"));
    }

    @org.junit.jupiter.api.Disabled("TODO: Fix mock RPC response ordering")
    @Test
    void logsDecodedRevert() {
        final FakeSigner signer = new FakeSigner(new Address("0x" + "1".repeat(40)));
        final FakePublicClient publicClient = new FakePublicClient();
        final FakeBraneProvider provider = new FakeBraneProvider()
                .respond("eth_chainId", "0x1")
                .respond("eth_getTransactionCount", "0x0")
                .respond("eth_estimateGas", "0x5208")
                .respondError(
                        "eth_sendRawTransaction",
                        -32000,
                        "execution reverted",
                        "0x08c379a00000000000000000000000000000000000000000000000000000000000000020"
                                + "0000000000000000000000000000000000000000000000000000000000000004"
                                + "626f6f6d00000000000000000000000000000000000000000000000000000000");

        DefaultWalletClient wallet = DefaultWalletClient.from(
                provider,
                publicClient,
                signer.asSigner(),
                signer.address(),
                1L,
                io.brane.core.chain.ChainProfiles.ANVIL_LOCAL);

        TransactionRequest request = TxBuilder.legacy()
                .from(signer.address())
                .to(new Address("0x" + "2".repeat(40)))
                .value(Wei.of(0))
                .gasPrice(Wei.of(1_000_000_000L))
                .data(new HexData("0x"))
                .build();

        BraneDebug.setEnabled(true);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        debugLogger.addAppender(appender);

        assertThrows(RevertException.class, () -> wallet.sendTransaction(request));

        final String combinedLogs = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .reduce("", (a, b) -> a + "\n" + b);
        assertTrue(combinedLogs.contains("[TX-REVERT]"));
        assertTrue(combinedLogs.toLowerCase().contains("boom"));
    }

    private static final class FakeSigner {
        private final Address address;

        private FakeSigner(final Address address) {
            this.address = address;
        }

        Address address() {
            return address;
        }

        TransactionSigner asSigner() {
            return (tx, chainId) -> {
                return "0xsigned";
            };
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
            throw new UnsupportedOperationException();
        }

        @Override
        public long getChainId() {
            return 1;
        }
    }

    private static final class FakeBraneProvider implements BraneProvider {
        private final List<JsonRpcResponse> responses = new ArrayList<>();

        FakeBraneProvider respond(final String method, final Object result) {
            responses.add(new JsonRpcResponse("2.0", result, null, "1"));
            return this;
        }

        FakeBraneProvider respondError(final String method, final int code, final String message, final String data) {
            responses.add(new JsonRpcResponse("2.0", null, new JsonRpcError(code, message, data), "1"));
            return this;
        }

        @Override
        public JsonRpcResponse send(final String method, final List<?> params) {
            if (responses.isEmpty()) {
                throw new io.brane.core.error.RpcException(
                        -1, "No response queued for " + method, null, null, null);
            }
            return responses.remove(0);
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
