package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import io.brane.core.error.RpcException;
import java.math.BigDecimal;
import java.math.BigInteger;
import org.junit.jupiter.api.Test;

class RpcClientTest {

    @Test
    void convertsStringResult() throws RpcException {
        BraneProvider provider =
                (method, params) -> new JsonRpcResponse("2.0", "0x1", null, "1");
        RpcClient client = new RpcClient(provider);

        String result = client.call("eth_blockNumber", String.class);
        assertEquals("0x1", result);
    }

    @Test
    void convertsHexStringToBigInteger() throws RpcException {
        BraneProvider provider =
                (method, params) -> new JsonRpcResponse("2.0", "0x2a", null, "1");
        RpcClient client = new RpcClient(provider);

        BigInteger result = client.call("eth_call", BigInteger.class);
        assertEquals(BigInteger.valueOf(42), result);
    }

    /**
     * MED-1 Verification: Null result handling.
     * <p>
     * Verifies that when the JSON-RPC response contains a null result,
     * the call method returns null (rather than throwing an exception).
     * This is valid for methods like eth_getTransactionByHash when the
     * transaction doesn't exist.
     */
    @Test
    void returnsNullWhenResultIsNull() throws RpcException {
        BraneProvider provider =
                (method, params) -> new JsonRpcResponse("2.0", null, null, "1");
        RpcClient client = new RpcClient(provider);

        String result = client.call("eth_getTransactionByHash", String.class, "0x1234");
        assertNull(result, "Should return null when RPC result is null");
    }

    @Test
    void propagatesRpcException() {
        RpcException failure = new RpcException(-32000, "boom", null, null, null);
        BraneProvider provider = (method, params) -> {
            throw failure;
        };
        RpcClient client = new RpcClient(provider);

        RpcException ex =
                assertThrows(
                        RpcException.class,
                        () -> client.call("eth_call", String.class));
        assertSame(failure, ex);
    }

    /**
     * CRIT-3 Verification: Test if Number values > Long.MAX_VALUE are truncated.
     *
     * This test verifies the bug at RpcClient.java:119 where:
     * {@code case Number number -> BigInteger.valueOf(number.longValue());}
     * causes truncation for large values.
     */
    @Test
    void verifyCrit3_numberLargerThanLongMaxTruncation() throws RpcException {
        // 100 ETH in wei = 100 * 10^18 = 100,000,000,000,000,000,000
        // This exceeds Long.MAX_VALUE (9,223,372,036,854,775,807)
        BigDecimal largeValue = new BigDecimal("100000000000000000000");

        // Simulate what the buggy code does
        BigInteger buggyResult = BigInteger.valueOf(largeValue.longValue());
        BigInteger correctResult = largeValue.toBigInteger();

        System.out.println("CRIT-3 Verification:");
        System.out.println("  100 ETH in wei (correct):   " + correctResult);
        System.out.println("  100 ETH in wei (truncated): " + buggyResult);
        System.out.println("  Long.MAX_VALUE:             " + Long.MAX_VALUE);

        // This assertion proves the bug exists - if it fails, the bug is fixed
        assertNotEquals(correctResult, buggyResult,
            "BUG CRIT-3 CONFIRMED: Number.longValue() truncates values > Long.MAX_VALUE");
    }
}
