package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import io.brane.core.error.RpcException;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class HttpClientTest {

    @Test
    void convertsStringResult() throws RpcException {
        BraneProvider provider =
                (method, params) -> new JsonRpcResponse("2.0", "0x1", null, "1");
        HttpClient client = new HttpClient(provider);

        String result = client.call("eth_blockNumber", String.class);
        assertEquals("0x1", result);
    }

    @Test
    void convertsHexStringToBigInteger() throws RpcException {
        BraneProvider provider =
                (method, params) -> new JsonRpcResponse("2.0", "0x2a", null, "1");
        HttpClient client = new HttpClient(provider);

        BigInteger result = client.call("eth_call", BigInteger.class);
        assertEquals(BigInteger.valueOf(42), result);
    }

    @Test
    void propagatesRpcException() {
        RpcException failure = new RpcException(-32000, "boom", null, null);
        BraneProvider provider = (method, params) -> {
            throw failure;
        };
        HttpClient client = new HttpClient(provider);

        RpcException ex =
                assertThrows(
                        RpcException.class,
                        () -> client.call("eth_call", String.class));
        assertSame(failure, ex);
    }
}
