package io.brane.contract;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import io.brane.core.error.RevertException;
import io.brane.core.abi.Abi;
import io.brane.core.error.RpcException;
import io.brane.core.types.Address;
import io.brane.rpc.Client;
import io.brane.rpc.HttpClient;
import java.math.BigInteger;

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
        String encoded = io.brane.primitives.Hex.encode(
                io.brane.core.abi.AbiEncoder.encode(
                        java.util.List.of(new io.brane.core.abi.UInt(256, BigInteger.valueOf(42)))));
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

    void readRevertThrowsRevertException() {
        // Error(string) selector = 0x08c379a0
        // string = "simple reason"
        String rawData = io.brane.primitives.Hex.encode(
                io.brane.core.abi.AbiEncoder.encodeFunction(
                        "Error(string)",
                        java.util.List.of(new io.brane.core.abi.Utf8String("simple reason"))));

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
