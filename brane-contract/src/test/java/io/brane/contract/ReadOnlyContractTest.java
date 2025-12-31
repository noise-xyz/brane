package io.brane.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.brane.core.error.AbiDecodingException;
import io.brane.core.abi.Abi;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.Transaction;
import io.brane.core.types.Address;
import io.brane.rpc.Subscription;
import io.brane.core.types.Hash;
import io.brane.rpc.PublicClient;
import java.math.BigInteger;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ReadOnlyContractTest {

    private static final String ECHO_ABI = """
            [
              {
                "inputs": [{ "internalType": "uint256", "name": "x", "type": "uint256" }],
                "name": "echo",
                "outputs": [{ "internalType": "uint256", "name": "", "type": "uint256" }],
                "stateMutability": "pure",
                "type": "function"
              }
            ]
            """;

    @Test
    void callReturnsDecodedValue() throws Exception {
        String encoded = io.brane.primitives.Hex.encode(
                io.brane.core.abi.AbiEncoder.encode(
                        java.util.List.of(new io.brane.core.abi.UInt(256, BigInteger.valueOf(42)))));
        PublicClient client = new FakePublicClient(encoded, null, null, null);

        Abi abi = Abi.fromJson(ECHO_ABI);
        ReadOnlyContract contract = ReadOnlyContract.from(
                new Address("0x0000000000000000000000000000000000001234"), abi, client);

        BigInteger result = contract.call("echo", BigInteger.class, BigInteger.valueOf(42));

        assertEquals(BigInteger.valueOf(42), result);
    }

    @Test

    void callRevertThrowsRevertException() {
        // Error(string) selector = 0x08c379a0
        // string = "simple reason"
        String rawData = io.brane.primitives.Hex.encode(
                io.brane.core.abi.AbiEncoder.encodeFunction(
                        "Error(string)",
                        java.util.List.of(new io.brane.core.abi.Utf8String("simple reason"))));

        RpcException rpcEx = new RpcException(3, "execution reverted", rawData, null, null);
        PublicClient client = new FakePublicClient(null, rpcEx, null, null);

        Abi abi = Abi.fromJson(ECHO_ABI);
        ReadOnlyContract contract = ReadOnlyContract.from(
                new Address("0x0000000000000000000000000000000000001234"), abi, client);

        RevertException ex = assertThrows(
                RevertException.class,
                () -> contract.call("echo", BigInteger.class, BigInteger.valueOf(42)));

        assertEquals("simple reason", ex.revertReason());
        assertEquals(rawData, ex.rawDataHex());
    }

    @Test
    void emptyResultThrowsAbiDecodingException() {
        PublicClient client = new FakePublicClient("0x", null, null, null);

        Abi abi = Abi.fromJson(ECHO_ABI);
        ReadOnlyContract contract = ReadOnlyContract.from(
                new Address("0x0000000000000000000000000000000000001234"), abi, client);

        assertThrows(
                AbiDecodingException.class,
                () -> contract.call("echo", BigInteger.class, BigInteger.valueOf(42)));
    }

    private static final class FakePublicClient implements PublicClient {

        private final String callResult;
        private final RpcException toThrow;
        private final BlockHeader blockHeader;
        private final Transaction transaction;

        private FakePublicClient(
                final String callResult,
                final RpcException toThrow,
                final BlockHeader blockHeader,
                final Transaction transaction) {
            this.callResult = callResult;
            this.toThrow = toThrow;
            this.blockHeader = blockHeader;
            this.transaction = transaction;
        }

        @Override
        public BlockHeader getLatestBlock() {
            if (blockHeader == null) {
                throw new UnsupportedOperationException("not implemented in fake");
            }
            return blockHeader;
        }

        @Override
        public BlockHeader getBlockByNumber(final long blockNumber) {
            if (blockHeader == null) {
                throw new UnsupportedOperationException("not implemented in fake");
            }
            return blockHeader;
        }

        @Override
        public Transaction getTransactionByHash(final Hash hash) {
            if (transaction == null) {
                throw new UnsupportedOperationException("not implemented in fake");
            }
            return transaction;
        }

        @Override
        public io.brane.core.types.HexData call(io.brane.rpc.CallRequest request, io.brane.rpc.BlockTag blockTag) {
            if (toThrow != null) {
                throw toThrow;
            }
            return callResult != null ? new io.brane.core.types.HexData(callResult) : io.brane.core.types.HexData.EMPTY;
        }

        @SuppressWarnings("deprecation")
        @Override
        public String call(final Map<String, Object> callObject, final String blockTag)
                throws RpcException {
            if (toThrow != null) {
                throw toThrow;
            }
            return callResult;
        }

        @Override
        public java.util.List<io.brane.core.model.LogEntry> getLogs(
                final io.brane.rpc.LogFilter filter) {
            throw new UnsupportedOperationException();
        }

        @Override
        public java.math.BigInteger getChainId() {
            return java.math.BigInteger.ONE;
        }

        @Override
        public java.math.BigInteger getBalance(io.brane.core.types.Address address) {
            return java.math.BigInteger.ZERO;
        }

        @Override
        public io.brane.rpc.Subscription subscribeToNewHeads(
                java.util.function.Consumer<io.brane.core.model.BlockHeader> callback) {
            return null;
        }

        @Override
        public io.brane.rpc.Subscription subscribeToLogs(io.brane.rpc.LogFilter filter,
                java.util.function.Consumer<io.brane.core.model.LogEntry> callback) {
            return null;
        }

        @Override
        public io.brane.core.model.AccessListWithGas createAccessList(io.brane.core.model.TransactionRequest request) {
            throw new UnsupportedOperationException();
        }

        @Override
        public io.brane.rpc.MulticallBatch createBatch() {
            throw new UnsupportedOperationException();
        }
    }
}
