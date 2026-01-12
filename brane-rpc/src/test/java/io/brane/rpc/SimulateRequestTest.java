// SPDX-License-Identifier: MIT OR Apache-2.0
package io.brane.rpc;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.brane.core.types.Address;
import io.brane.core.types.Wei;

class SimulateRequestTest {

    private static final Address ACCOUNT = new Address("0x1234567890123456789012345678901234567890");
    private static final Address TO = new Address("0x0987654321098765432109876543210987654321");

    @Test
    void testValidRequestCreation() {
        SimulateCall call = SimulateCall.builder().to(TO).build();
        AccountOverride override = AccountOverride.builder().balance(Wei.of(100)).build();

        SimulateRequest request = SimulateRequest.builder()
                .account(ACCOUNT)
                .call(call)
                .blockTag(BlockTag.LATEST)
                .stateOverride(TO, override)
                .traceAssetChanges(true)
                .traceTransfers(true)
                .validation(false)
                .build();

        assertNotNull(request);
        assertEquals(ACCOUNT, request.account());
        assertEquals(1, request.calls().size());
        assertEquals(call, request.calls().get(0));
        assertEquals(BlockTag.LATEST, request.blockTag());
        assertNotNull(request.stateOverrides());
        assertEquals(override, request.stateOverrides().get(TO));
        assertTrue(request.traceAssetChanges());
        assertTrue(request.traceTransfers());
        assertFalse(request.validation());
        assertFalse(request.fetchTokenMetadata());
    }

    @Test
    void testFetchTokenMetadataThrowsUnsupportedOperationException() {
        SimulateCall call = SimulateCall.builder().to(TO).build();

        var ex = assertThrows(UnsupportedOperationException.class, () ->
                SimulateRequest.builder()
                        .call(call)
                        .fetchTokenMetadata(true)
                        .build());

        assertTrue(ex.getMessage().contains("fetchTokenMetadata is not yet implemented"));
    }

    @Test
    void testRejectsNullCalls() {
        assertThrows(NullPointerException.class, () -> new SimulateRequest(null, null, null, null, false, false, false, false));
    }

    @Test
    void testRejectsEmptyCalls() {
        var ex1 = assertThrows(IllegalStateException.class, () -> SimulateRequest.builder().calls(List.of()).build());
        assertTrue(ex1.getMessage().contains("requires at least one call"));
        assertTrue(ex1.getMessage().contains(".call(SimulateCall)"));

        var ex2 = assertThrows(IllegalStateException.class, () -> SimulateRequest.builder().build());
        assertTrue(ex2.getMessage().contains("requires at least one call"));
    }

    @Test
    void testDefensiveCopies() {
        List<SimulateCall> mutableCalls = new ArrayList<>();
        mutableCalls.add(SimulateCall.builder().to(TO).build());

        SimulateRequest request = SimulateRequest.builder()
                .calls(mutableCalls)
                .build();

        // Modify original list
        mutableCalls.add(SimulateCall.builder().to(ACCOUNT).build());

        // Verify request didn't change
        assertEquals(1, request.calls().size());
    }

    @Test
    void testDefaults() {
        SimulateCall call = SimulateCall.builder().to(TO).build();
        SimulateRequest request = SimulateRequest.builder()
                .call(call)
                .build();

        assertNull(request.account());
        assertNull(request.blockTag()); // Default behavior is handled in builder/executor
        assertNull(request.stateOverrides());
        assertFalse(request.traceAssetChanges());
        assertFalse(request.traceTransfers());
        assertTrue(request.validation()); // Default is true for safety
        assertFalse(request.fetchTokenMetadata());
    }
}
