// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Address;
import io.brane.core.types.HexData;
import io.brane.core.types.Wei;

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

    /**
     * MED-2 Verification: Test if toMap() returns a mutable map.
     *
     * This test verifies that the map returned by toMap() can be modified,
     * which is a bug because it allows callers to corrupt the request data.
     */
    @Test
    void verifyMed2_toMapReturnsMutableMap() {
        CallRequest request = CallRequest.builder()
                .to(TEST_ADDRESS)
                .build();

        var map = request.toMap();
        int originalSize = map.size();

        // Try to modify the map - if this succeeds, the bug exists
        boolean canModify = false;
        try {
            map.put("injected", "value");
            canModify = true;
            System.out.println("MED-2 Verification:");
            System.out.println("  Map was modified: " + map);
            System.out.println("  BUG CONFIRMED: toMap() returns mutable map");
        } catch (UnsupportedOperationException e) {
            System.out.println("MED-2 Verification:");
            System.out.println("  Map is immutable - bug is FIXED");
        }

        // This assertion proves the bug exists - if it fails, the bug is fixed
        assertTrue(canModify,
            "BUG MED-2 CONFIRMED: toMap() returns a mutable map that callers can modify");
    }
}
