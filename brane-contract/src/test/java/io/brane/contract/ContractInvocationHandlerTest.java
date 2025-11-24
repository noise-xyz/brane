package io.brane.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.brane.core.model.BlockHeader;
import io.brane.core.model.LogEntry;
import io.brane.core.model.Transaction;
import io.brane.core.model.TransactionReceipt;
import io.brane.core.model.TransactionRequest;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import io.brane.internal.web3j.abi.TypeEncoder;
import io.brane.internal.web3j.abi.datatypes.Bool;
import io.brane.rpc.LogFilter;
import io.brane.rpc.PublicClient;
import io.brane.rpc.WalletClient;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ContractInvocationHandlerTest {

    private static final String ABI_JSON =
            """
            [
              {
                "name": "isActive",
                "type": "function",
                "stateMutability": "view",
                "inputs": [],
                "outputs": [{"name": "", "type": "bool"}]
              },
              {
                "name": "setValue",
                "type": "function",
                "stateMutability": "nonpayable",
                "inputs": [{"name": "value", "type": "uint256"}],
                "outputs": []
              }
            ]
            """;

    @Test
    void viewMethodUsesPublicClientOnly() {
        final RecordingPublicClient publicClient = new RecordingPublicClient();
        final RecordingWalletClient walletClient = new RecordingWalletClient();
        publicClient.response = "0x" + TypeEncoder.encode(new Bool(true));

        final SampleContract contract =
                BraneContract.bind(
                        new Address("0x" + "1".repeat(40)),
                        ABI_JSON,
                        publicClient,
                        walletClient,
                        SampleContract.class);

        final boolean result = contract.isActive();

        assertTrue(result);
        assertEquals(1, publicClient.callCount);
        assertEquals(0, walletClient.sendAndWaitCount);
        assertEquals("latest", publicClient.lastBlockTag);
        assertEquals("0x" + "1".repeat(40), publicClient.lastCallObject.get("to"));
        assertTrue(((String) publicClient.lastCallObject.get("data")).startsWith("0x"));
    }

    @Test
    void writeMethodUsesWalletClientOnly() {
        final RecordingPublicClient publicClient = new RecordingPublicClient();
        final RecordingWalletClient walletClient = new RecordingWalletClient();
        final TransactionReceipt receipt =
                new TransactionReceipt(
                        new Hash("0x1"), new Hash("0x2"), 0L, null, null, null, List.of(), true, Wei.of(0));
        walletClient.receipt = receipt;

        final Address address = new Address("0x" + "2".repeat(40));
        final SampleContract contract =
                BraneContract.bind(address, ABI_JSON, publicClient, walletClient, SampleContract.class);

        final TransactionReceipt result = contract.setValue(BigInteger.TEN);

        assertEquals(0, publicClient.callCount);
        assertEquals(1, walletClient.sendAndWaitCount);
        assertEquals(receipt, result);
        assertEquals(address, walletClient.lastRequest.to());
        assertEquals(new HexData(expectedSetValueData()), walletClient.lastRequest.data());
        assertEquals(BigInteger.ZERO, walletClient.lastRequest.value().value());
        assertTrue(walletClient.lastRequest.isEip1559());
    }

    private static String expectedSetValueData() {
        return Abi.fromJson(ABI_JSON).encodeFunction("setValue", BigInteger.TEN).data();
    }

    interface SampleContract {
        Boolean isActive();

        TransactionReceipt setValue(BigInteger value);
    }

    private static final class RecordingPublicClient implements PublicClient {
        Map<String, Object> lastCallObject;
        String lastBlockTag;
        String response;
        int callCount;

        @Override
        public BlockHeader getLatestBlock() {
            throw new UnsupportedOperationException();
        }

        @Override
        public BlockHeader getBlockByNumber(final long blockNumber) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Transaction getTransactionByHash(final Hash hash) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String call(final Map<String, Object> callObject, final String blockTag) {
            this.callCount++;
            this.lastCallObject = new LinkedHashMap<>(callObject);
            this.lastBlockTag = blockTag;
            return response;
        }

        @Override
        public List<LogEntry> getLogs(final LogFilter filter) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class RecordingWalletClient implements WalletClient {

        TransactionRequest lastRequest;
        TransactionReceipt receipt;
        int sendAndWaitCount;

        @Override
        public Hash sendTransaction(final TransactionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public TransactionReceipt sendTransactionAndWait(
                final TransactionRequest request,
                final long timeoutMillis,
                final long pollIntervalMillis) {
            this.lastRequest = request;
            this.sendAndWaitCount++;
            return receipt;
        }
    }
}
