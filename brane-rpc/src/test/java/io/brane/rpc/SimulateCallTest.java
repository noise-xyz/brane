package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

class SimulateCallTest {

    private static final Address FROM = new Address("0x1234567890123456789012345678901234567890");
    private static final Address TO = new Address("0x0987654321098765432109876543210987654321");
    private static final HexData DATA = new HexData("0xdeadbeef");
    private static final Wei VALUE = Wei.of(1000L);
    private static final BigInteger GAS = BigInteger.valueOf(21000L);
    private static final BigInteger GAS_PRICE = BigInteger.valueOf(1000000000L);
    private static final BigInteger MAX_FEE = BigInteger.valueOf(2000000000L);
    private static final BigInteger MAX_PRIORITY = BigInteger.valueOf(1500000000L);

    @Test
    void testValidLegacyCall() {
        SimulateCall call = SimulateCall.builder()
                .from(FROM)
                .to(TO)
                .data(DATA)
                .value(VALUE)
                .gas(GAS)
                .gasPrice(GAS_PRICE)
                .build();

        assertNotNull(call);
        assertEquals(FROM, call.from());
        assertEquals(TO, call.to());
        assertEquals(DATA, call.data());
        assertEquals(VALUE, call.value());
        assertEquals(GAS, call.gas());
        assertEquals(GAS_PRICE, call.gasPrice());
        assertNull(call.maxFeePerGas());
        assertNull(call.maxPriorityFeePerGas());
    }

    @Test
    void testValidEip1559Call() {
        SimulateCall call = SimulateCall.builder()
                .to(TO)
                .maxFeePerGas(MAX_FEE)
                .maxPriorityFeePerGas(MAX_PRIORITY)
                .build();

        assertNotNull(call);
        assertEquals(TO, call.to());
        assertEquals(MAX_FEE, call.maxFeePerGas());
        assertEquals(MAX_PRIORITY, call.maxPriorityFeePerGas());
        assertNull(call.gasPrice());
    }

    @Test
    void testRejectsNullTo() {
        assertThrows(NullPointerException.class, () -> SimulateCall.builder().build());
        assertThrows(NullPointerException.class, () -> new SimulateCall(null, null, null, null, null, null, null, null));
    }

    @Test
    void testRejectsMutuallyExclusiveGasFields() {
        // Both gasPrice and maxFeePerGas
        assertThrows(IllegalArgumentException.class, () -> SimulateCall.builder()
                .to(TO)
                .gasPrice(GAS_PRICE)
                .maxFeePerGas(MAX_FEE)
                .build());

        // Both gasPrice and maxPriorityFeePerGas
        assertThrows(IllegalArgumentException.class, () -> SimulateCall.builder()
                .to(TO)
                .gasPrice(GAS_PRICE)
                .maxPriorityFeePerGas(MAX_PRIORITY)
                .build());
    }

    @Test
    void testToMapForJsonRpc() {
        SimulateCall call = SimulateCall.builder()
                .from(FROM)
                .to(TO)
                .data(DATA)
                .value(VALUE)
                .gas(GAS)
                .maxFeePerGas(MAX_FEE)
                .maxPriorityFeePerGas(MAX_PRIORITY)
                .build();

        Map<String, Object> map = call.toMap();

        assertEquals(FROM.value(), map.get("from"));
        assertEquals(TO.value(), map.get("to"));
        assertEquals(DATA.value(), map.get("data"));
        assertEquals("0x3e8", map.get("value")); // 1000 in hex
        assertEquals("0x5208", map.get("gas")); // 21000 in hex
        assertEquals("0x77359400", map.get("maxFeePerGas")); // 2000000000 in hex
        assertEquals("0x59682f00", map.get("maxPriorityFeePerGas")); // 1500000000 in hex
        assertFalse(map.containsKey("gasPrice"));
    }

    @Test
    void testToMapWithLegacyGas() {
        SimulateCall call = SimulateCall.builder()
                .to(TO)
                .gasPrice(GAS_PRICE)
                .build();

        Map<String, Object> map = call.toMap();

        assertEquals(TO.value(), map.get("to"));
        assertEquals("0x3b9aca00", map.get("gasPrice")); // 1000000000 in hex
        assertFalse(map.containsKey("from"));
        assertFalse(map.containsKey("data"));
        assertFalse(map.containsKey("value"));
        assertFalse(map.containsKey("gas"));
        assertFalse(map.containsKey("maxFeePerGas"));
        assertFalse(map.containsKey("maxPriorityFeePerGas"));
    }

    @Test
    void testOfFactoryMethod() {
        SimulateCall call = SimulateCall.of(TO, DATA);

        assertEquals(TO, call.to());
        assertEquals(DATA, call.data());
        assertNull(call.from());
        assertNull(call.value());
        assertNull(call.gas());
        assertNull(call.gasPrice());
        assertNull(call.maxFeePerGas());
        assertNull(call.maxPriorityFeePerGas());
    }

    @Test
    void testOfFactoryMethodToMap() {
        SimulateCall call = SimulateCall.of(TO, DATA);
        Map<String, Object> map = call.toMap();

        assertEquals(TO.value(), map.get("to"));
        assertEquals(DATA.value(), map.get("data"));
        assertFalse(map.containsKey("from"));
        assertFalse(map.containsKey("value"));
        assertFalse(map.containsKey("gas"));
        assertFalse(map.containsKey("gasPrice"));
        assertFalse(map.containsKey("maxFeePerGas"));
        assertFalse(map.containsKey("maxPriorityFeePerGas"));
    }
}
