package io.brane.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.brane.core.error.AbiDecodingException;
import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.model.BlockHeader;
import io.brane.core.model.Transaction;
import io.brane.core.types.Address;
import io.brane.core.types.Hash;
import io.brane.internal.web3j.abi.FunctionEncoder;
import io.brane.internal.web3j.abi.TypeEncoder;
import io.brane.internal.web3j.abi.datatypes.Function;
import io.brane.internal.web3j.abi.datatypes.Type;
import io.brane.internal.web3j.abi.datatypes.Utf8String;
import io.brane.internal.web3j.abi.datatypes.generated.Uint256;
import io.brane.rpc.PublicClient;
import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ReadOnlyContractTest {

    private static final String ECHO_ABI =
            """
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
        String encoded = "0x" + TypeEncoder.encode(new Uint256(BigInteger.valueOf(42)));
        PublicClient client =
                new FakePublicClient(encoded, null, null, null, null);

        Abi abi = Abi.fromJson(ECHO_ABI);
        ReadOnlyContract contract =
                ReadOnlyContract.from(
                        new Address("0x0000000000000000000000000000000000001234"), abi, client);

        BigInteger result = contract.call("echo", BigInteger.class, BigInteger.valueOf(42));

        assertEquals(BigInteger.valueOf(42), result);
    }

    @Test
    void callRevertThrowsRevertException() {
        List<Type> inputs = List.of((Type) new Utf8String("simple reason"));
        String rawData = FunctionEncoder.encode(new Function("Error", inputs, List.of()));

        RpcException rpcEx = new RpcException(3, "execution reverted", rawData, null);
        PublicClient client =
                new FakePublicClient(null, rpcEx, null, null, null);

        Abi abi = Abi.fromJson(ECHO_ABI);
        ReadOnlyContract contract =
                ReadOnlyContract.from(
                        new Address("0x0000000000000000000000000000000000001234"), abi, client);

        RevertException ex =
                assertThrows(
                        RevertException.class,
                        () -> contract.call("echo", BigInteger.class, BigInteger.valueOf(42)));

        assertEquals("simple reason", ex.revertReason());
        assertEquals(rawData, ex.rawDataHex());
    }

    @Test
    void emptyResultThrowsAbiDecodingException() {
        PublicClient client =
                new FakePublicClient("0x", null, null, null, null);

        Abi abi = Abi.fromJson(ECHO_ABI);
        ReadOnlyContract contract =
                ReadOnlyContract.from(
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
        private final String txHash;

        private FakePublicClient(
                final String callResult,
                final RpcException toThrow,
                final BlockHeader blockHeader,
                final Transaction transaction,
                final String txHash) {
            this.callResult = callResult;
            this.toThrow = toThrow;
            this.blockHeader = blockHeader;
            this.transaction = transaction;
            this.txHash = txHash;
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
        public String call(final Map<String, Object> callObject, final String blockTag)
                throws RpcException {
            if (toThrow != null) {
                throw toThrow;
            }
            return callResult;
        }
    }
}
