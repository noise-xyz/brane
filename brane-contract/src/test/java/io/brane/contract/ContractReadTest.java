package io.brane.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.brane.core.RevertException;
import io.brane.core.RpcException;
import io.brane.internal.web3j.abi.FunctionEncoder;
import io.brane.internal.web3j.abi.TypeEncoder;
import io.brane.internal.web3j.abi.datatypes.Utf8String;
import io.brane.internal.web3j.abi.datatypes.generated.Uint256;
import io.brane.internal.web3j.abi.datatypes.Function;
import io.brane.internal.web3j.abi.datatypes.Type;
import io.brane.rpc.Client;
import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;

class ContractReadTest {

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
    void readSuccessDecodesReturnValue() throws RpcException, RevertException {
        String encoded = "0x" + TypeEncoder.encode(new Uint256(BigInteger.valueOf(42)));
        Client client = new FakeClient(encoded, null);
        Abi abi = Abi.fromJson(ECHO_ABI);
        Contract contract = new Contract("0x1234", abi, client);

        BigInteger result = contract.read("echo", BigInteger.class, BigInteger.valueOf(42));

        assertEquals(BigInteger.valueOf(42), result);
    }

    @Test
    void readRevertThrowsRevertException() {
        List<Type> inputs = List.of((Type) new Utf8String("simple reason"));
        String rawData = FunctionEncoder.encode(new Function("Error", inputs, List.of()));

        RpcException rpcEx = new RpcException(3, "execution reverted", rawData, null);

        Client client = new FakeClient(null, rpcEx);
        Abi abi = Abi.fromJson(ECHO_ABI);
        Contract contract = new Contract("0x1234", abi, client);

        RevertException ex =
                assertThrows(
                        RevertException.class,
                        () -> contract.read("echo", BigInteger.class, BigInteger.valueOf(42)));

        assertEquals("simple reason", ex.revertReason());
        assertEquals(rawData, ex.rawDataHex());
    }

    private static final class FakeClient implements Client {

        private final Object result;
        private final RpcException toThrow;

        private FakeClient(Object result, RpcException toThrow) {
            this.result = result;
            this.toThrow = toThrow;
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> T call(String method, Class<T> responseType, Object... params)
                throws RpcException {
            if (toThrow != null) {
                throw toThrow;
            }
            return (T) result;
        }
    }
}
