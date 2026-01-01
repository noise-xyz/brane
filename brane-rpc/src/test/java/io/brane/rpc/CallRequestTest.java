package io.brane.rpc;

import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.*;

class CallRequestTest {

    private static final Address TEST_ADDRESS = new Address("0x1234567890123456789012345678901234567890");

    @Test
    void testMinimalCallRequest() {
        CallRequest request = CallRequest.of(TEST_ADDRESS, HexData.EMPTY);
        assertEquals(TEST_ADDRESS, request.to());
        assertNull(request.gasPrice());
        assertNull(request.maxFeePerGas());
    }

    @Test
    void testLegacyGasOnly() {
        CallRequest request = CallRequest.builder()
                .to(TEST_ADDRESS)
                .gasPrice(BigInteger.valueOf(20_000_000_000L))
                .build();
        assertEquals(BigInteger.valueOf(20_000_000_000L), request.gasPrice());
        assertNull(request.maxFeePerGas());
    }

    @Test
    void testEip1559GasOnly() {
        CallRequest request = CallRequest.builder()
                .to(TEST_ADDRESS)
                .maxFeePerGas(BigInteger.valueOf(100_000_000_000L))
                .maxPriorityFeePerGas(BigInteger.valueOf(2_000_000_000L))
                .build();
        assertNull(request.gasPrice());
        assertEquals(BigInteger.valueOf(100_000_000_000L), request.maxFeePerGas());
        assertEquals(BigInteger.valueOf(2_000_000_000L), request.maxPriorityFeePerGas());
    }

    @Test
    void testMutuallyExclusiveGasFieldsThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                CallRequest.builder()
                        .to(TEST_ADDRESS)
                        .gasPrice(BigInteger.valueOf(20_000_000_000L))
                        .maxFeePerGas(BigInteger.valueOf(100_000_000_000L))
                        .build()
        );
        assertTrue(ex.getMessage().contains("Cannot set both legacy gasPrice and EIP-1559"));
    }

    @Test
    void testMutuallyExclusiveGasFieldsWithPriorityFeeThrows() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                CallRequest.builder()
                        .to(TEST_ADDRESS)
                        .gasPrice(BigInteger.valueOf(20_000_000_000L))
                        .maxPriorityFeePerGas(BigInteger.valueOf(2_000_000_000L))
                        .build()
        );
        assertTrue(ex.getMessage().contains("Cannot set both legacy gasPrice and EIP-1559"));
    }

    @Test
    void testToAddressRequired() {
        assertThrows(NullPointerException.class, () ->
                CallRequest.builder().build()
        );
    }

    @Test
    void testToMap() {
        CallRequest request = CallRequest.builder()
                .to(TEST_ADDRESS)
                .from(new Address("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"))
                .data(new HexData("0x12345678"))
                .value(Wei.of(BigInteger.valueOf(1_000_000_000L))) // 1 gwei in wei
                .gas(BigInteger.valueOf(21000))
                .build();

        var map = request.toMap();
        assertEquals(TEST_ADDRESS.value(), map.get("to"));
        assertEquals("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", map.get("from"));
        assertEquals("0x12345678", map.get("data"));
        assertEquals("0x3b9aca00", map.get("value")); // 1 gwei
        assertEquals("0x5208", map.get("gas")); // 21000
    }
}
