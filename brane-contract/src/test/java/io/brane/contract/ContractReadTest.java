package io.brane.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.brane.core.error.RevertException;
import io.brane.core.error.RpcException;
import io.brane.core.types.Address;
import io.brane.internal.web3j.abi.FunctionEncoder;
import io.brane.internal.web3j.abi.TypeEncoder;
import io.brane.internal.web3j.abi.datatypes.Utf8String;
import io.brane.internal.web3j.abi.datatypes.generated.Uint256;
import io.brane.internal.web3j.abi.datatypes.Function;
import io.brane.internal.web3j.abi.datatypes.Type;
import io.brane.rpc.Client;
import io.brane.rpc.HttpClient;
import java.math.BigInteger;
import java.util.List;
import java.net.URI;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

class ContractReadTest {

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
    void fakeClientEchoReturnsSameValue() throws RpcException, RevertException {
        String encoded = "0x" + TypeEncoder.encode(new Uint256(BigInteger.valueOf(42)));
        Client client = new FakeClient(encoded, null);
        Abi abi = Abi.fromJson(ECHO_ABI);
        Contract contract = new Contract(new Address("0x0000000000000000000000000000000000001234"), abi, client);

        BigInteger result = contract.read("echo", BigInteger.class, BigInteger.valueOf(42));

        assertEquals(BigInteger.valueOf(42), result);
    }

    @Test
    void echoReturnsSameValue() throws RpcException, RevertException {
        String rpcUrl = System.getProperty("brane.anvil.rpc");
        String contractAddress = System.getProperty("brane.anvil.revertExample.address");
        Assumptions.assumeTrue(rpcUrl != null && !rpcUrl.isBlank(), "Set -Dbrane.anvil.rpc");
        Assumptions.assumeTrue(
                contractAddress != null
                        && !contractAddress.equalsIgnoreCase("0x0000000000000000000000000000000000000000"),
                "Set -Dbrane.anvil.revertExample.address");

        Client client = new HttpClient(URI.create(rpcUrl));
        Abi abi = Abi.fromJson(ECHO_ABI);
        Contract contract = new Contract(new Address(contractAddress), abi, client);

        BigInteger input = BigInteger.valueOf(42);
        BigInteger output = contract.read("echo", BigInteger.class, input);

        assertEquals(input, output, "echo(42) should return 42");
    }

    @Test
    @SuppressWarnings("rawtypes")
    void readRevertThrowsRevertException() {
        List<Type> inputs = List.of((Type) new Utf8String("simple reason"));
        String rawData = FunctionEncoder.encode(new Function("Error", inputs, List.of()));

        RpcException rpcEx = new RpcException(3, "execution reverted", rawData, null, null);

        Client client = new FakeClient(null, rpcEx);
        Abi abi = Abi.fromJson(ECHO_ABI);
        Contract contract = new Contract(new Address("0x0000000000000000000000000000000000001234"), abi, client);

        RevertException ex = assertThrows(
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
